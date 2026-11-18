package com.neonvpn.app

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiEvent {
    data class Toast(val text: String) : UiEvent()
    data class RequestVpnPermission(val intent: Intent) : UiEvent()
    data class ScanMoreQr(val prompt: String) : UiEvent()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    private val _tunnels = MutableStateFlow(TunnelStore.list(app))
    val tunnels: StateFlow<List<TunnelEntry>> = _tunnels

    private val _selectedId = MutableStateFlow(TunnelStore.selected(app))
    val selectedId: StateFlow<String?> = _selectedId

    private val _traffic = MutableStateFlow(0L to 0L)
    val traffic: StateFlow<Pair<Long, Long>> = _traffic

    val phase = VpnController.phase
    val activeId = VpnController.activeId
    val since = VpnController.since

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _traffic.value = if (phase.value == Phase.ON) VpnController.traffic() ?: (0L to 0L) else (0L to 0L)
                delay(1000)
            }
        }
    }

    private fun toast(text: String) {
        events.trySend(UiEvent.Toast(text))
    }

    fun onPowerClick() {
        when (phase.value) {
            Phase.ON -> viewModelScope.launch { VpnController.disconnect()?.let(::toast) }
            Phase.OFF -> connectSelected()
            else -> Unit
        }
    }

    private fun connectSelected() {
        val entry = _tunnels.value.firstOrNull { it.id == _selectedId.value } ?: _tunnels.value.firstOrNull()
        if (entry == null) {
            toast("Сначала добавьте сервер")
            return
        }
        val permission = VpnService.prepare(ctx)
        if (permission != null) {
            events.trySend(UiEvent.RequestVpnPermission(permission))
            return
        }
        viewModelScope.launch { VpnController.connect(ctx, entry)?.let(::toast) }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) connectSelected() else toast("Без разрешения VPN не запустится")
    }

    fun select(id: String) {
        _selectedId.value = id
        TunnelStore.setSelected(ctx, id)
        if (phase.value == Phase.ON && activeId.value != id) {
            connectSelected()
        }
    }

    fun delete(id: String) {
        if (activeId.value == id) {
            toast("Сначала отключитесь от этого сервера")
            return
        }
        TunnelStore.delete(ctx, id)
        refresh()
    }

    private fun refresh() {
        _tunnels.value = TunnelStore.list(ctx)
        _selectedId.value = TunnelStore.selected(ctx)
    }

    fun importText(text: String, fallbackName: String) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.Default) { ConfigImporter.parse(text, fallbackName) }) {
                is ConfigImporter.Result.Ok -> {
                    try {
                        val entry = withContext(Dispatchers.IO) { TunnelStore.add(ctx, r.name, r.conf) }
                        refresh()
                        select(entry.id)
                        toast("Сервер «${entry.name}» добавлен")
                    } catch (e: Exception) {
                        toast(e.message ?: "Не удалось сохранить конфиг")
                    }
                }
                is ConfigImporter.Result.NeedMoreQr ->
                    events.trySend(UiEvent.ScanMoreQr("Отсканировано ${r.received} из ${r.total}. Наведите на следующий QR-код"))
                is ConfigImporter.Result.Fail -> toast(r.message)
            }
        }
    }

    fun startQrScan() {
        ConfigImporter.resetQr()
    }
}

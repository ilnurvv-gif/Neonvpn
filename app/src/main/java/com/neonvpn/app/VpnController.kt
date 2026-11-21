package com.neonvpn.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.Tunnel

enum class Phase { OFF, CONNECTING, ON, DISCONNECTING }

object VpnController {

    private val _phase = MutableStateFlow(Phase.OFF)
    val phase: StateFlow<Phase> = _phase

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    private val _since = MutableStateFlow<Long?>(null)
    val since: StateFlow<Long?> = _since

    private var current: AppTunnel? = null

    private class AppTunnel(val entryId: String) : Tunnel {
        override fun getName(): String = "NeonVPN"
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && current === this && _phase.value == Phase.ON) {
                markOff()
            }
        }
    }

    private fun markOff() {
        current = null
        _activeId.value = null
        _since.value = null
        _phase.value = Phase.OFF
    }

    /** Returns null on success or an error message. */
    suspend fun connect(ctx: Context, entry: TunnelEntry): String? = withContext(Dispatchers.IO) {
        val backend = App.instance.backend
        _phase.value = Phase.CONNECTING
        try {
            current?.let { backend.setState(it, Tunnel.State.DOWN, null) }
            current = null
            val config = TunnelStore.loadConfig(ctx, entry.id)
            val tunnel = AppTunnel(entry.id)
            val state = backend.setState(tunnel, Tunnel.State.UP, config)
            if (state == Tunnel.State.UP) {
                current = tunnel
                _activeId.value = entry.id
                _since.value = System.currentTimeMillis()
                _phase.value = Phase.ON
                null
            } else {
                markOff()
                "Не удалось подключиться"
            }
        } catch (e: BackendException) {
            markOff()
            when (e.reason) {
                BackendException.Reason.VPN_NOT_AUTHORIZED -> "Нет разрешения на VPN"
                else -> "Ошибка подключения: ${e.reason}"
            }
        } catch (e: Exception) {
            markOff()
            "Ошибка подключения: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun disconnect(): String? = withContext(Dispatchers.IO) {
        val tunnel = current ?: run { markOff(); return@withContext null }
        _phase.value = Phase.DISCONNECTING
        try {
            App.instance.backend.setState(tunnel, Tunnel.State.DOWN, null)
            markOff()
            null
        } catch (e: Exception) {
            markOff()
            "Ошибка отключения: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Returns rx/tx bytes for the active tunnel, or null if nothing is running. */
    suspend fun traffic(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val tunnel = current ?: return@withContext null
        try {
            val stats = App.instance.backend.getStatistics(tunnel)
            stats.totalRx() to stats.totalTx()
        } catch (e: Exception) {
            null
        }
    }
}

package com.neonvpn.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.neonvpn.app.ui.MainScreen
import com.neonvpn.app.ui.NeonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onVpnPermissionResult(it.resultCode == Activity.RESULT_OK)
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri)
    }

    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) vm.importText(text, "QR-сервер")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeonTheme {
                val tunnels by vm.tunnels.collectAsState()
                val selectedId by vm.selectedId.collectAsState()
                val phase by vm.phase.collectAsState()
                val activeId by vm.activeId.collectAsState()
                val since by vm.since.collectAsState()
                val traffic by vm.traffic.collectAsState()

                LaunchedEffect(Unit) {
                    vm.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.Toast ->
                                Toast.makeText(this@MainActivity, event.text, Toast.LENGTH_LONG).show()
                            is UiEvent.RequestVpnPermission -> vpnPermission.launch(event.intent)
                            is UiEvent.ScanMoreQr -> launchScanner(event.prompt)
                        }
                    }
                }

                MainScreen(
                    tunnels = tunnels,
                    selectedId = selectedId,
                    activeId = activeId,
                    phase = phase,
                    since = since,
                    rx = traffic.first,
                    tx = traffic.second,
                    onPower = vm::onPowerClick,
                    onSelect = vm::select,
                    onDelete = vm::delete,
                    onImportKey = { vm.importText(it, "Мой сервер") },
                    onImportFile = { openFile.launch(arrayOf("*/*")) },
                    onScanQr = {
                        vm.startQrScan()
                        launchScanner("Наведите камеру на QR-код")
                    },
                )
            }
        }
    }

    private fun launchScanner(prompt: String) {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(prompt)
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanQr.launch(options)
    }

    private fun importFile(uri: Uri) {
        lifecycleScope.launch {
            val (name, text) = withContext(Dispatchers.IO) {
                val n = displayName(uri)?.substringBeforeLast('.') ?: "Сервер"
                val t = try {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } catch (e: Exception) {
                    null
                }
                n to t
            }
            if (text == null) {
                Toast.makeText(this@MainActivity, "Не удалось прочитать файл", Toast.LENGTH_LONG).show()
            } else {
                vm.importText(text, name)
            }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}

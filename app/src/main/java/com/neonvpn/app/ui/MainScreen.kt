package com.neonvpn.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonvpn.app.Phase
import com.neonvpn.app.TunnelEntry
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    tunnels: List<TunnelEntry>,
    selectedId: String?,
    activeId: String?,
    phase: Phase,
    since: Long?,
    rx: Long,
    tx: Long,
    onPower: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImportKey: (String) -> Unit,
    onImportFile: () -> Unit,
    onScanQr: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TunnelEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F1A), NeonBg)))
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "NEON VPN",
            color = NeonCyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp,
        )
        Text(
            text = "AmneziaWG · WireGuard",
            color = NeonMuted,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(28.dp))
        PowerButton(phase = phase, onClick = onPower)
        Spacer(Modifier.height(16.dp))
        StatusBlock(phase, since, rx, tx, tunnels.firstOrNull { it.id == (activeId ?: selectedId) }?.name)
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Серверы", color = NeonText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { showAdd = true },
                border = BorderStroke(1.dp, NeonCyan),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = NeonCyan)
                Spacer(Modifier.width(6.dp))
                Text("Добавить", color = NeonCyan)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (tunnels.isEmpty()) {
            Text(
                text = "Пока нет серверов.\nДобавьте ключ vpn://, файл .conf или QR-код.",
                color = NeonMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tunnels, key = { it.id }) { t ->
                    ServerRow(
                        entry = t,
                        selected = t.id == selectedId,
                        active = t.id == activeId,
                        onClick = { onSelect(t.id) },
                        onDelete = { deleteTarget = t },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = NeonSurface,
            title = { Text("Добавить сервер", color = NeonText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonMenuButton("Вставить ключ vpn://") { showAdd = false; showKey = true }
                    NeonMenuButton("Открыть файл .conf") { showAdd = false; onImportFile() }
                    NeonMenuButton("Сканировать QR-код") { showAdd = false; onScanQr() }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена", color = NeonMuted) } },
        )
    }

    if (showKey) {
        KeyDialog(
            onDismiss = { showKey = false },
            onConfirm = { showKey = false; onImportKey(it) },
        )
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = NeonSurface,
            title = { Text("Удалить «${t.name}»?", color = NeonText) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDelete(t.id) }) { Text("Удалить", color = NeonMagenta) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена", color = NeonMuted) } },
        )
    }
}

@Composable
private fun NeonMenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeonSurface2, contentColor = NeonCyan),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeyDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember {
        val clip = clipboard.getText()?.text.orEmpty().trim()
        mutableStateOf(if (clip.startsWith("vpn://") || clip.contains("[Interface]")) clip else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        title = { Text("Ключ или конфиг", color = NeonText) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("vpn://...", color = NeonMuted) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = NeonMuted,
                    cursorColor = NeonCyan,
                    focusedTextColor = NeonText,
                    unfocusedTextColor = NeonText,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Добавить", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = NeonMuted) } },
    )
}

@Composable
private fun ServerRow(entry: TunnelEntry, selected: Boolean, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val borderColor = when {
        active -> NeonCyan
        selected -> NeonCyan.copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NeonSurface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) NeonCyan else if (selected) NeonCyan.copy(alpha = 0.4f) else NeonMuted.copy(alpha = 0.4f))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = NeonText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (active) "Подключено" else if (selected) "Выбран" else "Нажмите, чтобы выбрать",
                color = if (active) NeonCyan else NeonMuted,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = NeonMuted)
        }
    }
}

@Composable
private fun StatusBlock(phase: Phase, since: Long?, rx: Long, tx: Long, serverName: String?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) {
        while (phase == Phase.ON) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val (label, color) = when (phase) {
        Phase.OFF -> "Отключено" to NeonMuted
        Phase.CONNECTING -> "Подключение…" to NeonMagenta
        Phase.ON -> "Защищено" to NeonCyan
        Phase.DISCONNECTING -> "Отключение…" to NeonMagenta
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(serverName ?: "Сервер не выбран", color = NeonMuted, fontSize = 13.sp)
        if (phase == Phase.ON && since != null) {
            Spacer(Modifier.height(10.dp))
            Text(formatDuration(now - since), color = NeonText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("↓ ${formatBytes(rx)}", color = NeonCyan, fontSize = 13.sp)
                Text("↑ ${formatBytes(tx)}", color = NeonMagenta, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PowerButton(phase: Phase, onClick: () -> Unit) {
    val on = phase == Phase.ON
    val busy = phase == Phase.CONNECTING || phase == Phase.DISCONNECTING
    val transition = rememberInfiniteTransition(label = "power")
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    val color by animateColorAsState(
        targetValue = when {
            on -> NeonCyan
            busy -> NeonMagenta
            else -> NeonMuted
        },
        label = "color",
    )

    Box(
        modifier = Modifier
            .size(230.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !busy,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val glowR = if (on) r * pulse else r * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = if (on) 0.40f else 0.12f), Color.Transparent),
                    center = center,
                    radius = glowR,
                ),
                radius = glowR,
            )
            drawCircle(color = NeonSurface, radius = r * 0.62f)

            val ringR = r * 0.68f
            if (busy) {
                drawCircle(color = color.copy(alpha = 0.15f), radius = ringR, style = Stroke(5.dp.toPx()))
                drawArc(
                    color = color,
                    startAngle = spin,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = Size(ringR * 2, ringR * 2),
                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                )
            } else {
                drawCircle(color = color.copy(alpha = if (on) 1f else 0.5f), radius = ringR, style = Stroke(3.dp.toPx()))
            }

            val ir = r * 0.2f
            val sw = 7.dp.toPx()
            drawArc(
                color = color,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - ir, center.y - ir),
                size = Size(ir * 2, ir * 2),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - ir * 1.3f),
                end = Offset(center.x, center.y - ir * 0.15f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f ГБ".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f МБ".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f КБ".format(b / (1L shl 10).toDouble())
    else -> "$b Б"
}

package com.neonvpn.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonBg = Color(0xFF07080D)
val NeonSurface = Color(0xFF10131C)
val NeonSurface2 = Color(0xFF171B27)
val NeonCyan = Color(0xFF00F0FF)
val NeonMagenta = Color(0xFFFF2BD6)
val NeonText = Color(0xFFE6F7FF)
val NeonMuted = Color(0xFF6B7A90)

private val scheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonBg,
    secondary = NeonMagenta,
    onSecondary = NeonBg,
    background = NeonBg,
    onBackground = NeonText,
    surface = NeonSurface,
    onSurface = NeonText,
    surfaceVariant = NeonSurface2,
    onSurfaceVariant = NeonMuted,
    outline = NeonMuted,
)

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

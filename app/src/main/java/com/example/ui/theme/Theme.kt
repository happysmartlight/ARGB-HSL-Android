package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = NeonMagenta,
    tertiary = CyberGreen,
    background = DarkBackground,
    // Popups (dialogs, dropdown menus) and cards use `surface`/`surfaceVariant` as their
    // background — these MUST be opaque, otherwise content behind a popup bleeds through and
    // text overlaps. Intentional "glass" cards apply their own .copy(alpha=…) on top of this.
    surface = SynthSurface,
    surfaceContainer = SynthSurface,
    surfaceContainerHigh = Color(0xFF20203A),
    surfaceContainerHighest = Color(0xFF24243E),
    surfaceContainerLow = Color(0xFF16162A),
    surfaceContainerLowest = Color(0xFF101020),
    onPrimary = Color(0xFF001122),
    onSecondary = Color.White,
    onBackground = LightSlate,
    onSurface = LightIce,
    surfaceVariant = Color(0xFF24243E),
    onSurfaceVariant = LightSlate,
    outline = MutedSlate.copy(alpha = 0.4f)
)

private val CyberLightColorScheme = lightColorScheme(
    primary = Color(0xFF008299),
    secondary = Color(0xFFB0005C),
    tertiary = Color(0xFF1E88E5),
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF94A3B8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default since it looks incredible for smart light controls
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

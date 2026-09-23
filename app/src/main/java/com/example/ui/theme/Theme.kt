package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlueLight,
    onPrimary = Color.White,
    primaryContainer = AccentBluePrimary,
    onPrimaryContainer = Color.White,
    secondary = AccentCyan,
    onSecondary = Color.Black,
    background = Color(0xFF0B132B),
    onBackground = TextPrimary,
    surface = Color(0xFF1C2541),
    onSurface = TextPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBluePrimary,
    onPrimary = Color.White,
    primaryContainer = AccentBlueLight,
    onPrimaryContainer = Color.White,
    secondary = AccentCyan,
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    onBackground = TextPrimary,
    surface = Color(0xFF1E293B),
    onSurface = TextPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

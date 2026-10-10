package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppTheme = staticCompositionLocalOf { ThemePalette.default }

private fun buildDarkColorScheme(palette: ThemePalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryLight,
    onPrimaryContainer = Color.White,
    secondary = palette.secondary,
    onSecondary = Color.Black,
    background = palette.backgroundDark,
    onBackground = TextPrimary,
    surface = Color(0xFF1C2541),
    onSurface = TextPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

private fun buildLightColorScheme(palette: ThemePalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.secondary,
    onPrimaryContainer = Color.White,
    secondary = palette.secondary,
    onSecondary = Color.White,
    background = palette.backgroundDark,
    onBackground = TextPrimary,
    surface = Color(0xFF1E293B),
    onSurface = TextPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

@Composable
fun MyApplicationTheme(
    themePalette: ThemePalette = ThemePalette.default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) buildDarkColorScheme(themePalette) else buildLightColorScheme(themePalette)

    CompositionLocalProvider(LocalAppTheme provides themePalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

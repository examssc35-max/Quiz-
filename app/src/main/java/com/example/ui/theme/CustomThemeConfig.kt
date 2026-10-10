package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class ThemePalette(
    val id: String,
    val name: String,
    val bengaliName: String,
    val primary: Color,
    val primaryLight: Color,
    val secondary: Color,
    val backgroundDark: Color,
    val backgroundOverlayColors: List<Color>,
    val primaryGradient: Brush,
    val accentGradient: Brush,
    val borderBrush: Brush
) {
    companion object {
        fun create(
            id: String,
            name: String,
            bengaliName: String,
            primary: Color,
            secondary: Color,
            backgroundDark: Color = Color(0xFF070E1E)
        ): ThemePalette {
            val primaryLight = primary.copy(alpha = 0.85f)
            val overlayColors = listOf(
                backgroundDark,
                backgroundDark.copy(alpha = 0.92f),
                Color(0xFF030712)
            )
            return ThemePalette(
                id = id,
                name = name,
                bengaliName = bengaliName,
                primary = primary,
                primaryLight = primaryLight,
                secondary = secondary,
                backgroundDark = backgroundDark,
                backgroundOverlayColors = overlayColors,
                primaryGradient = Brush.horizontalGradient(listOf(primary, secondary)),
                accentGradient = Brush.horizontalGradient(listOf(secondary, primary)),
                borderBrush = Brush.verticalGradient(
                    listOf(
                        primary.copy(alpha = 0.5f),
                        secondary.copy(alpha = 0.2f)
                    )
                )
            )
        }

        val default: ThemePalette = ThemePreset.DEEP_MIDNIGHT.palette
    }
}

enum class ThemePreset(
    val id: String,
    val title: String,
    val bengaliTitle: String,
    val primaryHex: String,
    val secondaryHex: String,
    val bgHex: String
) {
    DEEP_MIDNIGHT(
        id = "deep_midnight",
        title = "Deep Midnight",
        bengaliTitle = "ডিফল্ট মিডনাইট",
        primaryHex = "#3B82F6",
        secondaryHex = "#06B6D4",
        bgHex = "#070E1E"
    ),
    EMERALD_FOREST(
        id = "emerald_forest",
        title = "Emerald Forest",
        bengaliTitle = "পান্না সবুজ",
        primaryHex = "#10B981",
        secondaryHex = "#14B8A6",
        bgHex = "#021A13"
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        title = "Cyberpunk Neon",
        bengaliTitle = "সাইবার নিয়ন",
        primaryHex = "#EC4899",
        secondaryHex = "#8B5CF6",
        bgHex = "#190623"
    ),
    SUNSET_AMBER(
        id = "sunset_amber",
        title = "Sunset Amber",
        bengaliTitle = "সূর্যাস্ত সোনালী",
        primaryHex = "#F97316",
        secondaryHex = "#FBBF24",
        bgHex = "#1E0C04"
    ),
    ROYAL_AMETHYST(
        id = "royal_amethyst",
        title = "Royal Amethyst",
        bengaliTitle = "রাজকীয় ভায়োলেট",
        primaryHex = "#8B5CF6",
        secondaryHex = "#C084FC",
        bgHex = "#130825"
    ),
    OCEAN_AQUA(
        id = "ocean_aqua",
        title = "Ocean Aqua",
        bengaliTitle = "মহাসাগর নীল",
        primaryHex = "#0284C7",
        secondaryHex = "#2DD4BF",
        bgHex = "#031626"
    ),
    CRIMSON_FIRE(
        id = "crimson_fire",
        title = "Crimson Fire",
        bengaliTitle = "অগ্নি লাল",
        primaryHex = "#E11D48",
        secondaryHex = "#FB7185",
        bgHex = "#1E050D"
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Theme",
        bengaliTitle = "নিজের তৈরি থিম",
        primaryHex = "#3B82F6",
        secondaryHex = "#06B6D4",
        bgHex = "#070E1E"
    );

    val palette: ThemePalette
        get() = ThemePalette.create(
            id = id,
            name = title,
            bengaliName = bengaliTitle,
            primary = parseHexColor(primaryHex, Color(0xFF3B82F6)),
            secondary = parseHexColor(secondaryHex, Color(0xFF06B6D4)),
            backgroundDark = parseHexColor(bgHex, Color(0xFF070E1E))
        )

    companion object {
        fun fromId(id: String): ThemePreset {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEEP_MIDNIGHT
        }
    }
}

val SWATCH_COLORS = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Emerald
    Color(0xFF14B8A6), // Teal
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFFF97316), // Orange
    Color(0xFFF59E0B), // Amber
    Color(0xFFEF4444), // Red
    Color(0xFF6366F1), // Indigo
    Color(0xFF84CC16), // Lime
    Color(0xFFA855F7), // Purple
    Color(0xFF38BDF8), // Electric Sky
    Color(0xFF34D399), // Mint
    Color(0xFFFB7185), // Coral
    Color(0xFFD946EF), // Fuchsia
    Color(0xFFEAB308), // Gold
    Color(0xFF14B8A6)  // Turquoise
)

data class BackgroundPreset(val hex: String, val name: String, val bengaliName: String)

val BACKGROUND_PRESETS = listOf(
    BackgroundPreset("#070E1E", "Midnight Slate", "মিডনাইট স্লেট"),
    BackgroundPreset("#030712", "AMOLED Black", "পিওর ব্ল্যাক"),
    BackgroundPreset("#11071F", "Deep Violet", "গভীর ভায়োলেট"),
    BackgroundPreset("#02140D", "Emerald Dark", "গাঢ় সবুজ"),
    BackgroundPreset("#1A050D", "Crimson Dark", "গাঢ় লাল")
)

fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> {
                val rgb = clean.toLong(16)
                val r = ((rgb shr 16) and 0xFF).toInt()
                val g = ((rgb shr 8) and 0xFF).toInt()
                val b = (rgb and 0xFF).toInt()
                Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
            }
            8 -> {
                val argb = clean.toLong(16)
                val a = ((argb shr 24) and 0xFF).toInt()
                val r = ((argb shr 16) and 0xFF).toInt()
                val g = ((argb shr 8) and 0xFF).toInt()
                val b = (argb and 0xFF).toInt()
                Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
            }
            else -> fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

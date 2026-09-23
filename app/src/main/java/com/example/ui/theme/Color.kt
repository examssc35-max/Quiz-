package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Glassmorphic Base Colors
val GlassBackground = Color(0x26FFFFFF)
val GlassBackgroundLight = Color(0x38FFFFFF)
val GlassBackgroundUltra = Color(0x15FFFFFF)
val GlassCardSurface = Color(0x28FFFFFF)
val GlassBorder = Color(0x38FFFFFF)
val GlassBorderActive = Color(0x803B82F6)

// Accent Colors
val AccentBluePrimary = Color(0xFF2563EB)
val AccentBlueLight = Color(0xFF3B82F6)
val AccentCyan = Color(0xFF06B6D4)
val AccentTeal = Color(0xFF14B8A6)

// Functional Colors
val CorrectGreen = Color(0xFF10B981)
val CorrectGreenBg = Color(0x3310B981)
val CorrectGreenBorder = Color(0x8010B981)

val WrongRed = Color(0xFFEF4444)
val WrongRedBg = Color(0x33EF4444)
val WrongRedBorder = Color(0x80EF4444)

val WarningYellow = Color(0xFFF59E0B)

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xCCF1F5F9)
val TextMuted = Color(0x88CBD5E1)

// Gradients
val PrimaryButtonGradient = Brush.horizontalGradient(
    listOf(AccentBluePrimary, AccentBlueLight)
)

val CyanGradient = Brush.horizontalGradient(
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
)

val GlassBorderBrush = Brush.verticalGradient(
    listOf(Color(0x55FFFFFF), Color(0x15FFFFFF))
)

val GlassBorderActiveBrush = Brush.verticalGradient(
    listOf(Color(0xAA60A5FA), Color(0x442563EB))
)

val GlassBorderCorrectBrush = Brush.verticalGradient(
    listOf(Color(0xAA34D399), Color(0x44059669))
)

val GlassBorderWrongBrush = Brush.verticalGradient(
    listOf(Color(0xAAF87171), Color(0x44DC2626))
)

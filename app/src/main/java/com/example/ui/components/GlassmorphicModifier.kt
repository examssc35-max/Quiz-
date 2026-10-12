package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.LocalAppTheme

/**
 * Visual intensity preset for glassmorphic surfaces.
 */
enum class GlassIntensity {
    /** Ultra sheer, delicate glass for nested items, chips, and subtle list cards */
    SUBTLE,

    /** Standard signature frosted glass for primary cards, containers, and sections */
    REGULAR,

    /** Deep opaque frosted glass for modals, bottom sheets, navigation bars, and dropdowns */
    ELEVATED,

    /** Theme-tinted glowing glass for active items, hero banners, and celebratory cards */
    ACCENT
}

/**
 * Reusable, signature Glassmorphic Component Modifier.
 *
 * Applies a luxurious frosted-glass aesthetic featuring:
 * 1. Multi-layered translucent surface with adaptive dynamic theme tint.
 * 2. Specular rim sheen along the top edge simulating realistic physical light refraction.
 * 3. Directional specular border gradient that catches ambient light.
 * 4. Ambient atmospheric depth glow.
 * 5. Optional ripple feedback with accessible touch targets for interactive elements.
 *
 * @param shape Shape of the glass surface (defaults to modern 22.dp rounded rectangle).
 * @param intensity Glass opacity and depth preset ([GlassIntensity.REGULAR] by default).
 * @param borderWidth Thickness of the glass edge highlight (1.dp default).
 * @param borderBrush Custom border brush. If null, automatically computes a physical specular gradient.
 * @param backgroundColor Custom surface color override. If null, adapts to [intensity] and [LocalAppTheme].
 * @param rimLight Whether to render the signature top specular light sheen.
 * @param glowColor Optional atmospheric aura color radiating behind the component.
 * @param glowRadius Radius of the atmospheric glow blur (0.dp for no aura).
 * @param onClick Optional tap callback with accessible ripple feedback.
 */
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(22.dp),
    intensity: GlassIntensity = GlassIntensity.REGULAR,
    borderWidth: Dp = 1.dp,
    borderBrush: Brush? = null,
    backgroundColor: Color? = null,
    rimLight: Boolean = true,
    glowColor: Color? = null,
    glowRadius: Dp = 0.dp,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val theme = LocalAppTheme.current

    // 1. Resolve surface base color based on intensity & theme
    val resolvedBgColor = backgroundColor ?: when (intensity) {
        GlassIntensity.SUBTLE -> Color(0x1A1E293B)
        GlassIntensity.REGULAR -> Color(0x351E293B)
        GlassIntensity.ELEVATED -> Color(0xF00F172A)
        GlassIntensity.ACCENT -> theme.primary.copy(alpha = 0.22f)
    }

    // 2. Resolve specular light border
    val resolvedBorderBrush = borderBrush ?: when (intensity) {
        GlassIntensity.ACCENT -> Brush.linearGradient(
            listOf(
                Color(0x80FFFFFF),
                theme.primary.copy(alpha = 0.7f),
                theme.secondary.copy(alpha = 0.4f),
                Color(0x20FFFFFF)
            )
        )
        GlassIntensity.ELEVATED -> Brush.verticalGradient(
            listOf(Color(0x60FFFFFF), Color(0x20FFFFFF), Color(0x10FFFFFF))
        )
        GlassIntensity.SUBTLE -> Brush.verticalGradient(
            listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
        )
        GlassIntensity.REGULAR -> GlassBorderBrush
    }

    // 3. Resolve ambient glow color
    val resolvedGlowColor = glowColor ?: if (intensity == GlassIntensity.ACCENT) {
        theme.primary.copy(alpha = 0.35f)
    } else null

    // Interaction source for ripple
    val interactionSource = remember { MutableInteractionSource() }

    this
        // Draw ambient glow behind shape if enabled
        .then(
            if (resolvedGlowColor != null && glowRadius > 0.dp) {
                Modifier.drawBehind {
                    drawCircle(
                        color = resolvedGlowColor,
                        radius = size.maxDimension * 0.6f + glowRadius.toPx(),
                        center = center
                    )
                }
            } else Modifier
        )
        // Clip to specified shape
        .clip(shape)
        // Base glass tinted surface
        .background(resolvedBgColor)
        // Top specular light sheen & atmospheric gradient
        .then(
            if (rimLight) {
                Modifier.drawBehind {
                    // Top rim specular highlight simulating light hitting glass from above
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (intensity == GlassIntensity.SUBTLE) 0.08f else 0.16f),
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.45f
                        )
                    )

                    // Subtle radial theme refraction glow at the top center
                    val themeAuraAlpha = if (intensity == GlassIntensity.ACCENT) 0.18f else 0.06f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                theme.primary.copy(alpha = themeAuraAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            } else Modifier
        )
        // Specular border reflection
        .border(
            border = BorderStroke(borderWidth, resolvedBorderBrush),
            shape = shape
        )
        // Interactive clickable touch feedback with accessible ripple
        .then(
            if (onClick != null) {
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = theme.primaryLight),
                        onClick = onClick
                    )
            } else Modifier
        )
}

/**
 * Convenience modifier for glassmorphic pill badges and chips.
 */
fun Modifier.glassmorphicPill(
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier = glassmorphic(
    shape = CircleShape,
    intensity = if (isSelected) GlassIntensity.ACCENT else GlassIntensity.SUBTLE,
    borderWidth = if (isSelected) 1.5.dp else 1.dp,
    rimLight = true,
    onClick = onClick
)

/**
 * Convenience modifier for prominent glassmorphic cards.
 */
fun Modifier.glassmorphicCard(
    shape: Shape = RoundedCornerShape(24.dp),
    glow: Boolean = false,
    onClick: (() -> Unit)? = null
): Modifier = glassmorphic(
    shape = shape,
    intensity = GlassIntensity.REGULAR,
    borderWidth = 1.dp,
    rimLight = true,
    glowRadius = if (glow) 12.dp else 0.dp,
    onClick = onClick
)

/**
 * Convenience modifier for modal dialogs and elevated floating surfaces.
 */
fun Modifier.glassmorphicElevated(
    shape: Shape = RoundedCornerShape(26.dp)
): Modifier = glassmorphic(
    shape = shape,
    intensity = GlassIntensity.ELEVATED,
    borderWidth = 1.dp,
    rimLight = true
)

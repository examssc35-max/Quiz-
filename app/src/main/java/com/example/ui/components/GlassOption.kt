package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.CorrectGreenBorder
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedBg
import com.example.ui.theme.WrongRedBorder

enum class OptionVisualState {
    DEFAULT,
    SELECTED_BLUE,
    CORRECT_GREEN,
    WRONG_RED,
    REVEALED_CORRECT
}

@Composable
fun GlassOption(
    letter: String,
    text: String,
    state: OptionVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusText: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 150-200ms scale animation on tap: 0.975 -> 1.0 (subtle responsive press feedback)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.975f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "option_scale"
    )

    val shape = RoundedCornerShape(20.dp)

    // Persistent background color based on answer state + temporary press illumination
    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            OptionVisualState.SELECTED_BLUE -> Color(0x3D2563EB) // Distinct deep indigo/blue glass
            OptionVisualState.CORRECT_GREEN -> CorrectGreenBg
            OptionVisualState.WRONG_RED -> WrongRedBg
            OptionVisualState.REVEALED_CORRECT -> CorrectGreenBg.copy(alpha = 0.35f)
            OptionVisualState.DEFAULT -> if (isPressed && enabled) Color(0x28FFFFFF) else GlassCardSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "option_bg"
    )

    // Border transition: persistent green/red/blue, or subtle glow on tap
    val borderStroke = when (state) {
        OptionVisualState.SELECTED_BLUE -> BorderStroke(
            2.dp,
            Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))
        )
        OptionVisualState.CORRECT_GREEN -> BorderStroke(2.dp, CorrectGreenBorder)
        OptionVisualState.WRONG_RED -> BorderStroke(2.dp, WrongRedBorder)
        OptionVisualState.REVEALED_CORRECT -> BorderStroke(2.dp, CorrectGreenBorder)
        OptionVisualState.DEFAULT -> BorderStroke(
            if (isPressed && enabled) 1.5.dp else 1.dp,
            if (isPressed && enabled) Brush.linearGradient(listOf(Color(0x88FFFFFF), Color(0x55FFFFFF))) else GlassBorderBrush
        )
    }

    val badgeBgColor = when (state) {
        OptionVisualState.SELECTED_BLUE -> Color(0xFF3B82F6) // Bright blue badge
        OptionVisualState.CORRECT_GREEN -> CorrectGreen
        OptionVisualState.WRONG_RED -> WrongRed
        OptionVisualState.REVEALED_CORRECT -> CorrectGreen
        OptionVisualState.DEFAULT -> Color.White.copy(alpha = 0.15f)
    }

    val badgeTextColor = when (state) {
        OptionVisualState.CORRECT_GREEN,
        OptionVisualState.WRONG_RED,
        OptionVisualState.REVEALED_CORRECT,
        OptionVisualState.SELECTED_BLUE -> Color.White
        OptionVisualState.DEFAULT -> TextPrimary
    }

    // Accessible description
    val accessibilityDescription = buildString {
        append("Option ")
        append(letter)
        append(": ")
        append(text)
        when (state) {
            OptionVisualState.CORRECT_GREEN -> append(", সঠিক উত্তর, Correct")
            OptionVisualState.WRONG_RED -> append(", ভুল উত্তর, Incorrect")
            OptionVisualState.REVEALED_CORRECT -> append(", সঠিক উত্তর, Correct")
            OptionVisualState.SELECTED_BLUE -> append(", নির্বাচিত, Selected")
            OptionVisualState.DEFAULT -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .border(borderStroke, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Custom scale + glass illumination replaces small default circular ripple
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                contentDescription = accessibilityDescription
            }
            .testTag("option_${letter.lowercase()}")
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Circle Badge A, B, C, D or Check / Close Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    OptionVisualState.CORRECT_GREEN, OptionVisualState.REVEALED_CORRECT -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Correct",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    OptionVisualState.WRONG_RED -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Incorrect",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    OptionVisualState.SELECTED_BLUE -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OptionVisualState.DEFAULT -> {
                        Text(
                            text = letter,
                            color = badgeTextColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = text,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = if (state != OptionVisualState.DEFAULT) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 16.dp, end = 8.dp)
                    .weight(1f)
            )

            // Trailing status badge pill for Practice (✓ সঠিক উত্তর / ✕ ভুল উত্তর) and Exam (✓ Selected)
            if (!statusText.isNullOrBlank()) {
                val (pillBg, pillBorder, pillTextColor) = when (state) {
                    OptionVisualState.CORRECT_GREEN, OptionVisualState.REVEALED_CORRECT ->
                        Triple(Color(0x3310B981), Color(0xFF10B981), Color(0xFF6EE7B7))
                    OptionVisualState.WRONG_RED ->
                        Triple(Color(0x33EF4444), Color(0xFFEF4444), Color(0xFFFCA5A5))
                    OptionVisualState.SELECTED_BLUE ->
                        Triple(Color(0x333B82F6), Color(0xFF3B82F6), Color(0xFF93C5FD))
                    OptionVisualState.DEFAULT ->
                        Triple(Color.Transparent, Color.Transparent, Color.Transparent)
                }

                if (pillTextColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBg)
                            .border(1.dp, pillBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = pillTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

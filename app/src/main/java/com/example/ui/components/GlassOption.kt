package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentBluePrimary
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenBg
import com.example.ui.theme.CorrectGreenBorder
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.PrimaryButtonGradient
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
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(20.dp)

    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            OptionVisualState.SELECTED_BLUE -> AccentBluePrimary
            OptionVisualState.CORRECT_GREEN -> CorrectGreenBg
            OptionVisualState.WRONG_RED -> WrongRedBg
            OptionVisualState.REVEALED_CORRECT -> CorrectGreenBg.copy(alpha = 0.25f)
            OptionVisualState.DEFAULT -> GlassCardSurface
        },
        label = "option_bg"
    )

    val borderStroke = when (state) {
        OptionVisualState.SELECTED_BLUE -> BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF60A5FA), AccentBluePrimary)))
        OptionVisualState.CORRECT_GREEN -> BorderStroke(2.dp, CorrectGreenBorder)
        OptionVisualState.WRONG_RED -> BorderStroke(2.dp, WrongRedBorder)
        OptionVisualState.REVEALED_CORRECT -> BorderStroke(1.5.dp, CorrectGreenBorder)
        OptionVisualState.DEFAULT -> BorderStroke(1.dp, GlassBorderBrush)
    }

    val badgeBgColor = when (state) {
        OptionVisualState.SELECTED_BLUE -> Color.White.copy(alpha = 0.25f)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(borderStroke, shape)
            .clickable(enabled = enabled, onClick = onClick)
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
                    else -> {
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
                    .padding(start = 16.dp)
                    .weight(1f)
            )
        }
    }
}

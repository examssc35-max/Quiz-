package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.PrimaryButtonGradient

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    gradient: Brush = PrimaryButtonGradient
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1.0f, label = "button_scale")

    val shape = RoundedCornerShape(50.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(
                if (enabled) gradient
                else Brush.horizontalGradient(listOf(Color(0x403B82F6), Color(0x302563EB)))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Box(modifier = Modifier.padding(start = 8.dp))
            }

            Text(
                text = text,
                color = if (enabled) Color.White else Color(0x80FFFFFF),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (trailingIcon != null) {
                Box(modifier = Modifier.padding(start = 8.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(50.dp)

    GlassCard(
        modifier = modifier.defaultMinSize(minHeight = 50.dp),
        shape = shape,
        backgroundColor = Color(0x28FFFFFF),
        borderBrush = GlassBorderBrush,
        onClick = if (enabled) onClick else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Box(modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else Color(0x77FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

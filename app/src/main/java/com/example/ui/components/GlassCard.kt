package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorderBrush
import com.example.ui.theme.GlassCardSurface

/**
 * Premium Glassmorphic Card Container powered by [Modifier.glassmorphic].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = GlassCardSurface,
    borderBrush: Brush = GlassBorderBrush,
    borderWidth: Dp = 1.dp,
    intensity: GlassIntensity = GlassIntensity.REGULAR,
    rimLight: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.glassmorphic(
            shape = shape,
            intensity = intensity,
            borderWidth = borderWidth,
            borderBrush = borderBrush,
            backgroundColor = backgroundColor,
            rimLight = rimLight,
            onClick = onClick
        )
    ) {
        content()
    }
}

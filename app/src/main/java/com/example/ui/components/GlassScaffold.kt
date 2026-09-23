package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R

@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    blurIntensity: String = "Medium",
    content: @Composable () -> Unit
) {
    val overlayAlpha = when (blurIntensity) {
        "Subtle" -> 0.65f
        "High" -> 0.88f
        else -> 0.78f // Medium default
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Photographic bokeh nature background
        Image(
            painter = painterResource(id = R.drawable.img_quiz_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Frosted vignette & dark moody overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070E1E).copy(alpha = overlayAlpha),
                            Color(0xFF0F172A).copy(alpha = overlayAlpha + 0.05f),
                            Color(0xFF050B14).copy(alpha = overlayAlpha + 0.1f)
                        )
                    )
                )
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            content()
        }
    }
}

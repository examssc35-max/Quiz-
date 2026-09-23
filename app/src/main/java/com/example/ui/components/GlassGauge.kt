package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun GlassScoreGauge(
    score: Int,
    maxScore: Int,
    percentage: Float,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    strokeWidth: Dp = 14.dp
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(percentage) {
        val target = (percentage / 100f).coerceIn(0f, 1f)
        animatedProgress.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val canvasRadius = (this.size.minDimension - strokePx) / 2
            val arcSize = androidx.compose.ui.geometry.Size(canvasRadius * 2, canvasRadius * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(
                (this.size.width - arcSize.width) / 2,
                (this.size.height - arcSize.height) / 2
            )

            // Background track
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Glowing progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        AccentCyan,
                        AccentTeal,
                        Color(0xFF3B82F6),
                        AccentCyan
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$score / $maxScore",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${percentage.toInt()}%",
                color = TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

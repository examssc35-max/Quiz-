package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multiple vibrant celebratory animation types as requested by the user:
 * "কংগ্রাচুলেশন করার সময়ই স্ক্রিনের মধ্যে আতশবাজি মত এনিমেশন থাকতে হবে আরো অনেক এনিমেশন শুধু একটা না ( একেক সময় একেক animation হবে )"
 */
enum class CelebrationType(val isCelebration: Boolean) {
    FIREWORKS(true),         // আতশবাজি (Multiple colorful explosive bursts with sparkle trails)
    CONFETTI_RAIN(true),     // রঙিন কনফেটি বৃষ্টি (Tumbling colorful confetti shapes & streamers)
    SHOOTING_STARS(true),    // উজ্জ্বল তারকারাজি (Radiant shooting stars & starburst sparkles)
    GOLDEN_RINGS(true),      // বিজয়ের বলয় (Concentric glowing shockwave ripple rings)
    BALLOONS_FLOAT(true),    // উৎসবের উড়ন্ত বেলুন ও হৃদয় (Floating balloons & festive party bubbles)
    CONSOLATION_GLOW(false)  // সান্ত্বনার আলো (Warm, soothing resilience aura for encouragement)
}

data class ActiveCelebration(
    val id: Long = System.currentTimeMillis(),
    val type: CelebrationType,
    val title: String,
    val isConsolation: Boolean = false,
    val durationMs: Long = 2600L
)

// Particle models for the canvas rendering
private class FireworkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val maxRadius: Float,
    val lifeMax: Float
) {
    var life: Float = lifeMax
}

private class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val isCircle: Boolean
)

private class StarParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var scale: Float,
    var alpha: Float,
    val angleOffset: Float
)

private class BalloonParticle(
    var x: Float,
    var y: Float,
    var vy: Float,
    var swayOffset: Float,
    var swaySpeed: Float,
    val radius: Float,
    val color: Color,
    val isHeart: Boolean
)

@Composable
fun CelebrationOverlay(
    celebration: ActiveCelebration?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (celebration == null) return

    val fadeAnim = remember(celebration.id) { Animatable(0f) }
    var elapsedSeconds by remember(celebration.id) { mutableFloatStateOf(0f) }

    // Particles state
    val fireworksList = remember(celebration.id) { mutableListOf<FireworkParticle>() }
    val confettiList = remember(celebration.id) { mutableListOf<ConfettiParticle>() }
    val starList = remember(celebration.id) { mutableListOf<StarParticle>() }
    val balloonList = remember(celebration.id) { mutableListOf<BalloonParticle>() }

    // Vibrant celebration palette
    val palette = remember {
        listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF3366), // Pink/Magenta
            Color(0xFF00E5FF), // Cyan
            Color(0xFF76FF03), // Lime green
            Color(0xFFFF9100), // Bright Orange
            Color(0xFFD500F9), // Purple Neon
            Color(0xFF00E676), // Emerald
            Color(0xFFFF1744)  // Vivid Red
        )
    }

    LaunchedEffect(celebration.id) {
        fadeAnim.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))

        val startTime = System.nanoTime()
        val durationNanos = celebration.durationMs * 1_000_000L

        while (true) {
            val now = System.nanoTime()
            val elapsed = now - startTime
            elapsedSeconds = elapsed / 1_000_000_000f

            if (elapsed >= durationNanos - 400_000_000L) {
                // Smooth fade out at the end
                val remaining = (durationNanos - elapsed).coerceAtLeast(0L) / 400_000_000f
                fadeAnim.snapTo(remaining.coerceIn(0f, 1f))
            }

            if (elapsed >= durationNanos) {
                break
            }
            withFrameNanos { /* next frame */ }
        }

        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // High performance Particle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas

            val alpha = fadeAnim.value
            if (alpha <= 0.01f) return@Canvas

            when (celebration.type) {
                CelebrationType.FIREWORKS -> {
                    // Populate fireworks bursts if empty
                    if (fireworksList.isEmpty()) {
                        // 4 distinct burst centers
                        val centers = listOf(
                            Offset(canvasW * 0.25f, canvasH * 0.28f),
                            Offset(canvasW * 0.75f, canvasH * 0.24f),
                            Offset(canvasW * 0.50f, canvasH * 0.40f),
                            Offset(canvasW * 0.35f, canvasH * 0.55f)
                        )
                        centers.forEach { center ->
                            val burstColor = palette.random()
                            val count = 35
                            for (i in 0 until count) {
                                val angle = (i.toFloat() / count) * 2f * PI.toFloat() + Random.nextFloat() * 0.2f
                                val speed = Random.nextFloat() * 320f + 140f
                                val color = if (Random.nextBoolean()) burstColor else palette.random()
                                fireworksList.add(
                                    FireworkParticle(
                                        x = center.x,
                                        y = center.y,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed,
                                        color = color,
                                        maxRadius = Random.nextFloat() * 4.5f + 3f,
                                        lifeMax = Random.nextFloat() * 0.8f + 1.2f
                                    )
                                )
                            }
                        }
                    }

                    // Update and draw fireworks particles
                    fireworksList.forEach { p ->
                        val t = elapsedSeconds.coerceAtLeast(0f)
                        val px = p.x + p.vx * t
                        val py = p.y + p.vy * t + 80f * t * t // Gravity
                        val lifeRatio = (1f - (t / p.lifeMax)).coerceIn(0f, 1f)
                        val pAlpha = alpha * lifeRatio

                        if (lifeRatio > 0.05f) {
                            // Sparkle flash
                            val sparkle = if (Random.nextFloat() > 0.3f) 1f else 0.4f
                            drawCircle(
                                color = p.color.copy(alpha = pAlpha * sparkle),
                                radius = p.maxRadius * lifeRatio,
                                center = Offset(px, py)
                            )
                            // Soft glow outer
                            drawCircle(
                                color = p.color.copy(alpha = (pAlpha * 0.35f)),
                                radius = p.maxRadius * lifeRatio * 2.2f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                CelebrationType.CONFETTI_RAIN -> {
                    // Populate confetti rain if empty
                    if (confettiList.isEmpty()) {
                        val count = 85
                        for (i in 0 until count) {
                            confettiList.add(
                                ConfettiParticle(
                                    x = Random.nextFloat() * canvasW,
                                    y = -Random.nextFloat() * canvasH * 0.6f,
                                    vx = (Random.nextFloat() - 0.5f) * 70f,
                                    vy = Random.nextFloat() * 260f + 180f,
                                    rotation = Random.nextFloat() * 360f,
                                    rotationSpeed = (Random.nextFloat() - 0.5f) * 360f,
                                    width = Random.nextFloat() * 12f + 8f,
                                    height = Random.nextFloat() * 18f + 10f,
                                    color = palette.random(),
                                    isCircle = Random.nextFloat() > 0.7f
                                )
                            )
                        }
                    }

                    confettiList.forEach { p ->
                        val t = elapsedSeconds
                        val px = (p.x + p.vx * t + sin((t * 3f + p.x) * 1.5f) * 35f).mod(canvasW)
                        val py = p.y + p.vy * t
                        val rot = p.rotation + p.rotationSpeed * t

                        if (py in -50f..canvasH + 50f) {
                            rotate(rot, pivot = Offset(px, py)) {
                                if (p.isCircle) {
                                    drawCircle(
                                        color = p.color.copy(alpha = alpha * 0.9f),
                                        radius = p.width * 0.6f,
                                        center = Offset(px, py)
                                    )
                                } else {
                                    drawRect(
                                        color = p.color.copy(alpha = alpha * 0.9f),
                                        topLeft = Offset(px - p.width / 2, py - p.height / 2),
                                        size = Size(p.width, p.height)
                                    )
                                }
                            }
                        }
                    }
                }

                CelebrationType.SHOOTING_STARS -> {
                    // Shooting stars across the screen
                    if (starList.isEmpty()) {
                        val count = 45
                        for (i in 0 until count) {
                            val angle = (Random.nextFloat() * 0.4f + 0.6f) * PI.toFloat() // Down-right
                            val speed = Random.nextFloat() * 400f + 250f
                            starList.add(
                                StarParticle(
                                    x = Random.nextFloat() * canvasW * 0.8f,
                                    y = Random.nextFloat() * canvasH * 0.5f,
                                    vx = cos(angle) * speed,
                                    vy = sin(angle) * speed,
                                    color = palette.random(),
                                    scale = Random.nextFloat() * 1.2f + 0.8f,
                                    alpha = Random.nextFloat() * 0.5f + 0.5f,
                                    angleOffset = Random.nextFloat() * 360f
                                )
                            )
                        }
                    }

                    starList.forEach { p ->
                        val t = elapsedSeconds
                        val px = p.x + p.vx * t
                        val py = p.y + p.vy * t
                        val pAlpha = (alpha * p.alpha).coerceIn(0f, 1f)

                        // Draw golden star
                        drawStar(
                            center = Offset(px, py),
                            radius = 16f * p.scale,
                            points = 5,
                            color = p.color.copy(alpha = pAlpha),
                            rotation = p.angleOffset + t * 90f
                        )
                        // Glowing star tail
                        drawLine(
                            color = p.color.copy(alpha = pAlpha * 0.45f),
                            start = Offset(px, py),
                            end = Offset(px - p.vx * 0.08f, py - p.vy * 0.08f),
                            strokeWidth = 3f * p.scale
                        )
                    }
                }

                CelebrationType.GOLDEN_RINGS -> {
                    // Concentric radiant expanding celebration rings
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.42f
                    val ringCount = 4

                    for (r in 0 until ringCount) {
                        val delayOffset = r * 0.4f
                        val ringProgress = ((elapsedSeconds - delayOffset) / 1.5f).coerceIn(0f, 1f)
                        if (ringProgress > 0f) {
                            val maxRadius = canvasW * 0.65f
                            val ringRadius = ringProgress * maxRadius
                            val ringAlpha = (1f - ringProgress) * alpha * 0.85f
                            val ringColor = palette[r % palette.size]

                            drawCircle(
                                color = ringColor.copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = (6f * (1f - ringProgress)).coerceAtLeast(1.5f))
                            )
                        }
                    }
                }

                CelebrationType.BALLOONS_FLOAT -> {
                    // Floating festive balloons rising upwards
                    if (balloonList.isEmpty()) {
                        val count = 26
                        for (i in 0 until count) {
                            balloonList.add(
                                BalloonParticle(
                                    x = Random.nextFloat() * canvasW,
                                    y = canvasH + Random.nextFloat() * 200f,
                                    vy = -(Random.nextFloat() * 160f + 140f),
                                    swayOffset = Random.nextFloat() * 2f * PI.toFloat(),
                                    swaySpeed = Random.nextFloat() * 2.5f + 1.5f,
                                    radius = Random.nextFloat() * 16f + 18f,
                                    color = palette.random(),
                                    isHeart = i % 4 == 0
                                )
                            )
                        }
                    }

                    balloonList.forEach { b ->
                        val t = elapsedSeconds
                        val px = b.x + sin(t * b.swaySpeed + b.swayOffset) * 28f
                        val py = b.y + b.vy * t

                        if (py in -100f..canvasH + 150f) {
                            // Balloon Body
                            drawCircle(
                                color = b.color.copy(alpha = alpha * 0.88f),
                                radius = b.radius,
                                center = Offset(px, py)
                            )
                            // Balloon Highlight shine
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.5f),
                                radius = b.radius * 0.28f,
                                center = Offset(px - b.radius * 0.35f, py - b.radius * 0.35f)
                            )
                            // Balloon string
                            drawLine(
                                color = Color(0x70FFFFFF),
                                start = Offset(px, py + b.radius),
                                end = Offset(px, py + b.radius + 24f),
                                strokeWidth = 1.8f
                            )
                        }
                    }
                }

                CelebrationType.CONSOLATION_GLOW -> {
                    // Gentle, warm encouragement aura (সান্ত্বনা ও অনুপ্রেরণা)
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.40f
                    val pulse = (sin(elapsedSeconds * 4.5f) * 0.5f + 0.5f)
                    val auraRadius = canvasW * 0.45f + pulse * 25f

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0x6000E5FF),
                                Color(0x303B82F6),
                                Color(0x0010B981)
                            ),
                            center = Offset(centerX, centerY),
                            radius = auraRadius
                        ),
                        radius = auraRadius,
                        center = Offset(centerX, centerY)
                    )
                }
            }
        }

        // Animated Sleek Floating Glass Badge with Short Praise/Consolation Word
        AnimatedVisibility(
            visible = fadeAnim.value > 0.15f,
            enter = fadeIn(tween(250)) + scaleIn(tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        ) {
            val isConsolation = celebration.isConsolation
            val primaryBadgeColor = if (isConsolation) Color(0xFF00E5FF) else Color(0xFFFFD700)
            val badgeBgGradient = if (isConsolation) {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xD90F172A),
                        Color(0xD91E293B)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xE61E1B4B),
                        Color(0xE6311042)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(badgeBgGradient)
                    .border(
                        width = 1.5.dp,
                        color = primaryBadgeColor.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConsolation) Icons.Default.Favorite else Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = primaryBadgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = celebration.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                    if (!isConsolation) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFF80BF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStar(
    center: Offset,
    radius: Float,
    points: Int = 5,
    color: Color,
    rotation: Float = 0f
) {
    val path = Path()
    val innerRadius = radius * 0.42f
    val step = (PI / points).toFloat()
    val rotRad = (rotation * PI / 180f).toFloat()

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = i * step + rotRad
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

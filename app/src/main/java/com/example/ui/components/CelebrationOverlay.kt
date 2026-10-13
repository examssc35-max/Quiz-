package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multiple vibrant celebratory animation types:
 * 1) FIREWORKS: Staggered rockets with incandescent heads & ember trails, ambient sky flashes,
 *               massive radial bursts with air drag, gravity & secondary crackling dragon eggs.
 * 2) CONFETTI_RAIN: Dual party-popper corner cannons + continuous top cascades, true 3D tumbling
 *                   physics with dual-sided shading & metallic specular glints.
 * 3) SHOOTING_STARS: Twinkling celestial starfield, blazing meteors with radiant ion tails &
 *                    terminal bolide stardust starbursts.
 * 4) GOLDEN_RINGS: Concentric chromatic shockwaves, wavefront gemstone nodes, and rotating
 *                  8-pointed celestial victory corona with rising sparklers.
 * 5) BALLOONS_FLOAT: 3D glossy spherical balloons & celebration hearts with pendulum strings,
 *                    floating micro-confetti sparkles, scaled to screen height.
 * 6) CONSOLATION_GLOW: Warm soothing aurora ripples and ascending comforting heart & ember orbs.
 */
enum class CelebrationType(val isCelebration: Boolean) {
    FIREWORKS(true),
    CONFETTI_RAIN(true),
    SHOOTING_STARS(true),
    GOLDEN_RINGS(true),
    BALLOONS_FLOAT(true),
    CONSOLATION_GLOW(false)
}

data class ActiveCelebration(
    val id: Long = System.currentTimeMillis(),
    val type: CelebrationType,
    val title: String,
    val isConsolation: Boolean = false,
    val durationMs: Long = 3400L
)

// Internal particle data models
private class FireworkRocket(
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val burstTime: Float,
    val color: Color
)

private class FireworkParticle(
    val centerX: Float,
    val centerY: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val maxRadius: Float,
    val startTime: Float,
    val lifeMax: Float,
    val isSecondaryCrackle: Boolean = false
)

private class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rollX: Float,
    var rollSpeedX: Float,
    var rollY: Float,
    var rollSpeedY: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val shapeType: Int // 0: rectangle, 1: circular sequin, 2: curly ribbon
)

private class MeteorParticle(
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val length: Float,
    val color: Color,
    val startTime: Float,
    val duration: Float
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

    // Particles collections initialized once per celebration instance
    val rocketsList = remember(celebration.id) { mutableListOf<FireworkRocket>() }
    val fireworksList = remember(celebration.id) { mutableListOf<FireworkParticle>() }
    val confettiList = remember(celebration.id) { mutableListOf<ConfettiParticle>() }
    val meteorList = remember(celebration.id) { mutableListOf<MeteorParticle>() }
    val balloonList = remember(celebration.id) { mutableListOf<BalloonParticle>() }

    // Vibrant chromatic celebratory palette
    val palette = remember {
        listOf(
            Color(0xFFFFD700), // Radiant Gold
            Color(0xFFFF2A6D), // Vivid Rose / Neon Pink
            Color(0xFF00F0FF), // Electric Cyan
            Color(0xFF05FFA1), // Neon Emerald
            Color(0xFFFF7700), // Fiery Orange
            Color(0xFFB026FF), // Ultraviolet Purple
            Color(0xFFFFF01F), // Laser Yellow
            Color(0xFF00E5FF), // Aqua Sky
            Color(0xFFFF4081)  // Pink Coral
        )
    }

    LaunchedEffect(celebration.id) {
        // Immediate fade in while physics run from t=0
        launch {
            fadeAnim.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        }

        val startTime = System.nanoTime()
        val durationNanos = celebration.durationMs * 1_000_000L

        while (true) {
            val now = System.nanoTime()
            val elapsed = now - startTime
            elapsedSeconds = elapsed / 1_000_000_000f

            // Smooth exit fade over last 400ms
            if (elapsed >= durationNanos - 400_000_000L) {
                val remaining = (durationNanos - elapsed).coerceAtLeast(0L) / 400_000_000f
                fadeAnim.snapTo(remaining.coerceIn(0f, 1f))
            }

            if (elapsed >= durationNanos) {
                break
            }
            withFrameNanos { /* next render frame */ }
        }

        onDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // High-Performance Particle Physics Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas

            val alpha = fadeAnim.value
            if (alpha <= 0.01f) return@Canvas

            when (celebration.type) {
                CelebrationType.FIREWORKS -> {
                    // Staggered rockets shoot up -> Ambient illumination flash -> Grand radial bursts -> Secondary crackling dragon eggs
                    if (rocketsList.isEmpty()) {
                        val rocketConfigs = listOf(
                            Triple(0.24f, 0.26f, 0.15f),
                            Triple(0.76f, 0.22f, 0.40f),
                            Triple(0.50f, 0.32f, 0.70f),
                            Triple(0.20f, 0.40f, 1.00f),
                            Triple(0.80f, 0.34f, 1.30f),
                            Triple(0.48f, 0.20f, 1.65f)
                        )
                        rocketConfigs.forEach { (xFrac, yFrac, burstSec) ->
                            val color = palette.random()
                            val targetX = canvasW * xFrac
                            val targetY = canvasH * yFrac
                            rocketsList.add(
                                FireworkRocket(
                                    startX = targetX + (Random.nextFloat() - 0.5f) * (canvasW * 0.12f),
                                    startY = canvasH + 40f,
                                    targetX = targetX,
                                    targetY = targetY,
                                    burstTime = burstSec,
                                    color = color
                                )
                            )

                            // Primary burst particles scaled to canvas size
                            val count = 52
                            val baseSpeed = canvasW * 0.45f
                            for (i in 0 until count) {
                                val angle = (i.toFloat() / count) * 2f * PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.18f
                                val speed = baseSpeed * (Random.nextFloat() * 0.7f + 0.55f)
                                fireworksList.add(
                                    FireworkParticle(
                                        centerX = targetX,
                                        centerY = targetY,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed,
                                        color = if (Random.nextBoolean()) color else palette.random(),
                                        maxRadius = Random.nextFloat() * 3.5f + 3.0f,
                                        startTime = burstSec,
                                        lifeMax = Random.nextFloat() * 0.8f + 1.2f,
                                        isSecondaryCrackle = false
                                    )
                                )
                            }

                            // Secondary crackling dragon eggs that pop with delay
                            for (c in 0 until 24) {
                                val angle = Random.nextFloat() * 2f * PI.toFloat()
                                val speed = (canvasW * 0.25f) * (Random.nextFloat() * 0.6f + 0.3f)
                                fireworksList.add(
                                    FireworkParticle(
                                        centerX = targetX,
                                        centerY = targetY,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed,
                                        color = Color(0xFFFFD700),
                                        maxRadius = Random.nextFloat() * 2.5f + 1.8f,
                                        startTime = burstSec + 0.28f + Random.nextFloat() * 0.35f,
                                        lifeMax = 0.80f,
                                        isSecondaryCrackle = true
                                    )
                                )
                            }
                        }
                    }

                    // 1. Draw Rocket Ascent Phase
                    rocketsList.forEach { rocket ->
                        if (elapsedSeconds < rocket.burstTime) {
                            val progress = (elapsedSeconds / rocket.burstTime).coerceIn(0f, 1f)
                            val rx = rocket.startX + (rocket.targetX - rocket.startX) * progress
                            val ry = rocket.startY + (rocket.targetY - rocket.startY) * progress

                            // Rocket glowing head
                            drawCircle(
                                color = Color.White.copy(alpha = alpha),
                                radius = 5f,
                                center = Offset(rx, ry)
                            )
                            drawCircle(
                                color = rocket.color.copy(alpha = alpha * 0.75f),
                                radius = 10f,
                                center = Offset(rx, ry)
                            )

                            // Falling sparks tail
                            for (t in 1..6) {
                                val trailProgress = (progress - t * 0.03f).coerceAtLeast(0f)
                                val tx = rocket.startX + (rocket.targetX - rocket.startX) * trailProgress + (Random.nextFloat() - 0.5f) * 6f
                                val ty = rocket.startY + (rocket.targetY - rocket.startY) * trailProgress + t * 4f
                                drawCircle(
                                    color = Color(0xFFFFD700).copy(alpha = alpha * (1f - t * 0.16f)),
                                    radius = 2.5f,
                                    center = Offset(tx, ty)
                                )
                            }
                        } else {
                            // Ambient flash at explosion instant
                            val flashT = elapsedSeconds - rocket.burstTime
                            if (flashT in 0f..0.16f) {
                                val flashAlpha = (1f - (flashT / 0.16f)) * alpha * 0.40f
                                drawRect(
                                    brush = Brush.radialGradient(
                                        listOf(rocket.color.copy(alpha = flashAlpha), Color.Transparent),
                                        center = Offset(rocket.targetX, rocket.targetY),
                                        radius = canvasW * 0.85f
                                    )
                                )
                            }
                        }
                    }

                    // 2. Draw Fireworks Particles with air drag, gravity & trailing embers
                    val gravityY = canvasH * 0.24f
                    fireworksList.forEach { p ->
                        if (elapsedSeconds >= p.startTime) {
                            val t = elapsedSeconds - p.startTime
                            if (t <= p.lifeMax) {
                                val drag = (1f - (t * 0.22f)).coerceAtLeast(0.35f)
                                val px = p.centerX + p.vx * t * drag
                                val py = p.centerY + p.vy * t * drag + gravityY * t * t

                                val prevT = (t - 0.035f).coerceAtLeast(0f)
                                val prevDrag = (1f - (prevT * 0.22f)).coerceAtLeast(0.35f)
                                val prevX = p.centerX + p.vx * prevT * prevDrag
                                val prevY = p.centerY + p.vy * prevT * prevDrag + gravityY * prevT * prevT

                                val lifeRatio = (1f - (t / p.lifeMax)).coerceIn(0f, 1f)
                                val sparkle = if (p.isSecondaryCrackle) {
                                    if (Random.nextFloat() > 0.35f) 1f else 0.15f
                                } else {
                                    0.75f + 0.25f * sin(t * 36f + px)
                                }
                                val pAlpha = (alpha * lifeRatio * sparkle).coerceIn(0f, 1f)

                                // Fading trailing spark streamer
                                drawLine(
                                    color = p.color.copy(alpha = pAlpha * 0.65f),
                                    start = Offset(prevX, prevY),
                                    end = Offset(px, py),
                                    strokeWidth = (p.maxRadius * lifeRatio * 1.3f).coerceAtLeast(1.5f)
                                )

                                // Spark core
                                drawCircle(
                                    color = Color.White.copy(alpha = pAlpha),
                                    radius = (p.maxRadius * lifeRatio * 0.6f).coerceAtLeast(1.2f),
                                    center = Offset(px, py)
                                )
                                // Soft glowing aura
                                drawCircle(
                                    color = p.color.copy(alpha = pAlpha * 0.45f),
                                    radius = p.maxRadius * lifeRatio * 2.6f,
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }
                }

                CelebrationType.CONFETTI_RAIN -> {
                    // Dual Party Popper Cannons blasting upwards from bottom corners + top cascading shower
                    if (confettiList.isEmpty()) {
                        val count = 140
                        for (i in 0 until count) {
                            val isLeftCannon = i < count / 2
                            val startX = if (isLeftCannon) canvasW * 0.05f else canvasW * 0.95f
                            val startY = canvasH + 20f
                            val angleRad = if (isLeftCannon) {
                                (-0.32f - Random.nextFloat() * 0.28f) * PI.toFloat() // Up & inward
                            } else {
                                (-0.68f + Random.nextFloat() * 0.28f) * PI.toFloat() // Up & inward
                            }
                            val speed = canvasH * (Random.nextFloat() * 0.40f + 0.35f)
                            confettiList.add(
                                ConfettiParticle(
                                    x = startX,
                                    y = startY,
                                    vx = cos(angleRad) * speed,
                                    vy = sin(angleRad) * speed,
                                    rollX = Random.nextFloat() * 2f * PI.toFloat(),
                                    rollSpeedX = Random.nextFloat() * 9f + 4f,
                                    rollY = Random.nextFloat() * 2f * PI.toFloat(),
                                    rollSpeedY = Random.nextFloat() * 7f + 3f,
                                    width = Random.nextFloat() * 10f + 8f,
                                    height = Random.nextFloat() * 18f + 12f,
                                    color = palette.random(),
                                    shapeType = i % 3
                                )
                            )
                        }
                    }

                    val gravityConfetti = canvasH * 0.32f
                    confettiList.forEach { p ->
                        val t = elapsedSeconds
                        val drag = (1f - (t * 0.18f)).coerceAtLeast(0.35f)
                        val px = p.x + p.vx * t * drag + sin(t * 3.4f + p.x) * 36f
                        val py = p.y + p.vy * t * drag + gravityConfetti * t * t

                        if (py in -80f..canvasH + 100f && px in -60f..canvasW + 60f) {
                            val cosX = cos(p.rollX + t * p.rollSpeedX)
                            val sinY = sin(p.rollY + t * p.rollSpeedY)
                            val effWidth = (p.width * abs(cosX)).coerceAtLeast(1.5f)
                            val isBackside = cosX < 0f
                            val flakeColor = if (isBackside) p.color.copy(alpha = alpha * 0.65f) else p.color.copy(alpha = alpha * 0.98f)

                            rotate(sinY * 45f, pivot = Offset(px, py)) {
                                when (p.shapeType) {
                                    1 -> {
                                        // Metallic Sequin with 3D elliptical squeeze
                                        drawOval(
                                            color = flakeColor,
                                            topLeft = Offset(px - effWidth / 2f, py - p.height * 0.4f),
                                            size = Size(effWidth, p.height * 0.8f)
                                        )
                                    }
                                    2 -> {
                                        // Serpentine Wavy Ribbon
                                        val ribbonPath = Path().apply {
                                            moveTo(px - effWidth / 2f, py - p.height / 2f)
                                            quadraticTo(px + effWidth / 2f, py, px - effWidth / 2f, py + p.height / 2f)
                                            lineTo(px + effWidth / 4f, py + p.height / 2f)
                                            quadraticTo(px + effWidth, py, px + effWidth / 4f, py - p.height / 2f)
                                            close()
                                        }
                                        drawPath(ribbonPath, flakeColor)
                                    }
                                    else -> {
                                        // Rectangular foil strip with bright metallic glint
                                        drawRect(
                                            color = flakeColor,
                                            topLeft = Offset(px - effWidth / 2f, py - p.height / 2f),
                                            size = Size(effWidth, p.height)
                                        )
                                        // Specular metallic sheen glint
                                        if (!isBackside && abs(cosX) > 0.45f) {
                                            drawLine(
                                                color = Color.White.copy(alpha = alpha * 0.55f),
                                                start = Offset(px - effWidth * 0.2f, py - p.height * 0.4f),
                                                end = Offset(px + effWidth * 0.2f, py + p.height * 0.4f),
                                                strokeWidth = 1.4f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                CelebrationType.SHOOTING_STARS -> {
                    // Twinkling Celestial Starfield + Cosmic Meteors with Stardust Trails & Terminal Bolide Bursts
                    // 1. Ambient Twinkling Stars
                    for (i in 0 until 36) {
                        val sx = ((i * 137.5f) % canvasW).coerceIn(12f, canvasW - 12f)
                        val sy = ((i * 219.3f) % (canvasH * 0.85f)).coerceIn(12f, canvasH * 0.85f)
                        val twinkle = (sin(elapsedSeconds * (2.8f + (i % 3) * 1.1f) + i.toFloat()) * 0.5f + 0.5f)
                        val starAlpha = twinkle * alpha * 0.90f
                        val starRadius = 2.2f + (i % 3) * 1.2f
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha),
                            radius = starRadius,
                            center = Offset(sx, sy)
                        )
                        if (i % 3 == 0) {
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = starAlpha * 0.45f),
                                radius = starRadius * 2.8f,
                                center = Offset(sx, sy)
                            )
                        }
                    }

                    // 2. Cosmic Meteors cutting diagonally across the sky
                    if (meteorList.isEmpty()) {
                        val count = 28
                        for (i in 0 until count) {
                            val startX = Random.nextFloat() * canvasW * 0.90f
                            val startY = -Random.nextFloat() * 120f
                            val targetDist = canvasH * (Random.nextFloat() * 0.55f + 0.45f)
                            val angle = (0.64f + Random.nextFloat() * 0.24f) * PI.toFloat()
                            val duration = Random.nextFloat() * 0.75f + 0.65f
                            meteorList.add(
                                MeteorParticle(
                                    startX = startX,
                                    startY = startY,
                                    targetX = startX + cos(angle) * targetDist,
                                    targetY = startY + sin(angle) * targetDist,
                                    length = Random.nextFloat() * 150f + 100f,
                                    color = if (i % 3 == 0) Color(0xFFFFD700) else if (i % 3 == 1) Color(0xFF00F0FF) else Color(0xFFFF2A6D),
                                    startTime = Random.nextFloat() * 1.6f,
                                    duration = duration
                                )
                            )
                        }
                    }

                    meteorList.forEach { m ->
                        if (elapsedSeconds >= m.startTime) {
                            val t = elapsedSeconds - m.startTime
                            if (t <= m.duration) {
                                val progress = t / m.duration
                                val headX = m.startX + (m.targetX - m.startX) * progress
                                val headY = m.startY + (m.targetY - m.startY) * progress

                                val dx = m.targetX - m.startX
                                val dy = m.targetY - m.startY
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                                val ux = dx / dist
                                val uy = dy / dist

                                val tailX = headX - ux * m.length
                                val tailY = headY - uy * m.length
                                val mAlpha = (1f - progress * 0.8f) * alpha

                                // Glowing Chromatic Comet Tail
                                drawLine(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = mAlpha),
                                            m.color.copy(alpha = mAlpha * 0.85f),
                                            Color.Transparent
                                        ),
                                        start = Offset(headX, headY),
                                        end = Offset(tailX, tailY)
                                    ),
                                    start = Offset(headX, headY),
                                    end = Offset(tailX, tailY),
                                    strokeWidth = 4.5f
                                )

                                // Brilliant Meteor Head
                                drawCircle(
                                    color = Color.White.copy(alpha = mAlpha),
                                    radius = 5.5f,
                                    center = Offset(headX, headY)
                                )
                                drawCircle(
                                    color = m.color.copy(alpha = mAlpha * 0.65f),
                                    radius = 13f,
                                    center = Offset(headX, headY)
                                )

                                // Shedding Stardust Sparks
                                for (s in 1..3) {
                                    val st = progress - s * 0.08f
                                    if (st > 0f) {
                                        val sx = headX - ux * (m.length * s * 0.28f) + sin(t * 18f + s) * 6f
                                        val sy = headY - uy * (m.length * s * 0.28f) + cos(t * 18f + s) * 6f
                                        drawCircle(
                                            color = m.color.copy(alpha = mAlpha * (1f - s * 0.28f)),
                                            radius = 2.4f,
                                            center = Offset(sx, sy)
                                        )
                                    }
                                }

                                // Diamond Starburst at head
                                drawStar(
                                    center = Offset(headX, headY),
                                    radius = 15f * (1f - progress * 0.35f),
                                    points = 5,
                                    color = Color.White.copy(alpha = mAlpha * 0.95f),
                                    rotation = t * 200f
                                )
                            }
                        }
                    }
                }

                CelebrationType.GOLDEN_RINGS -> {
                    // Concentric Radiant Shockwaves & Golden Energy Aura
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.38f
                    val ringCount = 6

                    for (r in 0 until ringCount) {
                        val delayOffset = r * 0.26f
                        val ringProgress = ((elapsedSeconds - delayOffset) / 1.5f).coerceIn(0f, 1f)
                        if (ringProgress > 0f) {
                            val maxRadius = canvasW * 0.75f
                            val ringRadius = ringProgress * maxRadius
                            val ringAlpha = (1f - ringProgress) * alpha * 0.92f
                            val ringColor = palette[r % palette.size]

                            // Outer iridescent shockwave
                            drawCircle(
                                color = ringColor.copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = (9f * (1f - ringProgress)).coerceAtLeast(1.8f))
                            )

                            // Inner golden radiance
                            drawCircle(
                                color = Color(0xFFFFD700).copy(alpha = ringAlpha * 0.50f),
                                radius = ringRadius * 0.94f,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 2.4f)
                            )

                            // Wavefront gemstone nodes
                            val sparkCount = 8
                            for (s in 0 until sparkCount) {
                                val angle = (s.toFloat() / sparkCount) * 2f * PI.toFloat() + elapsedSeconds * 0.9f
                                val sx = centerX + cos(angle) * ringRadius
                                val sy = centerY + sin(angle) * ringRadius
                                drawCircle(
                                    color = Color.White.copy(alpha = ringAlpha),
                                    radius = 3.2f * (1f - ringProgress * 0.45f),
                                    center = Offset(sx, sy)
                                )
                            }
                        }
                    }

                    // Rotating central 8-pointed golden victory star corona
                    val coronaPulse = (sin(elapsedSeconds * 4.5f) * 0.25f + 0.75f) * alpha
                    drawStar(
                        center = Offset(centerX, centerY),
                        radius = 44f,
                        points = 8,
                        color = Color(0xFFFFD700).copy(alpha = coronaPulse),
                        rotation = elapsedSeconds * 55f
                    )
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = coronaPulse * 0.35f),
                        radius = 60f,
                        center = Offset(centerX, centerY)
                    )
                }

                CelebrationType.BALLOONS_FLOAT -> {
                    // 3D Glossy Balloons & Hearts with Floating Festive Sparkles scaled to screen height
                    if (balloonList.isEmpty()) {
                        val count = 34
                        for (i in 0 until count) {
                            balloonList.add(
                                BalloonParticle(
                                    x = Random.nextFloat() * canvasW,
                                    y = canvasH + Random.nextFloat() * 180f,
                                    vy = -canvasH * (Random.nextFloat() * 0.20f + 0.26f), // Reaches top of screen naturally
                                    swayOffset = Random.nextFloat() * 2f * PI.toFloat(),
                                    swaySpeed = Random.nextFloat() * 2.8f + 1.6f,
                                    radius = Random.nextFloat() * 16f + 20f,
                                    color = palette.random(),
                                    isHeart = i % 4 == 0
                                )
                            )
                        }
                    }

                    // Floating micro-confetti sparkles rising with balloons
                    for (i in 0 until 26) {
                        val phase = (elapsedSeconds * 0.9f + i * 0.18f) % 2.5f
                        val progress = phase / 2.5f
                        val sx = (canvasW * 0.1f + ((i * 123.7f) % (canvasW * 0.8f)) + sin(elapsedSeconds * 3f + i) * 22f).mod(canvasW)
                        val sy = canvasH - progress * (canvasH * 1.15f)
                        val sAlpha = (sin(progress * PI.toFloat()) * alpha * 0.80f).coerceIn(0f, 1f)
                        drawCircle(
                            color = palette[i % palette.size].copy(alpha = sAlpha),
                            radius = 2.5f + (i % 3) * 1f,
                            center = Offset(sx, sy)
                        )
                    }

                    balloonList.forEach { b ->
                        val t = elapsedSeconds
                        val px = b.x + sin(t * b.swaySpeed + b.swayOffset) * 36f
                        val py = b.y + b.vy * t

                        if (py in -140f..canvasH + 160f) {
                            // Balloon 3D Body
                            drawCircle(
                                color = b.color.copy(alpha = alpha * 0.92f),
                                radius = b.radius,
                                center = Offset(px, py)
                            )
                            // 3D Specular Highlight Spot
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.65f),
                                radius = b.radius * 0.30f,
                                center = Offset(px - b.radius * 0.32f, py - b.radius * 0.32f)
                            )
                            // Soft edge depth shadow for spherical 3D realism
                            drawCircle(
                                color = Color.Black.copy(alpha = alpha * 0.18f),
                                radius = b.radius,
                                center = Offset(px, py),
                                style = Stroke(width = 2.2f)
                            )
                            // Wavy pendulum string
                            val stringPath = Path().apply {
                                moveTo(px, py + b.radius)
                                quadraticTo(px + sin(t * 4.2f) * 7f, py + b.radius + 15f, px, py + b.radius + 32f)
                            }
                            drawPath(
                                path = stringPath,
                                color = Color(0x95FFFFFF),
                                style = Stroke(width = 1.8f)
                            )
                        }
                    }
                }

                CelebrationType.CONSOLATION_GLOW -> {
                    // Gentle soothing aura and rising comforting ember orbs & hearts
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.40f
                    val pulse = (sin(elapsedSeconds * 3.8f) * 0.5f + 0.5f)
                    val auraRadius = canvasW * 0.55f + pulse * 40f

                    // Soothing multi-layer ambient aurora
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0x8000E5FF),
                                Color(0x453B82F6),
                                Color(0x2010B981),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY),
                            radius = auraRadius
                        ),
                        radius = auraRadius,
                        center = Offset(centerX, centerY)
                    )

                    // Soothing rising glowing embers & hearts
                    for (i in 0 until 24) {
                        val emberPhase = (elapsedSeconds * 0.75f + i * 0.20f) % 2.8f
                        val progress = emberPhase / 2.8f
                        val ex = centerX + sin(progress * 5.5f + i * 1.4f) * (canvasW * 0.38f)
                        val ey = canvasH * 0.80f - progress * (canvasH * 0.65f)
                        val emberAlpha = (sin(progress * PI.toFloat()) * alpha * 0.90f).coerceIn(0f, 1f)
                        val emberRadius = 4.2f + (i % 4) * 2f

                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = emberAlpha * 0.45f),
                            radius = emberRadius * 2.8f,
                            center = Offset(ex, ey)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = emberAlpha),
                            radius = emberRadius * 0.85f,
                            center = Offset(ex, ey)
                        )
                    }
                }
            }
        }

        // Floating Animated Badge with Short Congratulation Word
        AnimatedVisibility(
            visible = fadeAnim.value > 0.15f,
            enter = fadeIn(tween(250)) + scaleIn(tween(300, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp)
        ) {
            val isConsolation = celebration.isConsolation
            val primaryBadgeColor = if (isConsolation) Color(0xFF00E5FF) else Color(0xFFFFD700)
            val badgeBgGradient = if (isConsolation) {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xF00F172A),
                        Color(0xF01E293B)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    listOf(
                        Color(0xF01E1B4B),
                        Color(0xF0311042)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(badgeBgGradient)
                    .border(
                        width = 1.5.dp,
                        color = primaryBadgeColor.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 11.dp)
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
                        Spacer(modifier = Modifier.width(8.dp))
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

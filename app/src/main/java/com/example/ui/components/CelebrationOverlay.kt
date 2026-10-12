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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multiple vibrant celebratory animation types:
 * 1) FIREWORKS: Real rocket launch trails, apex detonation flash, spherical radial bursts with air drag, gravity & secondary crackling sparks.
 * 2) CONFETTI_RAIN: True 3D fluttering paper strips with perspective flip, undulating streamers, and sequins.
 * 3) SHOOTING_STARS: Cosmic meteors with glowing gradient comet tails and stellar starbursts.
 * 4) GOLDEN_RINGS: Concentric radiant shockwaves with victory crowns.
 * 5) BALLOONS_FLOAT: 3D glossy celebratory balloons and hearts with pendulum strings.
 * 6) CONSOLATION_GLOW: Warm, soothing resilience aura.
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
    val durationMs: Long = 3200L
)

// Rocket ascent model for realistic fireworks
private class FireworkRocket(
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val burstTime: Float,
    val color: Color
)

// Particle models for the canvas rendering
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
    val shapeType: Int // 0: rectangle, 1: circle/sequin, 2: ribbon strip
)

private class MeteorParticle(
    val startX: Float,
    val startY: Float,
    val vx: Float,
    val vy: Float,
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

    // Particles state
    val rocketsList = remember(celebration.id) { mutableListOf<FireworkRocket>() }
    val fireworksList = remember(celebration.id) { mutableListOf<FireworkParticle>() }
    val confettiList = remember(celebration.id) { mutableListOf<ConfettiParticle>() }
    val meteorList = remember(celebration.id) { mutableListOf<MeteorParticle>() }
    val balloonList = remember(celebration.id) { mutableListOf<BalloonParticle>() }

    // Vibrant realistic celebration palette
    val palette = remember {
        listOf(
            Color(0xFFFFD700), // Brilliant Gold
            Color(0xFFFF2A6D), // Vivid Rose / Neon Pink
            Color(0xFF00F0FF), // Electric Cyan
            Color(0xFF05FFA1), // Neon Emerald
            Color(0xFFFF7700), // Fiery Orange
            Color(0xFFB026FF), // Ultraviolet Purple
            Color(0xFFFFF01F), // Laser Yellow
            Color(0xFF00E5FF)  // Aqua Sky
        )
    }

    LaunchedEffect(celebration.id) {
        fadeAnim.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))

        val startTime = System.nanoTime()
        val durationNanos = celebration.durationMs * 1_000_000L

        while (true) {
            val now = System.nanoTime()
            val elapsed = now - startTime
            elapsedSeconds = elapsed / 1_000_000_000f

            if (elapsed >= durationNanos - 350_000_000L) {
                val remaining = (durationNanos - elapsed).coerceAtLeast(0L) / 350_000_000f
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
        modifier = modifier.fillMaxSize()
    ) {
        // High performance Particle Physics Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas

            val alpha = fadeAnim.value
            if (alpha <= 0.01f) return@Canvas

            when (celebration.type) {
                CelebrationType.FIREWORKS -> {
                    // Realistic Fireworks: Staggered rockets shoot up -> Ambient apex flash -> Radial bursts with drag, gravity & trailing embers -> Secondary crackles
                    if (rocketsList.isEmpty()) {
                        val rocketConfigs = listOf(
                            Triple(0.24f, 0.28f, 0.22f),
                            Triple(0.76f, 0.24f, 0.42f),
                            Triple(0.50f, 0.34f, 0.68f),
                            Triple(0.18f, 0.44f, 0.95f),
                            Triple(0.82f, 0.38f, 1.25f),
                            Triple(0.48f, 0.22f, 1.55f)
                        )
                        rocketConfigs.forEach { (xFrac, yFrac, burstSec) ->
                            val color = palette.random()
                            val targetX = canvasW * xFrac
                            val targetY = canvasH * yFrac
                            rocketsList.add(
                                FireworkRocket(
                                    startX = targetX + (Random.nextFloat() - 0.5f) * 60f,
                                    startY = canvasH + 40f,
                                    targetX = targetX,
                                    targetY = targetY,
                                    burstTime = burstSec,
                                    color = color
                                )
                            )

                            // Primary burst particles at target
                            val count = 48
                            for (i in 0 until count) {
                                val angle = (i.toFloat() / count) * 2f * PI.toFloat() + Random.nextFloat() * 0.15f
                                val speed = Random.nextFloat() * 360f + 190f
                                fireworksList.add(
                                    FireworkParticle(
                                        centerX = targetX,
                                        centerY = targetY,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed,
                                        color = if (Random.nextBoolean()) color else palette.random(),
                                        maxRadius = Random.nextFloat() * 4.5f + 3.2f,
                                        startTime = burstSec,
                                        lifeMax = Random.nextFloat() * 0.9f + 1.25f,
                                        isSecondaryCrackle = false
                                    )
                                )
                            }

                            // Secondary crackle sparkles that pop slightly later
                            for (c in 0 until 20) {
                                val angle = Random.nextFloat() * 2f * PI.toFloat()
                                val speed = Random.nextFloat() * 210f + 85f
                                fireworksList.add(
                                    FireworkParticle(
                                        centerX = targetX,
                                        centerY = targetY,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed,
                                        color = Color(0xFFFFD700),
                                        maxRadius = Random.nextFloat() * 2.8f + 1.5f,
                                        startTime = burstSec + 0.35f + Random.nextFloat() * 0.3f,
                                        lifeMax = 0.75f,
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
                                radius = 4.5f,
                                center = Offset(rx, ry)
                            )
                            drawCircle(
                                color = rocket.color.copy(alpha = alpha * 0.75f),
                                radius = 9f,
                                center = Offset(rx, ry)
                            )

                            // Falling sparks tail
                            for (t in 1..5) {
                                val trailProgress = (progress - t * 0.035f).coerceAtLeast(0f)
                                val tx = rocket.startX + (rocket.targetX - rocket.startX) * trailProgress + (Random.nextFloat() - 0.5f) * 6f
                                val ty = rocket.startY + (rocket.targetY - rocket.startY) * trailProgress + t * 4f
                                drawCircle(
                                    color = Color(0xFFFFD700).copy(alpha = alpha * (1f - t * 0.18f)),
                                    radius = 2.4f,
                                    center = Offset(tx, ty)
                                )
                            }
                        } else {
                            // Ambient Flash at explosion instant (within 140ms of burst)
                            val flashT = elapsedSeconds - rocket.burstTime
                            if (flashT in 0f..0.14f) {
                                val flashAlpha = (1f - (flashT / 0.14f)) * alpha * 0.38f
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

                    // 2. Draw Fireworks Particles with drag, gravity & trailing embers
                    fireworksList.forEach { p ->
                        if (elapsedSeconds >= p.startTime) {
                            val t = elapsedSeconds - p.startTime
                            if (t <= p.lifeMax) {
                                val drag = (1f - (t * 0.22f)).coerceAtLeast(0.35f)
                                val px = p.centerX + p.vx * t * drag
                                val py = p.centerY + p.vy * t * drag + 92f * t * t // Gravity

                                val prevT = (t - 0.035f).coerceAtLeast(0f)
                                val prevDrag = (1f - (prevT * 0.22f)).coerceAtLeast(0.35f)
                                val prevX = p.centerX + p.vx * prevT * prevDrag
                                val prevY = p.centerY + p.vy * prevT * prevDrag + 92f * prevT * prevT

                                val lifeRatio = (1f - (t / p.lifeMax)).coerceIn(0f, 1f)
                                val sparkle = if (p.isSecondaryCrackle) {
                                    if (Random.nextFloat() > 0.35f) 1f else 0.15f
                                } else {
                                    0.75f + 0.25f * sin(t * 36f + px)
                                }
                                val pAlpha = (alpha * lifeRatio * sparkle).coerceIn(0f, 1f)

                                // Fading trailing spark streamer
                                drawLine(
                                    color = p.color.copy(alpha = pAlpha * 0.6f),
                                    start = Offset(prevX, prevY),
                                    end = Offset(px, py),
                                    strokeWidth = (p.maxRadius * lifeRatio * 1.2f).coerceAtLeast(1.5f)
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
                                    radius = p.maxRadius * lifeRatio * 2.5f,
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }
                }

                CelebrationType.CONFETTI_RAIN -> {
                    // Realistic Confetti: 3D tumbling paper strips with perspective flip & undulating ribbons
                    if (confettiList.isEmpty()) {
                        val count = 95
                        for (i in 0 until count) {
                            confettiList.add(
                                ConfettiParticle(
                                    x = Random.nextFloat() * canvasW,
                                    y = -Random.nextFloat() * canvasH * 0.7f,
                                    vx = (Random.nextFloat() - 0.5f) * 60f,
                                    vy = Random.nextFloat() * 240f + 160f,
                                    rollX = Random.nextFloat() * 2f * PI.toFloat(),
                                    rollSpeedX = Random.nextFloat() * 8f + 4f,
                                    rollY = Random.nextFloat() * 2f * PI.toFloat(),
                                    rollSpeedY = Random.nextFloat() * 6f + 3f,
                                    width = Random.nextFloat() * 10f + 8f,
                                    height = Random.nextFloat() * 18f + 12f,
                                    color = palette.random(),
                                    shapeType = i % 3
                                )
                            )
                        }
                    }

                    confettiList.forEach { p ->
                        val t = elapsedSeconds
                        val px = (p.x + p.vx * t + sin(t * 3.5f + p.x) * 32f).mod(canvasW)
                        val py = p.y + p.vy * t

                        if (py in -60f..canvasH + 60f) {
                            val cosX = cos(p.rollX + t * p.rollSpeedX)
                            val sinY = sin(p.rollY + t * p.rollSpeedY)
                            val effWidth = (p.width * abs(cosX)).coerceAtLeast(1.5f)
                            val isBackside = cosX < 0f
                            val flakeColor = if (isBackside) p.color.copy(alpha = alpha * 0.65f) else p.color.copy(alpha = alpha * 0.95f)

                            rotate(sinY * 45f, pivot = Offset(px, py)) {
                                when (p.shapeType) {
                                    1 -> {
                                        // Circular Sequin with 3D elliptical squeeze
                                        drawOval(
                                            color = flakeColor,
                                            topLeft = Offset(px - effWidth / 2f, py - p.height * 0.4f),
                                            size = Size(effWidth, p.height * 0.8f)
                                        )
                                    }
                                    2 -> {
                                        // Wavy Ribbon Streamer
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
                                        // Rectangular paper foil strip with 3D flip
                                        drawRect(
                                            color = flakeColor,
                                            topLeft = Offset(px - effWidth / 2f, py - p.height / 2f),
                                            size = Size(effWidth, p.height)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                CelebrationType.SHOOTING_STARS -> {
                    // Realistic Shooting Stars: Comets cutting across sky with gradient tail & stardust
                    if (meteorList.isEmpty()) {
                        val count = 28
                        for (i in 0 until count) {
                            val startX = Random.nextFloat() * canvasW * 0.9f
                            val startY = -Random.nextFloat() * 120f
                            val speed = Random.nextFloat() * 550f + 450f
                            val angle = (0.65f + Random.nextFloat() * 0.25f) * PI.toFloat() // Diagonal down-right
                            meteorList.add(
                                MeteorParticle(
                                    startX = startX,
                                    startY = startY,
                                    vx = cos(angle) * speed,
                                    vy = sin(angle) * speed,
                                    length = Random.nextFloat() * 120f + 80f,
                                    color = if (i % 2 == 0) Color(0xFFFFD700) else Color(0xFF00F0FF),
                                    startTime = Random.nextFloat() * 1.2f,
                                    duration = Random.nextFloat() * 0.8f + 0.6f
                                )
                            )
                        }
                    }

                    meteorList.forEach { m ->
                        if (elapsedSeconds >= m.startTime) {
                            val t = elapsedSeconds - m.startTime
                            if (t <= m.duration) {
                                val progress = t / m.duration
                                val headX = m.startX + m.vx * t
                                val headY = m.startY + m.vy * t
                                val tailX = headX - (m.vx / 500f) * m.length
                                val tailY = headY - (m.vy / 500f) * m.length
                                val mAlpha = (1f - progress) * alpha

                                // Glowing Comet Tail
                                drawLine(
                                    brush = Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = mAlpha), m.color.copy(alpha = mAlpha * 0.8f), Color.Transparent),
                                        start = Offset(headX, headY),
                                        end = Offset(tailX, tailY)
                                    ),
                                    start = Offset(headX, headY),
                                    end = Offset(tailX, tailY),
                                    strokeWidth = 3.5f
                                )

                                // Brilliant Meteor Head
                                drawCircle(
                                    color = Color.White.copy(alpha = mAlpha),
                                    radius = 4.5f,
                                    center = Offset(headX, headY)
                                )
                                drawCircle(
                                    color = m.color.copy(alpha = mAlpha * 0.6f),
                                    radius = 11f,
                                    center = Offset(headX, headY)
                                )

                                // Starburst star
                                drawStar(
                                    center = Offset(headX, headY),
                                    radius = 14f * (1f - progress * 0.5f),
                                    points = 5,
                                    color = Color.White.copy(alpha = mAlpha * 0.9f),
                                    rotation = t * 180f
                                )
                            }
                        }
                    }
                }

                CelebrationType.GOLDEN_RINGS -> {
                    // Concentric radiant expanding victory rings with star corona
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.40f
                    val ringCount = 5

                    for (r in 0 until ringCount) {
                        val delayOffset = r * 0.32f
                        val ringProgress = ((elapsedSeconds - delayOffset) / 1.4f).coerceIn(0f, 1f)
                        if (ringProgress > 0f) {
                            val maxRadius = canvasW * 0.7f
                            val ringRadius = ringProgress * maxRadius
                            val ringAlpha = (1f - ringProgress) * alpha * 0.85f
                            val ringColor = palette[r % palette.size]

                            // Outer shockwave
                            drawCircle(
                                color = ringColor.copy(alpha = ringAlpha),
                                radius = ringRadius,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = (8f * (1f - ringProgress)).coerceAtLeast(1.5f))
                            )

                            // Inner golden aura
                            drawCircle(
                                color = Color(0xFFFFD700).copy(alpha = ringAlpha * 0.3f),
                                radius = ringRadius * 0.92f,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    // Rotating golden star corona at center
                    val coronaAlpha = (sin(elapsedSeconds * 4f) * 0.2f + 0.8f) * alpha
                    drawStar(
                        center = Offset(centerX, centerY),
                        radius = 36f,
                        points = 8,
                        color = Color(0xFFFFD700).copy(alpha = coronaAlpha),
                        rotation = elapsedSeconds * 60f
                    )
                }

                CelebrationType.BALLOONS_FLOAT -> {
                    // 3D Glossy Balloons & Hearts rising upwards
                    if (balloonList.isEmpty()) {
                        val count = 28
                        for (i in 0 until count) {
                            balloonList.add(
                                BalloonParticle(
                                    x = Random.nextFloat() * canvasW,
                                    y = canvasH + Random.nextFloat() * 220f,
                                    vy = -(Random.nextFloat() * 170f + 150f),
                                    swayOffset = Random.nextFloat() * 2f * PI.toFloat(),
                                    swaySpeed = Random.nextFloat() * 2.8f + 1.6f,
                                    radius = Random.nextFloat() * 18f + 18f,
                                    color = palette.random(),
                                    isHeart = i % 4 == 0
                                )
                            )
                        }
                    }

                    balloonList.forEach { b ->
                        val t = elapsedSeconds
                        val px = b.x + sin(t * b.swaySpeed + b.swayOffset) * 32f
                        val py = b.y + b.vy * t

                        if (py in -120f..canvasH + 150f) {
                            // Balloon 3D Body
                            drawCircle(
                                color = b.color.copy(alpha = alpha * 0.9f),
                                radius = b.radius,
                                center = Offset(px, py)
                            )
                            // 3D Specular Highlight Spot
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.55f),
                                radius = b.radius * 0.28f,
                                center = Offset(px - b.radius * 0.32f, py - b.radius * 0.32f)
                            )
                            // Soft edge shadow for spherical realism
                            drawCircle(
                                color = Color.Black.copy(alpha = alpha * 0.15f),
                                radius = b.radius,
                                center = Offset(px, py),
                                style = Stroke(width = 2f)
                            )
                            // Wavy pendulum balloon string
                            val stringPath = Path().apply {
                                moveTo(px, py + b.radius)
                                quadraticTo(px + sin(t * 4f) * 6f, py + b.radius + 14f, px, py + b.radius + 28f)
                            }
                            drawPath(
                                path = stringPath,
                                color = Color(0x80FFFFFF),
                                style = Stroke(width = 1.6f)
                            )
                        }
                    }
                }

                CelebrationType.CONSOLATION_GLOW -> {
                    // Gentle soothing aura and rising comforting ember orbs
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.40f
                    val pulse = (sin(elapsedSeconds * 4f) * 0.5f + 0.5f)
                    val auraRadius = canvasW * 0.52f + pulse * 32f

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0x7500E5FF),
                                Color(0x403B82F6),
                                Color(0x0010B981)
                            ),
                            center = Offset(centerX, centerY),
                            radius = auraRadius
                        ),
                        radius = auraRadius,
                        center = Offset(centerX, centerY)
                    )

                    // Soothing rising glowing embers
                    for (i in 0 until 18) {
                        val emberPhase = (elapsedSeconds * 0.8f + i * 0.22f) % 2.5f
                        val progress = emberPhase / 2.5f
                        val ex = centerX + sin(progress * 6f + i * 1.5f) * (canvasW * 0.35f)
                        val ey = canvasH * 0.75f - progress * (canvasH * 0.55f)
                        val emberAlpha = (sin(progress * PI.toFloat()) * alpha * 0.85f).coerceIn(0f, 1f)
                        val emberRadius = 4f + (i % 4) * 2f

                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = emberAlpha * 0.4f),
                            radius = emberRadius * 2.5f,
                            center = Offset(ex, ey)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = emberAlpha),
                            radius = emberRadius * 0.8f,
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

package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.live.SessionState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D-perspective glowing quantum orb inspired directly by the reference visual:
 * - Multilayered glowing neon nucleus with energetic core
 * - 4 tilted 3D elliptical orbital rings
 * - Orbiting photon/electron nodes with luminous motion trails
 * - Ambient energy plasma sparkles
 * - Audio-reactive amplitude dilation and pulsating waveform rings
 */
@Composable
fun OrbVisualizer(
    state: SessionState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbMotion")

    // Master continuous rotation angles
    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FastRotation"
    )

    val rotationMedium by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MediumRotation"
    )

    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SlowRotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Pre-calculated star particles
    val particleOffsets = remember {
        List(60) {
            val angle = (it * 6.28f / 60f)
            val distFactor = (0.2f + (it % 10) * 0.08f)
            val size = (1.5f + (it % 5) * 0.8f)
            val speedFactor = 0.8f + (it % 4) * 0.3f
            Particle(angle, distFactor, size, speedFactor)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val baseRadius = minOf(centerX, centerY) * 0.46f

            // Dynamic reactive scale influenced by audio amplitude and state
            val stateMultiplier = when (state) {
                SessionState.DISCONNECTED -> 0.88f
                SessionState.CONNECTING -> 1.0f + (corePulse - 1f) * 1.5f
                SessionState.LISTENING -> 1.0f + amplitude * 0.45f
                SessionState.SPEAKING -> 1.05f + amplitude * 0.65f
                SessionState.ERROR -> 0.9f
            }

            val currentCoreRadius = baseRadius * corePulse * stateMultiplier

            // 1. Draw outer audio-reactive sound wave rings (when listening or speaking)
            if (state == SessionState.LISTENING || state == SessionState.SPEAKING) {
                val waveAlpha = (0.25f + amplitude * 0.5f).coerceIn(0.1f, 0.7f)
                val waveColor = if (state == SessionState.SPEAKING) {
                    Color(0xFF00F0FF).copy(alpha = waveAlpha)
                } else {
                    Color(0xFF38BDF8).copy(alpha = waveAlpha * 0.8f)
                }

                drawCircle(
                    color = waveColor,
                    radius = currentCoreRadius * 1.55f + amplitude * 40f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.5f + amplitude * 4f)
                )

                drawCircle(
                    color = waveColor.copy(alpha = waveAlpha * 0.5f),
                    radius = currentCoreRadius * 1.85f + amplitude * 70f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.5f)
                )
            }

            // 2. Draw deep ambient background glow behind core
            val glowColor = when (state) {
                SessionState.SPEAKING -> Color(0xFF00E5FF)
                SessionState.LISTENING -> Color(0xFF0284C7)
                SessionState.CONNECTING -> Color(0xFF6366F1)
                SessionState.ERROR -> Color(0xFFEF4444)
                SessionState.DISCONNECTED -> Color(0xFF1E3A8A)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.45f + amplitude * 0.35f),
                        glowColor.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = currentCoreRadius * 2.2f
                ),
                radius = currentCoreRadius * 2.2f,
                center = Offset(centerX, centerY)
            )

            // 3. Draw 4 tilted 3D elliptical orbits with nodes & luminous trails
            val speedBoost = when (state) {
                SessionState.CONNECTING -> 1.8f
                SessionState.SPEAKING -> 1.4f + amplitude * 1.5f
                SessionState.LISTENING -> 1.1f + amplitude * 0.8f
                else -> 1.0f
            }

            // Orbit 1: Tilted -30 deg
            draw3DOrbit(
                centerX = centerX,
                centerY = centerY,
                radiusX = baseRadius * 1.45f * stateMultiplier,
                radiusY = baseRadius * 0.65f * stateMultiplier,
                tiltAngleDeg = -30f,
                rotationProgress = ((rotationMedium * speedBoost) % 360f) / 360f,
                orbitColor = Color(0xFF38BDF8).copy(alpha = 0.65f),
                electronColor = Color(0xFFE0F2FE),
                nodeCount = 4
            )

            // Orbit 2: Tilted +45 deg
            draw3DOrbit(
                centerX = centerX,
                centerY = centerY,
                radiusX = baseRadius * 1.35f * stateMultiplier,
                radiusY = baseRadius * 0.55f * stateMultiplier,
                tiltAngleDeg = 45f,
                rotationProgress = ((rotationFast * speedBoost) % 360f) / 360f,
                orbitColor = Color(0xFF00F0FF).copy(alpha = 0.85f),
                electronColor = Color(0xFFFFFFFF),
                nodeCount = 3
            )

            // Orbit 3: Tilted +85 deg (nearly vertical)
            draw3DOrbit(
                centerX = centerX,
                centerY = centerY,
                radiusX = baseRadius * 1.5f * stateMultiplier,
                radiusY = baseRadius * 0.5f * stateMultiplier,
                tiltAngleDeg = 85f,
                rotationProgress = (((rotationSlow * speedBoost) % 360f) / 360f),
                orbitColor = Color(0xFF2563EB).copy(alpha = 0.7f),
                electronColor = Color(0xFF93C5FD),
                nodeCount = 5
            )

            // Orbit 4: Horizontal ring
            draw3DOrbit(
                centerX = centerX,
                centerY = centerY,
                radiusX = baseRadius * 1.6f * stateMultiplier,
                radiusY = baseRadius * 0.7f * stateMultiplier,
                tiltAngleDeg = 5f,
                rotationProgress = (((rotationFast * 0.75f * speedBoost) % 360f) / 360f),
                orbitColor = Color(0xFF00E5FF).copy(alpha = 0.75f),
                electronColor = Color(0xFFFFFFFF),
                nodeCount = 3
            )

            // 4. Central Energetic Core Nucleus
            val nucleusRadius = currentCoreRadius * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFF67E8F9),
                        Color(0xFF0284C7),
                        Color(0xFF0369A1).copy(alpha = 0.8f),
                        Color(0xFF0F172A).copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = nucleusRadius
                ),
                radius = nucleusRadius,
                center = Offset(centerX, centerY)
            )

            // 5. Draw inner quantum dust particles inside/around nucleus
            particleOffsets.forEach { p ->
                val dynamicAngle = p.baseAngle + (rotationSlow * 0.015f * p.speedFactor)
                val dist = nucleusRadius * p.distFactor * (1f + amplitude * 0.3f)
                val px = centerX + cos(dynamicAngle) * dist
                val py = centerY + sin(dynamicAngle) * dist

                drawCircle(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.85f),
                    radius = p.size * (1f + amplitude * 0.5f),
                    center = Offset(px, py)
                )
            }

            // 6. Draw central pinpoint energy spark
            drawCircle(
                color = Color.White,
                radius = (4f + amplitude * 6f),
                center = Offset(centerX, centerY)
            )
        }
    }
}

private data class Particle(
    val baseAngle: Float,
    val distFactor: Float,
    val size: Float,
    val speedFactor: Float
)

/**
 * Draws an inclined 3D ellipse with moving electron nodes and trailing light arcs
 */
private fun DrawScope.draw3DOrbit(
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    tiltAngleDeg: Float,
    rotationProgress: Float,
    orbitColor: Color,
    electronColor: Color,
    nodeCount: Int
) {
    val tiltRad = Math.toRadians(tiltAngleDeg.toDouble())
    val cosTilt = cos(tiltRad).toFloat()
    val sinTilt = sin(tiltRad).toFloat()

    // 1. Draw orbital ellipse path
    val steps = 80
    val path = Path()
    for (i in 0..steps) {
        val angle = (i * 2 * PI / steps)
        val ex = (radiusX * cos(angle)).toFloat()
        val ey = (radiusY * sin(angle)).toFloat()

        // 2D rotation for tilt
        val rotatedX = ex * cosTilt - ey * sinTilt + centerX
        val rotatedY = ex * sinTilt + ey * cosTilt + centerY

        if (i == 0) path.moveTo(rotatedX, rotatedY) else path.lineTo(rotatedX, rotatedY)
    }
    path.close()

    drawPath(
        path = path,
        color = orbitColor,
        style = Stroke(width = 1.8f)
    )

    // 2. Draw orbiting electrons with luminous trails
    for (n in 0 until nodeCount) {
        val baseNodeProgress = (rotationProgress + (n.toFloat() / nodeCount)) % 1f
        val nodeAngle = baseNodeProgress * 2 * PI

        // Electron position
        val ex = (radiusX * cos(nodeAngle)).toFloat()
        val ey = (radiusY * sin(nodeAngle)).toFloat()
        val nodeX = ex * cosTilt - ey * sinTilt + centerX
        val nodeY = ex * sinTilt + ey * cosTilt + centerY

        // Motion trail behind node (arc of 5 trailing points)
        val trailSteps = 6
        for (t in 1..trailSteps) {
            val trailAngle = nodeAngle - (t * 0.08)
            val tx = (radiusX * cos(trailAngle)).toFloat()
            val ty = (radiusY * sin(trailAngle)).toFloat()
            val trailX = tx * cosTilt - ty * sinTilt + centerX
            val trailY = tx * sinTilt + ey * cosTilt + centerY

            val trailAlpha = (1f - (t.toFloat() / trailSteps)) * 0.6f
            drawCircle(
                color = electronColor.copy(alpha = trailAlpha),
                radius = 3.5f - (t * 0.4f),
                center = Offset(trailX, trailY)
            )
        }

        // Draw electron outer glow
        drawCircle(
            color = Color(0xFF00F0FF).copy(alpha = 0.5f),
            radius = 8.5f,
            center = Offset(nodeX, nodeY)
        )

        // Draw bright electron core
        drawCircle(
            color = electronColor,
            radius = 4.2f,
            center = Offset(nodeX, nodeY)
        )

        // Occasional energetic connector line to center
        if (n == 0) {
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                start = Offset(nodeX, nodeY),
                end = Offset(centerX, centerY),
                strokeWidth = 1.2f
            )
        }
    }
}

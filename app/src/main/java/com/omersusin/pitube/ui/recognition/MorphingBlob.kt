package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MorphingBlob(
    amplitude: Float,
    levels: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(colorScheme) {
        listOf(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
        )
    }

    val ORB_COUNT = 12
    val ORB_GLOW_RADIUS_DP = 68f
    val ORBIT_RADIUS_MIN_DP = 28f
    val ORBIT_RADIUS_MAX_DP = 110f
    val ANGULAR_SPEED_MIN = 0.18f
    val ANGULAR_SPEED_MAX = 0.62f
    val VERTICAL_SQUASH = 0.68f
    val HUE_ROTATION_SPEED = 10f
    val MID_STOP = 0.42f
    val MID_ALPHA = 0.52f

    val density = LocalDensity.current
    val glowRadiusPx = with(density) { ORB_GLOW_RADIUS_DP.dp.toPx() }
    val orbitMinPx = with(density) { ORBIT_RADIUS_MIN_DP.dp.toPx() }
    val orbitMaxPx = with(density) { ORBIT_RADIUS_MAX_DP.dp.toPx() }

    val smoothedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "blobAmp",
    )

    val beatPulse = remember(levels) {
        if (levels.size < 8) 0f
        else {
            val recent = levels.takeLast(12)
            val avg = recent.average().toFloat()
            val last = recent.lastOrNull() ?: 0f
            val prev = recent.getOrNull(recent.size - 2) ?: 0f
            val isRising = last > prev && last > avg * 1.45f && last > 0.14f
            val isPeak = last > 0.18f && last == recent.maxOrNull()
            if (isRising || isPeak) ((last - avg).coerceIn(0f, 0.6f) / 0.6f) else 0f
        }
    }
    val animatedBeat by animateFloatAsState(
        targetValue = beatPulse,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "beatPulse",
    )

    BoxWithConstraints(
        modifier = modifier.clip(CircleShape)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val orbs = remember(widthPx, heightPx, ORB_COUNT) {
            val rng = Random(0xA0BEEFL)
            List(ORB_COUNT) { i ->
                OrbSpec(
                    centerX = rng.nextFloat() * widthPx,
                    centerY = rng.nextFloat() * heightPx,
                    orbitT = rng.nextFloat(),
                    angularT = rng.nextFloat(),
                    phase = rng.nextFloat() * (2f * Math.PI.toFloat()),
                    colorIndex = i,
                    hueSeed = rng.nextFloat() * 360f,
                )
            }
        }

        var time by remember { mutableStateOf(0f) }
        var tick by remember { mutableStateOf(0L) }

        LaunchedEffect(Unit) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastNanos == 0L) lastNanos = now
                    val dt = (now - lastNanos) / 1_000_000_000f
                    lastNanos = now
                    time += dt
                    tick++
                }
            }
        }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tick

            val rhythmScale = 1f + smoothedAmp * 0.35f + animatedBeat * 0.28f
            val wobble = sin(time * 1.8f) * 0.06f * smoothedAmp
            val ampScale = (0.62f + smoothedAmp * 0.82f + animatedBeat * 0.22f + wobble).coerceIn(0.5f, 1.7f)

            orbs.forEach { orb ->
                val orbitR =
                    (orbitMinPx + orb.orbitT * (orbitMaxPx - orbitMinPx).coerceAtLeast(0f)) *
                        (0.78f + smoothedAmp * 0.65f + animatedBeat * 0.32f)
                val angularSpeed =
                    ANGULAR_SPEED_MIN + orb.angularT * (ANGULAR_SPEED_MAX - ANGULAR_SPEED_MIN)
                val speedBoost = 1f + animatedBeat * 0.9f + smoothedAmp * 0.4f
                val baseColor = palette[orb.colorIndex % palette.size]
                val angle = orb.phase + angularSpeed * speedBoost * time
                val px = orb.centerX + cos(angle) * orbitR
                val py = orb.centerY + sin(angle) * orbitR * VERTICAL_SQUASH
                val shifted = shiftHue(baseColor, orb.hueSeed + time * HUE_ROTATION_SPEED)

                val pulseAlpha = (0.72f + animatedBeat * 0.28f).coerceIn(0f, 1f)
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to shifted.copy(alpha = pulseAlpha),
                        MID_STOP to shifted.copy(alpha = MID_ALPHA * pulseAlpha),
                        1f to Color.Transparent,
                    ),
                    center = Offset(px, py),
                    radius = glowRadiusPx * ampScale,
                )

                drawCircle(
                    brush = brush,
                    radius = glowRadiusPx * ampScale,
                    center = Offset(px, py),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

private data class OrbSpec(
    val centerX: Float,
    val centerY: Float,
    val orbitT: Float,
    val angularT: Float,
    val phase: Float,
    val colorIndex: Int,
    val hueSeed: Float,
)

private fun shiftHue(c: Color, deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
        hsv,
    )
    hsv[0] = ((hsv[0] + deg) % 360f + 360f) % 360f
    val rgb = android.graphics.Color.HSVToColor(hsv)
    return Color(rgb).copy(alpha = c.alpha)
}

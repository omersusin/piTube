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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MorphingBlob(
    amplitude: Float,
    levels: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val palette = remember(cs) {
        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer, cs.secondaryContainer)
    }

    val smoothedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 340f),
        label = "blobAmp",
    )

    val bass = remember(levels, smoothedAmp) {
        if (levels.size >= 8) levels.takeLast(8).take(3).average().toFloat().coerceIn(0f, 1f)
        else smoothedAmp
    }
    val mid = remember(levels, smoothedAmp) {
        if (levels.size >= 8) levels.takeLast(8).drop(3).take(3).average().toFloat().coerceIn(0f, 1f)
        else smoothedAmp * 0.75f
    }
    val treble = remember(levels, smoothedAmp) {
        if (levels.size >= 8) levels.takeLast(8).drop(6).take(2).average().toFloat().coerceIn(0f, 1f)
        else smoothedAmp * 0.60f
    }
    val beatPulse = remember(levels) {
        if (levels.size < 6) 0f else {
            val recent = levels.takeLast(8)
            val avg = recent.average().toFloat()
            val last = recent.lastOrNull() ?: 0f
            val prev = recent.getOrNull(recent.size - 2) ?: 0f
            val isRising = last > prev && last > avg * 1.25f && last > 0.10f
            val isPeak = last > 0.14f && last + 1e-6f >= (recent.maxOrNull() ?: 0f)
            if (isRising || isPeak) ((last - avg).coerceIn(0f, 0.6f) / 0.6f) else 0f
        }
    }
    val animatedBeat by animateFloatAsState(targetValue = beatPulse, animationSpec = spring(dampingRatio = 0.35f, stiffness = 720f), label = "beatPulse")

    val density = LocalDensity.current
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

    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer(clip = true, shape = CircleShape),
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val cx = wPx / 2f
        val cy = hPx / 2f
        val baseRadius = minOf(wPx, hPx) * 0.34f

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen),
        ) {
            @Suppress("UNUSED_EXPRESSION") tick

            val rhythmScale = 1f + bass * 0.55f + animatedBeat * 0.42f + treble * 0.10f
            val hueShift = time * (6f + mid * 18f)
            val pointCount = 8
            val outerGlow = baseRadius * (1.50f + bass * 0.45f + animatedBeat * 0.22f)
            val innerRadius = baseRadius * rhythmScale

            val glowPalette = palette
            val primaryShifted = shiftHue(glowPalette[0], hueShift)
            val secondaryShifted = shiftHue(glowPalette[1], hueShift * 0.6f)
            val tertiaryShifted = shiftHue(glowPalette[2], hueShift * 0.35f)

            val outerBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to primaryShifted.copy(alpha = 0.42f + bass * 0.22f + animatedBeat * 0.2f),
                    0.48f to secondaryShifted.copy(alpha = 0.28f + mid * 0.16f),
                    0.78f to tertiaryShifted.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = outerGlow,
            )
            drawCircle(brush = outerBrush, radius = outerGlow, center = Offset(cx, cy))

            val points = List(pointCount) { i ->
                val angle = (2f * PI.toFloat() * i / pointCount) + time * (0.22f + mid * 0.32f + animatedBeat * 0.15f)
                val wobble = sin(time * 1.7f + i * 0.9f) * (0.09f + treble * 0.05f) * (0.7f + bass * 0.6f)
                val bassBump = bass * 0.28f * sin(time * 2.2f + i * 1.3f).coerceIn(-1f, 1f)
                val beatBump = animatedBeat * 0.32f * cos(time * 3.4f + i).coerceIn(-1f, 1f)
                val r = innerRadius * (1f + wobble + bassBump + beatBump)
                Offset(cx + cos(angle) * r, cy + sin(angle) * r)
            }

            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points[0].x, points[0].y)
                    for (i in points.indices) {
                        val cur = points[i]
                        val next = points[(i + 1) % points.size]
                        val midX = (cur.x + next.x) / 2f
                        val midY = (cur.y + next.y) / 2f
                        if (i == 0) {
                            val prev = points.last()
                            val startMidX = (prev.x + cur.x) / 2f
                            val startMidY = (prev.y + cur.y) / 2f
                            moveTo(startMidX, startMidY)
                        }
                        val c1x = cur.x + (midX - cur.x) * 0.55f
                        val c1y = cur.y + (midY - cur.y) * 0.55f
                        quadraticTo(cur.x, cur.y, midX, midY)
                    }
                    close()
                }
            }

            val bodyBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to primaryShifted.copy(alpha = 0.96f),
                    0.42f to secondaryShifted.copy(alpha = 0.88f),
                    0.72f to tertiaryShifted.copy(alpha = 0.62f),
                    1f to tertiaryShifted.copy(alpha = 0.38f),
                ),
                center = Offset(cx - baseRadius * 0.18f, cy - baseRadius * 0.22f),
                radius = innerRadius * 1.35f,
            )
            drawPath(path = path, brush = bodyBrush, style = Fill)

            val highlightRadius = innerRadius * 0.32f * (0.9f + treble * 0.35f + animatedBeat * 0.2f)
            val hx = cx - innerRadius * 0.22f + sin(time * 0.9f) * innerRadius * 0.06f
            val hy = cy - innerRadius * 0.28f + cos(time * 1.1f) * innerRadius * 0.05f
            val highlightBrush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.34f + treble * 0.18f),
                1f to Color.Transparent,
                center = Offset(hx, hy),
                radius = highlightRadius,
            )
            drawCircle(brush = highlightBrush, radius = highlightRadius, center = Offset(hx, hy))

            if (animatedBeat > 0.12f || bass > 0.22f) {
                val ringAlpha = (animatedBeat * 0.42f + bass * 0.18f).coerceIn(0f, 0.42f)
                val ringRadius = innerRadius * (1.08f + animatedBeat * 0.18f)
                drawCircle(
                    color = cs.onPrimary.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = wPx * 0.007f),
                )
            }
        }
    }
}

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

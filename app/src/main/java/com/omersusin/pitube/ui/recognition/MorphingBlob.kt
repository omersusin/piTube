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
import androidx.compose.runtime.rememberUpdatedState
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
    // Theme tokens can be near-gray (muted dynamic palettes, light themes),
    // which reads as a dull blob. Normalize every entry to a saturated,
    // mid-lightness color so the blob is ALWAYS vivid, then let the existing
    // hue-shift animation provide variety.
    val palette = remember(cs) {
        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer, cs.secondaryContainer)
            .map { it.vivid() }
    }

    val smoothedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 340f),
        label = "blobAmp",
    )

    // Band targets are recomputed whenever a fresh chunk arrives; the rendered
    // values below are per-frame exponential smooths of these targets so the
    // blob flows between chunk updates instead of stepping discretely.
    val latestLevels by rememberUpdatedState(levels)
    fun targetBand(range: IntRange, fallback: Float): Float {
        val ls = latestLevels
        return if (ls.size >= 8) ls.takeLast(8).drop(range.first).take(range.count())
            .average().toFloat().coerceIn(0f, 1f)
        else fallback
    }

    var smoothBass by remember { mutableStateOf(0f) }
    var smoothMid by remember { mutableStateOf(0f) }
    var smoothTreble by remember { mutableStateOf(0f) }
    var smoothBeat by remember { mutableStateOf(0f) }
    fun targetBeat(): Float {
        val recent = latestLevels.takeLast(8)
        if (recent.size < 6) return 0f
        val avg = recent.average().toFloat()
        val last = recent.lastOrNull() ?: 0f
        val prev = recent.getOrNull(recent.size - 2) ?: 0f
        val isRising = last > prev && last > avg * 1.25f && last > 0.10f
        val isPeak = last > 0.14f && last + 1e-6f >= (recent.maxOrNull() ?: 0f)
        return if (isRising || isPeak) ((last - avg).coerceIn(0f, 0.6f) / 0.6f) else 0f
    }

    val density = LocalDensity.current
    var time by remember { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos == 0L) lastNanos = now
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(1e-4f, 0.05f)
                lastNanos = now
                time += dt
                tick++
                // Exponential smoothing toward current band targets — fast
                // attack (12/s), slower release (5/s) for organic motion.
                fun follow(current: Float, target: Float): Float =
                    current + (target - current) * (if (target > current) 1f - kotlin.math.exp(-dt * 12f) else 1f - kotlin.math.exp(-dt * 5f))
                smoothBass = follow(smoothBass, targetBand(0..2, smoothedAmp))
                smoothMid = follow(smoothMid, targetBand(3..5, smoothedAmp * 0.75f))
                smoothTreble = follow(smoothTreble, targetBand(6..7, smoothedAmp * 0.60f))
                smoothBeat = follow(smoothBeat, targetBeat())
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

            val rhythmScale = 1f + smoothBass * 0.55f + smoothBeat * 0.42f + smoothTreble * 0.10f
            val hueShift = time * (6f + smoothMid * 18f)
            val pointCount = 8
            val outerGlow = baseRadius * (1.50f + smoothBass * 0.45f + smoothBeat * 0.22f)
            val innerRadius = baseRadius * rhythmScale

            val glowPalette = palette
            val primaryShifted = shiftHue(glowPalette[0], hueShift)
            val secondaryShifted = shiftHue(glowPalette[1], hueShift * 0.6f)
            val tertiaryShifted = shiftHue(glowPalette[2], hueShift * 0.35f)

            val outerBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to primaryShifted.copy(alpha = 0.42f + smoothBass * 0.22f + smoothBeat * 0.2f),
                    0.48f to secondaryShifted.copy(alpha = 0.28f + smoothMid * 0.16f),
                    0.78f to tertiaryShifted.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = outerGlow,
            )
            drawCircle(brush = outerBrush, radius = outerGlow, center = Offset(cx, cy))

            val points = List(pointCount) { i ->
                val angle = (2f * PI.toFloat() * i / pointCount) + time * (0.22f + smoothMid * 0.32f + smoothBeat * 0.15f)
                val wobble = sin(time * 1.7f + i * 0.9f) * (0.09f + smoothTreble * 0.05f) * (0.7f + smoothBass * 0.6f)
                val bassBump = smoothBass * 0.28f * sin(time * 2.2f + i * 1.3f).coerceIn(-1f, 1f)
                val beatBump = smoothBeat * 0.32f * cos(time * 3.4f + i).coerceIn(-1f, 1f)
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

            val highlightRadius = innerRadius * 0.32f * (0.9f + smoothTreble * 0.35f + smoothBeat * 0.2f)
            val hx = cx - innerRadius * 0.22f + sin(time * 0.9f) * innerRadius * 0.06f
            val hy = cy - innerRadius * 0.28f + cos(time * 1.1f) * innerRadius * 0.05f
            val highlightBrush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.34f + smoothTreble * 0.18f),
                1f to Color.Transparent,
                center = Offset(hx, hy),
                radius = highlightRadius,
            )
            drawCircle(brush = highlightBrush, radius = highlightRadius, center = Offset(hx, hy))

            if (smoothBeat > 0.12f || smoothBass > 0.22f) {
                val ringAlpha = (smoothBeat * 0.42f + smoothBass * 0.18f).coerceIn(0f, 0.42f)
                val ringRadius = innerRadius * (1.08f + smoothBeat * 0.18f)
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

/**
 * Forces a theme color into the vivid range: saturation >= 0.60 and
 * brightness clamped to 0.40..0.85. Keeps hue (and alpha untouched), so the
 * blob keeps the account's palette personality without ever looking gray.
 */
private fun Color.vivid(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsv,
    )
    hsv[1] = hsv[1].coerceAtLeast(0.60f)
    hsv[2] = hsv[2].coerceIn(0.40f, 0.85f)
    val rgb = android.graphics.Color.HSVToColor(hsv)
    return Color(rgb).copy(alpha = alpha)
}

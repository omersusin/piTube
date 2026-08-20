package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Organic gooey metaball for the Song listening state.
 *
 * A single closed blob whose outline is a Catmull-Rom-smoothed ring of control
 * points, each point's radius modulated by several sine waves at different
 * frequencies and speeds. Layering incommensurate frequencies is what makes the
 * silhouette read as liquid rather than as a pulsing circle: no two lobes ever
 * return to the same place at the same time.
 *
 * The live microphone level ([amplitude], 0..1) drives three things at once —
 * how deep the lobes deform, the overall scale, and the glow intensity — so the
 * blob visibly reacts to sound. At amplitude 0 it keeps a slow idle breath so
 * the idle state still looks alive.
 *
 * Colors come from [MaterialTheme] so the blob matches the app palette. Drawn
 * entirely in Compose Canvas: no external assets or dependencies.
 */
@Composable
fun MorphingBlob(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // Spring-smoothed so a spiky mic level becomes a fluid motion rather than a
    // jitter. Underdamped on purpose: the blob overshoots slightly and settles,
    // which is what makes it feel gooey instead of mechanical.
    val amp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "blobAmp",
    )

    // Frame-driven clock. A plain accumulating time value keeps every wave
    // continuous; an infiniteRepeatable would restart phases at each cycle
    // boundary and produce a visible hitch in the outline.
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos == 0L) lastNanos = now
                time.floatValue += (now - lastNanos) / 1_000_000_000f
                lastNanos = now
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                // Offscreen so the additive glow layers composite against this
                // blob's own pixels instead of whatever is already on screen.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val t = time.floatValue
            val center = Offset(w / 2f, h / 2f)
            // Leave headroom for the glow so a loud peak can't clip at the edge.
            val baseRadius = minOf(w, h) * 0.30f * (1f + amp * 0.16f)

            val path = blobPath(
                center = center,
                baseRadius = baseRadius,
                time = t,
                amplitude = amp,
            )

            // ── Outer glow: a few progressively larger, fainter halos ─────────
            // Cheaper and softer-looking than a real blur, and it keeps the
            // silhouette crisp because the fill is drawn last.
            val glowRadius = baseRadius * 2.1f
            repeat(GLOW_LAYERS) { layer ->
                val spread = 1f + layer * 0.42f
                val alpha = (0.20f - layer * 0.05f) * (0.55f + amp * 0.75f)
                if (alpha <= 0f) return@repeat
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to tertiary.copy(alpha = alpha),
                            0.55f to primary.copy(alpha = alpha * 0.55f),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = glowRadius * spread,
                    ),
                    radius = glowRadius * spread,
                    center = center,
                    blendMode = BlendMode.Plus,
                )
            }

            // ── Body ─────────────────────────────────────────────────────────
            // Gradient offset toward the top-left so the blob reads as a
            // three-dimensional droplet with a light source rather than a flat
            // silhouette.
            val bodyCenter = Offset(
                center.x - baseRadius * 0.30f,
                center.y - baseRadius * 0.34f,
            )
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to lightenToward(tertiary, 0.35f + amp * 0.25f),
                        0.45f to primary,
                        1f to secondary,
                    ),
                    center = bodyCenter,
                    radius = baseRadius * 1.85f,
                ),
            )

            // ── Specular highlight ───────────────────────────────────────────
            // Small, offset, additive: sells the wet/gooey surface. It drifts on
            // its own slow phase so it doesn't look pinned to the shape.
            val hx = center.x - baseRadius * (0.34f + 0.05f * sin(t * 0.7f))
            val hy = center.y - baseRadius * (0.40f + 0.05f * cos(t * 0.5f))
            val highlightRadius = baseRadius * (0.42f + amp * 0.10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.30f + amp * 0.16f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(hx, hy),
                    radius = highlightRadius,
                ),
                radius = highlightRadius,
                center = Offset(hx, hy),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

private const val CONTROL_POINTS = 14
private const val GLOW_LAYERS = 3

/**
 * The blob outline: [CONTROL_POINTS] radii around a circle, each modulated by
 * three sine waves, then joined with a Catmull-Rom spline.
 *
 * The three waves use deliberately non-integer frequency ratios (3 / 5.3 / 2.1)
 * and different speeds. Integer ratios would resynchronize every cycle and the
 * blob would visibly repeat a fixed shape.
 */
private fun blobPath(
    center: Offset,
    baseRadius: Float,
    time: Float,
    amplitude: Float,
): Path {
    // Idle floor keeps the shape breathing at silence; amplitude adds the reactive
    // deformation on top. Capped so the blob can never fold in on itself.
    val deform = (0.06f + amplitude * 0.20f).coerceAtMost(0.30f)

    val points = ArrayList<Offset>(CONTROL_POINTS)
    for (i in 0 until CONTROL_POINTS) {
        val angle = (i.toFloat() / CONTROL_POINTS) * 2f * PI.toFloat()
        val wobble =
            sin(angle * 3f + time * 1.15f) * 0.55f +
                sin(angle * 5.3f - time * 0.80f) * 0.30f +
                sin(angle * 2.1f + time * 1.70f) * 0.15f
        // Slow global breath, independent of the mic, so idle is never static.
        val breath = 1f + sin(time * 0.85f) * 0.030f
        val r = baseRadius * breath * (1f + wobble * deform)
        points += Offset(
            center.x + cos(angle) * r,
            center.y + sin(angle) * r,
        )
    }
    return closedSpline(points)
}

/**
 * Closed Catmull-Rom spline through [points], emitted as cubic Béziers.
 *
 * Catmull-Rom passes through every control point, so the radii computed above
 * are the actual silhouette; the standard 1/6 tangent scale converts each
 * segment to its equivalent cubic.
 */
private fun closedSpline(points: List<Offset>): Path {
    val path = Path()
    val n = points.size
    if (n < 3) return path

    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]

        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)

        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.close()
    return path
}

/** Blend [color] toward white by [fraction] (0 = unchanged, 1 = white). */
private fun lightenToward(color: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = color.red + (1f - color.red) * f,
        green = color.green + (1f - color.green) * f,
        blue = color.blue + (1f - color.blue) * f,
        alpha = color.alpha,
    )
}

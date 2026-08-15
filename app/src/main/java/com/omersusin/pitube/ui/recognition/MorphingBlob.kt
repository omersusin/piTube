package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Morphing gradient blob for the Song listening state. A smooth Bézier blob
 * whose control points continuously rotate/morph, plus an overall ballooning
 * term driven by the live microphone amplitude — so the blob visibly reacts to
 * ambient audio rather than playing a fixed animation loop. Painted with a
 * pink→purple gradient (matching the spec's reference "Keep going / Almost
 * there" screens). No external dependencies.
 */
@Composable
fun MorphingBlob(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val pink = Color(0xFFE91E63)
    val purple = Color(0xFF7C4DFF)

    val smoothedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "blobAmp",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "blobTime")
    val elapsed by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 8000, easing = LinearEasing)),
        label = "blobPhase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center = Offset(w / 2f, h / 2f)
        val baseRadius = minOf(w, h) * 0.34f

        // Audio pushes the blob outwards on top of its continuous morph.
        val radius = baseRadius * (0.88f + smoothedAmp * 0.34f)
        val ripple = 0.14f + smoothedAmp * 0.22f
        val theta = elapsed * 0.0025f

        val blobPath =
            buildBlobPath(
                center = center,
                radius = radius,
                ripple = ripple,
                time = theta,
            )

        drawPath(
            path = blobPath,
            brush =
                Brush.linearGradient(
                    colors = listOf(pink, purple),
                    start = Offset(center.x - radius * 1.2f, center.y - radius * 1.2f),
                    end = Offset(center.x + radius * 1.2f, center.y + radius * 1.2f),
                ),
        )
    }
}

/**
 * Builds a closed smooth curve through [knotCount] polar points, each rocking
 * around [radius] with a phase-shifted ripple and drifting in angle over time,
 * then rounds it off with the midpoint cubic-Bézier technique (each segment
 * runs from the midpoint of two knots to the midpoint of the next two, guided
 * by the original knot) so the blob looks organic instead of polygonal.
 */
private fun buildBlobPath(
    center: Offset,
    radius: Float,
    ripple: Float,
    time: Float,
): Path {
    val knotCount = 12
    val phaseBase = time
    val churn = 1.7f * time

    val points = List(knotCount) { i ->
        val baseAngle = (i.toFloat() / knotCount) * 2f * PI.toFloat()
        val angle = baseAngle + phaseBase * 0.35f
        val k = radius * (1f + ripple * sin(baseAngle * 3f + churn + i * 1.1f))
        Offset(
            x = center.x + cos(angle) * k,
            y = center.y + sin(angle) * k,
        )
    }

    val path = Path()
    val n = points.size
    val start = midpoint(points[n - 1], points[0])
    path.moveTo(start.x, start.y)
    for (i in 0 until n) {
        val current = points[i]
        val next = midpoint(current, points[(i + 1) % n])
        // Guide the curve through `current` (the original knot).
        path.cubicTo(
            current.x, current.y,
            current.x, current.y,
            next.x, next.y,
        )
    }
    path.close()
    return path
}

private fun midpoint(a: Offset, b: Offset): Offset =
    Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
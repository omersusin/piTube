package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Google voice-search style "bodyless face" for the Voice listening state:
 * two eyebrow arcs, two static dot eyes and a mouth whose openness follows the
 * live microphone amplitude, so the face appears to talk along with the user.
 * Not phoneme-accurate lip-sync — just amplitude-driven mouth opening, with an
 * exponential-moving-average-style smoothing via a spring animation.
 *
 * Drawn entirely in Compose Canvas; no external assets or dependencies.
 */
@Composable
fun TalkingFace(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val faceColor = MaterialTheme.colorScheme.onSurface
    val smoothed by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 550f),
        label = "faceAmp",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val faceTop = h * 0.12f
        val browY = faceTop + h * 0.13f
        val eyeY = faceTop + h * 0.28f
        val mouthY = faceTop + h * 0.56f

        val eyeGap = w * 0.20f
        val eyeRadius = w * 0.026f
        val mouthHalf = w * 0.30f
        val browHalf = w * 0.15f
        val browLift = smoothed * h * 0.018f

        // ── Eyebrows ─────────────────────────────────────────────────────
        val browStroke = Stroke(width = w * 0.02f, cap = StrokeCap.Round)
        drawPath(
            eyebrowPath(cx - eyeGap, browY, browHalf, browLift),
            color = faceColor,
            style = browStroke,
        )
        drawPath(
            eyebrowPath(cx + eyeGap, browY, browHalf, browLift),
            color = faceColor,
            style = browStroke,
        )

        // ── Eyes (static dots) ────────────────────────────────────────────
        drawCircle(color = faceColor, radius = eyeRadius, center = androidx.compose.ui.geometry.Offset(cx - eyeGap, eyeY))
        drawCircle(color = faceColor, radius = eyeRadius, center = androidx.compose.ui.geometry.Offset(cx + eyeGap, eyeY))

        // ── Mouth: openness follows the smoothed amplitude ───────────────
        val mouthStyle =
            if (smoothed < 0.04f) {
                Stroke(width = h * 0.010f, cap = StrokeCap.Round)
            } else {
                androidx.compose.ui.graphics.drawscope.Fill
            }
        drawPath(
            mouthPath(cx, mouthY, mouthHalf, h, smoothed),
            color = faceColor,
            style = mouthStyle,
        )
    }
}

private fun eyebrowPath(
    centerX: Float,
    y: Float,
    halfSpan: Float,
    lift: Float,
): Path = Path().apply {
    moveTo(centerX - halfSpan, y - lift * 0.3f)
    quadraticTo(centerX, y - lift - halfSpan * 0.10f, centerX + halfSpan, y - lift * 0.3f)
}

private fun mouthPath(
    cx: Float,
    y: Float,
    halfWidth: Float,
    h: Float,
    openness: Float,
): Path {
    if (openness < 0.04f) {
        // Calm closed mouth: a gentle smile line.
        return Path().apply {
            moveTo(cx - halfWidth, y)
            quadraticTo(cx, y - h * 0.018f, cx + halfWidth, y)
        }
    }
    val halfHeight = (h * 0.030f + openness * h * 0.11f).coerceAtMost(h * 0.16f)
    return Path().apply {
        moveTo(cx - halfWidth, y)
        cubicTo(
            cx - halfWidth * 0.4f, y - halfHeight,
            cx + halfWidth * 0.4f, y - halfHeight,
            cx + halfWidth, y,
        )
        cubicTo(
            cx + halfWidth * 0.4f, y + halfHeight,
            cx - halfWidth * 0.4f, y + halfHeight,
            cx - halfWidth, y,
        )
        close()
    }
}
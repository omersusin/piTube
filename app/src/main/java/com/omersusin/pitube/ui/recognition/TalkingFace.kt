package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

/**
 * Google voice-search style "bodyless face" for the Voice listening state:
 * two eyebrow arcs, two dot eyes and a mouth whose openness follows the live
 * microphone amplitude, so the face appears to talk along with the user.
 * Not phoneme-accurate lip-sync — just amplitude-driven mouth opening, plus a
 * constant aliveness: slow idle breathing (whole face sways), a periodic eye
 * blink and a subtle amplitude-aware tremor at the mouth.
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
    val infinite = rememberInfiniteTransition(label = "talkingFace")

    // Idle breathing: drives the gentle up/down sway of the whole face.
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_800, easing = LinearEasing)),
        label = "breath",
    )
    // Periodic blink, one short closure around the 52% mark of its 4.2s cycle.
    val blinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4_200, easing = LinearEasing)),
        label = "blink",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val tau = 2f * Math.PI.toFloat()

        val bob = sin(breathPhase * tau) * h * 0.020f
        val blinkOpen = blinkOpenness(blinkPhase)

        val faceTop = h * 0.12f + bob
        val browY = faceTop + h * 0.13f
        val eyeY = faceTop + h * 0.28f
        val mouthY = faceTop + h * 0.56f

        val eyeGap = w * 0.20f
        val eyeRadius = w * 0.026f
        val mouthHalf = w * 0.30f
        val browHalf = w * 0.15f
        val browLift = smoothed * h * 0.018f
        // Double-frequency mouth tremor, stronger the louder the user talks.
        val mouthTremble = (smoothed * 0.6f + 0.4f) * sin(breathPhase * tau * 2f) * w * 0.004f

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

        // ── Eyes (dots that briefly close on the blink) ───────────────────
        val eyeH = eyeRadius * 2f * blinkOpen.coerceIn(0.12f, 1f)
        drawOval(
            color = faceColor,
            topLeft = Offset(cx - eyeGap - eyeRadius, eyeY - eyeH / 2f),
            size = Size(eyeRadius * 2f, eyeH),
        )
        drawOval(
            color = faceColor,
            topLeft = Offset(cx + eyeGap - eyeRadius, eyeY - eyeH / 2f),
            size = Size(eyeRadius * 2f, eyeH),
        )

        // ── Mouth: openness follows the smoothed amplitude, with a tremor ─
        val mouthStyle =
            if (smoothed < 0.04f) {
                Stroke(width = h * 0.010f, cap = StrokeCap.Round)
            } else {
                Fill
            }
        drawPath(
            mouthPath(cx, mouthY, mouthHalf + mouthTremble, h, smoothed),
            color = faceColor,
            style = mouthStyle,
        )
    }
}

/** 0 = eyes fully closed (blink center), 1 = fully open outside the blink. */
private fun blinkOpenness(phase: Float): Float {
    val t = ((phase - 0.52f) * 45f).coerceIn(-1f, 1f)
    return t * t
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
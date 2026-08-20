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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TalkingFace(
    amplitude: Float,
    levels: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val secondary = cs.secondary
    val tertiary = cs.tertiary
    val onSurface = cs.onSurface

    val smoothed by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "faceAmp",
    )

    val infinite = rememberInfiniteTransition(label = "voiceVisualizer")
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "breath",
    )
    val shimmerPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shimmer",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f
        val baseRadius = minOf(w, h) * 0.22f
        val pulseRadius = baseRadius * (1f + smoothed * 0.42f + sin(breathPhase * 2 * PI).toFloat() * 0.03f)
        val outerRadius = pulseRadius * 1.55f

        val glowBrush = Brush.radialGradient(
            0f to primary.copy(alpha = 0.38f + smoothed * 0.22f),
            0.55f to secondary.copy(alpha = 0.18f + smoothed * 0.12f),
            1f to tertiary.copy(alpha = 0f),
            center = Offset(cx, cy),
            radius = outerRadius,
        )
        drawCircle(brush = glowBrush, radius = outerRadius, center = Offset(cx, cy))

        drawCircle(
            color = primary.copy(alpha = 0.92f),
            radius = pulseRadius,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = cs.onPrimary.copy(alpha = 0.28f + smoothed * 0.22f),
            radius = pulseRadius * 0.62f,
            center = Offset(cx, cy + sin(shimmerPhase * 2 * PI).toFloat() * h * 0.012f),
        )

        val barCount = 28
        val history = if (levels.size >= barCount) levels.takeLast(barCount) else List(barCount) { smoothed * (0.7f + 0.3f * sin(it * 0.9f).toFloat().coerceIn(0f, 1f)) }
        val barWidth = w * 0.012f
        val gap = w * 0.014f
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        var x = cx - totalWidth / 2f
        val maxBarH = h * 0.28f
        val minBarH = h * 0.018f
        val idleWave = sin(breathPhase * 2 * PI).toFloat() * h * 0.008f

        for (i in 0 until barCount) {
            val raw = history[i].coerceIn(0f, 1f)
            val distanceFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
            val centerBoost = (1f - distanceFromCenter * 0.35f)
            val barH = (minBarH + raw * maxBarH * centerBoost + idleWave * (1f - raw) * 0.5f).coerceIn(minBarH, maxBarH)
            val barCx = x + barWidth / 2f
            val topY = cy + pulseRadius + h * 0.06f

            val alpha = 0.55f + raw * 0.45f
            val barColor = when {
                raw > 0.6f -> primary.copy(alpha = alpha)
                raw > 0.3f -> secondary.copy(alpha = alpha * 0.95f)
                else -> onSurface.copy(alpha = 0.38f + raw * 0.35f)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(barCx - barWidth / 2f, topY),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )

            val mirrorTopY = cy - pulseRadius - h * 0.06f - barH
            drawRoundRect(
                color = barColor.copy(alpha = barColor.alpha * 0.42f),
                topLeft = Offset(barCx - barWidth / 2f, mirrorTopY),
                size = androidx.compose.ui.geometry.Size(barWidth, barH * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )

            x += barWidth + gap
        }

        if (smoothed > 0.08f) {
            val ringCount = 2
            for (r in 0 until ringCount) {
                val ringPhase = (shimmerPhase + r * 0.5f) % 1f
                val ringRadius = pulseRadius + r * w * 0.06f + smoothed * w * 0.08f + ringPhase * w * 0.02f
                val ringAlpha = (0.22f - ringPhase * 0.18f).coerceIn(0f, 0.22f) * (0.6f + smoothed)
                drawCircle(
                    color = secondary.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.006f, cap = StrokeCap.Round),
                )
            }
        }

        if (smoothed < 0.04f) {
            val dotAlpha = 0.5f + sin(breathPhase * 2 * PI).toFloat() * 0.2f
            val dotRadius = w * 0.016f
            for (i in 0..2) {
                val angle = (shimmerPhase * 2 * PI + i * 2 * PI / 3).toFloat()
                val dist = pulseRadius * 0.42f
                val dx = cos(angle) * dist
                val dy = sin(angle) * dist
                drawCircle(
                    color = cs.onPrimary.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
                    radius = dotRadius * (0.9f + sin(angle * 1.5f) * 0.15f),
                    center = Offset(cx + dx, cy + dy),
                )
            }
        }
    }
}

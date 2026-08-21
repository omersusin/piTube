package com.omersusin.pitube.ui.recognition

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

    val smoothed by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "voiceAmp",
    )

    val infinite = rememberInfiniteTransition(label = "voiceOrb")
    val breathPhase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "breath",
    )
    val shimmerPhase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "shimmer",
    )

    var time by remember { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = (now - last) / 1_000_000_000f
                last = now
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
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen),
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val cx = w / 2f
            val cy = h / 2f
            val baseRadius = minOf(w, h) * 0.30f
            val breath = sin(breathPhase * 2 * PI).toFloat()
            val voiceEnergy = if (levels.isNotEmpty()) levels.takeLast(6).average().toFloat().coerceIn(0f, 1f) else smoothed
            val pulseRadius = baseRadius * (1f + smoothed * 0.42f + voiceEnergy * 0.18f + breath * 0.020f)
            val outerRadius = pulseRadius * (1.72f + voiceEnergy * 0.12f)

            val glowBrush = Brush.radialGradient(
                0f to primary.copy(alpha = 0.36f + smoothed * 0.28f + voiceEnergy * 0.12f),
                0.52f to secondary.copy(alpha = 0.20f + smoothed * 0.16f),
                0.82f to tertiary.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = outerRadius,
            )
            drawCircle(brush = glowBrush, radius = outerRadius, center = Offset(cx, cy))

            val coreBrush = Brush.radialGradient(
                0f to primary.copy(alpha = 0.98f),
                0.45f to secondary.copy(alpha = 0.92f),
                0.78f to tertiary.copy(alpha = 0.72f),
                1f to tertiary.copy(alpha = 0.42f),
                center = Offset(cx - pulseRadius * 0.18f, cy - pulseRadius * 0.22f),
                radius = pulseRadius * 1.35f,
            )
            drawCircle(brush = coreBrush, radius = pulseRadius, center = Offset(cx, cy))

            val highlightR = pulseRadius * 0.32f * (0.88f + smoothed * 0.22f)
            val hx = cx - pulseRadius * 0.20f + sin(time * 0.9f) * pulseRadius * 0.05f
            val hy = cy - pulseRadius * 0.26f + cos(time * 1.05f) * pulseRadius * 0.04f
            val highlight = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.38f + smoothed * 0.12f),
                1f to Color.Transparent,
                center = Offset(hx, hy),
                radius = highlightR,
            )
            drawCircle(brush = highlight, radius = highlightR, center = Offset(hx, hy))

            val ringPoints = 48
            val ringBase = pulseRadius * 1.18f
            val maxWave = h * (0.085f + voiceEnergy * 0.04f)
            val points = List(ringPoints) { i ->
                val angle = (2 * PI * i / ringPoints - PI / 2).toFloat()
                val levelIdx = if (levels.isNotEmpty()) (i * levels.size / ringPoints).coerceIn(0, levels.size - 1) else -1
                val raw = if (levelIdx >= 0) levels[levelIdx].coerceIn(0f, 1f)
                else {
                    val idle = (sin(time * 1.6 + i * 0.55).toFloat() * 0.5f + 0.5f) * 0.08f
                    (smoothed * 0.42f + idle).coerceIn(0f, 1f)
                }
                val r = ringBase + raw * maxWave + smoothed * h * 0.010f + voiceEnergy * h * 0.008f + breath * h * 0.003f
                Offset(cx + cos(angle) * r, cy + sin(angle) * r)
            }

            val ringPath = Path().apply {
                if (points.isNotEmpty()) {
                    val firstMid = Offset(
                        (points.last().x + points[0].x) / 2f,
                        (points.last().y + points[0].y) / 2f,
                    )
                    moveTo(firstMid.x, firstMid.y)
                    for (i in points.indices) {
                        val cur = points[i]
                        val next = points[(i + 1) % points.size]
                        val midX = (cur.x + next.x) / 2f
                        val midY = (cur.y + next.y) / 2f
                        quadraticTo(cur.x, cur.y, midX, midY)
                    }
                    close()
                }
            }

            val ringStroke = if (smoothed > 0.06f) w * 0.009f else w * 0.006f
            val ringAlpha = (0.55f + smoothed * 0.45f).coerceIn(0f, 1f)
            val ringColor = if (smoothed > 0.35f) primary.copy(alpha = ringAlpha)
            else cs.onSurface.copy(alpha = 0.38f + smoothed * 0.42f)

            drawPath(
                path = ringPath,
                color = ringColor,
                style = Stroke(width = ringStroke),
            )

            val innerRingAlpha = (0.28f + smoothed * 0.22f).coerceIn(0f, 0.5f)
            if (smoothed > 0.08f || levels.isNotEmpty()) {
                val innerBase = pulseRadius * 1.06f
                val innerPoints = List(ringPoints) { i ->
                    val angle = (2 * PI * i / ringPoints - PI / 2 + 0.18f).toFloat()
                    val idx = if (levels.isNotEmpty()) (i * levels.size / ringPoints).coerceIn(0, levels.size - 1) else -1
                    val raw = if (idx >= 0) levels[idx].coerceIn(0f, 1f) * 0.62f else smoothed * 0.22f
                    val r = innerBase + raw * maxWave * 0.55f
                    Offset(cx + cos(angle) * r, cy + sin(angle) * r)
                }
                val innerPath = Path().apply {
                    if (innerPoints.isNotEmpty()) {
                        val fm = Offset(
                            (innerPoints.last().x + innerPoints[0].x) / 2f,
                            (innerPoints.last().y + innerPoints[0].y) / 2f,
                        )
                        moveTo(fm.x, fm.y)
                        for (i in innerPoints.indices) {
                            val cur = innerPoints[i]
                            val nxt = innerPoints[(i + 1) % innerPoints.size]
                            val mx = (cur.x + nxt.x) / 2f
                            val my = (cur.y + nxt.y) / 2f
                            quadraticTo(cur.x, cur.y, mx, my)
                        }
                        close()
                    }
                }
                drawPath(
                    path = innerPath,
                    color = secondary.copy(alpha = innerRingAlpha),
                    style = Stroke(width = w * 0.0055f),
                )
            }

            if (smoothed < 0.04f) {
                val dotAlpha = 0.45f + sin(breathPhase * 2 * PI).toFloat() * 0.18f
                val dotR = w * 0.014f
                for (i in 0..2) {
                    val ang = (shimmerPhase * 2 * PI + i * 2 * PI / 3).toFloat()
                    val dist = pulseRadius * 0.48f
                    drawCircle(
                        color = cs.onPrimary.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
                        radius = dotR * (0.92f + sin(ang * 1.4f) * 0.12f),
                        center = Offset(cx + cos(ang) * dist, cy + sin(ang) * dist),
                    )
                }
            }
        }
    }
}

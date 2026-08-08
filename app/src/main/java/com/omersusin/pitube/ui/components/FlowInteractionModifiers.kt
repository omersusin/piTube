package com.omersusin.pitube.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

fun Modifier.thumbnailGradientOverlay(
    color: Color = Color.Black,
    alpha: Float = 0.25f,
    startFraction: Float = 0.6f
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = alpha)
        ),
        startY = size.height * startFraction,
        endY = size.height
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush)
    }
}

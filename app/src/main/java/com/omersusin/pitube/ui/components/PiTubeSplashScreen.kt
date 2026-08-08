package com.omersusin.pitube.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PiTubeSplashScreen(
    onAnimationFinished: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onBackground
    val loadingTrackColor = colorScheme.onBackground.copy(alpha = 0.12f)
    val loadingGradient = listOf(colorScheme.primary, colorScheme.tertiary)

    val scale = remember { Animatable(0f) }
    val lineProgress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(true) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        launch {
            delay(200)
            lineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
        }
        delay(1200)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400)
        )
        onAnimationFinished()
    }

    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .alpha(alpha.value),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .scale(scale.value)
                        .size(80.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "pi",
                        color = colorScheme.onPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "piTube",
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.alpha(scale.value)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 180.dp)
                    .width(160.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(loadingTrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(lineProgress.value)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.horizontalGradient(colors = loadingGradient)
                        )
                )
            }
        }
    }
}

package com.omersusin.pitube.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaybackSpeedSlider(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    val currentIndex = speeds.indexOfFirst { it == currentSpeed }.coerceAtLeast(0)

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Speed: ${currentSpeed}x",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { index ->
                val idx = index.toInt().coerceIn(0, speeds.lastIndex)
                onSpeedChange(speeds[idx])
            },
            valueRange = 0f..speeds.lastIndex.toFloat(),
            steps = speeds.size - 2,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            speeds.forEach { speed ->
                val isSelected = speed == currentSpeed
                Text(
                    text = if (speed == 1.0f) "1x" else "${speed}x",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

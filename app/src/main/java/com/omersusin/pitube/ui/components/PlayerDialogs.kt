package com.omersusin.pitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class QualityOption(
    val label: String,
    val height: Int,
    val bitrate: Long = 0,
    val mimeType: String = ""
)

@Composable
fun QualitySelectorDialog(
    qualities: List<QualityOption>,
    currentQuality: QualityOption?,
    onQualitySelected: (QualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Quality") },
        text = {
            LazyColumn {
                item {
                    QualityItem(
                        label = "Auto",
                        isSelected = currentQuality == null,
                        onClick = {
                            onQualitySelected(QualityOption("Auto", 0))
                            onDismiss()
                        }
                    )
                }
                items(qualities) { quality ->
                    QualityItem(
                        label = quality.label,
                        isSelected = currentQuality?.height == quality.height,
                        onClick = {
                            onQualitySelected(quality)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QualityItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            LazyColumn {
                items(speeds) { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSpeedSelected(speed)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = {
                                onSpeedSelected(speed)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (speed == 1.0f) FontWeight.Bold else FontWeight.Normal
                        )
                        if (speed == 1.0f) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Normal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AudioTrackSelectorDialog(
    audioTracks: List<Pair<String, String>>,
    currentTrack: Pair<String, String>?,
    onTrackSelected: (Pair<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Track") },
        text = {
            if (audioTracks.isEmpty()) {
                Text("No audio tracks available")
            } else {
                LazyColumn {
                    items(audioTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTrackSelected(track)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTrack?.first == track.first,
                                onClick = {
                                    onTrackSelected(track)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = track.first,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (track.second.isNotBlank()) {
                                    Text(
                                        text = track.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.omersusin.pitube.ui.components

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.videoPlayerControls(
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    onSeekForward: (Int) -> Unit,
    onSeekBack: (Int) -> Unit,
    currentPosition: () -> Long,
    duration: Long,
    isFullscreen: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onShowBrightnessChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShowVolumeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    brightnessLevel: Float,
    volumeLevel: Float,
    maxVolume: Int,
    audioManager: AudioManager?,
    activity: Activity?,
    onPlayPause: () -> Unit,
    doubleTapSeekMs: Long = 10_000L,
    onExitFullscreen: (() -> Unit)? = null
): Modifier = composed {
    var accumulatedForwardMs by remember { mutableStateOf(0L) }
    var accumulatedBackMs by remember { mutableStateOf(0L) }
    var lastForwardTapTime by remember { mutableStateOf(0L) }
    var lastBackTapTime by remember { mutableStateOf(0L) }
    val accumulationWindowMs = 1000L

    val lastBrightnessApplied = remember { floatArrayOf(-2f) }
    val lastBrightnessAppliedAt = remember { longArrayOf(0L) }

    this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    val screenWidth = size.width
                    val tapPosition = offset.x
                    val now = System.currentTimeMillis()

                    if (tapPosition < screenWidth / 3f) {
                        // Left third - seek back
                        accumulatedBackMs += doubleTapSeekMs
                        lastBackTapTime = now
                        onSeekBack(-(accumulatedBackMs / 1000L).toInt())
                    } else if (tapPosition > screenWidth * 2f / 3f) {
                        // Right third - seek forward
                        accumulatedForwardMs += doubleTapSeekMs
                        lastForwardTapTime = now
                        onSeekForward((accumulatedForwardMs / 1000L).toInt())
                    } else {
                        // Center - toggle controls or play/pause
                        onShowControlsChange(!showControls)
                    }
                },
                onDoubleTap = { offset ->
                    val screenWidth = size.width
                    val tapPosition = offset.x
                    val now = System.currentTimeMillis()

                    if (tapPosition < screenWidth / 3f) {
                        // Left third - seek back with accumulation
                        val continuingBackSeek = now - lastBackTapTime < accumulationWindowMs
                        if (continuingBackSeek) {
                            accumulatedBackMs += doubleTapSeekMs
                        } else {
                            accumulatedBackMs = doubleTapSeekMs
                        }
                        lastBackTapTime = now
                        onSeekBack(-(accumulatedBackMs / 1000L).toInt())
                    } else if (tapPosition > screenWidth * 2f / 3f) {
                        // Right third - seek forward with accumulation
                        val continuingForwardSeek = now - lastForwardTapTime < accumulationWindowMs
                        if (continuingForwardSeek) {
                            accumulatedForwardMs += doubleTapSeekMs
                        } else {
                            accumulatedForwardMs = doubleTapSeekMs
                        }
                        lastForwardTapTime = now
                        onSeekForward((accumulatedForwardMs / 1000L).toInt())
                    } else {
                        // Center double tap - play/pause
                        onPlayPause()
                    }
                }
            )
        }
        .pointerInput(isFullscreen) {
            var totalDragY = 0f
            var isDraggingVertical = false
            val dragThreshold = 20f
            val edgeIgnoreThreshold = 120f
            var startTouchX = 0f
            var isCenterZone = false
            var exitDragAccum = 0f

            if (isFullscreen) {
                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        isDraggingVertical = false
                        exitDragAccum = 0f

                        val distanceFromTop = offset.y
                        val distanceFromBottom = size.height - offset.y

                        if (distanceFromTop < edgeIgnoreThreshold || distanceFromBottom < edgeIgnoreThreshold) {
                            return@detectDragGestures
                        }

                        startTouchX = offset.x
                        val screenWidth = size.width
                        isCenterZone = startTouchX > screenWidth * 0.33f && startTouchX < screenWidth * 0.67f
                    },
                    onDragEnd = {
                        if (isCenterZone && exitDragAccum > 80f) {
                            onExitFullscreen?.invoke()
                        }
                        isCenterZone = false
                        exitDragAccum = 0f
                        isDraggingVertical = false
                    },
                    onDragCancel = {
                        isCenterZone = false
                        exitDragAccum = 0f
                        isDraggingVertical = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y

                        if (!isDraggingVertical) {
                            if (abs(totalDragY) > dragThreshold) {
                                isDraggingVertical = true
                            }
                        }

                        if (isDraggingVertical) {
                            val screenHeight = size.height.toFloat()
                            val screenWidth = size.width.toFloat()
                            val dragPosition = change.position.x

                            if (isCenterZone) {
                                if (dragAmount.y > 0) {
                                    exitDragAccum += dragAmount.y
                                }
                            } else if (screenHeight > 0) {
                                if (dragPosition < screenWidth / 2) {
                                    // Left side - brightness
                                    val sensitivity = 1.5f
                                    val delta = -dragAmount.y / screenHeight * sensitivity

                                    val startLevel = if (brightnessLevel < 0) 0f else brightnessLevel
                                    val rawNewLevel = startLevel + delta

                                    val newBrightness = if (rawNewLevel < -0.05f) {
                                        -1.0f // Auto mode
                                    } else {
                                        rawNewLevel.coerceIn(0f, 1f)
                                    }

                                    onBrightnessChange(newBrightness)

                                    val now = android.os.SystemClock.uptimeMillis()
                                    val brightnessDelta = abs(newBrightness - lastBrightnessApplied[0])
                                    val timeDelta = now - lastBrightnessAppliedAt[0]

                                    if (brightnessDelta > 0.004f || timeDelta >= 16L) {
                                        try {
                                            activity?.window?.let { window ->
                                                val layoutParams = window.attributes
                                                layoutParams.screenBrightness = newBrightness
                                                window.attributes = layoutParams
                                            }
                                            lastBrightnessApplied[0] = newBrightness
                                            lastBrightnessAppliedAt[0] = now
                                        } catch (_: Exception) {}
                                    }
                                    onShowBrightnessChange(true)
                                } else {
                                    // Right side - volume
                                    val sensitivity = 1.5f
                                    val delta = -dragAmount.y / screenHeight * sensitivity

                                    val newVolumeLevel = (volumeLevel + delta).coerceIn(0f, 1.0f)
                                    onVolumeChange(newVolumeLevel)

                                    if (newVolumeLevel <= 1.0f) {
                                        val newVolume = (newVolumeLevel * maxVolume).toInt()
                                        audioManager?.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolume,
                                            0
                                        )
                                    }
                                    onShowVolumeChange(true)
                                }
                            }
                        }
                    }
                )
            }
        }
}

package com.omersusin.pitube.data

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object SleepTimerManager {
    var isActive = false
        private set
    var remainingMs = 0L
        private set
    var closeAppOnExpiry = false
        private set
    
    private var timerJob: Job? = null
    private var pauseCallback: (() -> Unit)? = null
    private var exitCallback: (() -> Unit)? = null
    
    fun setCallbacks(
        onPause: () -> Unit,
        onExit: () -> Unit
    ) {
        pauseCallback = onPause
        exitCallback = onExit
    }
    
    fun start(minutes: Int, closeApp: Boolean = false) {
        cancel()
        closeAppOnExpiry = closeApp
        val durationMs = minutes * 60_000L
        remainingMs = durationMs
        isActive = true
        
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            // Tick every second to update remaining time display
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - startTime
                remainingMs = (durationMs - elapsed).coerceAtLeast(0)
                if (remainingMs <= 0) {
                    firePause()
                    break
                }
            }
        }
    }
    
    fun startEndOfMedia(closeApp: Boolean = false) {
        cancel()
        closeAppOnExpiry = closeApp
        isActive = true
        // This will be checked when media ends
    }
    
    fun onMediaEnded() {
        if (isActive && closeAppOnExpiry) {
            firePause()
        }
    }
    
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        isActive = false
        remainingMs = 0L
        closeAppOnExpiry = false
    }
    
    fun getRemainingMinutes(): Int = (remainingMs / 60_000).toInt()
    
    fun getRemainingFormatted(): String {
        val hours = remainingMs / 3_600_000
        val minutes = (remainingMs % 3_600_000) / 60_000
        val seconds = (remainingMs % 60_000) / 1000
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun firePause() {
        isActive = false
        if (closeAppOnExpiry) {
            exitCallback?.invoke()
        } else {
            pauseCallback?.invoke()
        }
        cancel()
    }
}

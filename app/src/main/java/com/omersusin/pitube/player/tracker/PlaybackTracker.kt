package com.omersusin.pitube.player.tracker

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.omersusin.pitube.player.config.PlayerConfig
import com.omersusin.pitube.player.state.EnhancedPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
    
@UnstableApi
class PlaybackTracker(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val onSponsorBlockCheck: (Long) -> Long?, // Returns seek position if skip needed
    private val onBufferingDetected: () -> Unit,
    private val onSmoothPlayback: () -> Unit,
    private val onBandwidthCheckNeeded: () -> Unit,
    private val onLivePlaybackTick: (ExoPlayer) -> Unit = {},
    private val onStallEscalation: (Long) -> Unit = {}, // Fires with frozen position after prolonged buffering
    private val onAutoSavePosition: (Long) -> Unit = {}, // Periodic position persist (works in background/audio-only)
) {
    companion object {
        private const val TAG = "PlaybackTracker"
    }
    
    private var positionTrackerJob: Job? = null
    private var lastCheckedPosition = 0L
    private var stuckCount = 0
    private var lastSaveTime = 0L
    private var stallMs = 0L
    private var stallEscalated = false
    private var lastFrozenBufferedPos = -1L

    /**
     * Start position tracking.
     */
    fun start(player: ExoPlayer) {
        Log.d(TAG, "start() called")
        stop()
        positionTrackerJob = scope.launch {
            Log.d(TAG, "Position tracker coroutine started")
            lastCheckedPosition = 0L
            stuckCount = 0
            lastSaveTime = 0L
            
            while (true) {
                trackPosition(player)
                when {
                    player.isPlaying || player.playbackState == Player.STATE_BUFFERING ->
                        delay(PlayerConfig.POSITION_TRACKER_INTERVAL_MS)

                    stateFlow.value.isPlaying || stateFlow.value.isBuffering ->
                        delay(PlayerConfig.POSITION_TRACKER_INTERVAL_MS)

                    else ->
                        withTimeoutOrNull(5000) { stateFlow.first { it.isPlaying || it.isBuffering } }
                }
            }
        }
    }
    
    /**
     * Stop position tracking.
     */
    fun stop() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
        stuckCount = 0
    }
    
    private suspend fun trackPosition(player: ExoPlayer) {
        if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
            val bufferedPos = player.bufferedPosition
            val currentPos = player.currentPosition
            
            // Debug log every 5 seconds (approx 10 ticks)
            if (System.currentTimeMillis() % 5000 < PlayerConfig.POSITION_TRACKER_INTERVAL_MS * 2) {
                 Log.d(TAG, "Tracking position: $currentPos ms")
            }
            
            val duration = player.duration.coerceAtLeast(1)
            val bufferedPct = ((bufferedPos.toFloat() / duration.toFloat())
                .coerceIn(0f, 1f) * 100f).toInt() / 100f

            if (stateFlow.value.bufferedPercentage != bufferedPct) {
                stateFlow.value = stateFlow.value.copy(
                    bufferedPercentage = bufferedPct
                )
            }
            onLivePlaybackTick(player)

            // Periodic auto-save signal (every 30 seconds)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime >= PlayerConfig.AUTO_SAVE_INTERVAL_MS && player.isPlaying) {
                Log.d(TAG, "Auto-save trigger: at ${currentPos}ms")
                lastSaveTime = currentTime
                // Persist outside the UI layer so positions survive background/audio-only
                // playback and mid-play stalls (the composable save loop pauses then).
                onAutoSavePosition(currentPos)
            }

            // SponsorBlock Skip Logic
            val skipPosition = onSponsorBlockCheck(currentPos)
            if (skipPosition != null) {
                Log.d(TAG, "Skipping to $skipPosition ms")
                player.seekTo(skipPosition)
            }
            
            // Smart stall detection
            if (player.playbackState == Player.STATE_BUFFERING) {
                if (currentPos == lastCheckedPosition && player.playWhenReady) {
                    stuckCount++
                    val bufferFrozen = bufferedPos == lastFrozenBufferedPos
                    lastFrozenBufferedPos = bufferedPos

                    // Only log if actually stuck for more than 1 second
                    if (stuckCount >= PlayerConfig.STUCK_DETECTION_THRESHOLD) {
                        val bufferAhead = bufferedPos - currentPos
                        Log.d(TAG, "STALL: Pos=${currentPos}ms | Buff=${bufferedPos}ms (+${bufferAhead}ms ahead) | StuckFor=${stuckCount * PlayerConfig.POSITION_TRACKER_INTERVAL_MS}ms")
                        onBufferingDetected()
                    }

                    // Watchdog: prolonged buffering with a frozen playhead AND frozen buffer
                    // means the producer is dead (e.g. expired stream URL, SABR hang).
                    // Escalate to full stream re-resolution instead of waiting forever.
                    if (bufferFrozen) {
                        stallMs += PlayerConfig.POSITION_TRACKER_INTERVAL_MS
                        if (stallMs >= PlayerConfig.STALL_ESCALATION_MS && !stallEscalated) {
                            stallEscalated = true
                            Log.w(
                                TAG,
                                "Stall escalation: buffered ${PlayerConfig.STALL_ESCALATION_MS / 1000}s+ " +
                                    "with frozen position ${currentPos}ms — requesting stream re-resolution"
                            )
                            onStallEscalation(currentPos.coerceAtLeast(0L))
                        }
                    }
                } else {
                    // Position advanced while still buffering — healthy progress,
                    // not a stall. Reset the watchdog.
                    resetStallWatchdog()
                    stuckCount = 0
                }
            } else {
                resetStallWatchdog()
                stuckCount = 0
                onSmoothPlayback()
                
                // Periodic bandwidth check for quality upgrade
                if (player.isPlaying) {
                    onBandwidthCheckNeeded()
                }
            }
            
            lastCheckedPosition = currentPos
        }
    }

    /**
     * Reset tracking state for a new video.
     */
    fun reset() {
        lastCheckedPosition = 0L
        stuckCount = 0
        lastSaveTime = 0L
        resetStallWatchdog()
    }

    private fun resetStallWatchdog() {
        stallMs = 0L
        stallEscalated = false
        lastFrozenBufferedPos = -1L
    }
}

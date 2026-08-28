package com.omersusin.pitube.player.shorts

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.model.ShortVideo
import com.omersusin.pitube.data.shorts.ShortsRepository
import com.omersusin.pitube.player.analytics.PlaybackAnalyticsLogger
import com.omersusin.pitube.player.config.PlayerConfig
import com.omersusin.pitube.player.datasource.YouTubeHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ShortsPlayerPool — 3-player pool for instant swipe transitions.
 *
 * Architecture:
 * - 3 ExoPlayer instances with roles: PREVIOUS, CURRENT, NEXT
 * - Aggressive buffer settings (2.5s min / 15s max / 500ms playback / 1.5s rebuffer)
 * - REPEAT_MODE_ONE for looping shorts
 * - RESIZE_MODE_ZOOM for full-screen display
 * - Role rotation on swipe: no player creation/destruction, just role reassignment
 * - Pre-loading: NEXT player starts buffering before user swipes
 *
 * Lifecycle:
 * 1. initialize(context) — creates 3 players
 * 2. prepareCurrent(videoUrl, audioUrl) — loads media into CURRENT player
 * 3. prepareNext(videoUrl, audioUrl) — pre-loads NEXT player
 * 4. swipeForward() — rotates: CURRENT→PREVIOUS, NEXT→CURRENT, PREVIOUS→NEXT
 * 5. swipeBackward() — rotates: CURRENT→NEXT, PREVIOUS→CURRENT, NEXT→PREVIOUS
 * 6. release() — destroys all players
 */
@OptIn(UnstableApi::class)
class ShortsPlayerPool private constructor() {

    companion object {
        private const val TAG = "ShortsPlayerPool"
        private const val POOL_SIZE = 3

        private const val MIN_BUFFER_MS = 1_500
        private const val MAX_BUFFER_MS = 8_000
        private const val BUFFER_FOR_PLAYBACK_MS = 250
        private const val BUFFER_FOR_REBUFFER_MS = 750
        private const val BACK_BUFFER_MS = 2_000
        private const val MAX_STREAM_RECOVERIES = 2

        @Volatile
        private var instance: ShortsPlayerPool? = null

        fun getInstance(): ShortsPlayerPool {
            return instance ?: synchronized(this) {
                instance ?: ShortsPlayerPool().also { instance = it }
            }
        }
    }

    private val players = arrayOfNulls<ExoPlayer>(POOL_SIZE)
    private val playerVideoIds = arrayOfNulls<String>(POOL_SIZE)

    // Tracks which content index (absolute position in the list) currently owns this player slot
    private val playerOwnerIndices = arrayOfNulls<Int>(POOL_SIZE)

    // Track the last video and audio URLs per slot so we can hot-swap audio/quality
    private val playerVideoUrls = arrayOfNulls<String>(POOL_SIZE)
    private val playerAudioUrls = arrayOfNulls<String?>(POOL_SIZE)

    private var isInitialized = false
    private var dataSourceFactory: DefaultDataSource.Factory? = null
    private var preferredAudioLanguage: String = "original"
    private var shortsPlaybackMode: String = "loop"
    private var basePlaybackSpeed: Float = 1f
    private var appContextRef: Context? = null

    // ── 403/410 stream-expiry recovery ───────────────────────────────────────
    // Shorts playback used to die on the first expired-URL 403 (logcat:
    // "Playback error ... InvalidResponseCodeException: Response code: 403")
    // because the pool had no error listener at all. Now the failed video's
    // cached streams are evicted, re-resolved, and hot-swapped in place —
    // the main player already does the same through PlayerErrorHandler.
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryAttempts = mutableMapOf<String, Int>()
    private val recoveryInFlight = mutableSetOf<String>()

    private val preferenceObservers = ShortsPreferenceObservers()

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    private val _currentVideo = MutableStateFlow<ShortVideo?>(null)
    val currentVideo: StateFlow<ShortVideo?> = _currentVideo.asStateFlow()

    fun setCurrentVideo(video: ShortVideo?) {
        _currentVideo.value = video
    }

    fun playbackPosition(): Long = findActivePlayer()?.currentPosition ?: 0L

    fun playbackDuration(): Long = findActivePlayer()?.duration?.coerceAtLeast(0L) ?: 0L

    fun isPlaying(): Boolean = findActivePlayer()?.isPlaying == true

    // INITIALIZATION
    fun initialize(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext
        appContextRef = appContext
        Log.d(TAG, "Initializing 3-player pool for Shorts")
        dataSourceFactory = DefaultDataSource.Factory(appContext, YouTubeHttpDataSource.Factory())
        val preferences = PlayerPreferences(appContext)
        preferenceObservers.start(
            preferredAudioLanguage = preferences.preferredAudioLanguage,
            playbackMode = preferences.shortsPlaybackMode,
            playbackSpeed = preferences.shortsPlaybackSpeed,
            onPreferredAudioLanguage = { language ->
                preferredAudioLanguage = language
                updateTrackSelectors(language)
            },
            onPlaybackMode = { mode ->
                shortsPlaybackMode = mode
                Log.d(TAG, "Shorts playback mode changed to: $mode")
            },
            onPlaybackSpeed = { speed ->
                setBasePlaybackSpeed(speed)
            },
        )

        try {
            for (i in 0 until POOL_SIZE) {
                players[i] = createShortsPlayer(appContext, i)
                playerOwnerIndices[i] = null
                playerVideoIds[i] = null
            }
            isInitialized = true
            Log.d(TAG, "Player pool initialized with $POOL_SIZE players")
        } catch (error: Throwable) {
            release()
            throw error
        }
    }

    private fun updateTrackSelectors(language: String) {
        players.filterNotNull().forEach { player ->
            val trackSelector = player.trackSelector as? DefaultTrackSelector
            trackSelector?.let { selector ->
                val builder = selector.buildUponParameters()
                when (language) {
                    "original", "" -> {
                    }
                    else -> {
                        builder.setPreferredAudioLanguage(language)
                    }
                }
                selector.setParameters(builder)
            }
        }
    }

    // setViewportSizeToPhysicalDisplaySize has no non-deprecated API 35 replacement.
    @Suppress("DEPRECATION")
    private fun createShortsPlayer(context: Context, slot: Int): ExoPlayer {
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val (maxVideoWidth, maxVideoHeight) = maxVideoSizeForHeap(context)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS
            )
            .setBackBuffer(BACK_BUFFER_MS, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(PlayerConfig.SHORTS_TARGET_BUFFER_BYTES)
            .build()

        val trackSelector = DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory()
        ).apply {
            val builder = buildUponParameters()
                .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setForceHighestSupportedBitrate(false)
                .setViewportSizeToPhysicalDisplaySize(context, true)
                .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            
            if (preferredAudioLanguage != "original" && preferredAudioLanguage.isNotEmpty()) {
                builder.setPreferredAudioLanguage(preferredAudioLanguage)
            }
            
            setParameters(builder.build())
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory!!))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                addAnalyticsListener(PlaybackAnalyticsLogger(TAG) { _currentVideoId.value })
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        handlePoolPlayerError(slot, error)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // A slot that reached READY is playing fine — renew its
                        // future recovery budget so an hours-later expiry can
                        // still recover.
                        if (playbackState == Player.STATE_READY) {
                            playerVideoIds[slot]?.let { recoveryAttempts.remove(it) }
                        }
                    }
                })
            }
    }

    /**
     * Expired-URL (403/410) recovery for a pool player: evict the failed
     * video's cached streams, re-resolve fresh URLs, and hot-swap them into
     * the same slot at the same position. Max 2 recoveries per video, then
     * the error surfaces (matching the main player's limiter behavior).
     */
    private fun handlePoolPlayerError(slot: Int, error: androidx.media3.common.PlaybackException) {
        val causeText = error.cause?.message.orEmpty()
        val isExpiredUrl = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
            (causeText.contains("Response code: 403") || causeText.contains("Response code: 410")) ||
            ((error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) &&
                (causeText.contains("Response code: 403") || causeText.contains("Response code: 410")))
        if (!isExpiredUrl) return

        val videoId = playerVideoIds[slot] ?: return
        val index = playerOwnerIndices[slot] ?: return
        val attempts = (recoveryAttempts[videoId] ?: 0) + 1
        recoveryAttempts[videoId] = attempts
        if (attempts > MAX_STREAM_RECOVERIES || videoId in recoveryInFlight) {
            Log.w(TAG, "Stream recovery for $videoId skipped (attempt $attempts, inFlight=${videoId in recoveryInFlight})")
            return
        }

        Log.w(TAG, "Stream 403/410 for short $videoId — evicting cache and re-resolving (attempt $attempts/$MAX_STREAM_RECOVERIES)")
        val context = appContextRef ?: return
        recoveryInFlight.add(videoId)
        recoveryScope.launch {
            try {
                val repository = ShortsRepository.getInstance(context)
                repository.evictStreamsFor(videoId)
                val fresh = repository.resolvePlaybackStreams(videoId, 0, preferredAudioLanguage)
                if (fresh != null) {
                    withContext(Dispatchers.Main) {
                        reloadWithUrls(index, videoId, fresh.videoUrl, fresh.audioUrl)
                        Log.w(TAG, "Stream recovery for $videoId succeeded — hot-swapped fresh URLs")
                    }
                } else {
                    Log.e(TAG, "Stream recovery for $videoId failed to resolve fresh URLs")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream recovery for $videoId crashed: ${e.message}")
            } finally {
                recoveryInFlight.remove(videoId)
            }
        }
    }

    private fun maxVideoSizeForHeap(context: Context): Pair<Int, Int> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 256
        val isLowMemoryDevice = activityManager?.isLowRamDevice == true || memoryClassMb <= 256
        return when {
            isLowMemoryDevice -> 1080 to 1920
            memoryClassMb <= 384 -> 1440 to 2560
            else -> 2160 to 3840
        }
    }

    // PLAYER ACCESS
    /**
     * Gets the player assigned to this specific content index.
     * The index corresponds to the list position (0, 1, 2, ...).
     * The pool automatically maps this to a physical player slot using modulo arithmetic.
     */
    fun getPlayerForIndex(index: Int): ExoPlayer? {
        if (!isInitialized || index < 0) return null
        val slot = index % POOL_SIZE
        
        return players[slot]
    }

    fun getVideoUrlForIndex(index: Int): String? {
        if (!isInitialized || index < 0) return null
        val slot = index % POOL_SIZE
        return playerVideoUrls[slot].takeIf { playerOwnerIndices[slot] == index }
    }

    fun getCurrentVideoId(): String? {
        return _currentVideoId.value
    }

    // MEDIA LOADING
    /**
     * Prepare the player for a specific index with video + audio streams.
     * @param index The absolute list position of the video
     * @param shouldPlay If true, starts playback immediately (for current item). If false, just buffers (for next/prev).
     */
    fun prepare(index: Int, videoId: String, videoUrl: String, audioUrl: String?, shouldPlay: Boolean) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return

        val isSameVideo = playerVideoIds[slot] == videoId && playerOwnerIndices[slot] == index
        
        if (isSameVideo) {
            if (shouldPlay && !player.isPlaying) {
                Log.d(TAG, "Player at index $index (slot $slot) already prepared. Resuming.")
                activatePlayer(index)
            }
            return
        }

        Log.d(TAG, "Preparing player at index $index (slot $slot) for video $videoId. AutoPlay: $shouldPlay")
        
        // Stop any previous playback in this slot
        player.stop()
        player.clearMediaItems()
        
        // Update ownership
        playerOwnerIndices[slot] = index
        playerVideoIds[slot] = videoId
        playerVideoUrls[slot] = videoUrl
        playerAudioUrls[slot] = audioUrl
        
        // Load media
        preparePlayerInternal(player, videoUrl, audioUrl)

        // Set playback state
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = shouldPlay
        // Set repeat mode based on playback preference: "loop" → REPEAT_MODE_ONE, "auto_next" → REPEAT_MODE_OFF
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        
        if (shouldPlay) {
            _currentVideoId.value = videoId
            
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true 
            )
        } else {
             player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
        }
    }

    /**
     * Activates the player at the given index (play) and pauses all others.
     * Call this when a page settles.
     */
    fun activatePlayer(index: Int) {
        if (!isInitialized) return
        val activeSlot = index % POOL_SIZE
        
        for (i in 0 until POOL_SIZE) {
            val player = players[i] ?: continue
            val isTarget = (i == activeSlot)
            
            if (isTarget) {
                if (playerOwnerIndices[i] == index) {
                    player.playWhenReady = true
                    player.setPlaybackSpeed(basePlaybackSpeed)
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        true
                    )
                    _currentVideoId.value = playerVideoIds[i]
                }
            } else {
                player.playWhenReady = false
            }
        }
    }

    fun releaseUnusedPlayers(currentIndex: Int) {
         if (!isInitialized) return
         
         for (i in 0 until POOL_SIZE) {
             val ownerIndex = playerOwnerIndices[i] ?: continue
             val diff = kotlin.math.abs(ownerIndex - currentIndex)
             if (diff > 1) {
                 Log.d(TAG, "Releasing stale player slot $i (owned by index $ownerIndex, current is $currentIndex)")
                 players[i]?.stop()
                 players[i]?.clearMediaItems()
                 playerOwnerIndices[i] = null
                 playerVideoIds[i] = null
                 playerVideoUrls[i] = null
                 playerAudioUrls[i] = null
             }
         }
    }

    /**
     * Hot-swap the audio track for an already-prepared player slot, keeping the same video URL.
     * Used for the Shorts audio track selector.
     */
    fun reloadWithAudioUrl(index: Int, videoId: String, newAudioUrl: String?) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return
        if (playerOwnerIndices[slot] != index) return

        val videoUrl = playerVideoUrls[slot] ?: return
        val wasPlaying = player.isPlaying || player.playWhenReady

        player.stop()
        player.clearMediaItems()
        playerAudioUrls[slot] = newAudioUrl

        preparePlayerInternal(player, videoUrl, newAudioUrl)
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = wasPlaying
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Hot-swap the video quality (URL) for an already-prepared player slot.
     * Used for the Shorts quality selector.
     */
    fun reloadWithVideoUrl(index: Int, videoId: String, newVideoUrl: String) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return
        if (playerOwnerIndices[slot] != index) return

        val wasPlaying = player.isPlaying || player.playWhenReady
        val position = player.currentPosition

        player.stop()
        player.clearMediaItems()
        playerVideoUrls[slot] = newVideoUrl

        val audioUrl = playerAudioUrls[slot]
        preparePlayerInternal(player, newVideoUrl, audioUrl)
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = wasPlaying
        player.seekTo(position)
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Hot-swap BOTH video and audio URLs for an already-prepared player slot
     * (stream-expiry recovery). Keeps position and playback state.
     */
    fun reloadWithUrls(index: Int, videoId: String, newVideoUrl: String, newAudioUrl: String?) {
        if (!isInitialized || index < 0) return
        val slot = index % POOL_SIZE
        val player = players[slot] ?: return
        if (playerOwnerIndices[slot] != index) return

        val wasPlaying = player.isPlaying || player.playWhenReady
        val position = player.currentPosition

        player.stop()
        player.clearMediaItems()
        playerVideoUrls[slot] = newVideoUrl
        playerAudioUrls[slot] = newAudioUrl

        preparePlayerInternal(player, newVideoUrl, newAudioUrl)
        player.setPlaybackSpeed(basePlaybackSpeed)
        player.playWhenReady = wasPlaying
        if (position > 0) player.seekTo(position)
        player.repeatMode = if (shortsPlaybackMode == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun preparePlayerInternal(player: ExoPlayer, videoUrl: String, audioUrl: String?) {
        val factory = dataSourceFactory ?: return

        if (audioUrl != null && audioUrl != videoUrl) {
            val videoSource = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(videoUrl))
            val audioSource = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            val mergingSource = MergingMediaSource(true, true, videoSource, audioSource)
            player.setMediaSource(mergingSource)
        } else {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
        }

        player.prepare()
        player.setPlaybackSpeed(basePlaybackSpeed)
    }


    // PLAYBACK CONTROL
    
    /**
     * Helper to find the actively playing player slot
     */
    private fun findActivePlayer(): ExoPlayer? {
        val activeVideoId = _currentVideoId.value ?: return null
        for (i in 0 until POOL_SIZE) {
            if (playerVideoIds[i] == activeVideoId) {
                return players[i]
            }
        }
        return null
    }

    fun play() {
        findActivePlayer()?.let { player ->
            player.setPlaybackSpeed(basePlaybackSpeed)
            player.playWhenReady = true
        }
    }

    fun pause() {
        findActivePlayer()?.playWhenReady = false
    }

    fun togglePlayPause() {
        val player = findActivePlayer() ?: return
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        findActivePlayer()?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        findActivePlayer()?.setPlaybackSpeed(speed)
    }

    fun resetPlaybackSpeed() {
        findActivePlayer()?.setPlaybackSpeed(basePlaybackSpeed)
    }

    fun setBasePlaybackSpeed(speed: Float) {
        basePlaybackSpeed = speed
        findActivePlayer()?.setPlaybackSpeed(speed)
    }

    fun getBasePlaybackSpeed(): Float = basePlaybackSpeed

    /** Pause ALL players */
    fun pauseAll() {
        for (i in 0 until POOL_SIZE) {
            players[i]?.playWhenReady = false
        }
    }

    /**
     * Release all players and reset state.
     * Call when leaving the Shorts screen.
     */
    fun release() {
        Log.d(TAG, "Releasing player pool")
        preferenceObservers.stop()
        for (i in 0 until POOL_SIZE) {
            players[i]?.stop()
            players[i]?.release()
            players[i] = null
            playerVideoIds[i] = null
            playerOwnerIndices[i] = null
            playerVideoUrls[i] = null
            playerAudioUrls[i] = null
        }
        dataSourceFactory = null
        isInitialized = false
        _currentVideoId.value = null
        _currentVideo.value = null
    }

    fun isReady(): Boolean = isInitialized && players[0] != null
}

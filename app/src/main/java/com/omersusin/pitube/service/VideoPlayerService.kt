package com.omersusin.pitube.service

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.omersusin.pitube.R
import com.omersusin.pitube.notification.NotificationHelper
import com.omersusin.pitube.player.EnhancedPlayerManager
import com.omersusin.pitube.player.GlobalPlayerState
import com.omersusin.pitube.player.PopupPlayerWindow
import com.omersusin.pitube.player.error.PlayerDiagnostics
import com.omersusin.pitube.utils.FlowCrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
class VideoPlayerService : MediaSessionService() {
    companion object {
        private const val TAG = "VideoPlayerService"
        private const val LOCK_RELEASE_DELAY_MS = 30_000L
        const val ACTION_SHOW_POPUP = "com.omersusin.pitube.action.SHOW_POPUP_PLAYER"
        const val ACTION_HIDE_POPUP = "com.omersusin.pitube.action.HIDE_POPUP_PLAYER"
        const val ACTION_NOTIF_TOGGLE_LIKE = "com.omersusin.pitube.action.NOTIF_TOGGLE_LIKE"
        const val ACTION_NOTIF_TOGGLE_DISLIKE = "com.omersusin.pitube.action.NOTIF_TOGGLE_DISLIKE"
        const val ACTION_NOTIF_TOGGLE_RADIO = "com.omersusin.pitube.action.NOTIF_TOGGLE_RADIO"

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_CHANNEL = "video_channel"
        const val EXTRA_VIDEO_THUMBNAIL = "video_thumbnail"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var lockReleaseJob: Job? = null
    private var popupPlayerWindow: PopupPlayerWindow? = null

    private var playbackNotificationProvider: PlaybackNotificationProvider? = null

    private fun serviceSnapshot(): String {
        val player = EnhancedPlayerManager.getInstance().getPlayer()
        val state =
            when (player?.playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                null -> "NO_PLAYER"
                else -> "UNKNOWN(${player.playbackState})"
            }
        return "exo=$state pwr=${player?.playWhenReady} playing=${player?.isPlaying} " +
            "pos=${player?.currentPosition}/${player?.duration} " +
            "idx=${player?.currentMediaItemIndex} count=${player?.mediaItemCount} " +
            "wakeHeld=${wakeLock?.isHeld} wifiHeld=${wifiLock?.isHeld} " +
            "ongoing=${runCatching { isPlaybackOngoing() }.getOrDefault(false)} " +
            "activeForLocks=${isPlaybackActiveForLocks()}"
    }

    private fun serviceLog(message: String) {
        val full = "$message | ${serviceSnapshot()}"
        Log.w(TAG, full)
        PlayerDiagnostics.logWarning(TAG, full)
    }

    override fun onCreate() {
        super.onCreate()
        val notificationProvider =
            DefaultMediaNotificationProvider
                .Builder(this)
                .setChannelId(NotificationHelper.CHANNEL_PLAYBACK)
                .setChannelName(R.string.notification_channel_video_playback)
                .setNotificationId(NotificationHelper.NOTIFICATION_PLAYBACK)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_logo) }
        val wrappedProvider = PlaybackNotificationProvider(this, notificationProvider)
        playbackNotificationProvider = wrappedProvider
        setMediaNotificationProvider(wrappedProvider)

        // Keep the custom notification buttons in sync with the settings.
        val prefs = com.omersusin.pitube.data.local.PlayerPreferences(this)
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                prefs.notificationActionLike,
                prefs.notificationActionDislike,
                prefs.notificationActionRadio,
            ) { like, dislike, radio -> Triple(like, dislike, radio) }
                .collect { (like, dislike, radio) ->
                    val provider = wrappedProvider
                    val changed =
                        provider.showLike != like ||
                            provider.showDislike != dislike ||
                            provider.showRadio != radio
                    provider.showLike = like
                    provider.showDislike = dislike
                    provider.showRadio = radio
                    if (changed) {
                        runCatching {
                            EnhancedPlayerManager.getInstance()
                                .getVideoMediaSession()
                                ?.mediaNotificationManager
                                ?.invalidateNotification()
                        }
                    }
                }
        }
        recordForegroundStartFailures("video-service")

        FlowCrashHandler.recordPhase("video-service", "onCreate")
        serviceLog("onCreate")

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:VideoPlayerWakeLock")
            wakeLock?.setReferenceCounted(false)

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Flow:VideoPlayerWifiLock")
            wifiLock?.setReferenceCounted(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create locks", e)
        }

        EnhancedPlayerManager.getInstance().initialize(applicationContext)

        serviceScope.launch {
            EnhancedPlayerManager.getInstance().playerState.collectLatest {
                serviceLog("playerState collect update")
                updateLocks(isPlaybackActiveForLocks())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        EnhancedPlayerManager.getInstance().initialize(applicationContext)
        serviceLog("onGetSession controller=${controllerInfo.packageName}")
        return EnhancedPlayerManager.getInstance().getVideoMediaSession()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val startResult = super.onStartCommand(intent, flags, startId)
        FlowCrashHandler.recordPhase(
            "video-service",
            "onStartCommand action=${intent?.action} startId=$startId",
        )
        serviceLog("onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_SHOW_POPUP -> showPopupPlayer()
            ACTION_HIDE_POPUP -> popupPlayerWindow?.dismiss()
            ACTION_NOTIF_TOGGLE_LIKE -> serviceScope.launch { toggleLikeDislike(like = true) }
            ACTION_NOTIF_TOGGLE_DISLIKE -> serviceScope.launch { toggleLikeDislike(like = false) }
            ACTION_NOTIF_TOGGLE_RADIO -> serviceScope.launch {
                val prefs = com.omersusin.pitube.data.local.PlayerPreferences(this@VideoPlayerService)
                prefs.setRadioModeEnabled(!prefs.radioModeEnabled.first())
                refreshPlaybackNotification()
            }
        }

        updateLocks(isPlaybackActiveForLocks())
        return startResult
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceLog("onTaskRemoved pip=${GlobalPlayerState.isInPipMode.value}")
        if (GlobalPlayerState.isInPipMode.value) {
            GlobalPlayerState.requestDismiss()
            EnhancedPlayerManager.getInstance().stop()
            releaseLocks()
            stopSelf()
            return
        }

        if (isPlaybackOngoing()) return

        EnhancedPlayerManager.getInstance().stop()
        releaseLocks()
        stopSelf()
    }

    override fun onDestroy() {
        serviceLog("onDestroy")
        popupPlayerWindow?.dismiss()
        popupPlayerWindow = null
        lockReleaseJob?.cancel()
        lockReleaseJob = null
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showPopupPlayer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !android.provider.Settings.canDrawOverlays(this)) return
        val player = EnhancedPlayerManager.getInstance().getPlayer() ?: return
        if (popupPlayerWindow == null) {
            popupPlayerWindow =
                PopupPlayerWindow(this) {
                    popupPlayerWindow?.dismiss()
                    EnhancedPlayerManager.getInstance().stop()
                    stopSelf()
                }
        }
        popupPlayerWindow?.show(player)
    }

    private fun acquireLocks() {
        serviceLog("acquireLocks")
        if (wakeLock?.isHeld != true) wakeLock?.acquire()
        if (wifiLock?.isHeld != true) wifiLock?.acquire()
    }

    private fun updateLocks(isPlaybackActive: Boolean) {
        serviceLog("updateLocks active=$isPlaybackActive")
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (isPlaybackActive) {
            acquireLocks()
            return
        }

        lockReleaseJob =
            serviceScope.launch {
                delay(LOCK_RELEASE_DELAY_MS)
                releaseLocks()
            }
    }

    private fun isPlaybackActiveForLocks(): Boolean {
        val player = EnhancedPlayerManager.getInstance().getPlayer() ?: return false
        return player.isPlaying ||
            (
                player.playWhenReady &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED
            )
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wifi lock", e)
        }
    }

    /** Like/dislike the currently playing video from a notification button. */
    private suspend fun toggleLikeDislike(like: Boolean) {
        val video = GlobalPlayerState.currentVideo.value ?: return
        val likedRepo = com.omersusin.pitube.data.local.LikedVideosRepository.getInstance(this)
        val accountActions = com.omersusin.pitube.data.local.AccountActions(this)
        val currentState = likedRepo.getLikeState(video.id).first()
        if (like) {
            if (currentState == "LIKED") {
                likedRepo.removeLikeState(video.id)
                accountActions.setLikeStatus(video.id, null)
            } else {
                likedRepo.likeVideo(
                    com.omersusin.pitube.data.local.LikedVideoInfo(
                        videoId = video.id,
                        title = video.title,
                        thumbnail = video.thumbnailUrl,
                        channelName = video.channelName,
                    )
                )
                accountActions.setLikeStatus(video.id, "LIKE")
            }
        } else {
            if (currentState == "DISLIKED") {
                likedRepo.removeLikeState(video.id)
                accountActions.setLikeStatus(video.id, null)
            } else {
                likedRepo.dislikeVideo(video.id)
                accountActions.setLikeStatus(video.id, "DISLIKE")
            }
        }
        refreshPlaybackNotification()
    }

    private fun refreshPlaybackNotification() {
        runCatching {
            EnhancedPlayerManager.getInstance()
                .getVideoMediaSession()
                ?.mediaNotificationManager
                ?.invalidateNotification()
        }
    }
}

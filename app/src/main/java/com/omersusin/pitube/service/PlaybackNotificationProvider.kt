package com.omersusin.pitube.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaNotificationData
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.omersusin.pitube.R

/**
 * Wraps media3's default playback notification and appends the user-selected
 * custom action buttons (like / dislike / radio, RiMusic-style). The delegate
 * keeps building the standard MediaStyle notification; this provider only
 * attaches the extra actions and forwards everything else.
 */
class PlaybackNotificationProvider(
    private val context: Context,
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider {

    @Volatile var showLike: Boolean = false
    @Volatile var showDislike: Boolean = false
    @Volatile var showRadio: Boolean = false

    private fun customAction(code: Int, title: String, iconRes: Int): Notification.Action =
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, iconRes),
            title,
            PendingIntent.getService(
                context,
                code,
                Intent(context, VideoPlayerService::class.java)
                    .setAction(
                        when (code) {
                            101 -> VideoPlayerService.ACTION_NOTIF_TOGGLE_LIKE
                            102 -> VideoPlayerService.ACTION_NOTIF_TOGGLE_DISLIKE
                            else -> VideoPlayerService.ACTION_NOTIF_TOGGLE_RADIO
                        },
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    override fun createNotification(
        mediaSession: MediaSession,
        mediaNotificationData: MediaNotificationData,
    ): Notification {
        val notification = delegate.createNotification(mediaSession, mediaNotificationData)
        val extras = mutableListOf<Notification.Action>()
        if (showLike) {
            extras += customAction(101, context.getString(R.string.like), R.drawable.ic_notif_like)
        }
        if (showDislike) {
            extras += customAction(102, context.getString(R.string.action_dislike), R.drawable.ic_notif_dislike)
        }
        if (showRadio) {
            extras += customAction(103, context.getString(R.string.player_settings_radio_mode), R.drawable.ic_notif_radio)
        }
        if (extras.isNotEmpty()) {
            notification.actions = (notification.actions.orEmpty() + extras).toTypedArray()
        }
        return notification
    }

    override fun handleCommand(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
    ): Boolean = delegate.handleCommand(mediaSession, controller, customCommand)

    override fun getMediaButtons(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        intent: Intent,
    ): List<CommandButton>? = delegate.getMediaButtons(mediaSession, controller, intent)
}

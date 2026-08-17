package com.omersusin.pitube.service

import android.content.Intent
import android.os.Bundle
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.omersusin.pitube.R

/**
 * Wraps media3's default playback notification and appends the user-selected
 * custom action buttons (like / dislike / radio, RiMusic-style). The delegate
 * keeps building the standard MediaStyle notification; this provider only
 * attaches the extra actions and forwards everything else.
 *
 * The extra actions go through [MediaNotification.ActionFactory.createCustomAction]
 * so media3 routes them to [handleCustomCommand] (this provider) and lays them
 * out in the notification like any other media button.
 */
class PlaybackNotificationProvider(
    private val context: Context,
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider {

    @Volatile var showLike: Boolean = false
    @Volatile var showDislike: Boolean = false
    @Volatile var showRadio: Boolean = false

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.omersusin.pitube.notification.TOGGLE_LIKE"
        const val ACTION_TOGGLE_DISLIKE = "com.omersusin.pitube.notification.TOGGLE_DISLIKE"
        const val ACTION_TOGGLE_RADIO = "com.omersusin.pitube.notification.TOGGLE_RADIO"
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val built = delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
        val notification = built.notification
        val extras = mutableListOf<NotificationCompat.Action>()
        if (showLike) {
            extras += actionFactory.createCustomAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notif_like),
                context.getString(R.string.like),
                ACTION_TOGGLE_LIKE,
                Bundle.EMPTY,
            )
        }
        if (showDislike) {
            extras += actionFactory.createCustomAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notif_dislike),
                context.getString(R.string.action_dislike),
                ACTION_TOGGLE_DISLIKE,
                Bundle.EMPTY,
            )
        }
        if (showRadio) {
            extras += actionFactory.createCustomAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_notif_radio),
                context.getString(R.string.player_settings_radio_mode),
                ACTION_TOGGLE_RADIO,
                Bundle.EMPTY,
            )
        }
        if (extras.isNotEmpty()) {
            notification.actions = (notification.actions.orEmpty() + extras).toTypedArray()
        }
        return MediaNotification(built.notificationId, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean {
        val context = context
        when (action) {
            ACTION_TOGGLE_LIKE -> {
                val intent = Intent(context, VideoPlayerService::class.java)
                    .setAction(VideoPlayerService.ACTION_NOTIF_TOGGLE_LIKE)
                runCatching { context.startService(intent) }
                return true
            }
            ACTION_TOGGLE_DISLIKE -> {
                val intent = Intent(context, VideoPlayerService::class.java)
                    .setAction(VideoPlayerService.ACTION_NOTIF_TOGGLE_DISLIKE)
                runCatching { context.startService(intent) }
                return true
            }
            ACTION_TOGGLE_RADIO -> {
                val intent = Intent(context, VideoPlayerService::class.java)
                    .setAction(VideoPlayerService.ACTION_NOTIF_TOGGLE_RADIO)
                runCatching { context.startService(intent) }
                return true
            }
        }
        return delegate.handleCustomCommand(session, action, extras)
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}

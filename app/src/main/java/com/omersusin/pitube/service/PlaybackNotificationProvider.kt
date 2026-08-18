package com.omersusin.pitube.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * attaches the extra actions (plain platform actions routed straight to
 * [VideoPlayerService]) and forwards everything else.
 */
class PlaybackNotificationProvider(
    private val context: Context,
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider {

    @Volatile var showLike: Boolean = false
    @Volatile var showDislike: Boolean = false
    @Volatile var showRadio: Boolean = false

    private fun customAction(code: Int, title: String, iconRes: Int): NotificationCompat.Action =
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
        val combined = mutableListOf<NotificationCompat.Action>()
        notification.actions.orEmpty().forEach {
            combined += NotificationCompat.Action.Builder(it).build()
        }
        if (showLike) {
            combined += customAction(101, context.getString(R.string.like), R.drawable.ic_notif_like)
        }
        if (showDislike) {
            combined += customAction(102, context.getString(R.string.action_dislike), R.drawable.ic_notif_dislike)
        }
        if (showRadio) {
            combined += customAction(103, context.getString(R.string.player_settings_radio_mode), R.drawable.ic_notif_radio)
        }
        // The platform `Notification.Builder(Context, Notification)` constructor
        // is @hide — NotificationCompat rebuilds from the existing notification
        // while setActions replaces the action list.
        val rebuilt =
            NotificationCompat.Builder(context, notification)
                .setActions(*combined.toTypedArray())
                .build()
        return MediaNotification(built.notificationId, rebuilt)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}

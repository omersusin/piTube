package com.omersusin.pitube.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omersusin.pitube.MainActivity
import com.omersusin.pitube.R

/**
 * Recognition notifications (Port of Audile's notification behavior): a
 * persistent "Song Recognition" notification whose tap reopens the recognition
 * modal, plus a one-shot notification when an offline-retried recording
 * finally matches. Both only appear when the "Notifications" preference is on.
 */
class RecognitionNotifier private constructor(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_RECOGNITION = "recognition_channel"
        const val NOTIFICATION_RECOGNITION_ENTRY = 4100
        const val NOTIFICATION_RECOGNITION_RESULT = 4101

        @Volatile
        private var instance: RecognitionNotifier? = null

        fun getInstance(context: Context): RecognitionNotifier =
            instance ?: synchronized(this) {
                instance ?: RecognitionNotifier(context.applicationContext).also { instance = it }
            }

        /** No-op outside O+; idempotent. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_RECOGNITION) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RECOGNITION,
                    context.getString(R.string.notification_channel_recognition),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_recognition_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                },
            )
        }

        /** Intent that brings the app forward and opens the recognition modal. */
        fun openRecognitionModalIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_RECOGNITION_MODAL, true)
            }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private fun hasPermission(): Boolean = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Persistent entry-point notification (shown while the toggle is on). */
    fun showEntryNotification() {
        ensureChannel(context)
        if (!hasPermission()) return
        val contentIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_RECOGNITION_ENTRY,
                openRecognitionModalIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_RECOGNITION)
                .setSmallIcon(R.drawable.ic_recognition_mic)
                .setContentTitle(context.getString(R.string.recognition_notification_title))
                .setContentText(context.getString(R.string.recognition_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        runCatching { notificationManager.notify(NOTIFICATION_RECOGNITION_ENTRY, notification) }
    }

    fun cancelEntryNotification() {
        runCatching { notificationManager.cancel(NOTIFICATION_RECOGNITION_ENTRY) }
    }

    /**
     * Shown when an offline-saved recording matches after reconnect, or when a
     * background floating-button recognition succeeds. With [openSearch] the
     * tap opens the app with the song query prefilled in Search (instead of
     * reopening the recognition modal).
     */
    fun showMatchedTrackNotification(track: TrackMatch, openSearch: Boolean = false) {
        ensureChannel(context)
        if (!hasPermission()) return
        val tapIntent =
            if (openSearch) {
                RecognitionOverlayService.openSearchPreloadedIntent(context, track.searchQuery)
            } else {
                openRecognitionModalIntent(context)
            }
        val contentIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_RECOGNITION_RESULT,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val text = context.getString(R.string.recognition_result_notification_text, track.title, track.artist)
        val notification =
            NotificationCompat.Builder(context, CHANNEL_RECOGNITION)
                .setSmallIcon(R.drawable.ic_recognition_mic)
                .setContentTitle(context.getString(R.string.recognition_result_notification_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        runCatching { notificationManager.notify(NOTIFICATION_RECOGNITION_RESULT, notification) }
    }
}

const val EXTRA_OPEN_RECOGNITION_MODAL = "open_recognition_modal"
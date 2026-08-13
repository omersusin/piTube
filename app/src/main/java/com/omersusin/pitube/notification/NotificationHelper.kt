package com.omersusin.pitube.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.omersusin.pitube.MainActivity
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.AppDatabase
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.entity.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Comprehensive notification helper for the app
 * Handles all notification channels and provides methods for showing various notification types
 */
object NotificationHelper {
    // Notification Channel IDs
    const val CHANNEL_DOWNLOADS = "downloads_channel"
    const val CHANNEL_PLAYBACK = "playback_channel"
    const val CHANNEL_MUSIC_PLAYBACK = "music_playback_channel"
    const val CHANNEL_GENERAL = "general_channel"
    const val CHANNEL_REMINDERS = "reminders_channel"
    const val CHANNEL_UPDATES = "updates_channel"

    // Notification IDs
    const val NOTIFICATION_DOWNLOAD_PROGRESS = 1001
    const val NOTIFICATION_DOWNLOAD_COMPLETE = 1002
    const val NOTIFICATION_DOWNLOAD_FAILED = 1003
    const val NOTIFICATION_PLAYBACK = 3001
    const val NOTIFICATION_MUSIC_PLAYBACK = 3002
    const val NOTIFICATION_GENERAL = 4000
    const val NOTIFICATION_REMINDER = 5000
    private const val NOTIFICATION_BITMAP_MAX_PX = 512

    private var channelsCreated = false

    /**
     * Store notification in database
     */
    private suspend fun storeNotification(
        context: Context,
        entity: NotificationEntity,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                db.notificationDao().insertNotification(entity)
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to store notification", e)
            }
        }
    }

    /**
     * Initialize all notification channels
     * Should be called once when the app starts (e.g., in Application.onCreate())
     */
    fun createNotificationChannels(context: Context) {
        if (channelsCreated) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Downloads channel - High importance for active downloads
            val downloadsChannel =
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_downloads_description)
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(false)
                }

            // Video playback channel - Low importance for background playback
            val playbackChannel =
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    context.getString(R.string.notification_channel_video_playback),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_video_playback_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

            // Music playback channel - Low importance for background music
            val musicPlaybackChannel =
                NotificationChannel(
                    CHANNEL_MUSIC_PLAYBACK,
                    context.getString(R.string.notification_channel_music_playback),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_music_playback_description)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

            // General notifications channel
            val generalChannel =
                NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_general_description)
                    setShowBadge(true)
                }

            val remindersChannel =
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_break_reminders_description)
                    setShowBadge(true)
                }

            // Updates channel
            val updatesChannel =
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notification_channel_updates),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_updates_description)
                    setShowBadge(true)
                }

            notificationManager.createNotificationChannels(
                listOf(
                    downloadsChannel,
                    playbackChannel,
                    musicPlaybackChannel,
                    generalChannel,
                    remindersChannel,
                    updatesChannel,
                ),
            )

            channelsCreated = true
        }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (!runBlocking { PlayerPreferences(context).notificationsEnabled.first() }) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ========== IMPORT NOTIFICATIONS ==========


    // ========== DOWNLOAD NOTIFICATIONS ==========

    /**
     * Show download progress notification
     */
    fun showDownloadProgress(
        context: Context,
        videoTitle: String,
        progress: Int,
        downloadSpeed: String? = null,
        largeIcon: Bitmap? = null,
        downloadId: Long = -1,
        notificationId: Int = NOTIFICATION_DOWNLOAD_PROGRESS,
    ) {
        if (!hasNotificationPermission(context)) return

        val cancelIntent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_CANCEL_DOWNLOAD
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(NotificationActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            }
        val cancelPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText =
            if (downloadSpeed != null) {
                "$progress% • $downloadSpeed"
            } else {
                "$progress%"
            }

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_downloading, videoTitle))
                .setContentText(contentText)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_delete,
                    context.getString(R.string.cancel),
                    cancelPendingIntent,
                ).setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * Show download complete notification
     */
    suspend fun showDownloadComplete(
        context: Context,
        videoTitle: String,
        filePath: String? = null,
        thumbnailUrl: String? = null,
        notificationId: Int = NOTIFICATION_DOWNLOAD_COMPLETE,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!PlayerPreferences(context).notifDownloadsEnabled.first()) return

        // Intent to open the downloaded file or app
        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_downloads", true)
            }
        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_download_complete))
                .setContentText(videoTitle)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)

        // Cancel progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_DOWNLOAD_PROGRESS)

        // Load thumbnail if provided
        if (!thumbnailUrl.isNullOrEmpty()) {
            val bitmap = getBitmapFromUrl(context, thumbnailUrl)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * Show download failed notification
     */
    fun showDownloadFailed(
        context: Context,
        videoTitle: String,
        errorMessage: String? = null,
        notificationId: Int = NOTIFICATION_DOWNLOAD_FAILED,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifDownloadsEnabled.first() }) return

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_download_failed))
                .setContentText(videoTitle)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText("$videoTitle\n${errorMessage ?: context.getString(R.string.error_generic_hint)}"),
                ).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()

        // Cancel progress notification
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_DOWNLOAD_PROGRESS)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    // ========== UPDATE NOTIFICATIONS ==========

    /**
     * Show notification for new app update
     */
    fun showUpdateNotification(
        context: Context,
        version: String,
        changelog: String,
        downloadUrl: String,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifUpdatesEnabled.first() }) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_UPDATE_VERSION", version)
                putExtra("EXTRA_UPDATE_CHANGELOG", changelog)
                putExtra("EXTRA_UPDATE_URL", downloadUrl)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_update_available, version))
                .setContentText(context.getString(R.string.notification_tap_to_update))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()

        NotificationManagerCompat.from(context).notify(9999, notification)
    }

    // ========== GENERAL NOTIFICATIONS ==========

    /**
     * Show a simple notification
     */
    fun showSimpleNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_GENERAL,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifGeneralEnabled.first() }) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Show watch later reminder notification
     */
    fun showWatchLaterReminder(
        context: Context,
        videoTitle: String,
        videoId: String,
        thumbnailUrl: String? = null,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifGeneralEnabled.first() }) return

        val notificationId = NOTIFICATION_GENERAL + videoId.hashCode().and(0xFFF)

        val watchIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("video_id", videoId)
            }
        val watchPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                watchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_watch_later_reminder))
                .setContentText(videoTitle)
                .setContentIntent(watchPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    // ========== UTILITY FUNCTIONS ==========

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(
        context: Context,
        notificationId: Int,
    ) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Show reminder notification (Bedtime, Take a break)
     */
    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifRemindersEnabled.first() }) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER, builder.build())
            }
        } catch (e: SecurityException) {
            // Should be covered by hasNotificationPermission check, but safety first
            e.printStackTrace()
        }
    }

    fun showUpcomingVideoLiveNotification(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
    ) {
        if (!hasNotificationPermission(context)) return
        if (!runBlocking { PlayerPreferences(context).notifRemindersEnabled.first() }) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_video_id", videoId)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                videoId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val liveText = context.getString(R.string.notification_channel_live, channelName, title)
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notification_video_live))
                .setContentText(liveText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(liveText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        runBlocking {
            val bitmap = thumbnailUrl?.let { getBitmapFromUrl(context, it) }
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER + videoId.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Load bitmap from URL for notification large icon/picture.
     *
     * Uses the app's shared Coil ImageLoader so notification artwork reuses the memory/disk
     * cache the feed already populated instead of refetching through a second image stack.
     * Hardware bitmaps are disabled because notification bitmaps must be parcelable to
     * SystemUI, and INEXACT precision keeps the "never upscale" behaviour of the previous
     * centerInside/onlyScaleDown request.
     */
    suspend fun getBitmapFromUrl(
        context: Context,
        url: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (url.isEmpty()) return@withContext null
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(NOTIFICATION_BITMAP_MAX_PX)
                        .scale(Scale.FIT)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)
                    ?.image
                    ?.toBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}

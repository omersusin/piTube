package com.omersusin.pitube.data.local

import android.content.Context
import com.omersusin.pitube.data.local.entity.NotificationEntity
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.pages.NotificationEntry

/**
 * Pulls the signed-in user's YouTube notification inbox into the local
 * `notifications` table. A no-op when not signed in. The inbox is mirrored
 * wholesale (clear + insert) so it always matches the account's real inbox.
 */
object NotificationSync {

    suspend fun sync(context: Context) {
        val dao = AppDatabase.getDatabase(context).notificationDao()
        if (YouTube.cookie.isNullOrBlank()) return

        val page = YouTube.getNotificationInbox().getOrNull() ?: return

        dao.deleteAllNotifications()
        dao.insertAllNotifications(page.notifications.map { it.toEntity() })
    }
}

private fun NotificationEntry.toEntity(): NotificationEntity {
    val hasVideo = videoId != null
    return NotificationEntity(
        videoId = videoId ?: "general",
        title = message,
        channelName = "",
        thumbnailUrl = if (hasVideo) {
            videoThumbnailUrl ?: channelAvatarUrl
        } else {
            channelAvatarUrl
        },
        timestamp = parseRelativeTime(sentTimeText),
        isRead = isRead,
        type = if (hasVideo) "NEW_VIDEO" else "GENERAL",
    )
}

private fun parseRelativeTime(text: String): Long {
    val now = System.currentTimeMillis()
    val value = text.filter { it.isDigit() }.toLongOrNull() ?: return now
    return when {
        text.contains("second") -> now - value * 1000
        text.contains("minute") -> now - value * 60_000
        text.contains("hour") -> now - value * 3_600_000
        text.contains("day") -> now - value * 86_400_000
        text.contains("week") -> now - value * 604_800_000
        text.contains("month") -> now - value * 2_592_000_000L
        text.contains("year") -> now - value * 31_536_000_000L
        else -> now
    }
}

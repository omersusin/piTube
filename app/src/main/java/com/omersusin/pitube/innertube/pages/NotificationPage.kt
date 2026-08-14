package com.omersusin.pitube.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The signed-in notification inbox, parsed from
 * `notification/get_notification_menu`. Each entry is a `notificationRenderer`.
 */
data class NotificationPage(
    val notifications: List<NotificationEntry>,
    val continuation: String?,
)

data class NotificationEntry(
    val message: String,
    val sentTimeText: String,
    val channelAvatarUrl: String?,
    val videoThumbnailUrl: String?,
    val videoId: String?,
    val isRead: Boolean,
)

/** Parse one page of the notification menu (top-level items). */
internal fun JsonElement.toNotificationPage(): NotificationPage {
    val root = this as? JsonObject
        ?: return NotificationPage(notifications = emptyList(), continuation = null)

    val renderers = mutableListOf<JsonObject>()
    findNotificationRenderers(root, renderers)

    val notifications = renderers.mapNotNull { renderer ->
        val message = renderer["shortMessage"].youtubeText()?.takeIf { it.isNotBlank() }
            ?: renderer["message"].youtubeText()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val videoId = renderer["navigationEndpoint"].objectOrNull()
            ?.get("watchEndpoint").objectOrNull()
            ?.get("videoId").stringOrNull()
            ?.takeIf { it.isNotBlank() }
        NotificationEntry(
            message = message,
            sentTimeText = renderer["sentTimeText"].youtubeText().orEmpty(),
            channelAvatarUrl = renderer["thumbnail"].lastThumbnailUrl(),
            videoThumbnailUrl = renderer["videoThumbnail"].lastThumbnailUrl(),
            videoId = videoId,
            isRead = renderer["read"].let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
        )
    }

    val continuationTokens = mutableListOf<String>()
    findNotificationContinuationTokens(root, continuationTokens)

    return NotificationPage(
        notifications = notifications.distinctBy { it.videoId to it.message },
        continuation = continuationTokens.firstOrNull(),
    )
}

private fun JsonElement?.lastThumbnailUrl(): String? {
    val thumbnails = objectOrNull()?.get("thumbnails").arrayOrNull()
        ?: arrayOrNull()
        ?: return null
    val url = thumbnails.lastOrNull()?.objectOrNull()
        ?.get("url").stringOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return if (url.startsWith("//")) "https:$url" else url
}

private fun findNotificationRenderers(node: JsonElement?, results: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> {
            node["notificationRenderer"].objectOrNull()?.let { results.add(it) }
            node.values.forEach { child ->
                if (child is JsonObject || child is JsonArray) findNotificationRenderers(child, results)
            }
        }
        is JsonArray -> node.forEach { findNotificationRenderers(it, results) }
        else -> Unit
    }
}

private fun findNotificationContinuationTokens(node: JsonElement?, results: MutableList<String>) {
    if (node is JsonObject) {
        node["getNotificationMenuEndpoint"].objectOrNull()
            ?.get("ctoken").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node["continuationItemRenderer"].objectOrNull()
            ?.get("continuationEndpoint").objectOrNull()
            ?.get("getNotificationMenuEndpoint").objectOrNull()
            ?.get("ctoken").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node.values.forEach { findNotificationContinuationTokens(it, results) }
    } else if (node is JsonArray) {
        node.forEach { findNotificationContinuationTokens(it, results) }
    }
}

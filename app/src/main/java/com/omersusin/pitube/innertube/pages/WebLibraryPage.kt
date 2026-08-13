package com.omersusin.pitube.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Account library reads through the WEB client (www.youtube.com), ported from
 * Koda's getSubscribedChannels / getVideoPlaylists / getPlaylistVideos.
 * The music-oriented WEB_REMIX equivalents stay empty for video-only accounts,
 * which is why account sync used to report all-zero counts.
 */

data class RemoteChannel(
    val id: String,
    val name: String,
    val thumbnail: String = "",
)

data class RemotePlaylist(
    val id: String,
    val title: String,
    val thumbnail: String = "",
    val videoCountText: String = "",
)

data class RemotePlaylistVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    val thumbnail: String = "",
)

/** Channels from the signed FEchannels /browse (channelRenderer items). */
internal fun JsonElement.toRemoteChannels(): List<RemoteChannel> {
    val renderers = mutableListOf<JsonObject>()
    findChannelRenderersInSubscriptionGrids(this, renderers)
    return renderers.mapNotNull { renderer ->
        val channelId = renderer["channelId"].stringOrNull()
            ?.takeIf { it.startsWith("UC") && it.length > 10 } ?: return@mapNotNull null
        val name = renderer["title"].youtubeText()
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val thumbs = renderer["thumbnail"].objectOrNull()
            ?.get("thumbnails").arrayOrNull().orEmpty()
        val avatarUrl = thumbs.lastOrNull()?.objectOrNull()
            ?.get("url").stringOrNull()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        RemoteChannel(
            id = channelId,
            name = name,
            thumbnail = avatarUrl.orEmpty(),
        )
    }.distinctBy { it.id }
}

/**
 * Collects only the `channelRenderer` items that represent *your* subscription
 * grid, skipping recommendation shelves. FEchannels can mix the subscribed
 * channels grid with "channels you may like" shelves whose channelRenderers
 * must never be imported as subscriptions — walking the whole tree (as
 * [findObjectsByKey] does) wrote those back into the local library in earlier
 * builds, which is how channels the user never subscribed to appeared as
 * subscribed. Channel renderers are only accepted when they live inside a
 * grid-ish container (`gridRenderer` / `channelListRowRenderer` / a direct
 * `channelRenderer` item) rather than a shelf (`shelfRenderer`,
 * `horizontalListRenderer`, `expandedShelfContentsRenderer`).
 */
private fun findChannelRenderersInSubscriptionGrids(node: JsonElement?, results: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> {
            val inlineChannel = node["channelRenderer"].objectOrNull()
            if (inlineChannel != null) {
                results.add(inlineChannel)
            }
            val grid = node["gridRenderer"].objectOrNull()
            if (grid != null) {
                grid.findChannelRenderersInSubscriptionGrids(results)
            }
            val row = node["channelListRowRenderer"].objectOrNull()
            if (row != null) {
                row.findChannelRenderersInSubscriptionGrids(results)
            }
            // Skip any shelf container entirely: this is where YouTube hides
            // "related / you might like" channels on the subscriptions page.
            val hasShelf = listOf(
                "shelfRenderer",
                "horizontalListRenderer",
                "expandedShelfContentsRenderer",
                "richShelfRenderer",
            ).any { node[it].objectOrNull() != null }
            if (hasShelf) return
            node.values.forEach { child ->
                if (child is JsonObject || child is JsonArray) {
                    child.findChannelRenderersInSubscriptionGrids(results)
                }
            }
        }
        is JsonArray -> node.forEach {
            it.findChannelRenderersInSubscriptionGrids(results)
        }
        else -> Unit
    }
}

private fun JsonObject.findChannelRenderersInSubscriptionGrids(results: MutableList<JsonObject>) {
    findChannelRenderersInSubscriptionGrids(this, results)
}

/** First continuation token of a browse response (richGrid/continuation items). */
internal fun JsonElement.browseContinuation(): String? {
    val tokens = mutableListOf<String>()
    findContinuationTokens(this, tokens)
    return tokens.firstOrNull()
}

/**
 * Continuation token of a playlist page (`playlistVideoListContinuation`).
 * Looked up specifically inside that section so a menu/endpoint token from a
 * video item can never be mistaken for the "next page" token — with this,
 * liked videos ("LL") and large playlists can be crawled past the first
 * ~100-item page.
 */
internal fun JsonElement.playlistVideoListContinuationToken(): String? {
    val sections = mutableListOf<JsonObject>()
    findObjectsByKey(this, "playlistVideoListContinuation", sections)
    return sections.firstNotNullOfOrNull { section ->
        section["continuations"].arrayOrNull()
            ?.firstOrNull()?.objectOrNull()
            ?.get("nextContinuationData").objectOrNull()
            ?.get("continuation").stringOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}

/** Playlists from the signed FEplaylist_aggregation /browse (lockupViewModel items). */
internal fun JsonElement.toRemotePlaylists(): List<RemotePlaylist> {
    val lockups = mutableListOf<JsonObject>()
    findObjectsByKey(this, "lockupViewModel", lockups)
    return lockups.mapNotNull { lockup ->
        val contentType = lockup["contentType"].stringOrNull()
        if (contentType != "LOCKUP_CONTENT_TYPE_PLAYLIST" &&
            contentType != "LOCKUP_CONTENT_TYPE_PODCAST"
        ) return@mapNotNull null
        val playlistId = lockup["contentId"].stringOrNull()
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val metadata = lockup["metadata"].objectOrNull()
            ?.get("lockupMetadataViewModel").objectOrNull()
        val title = metadata?.get("title").objectOrNull()
            ?.get("content").stringOrNull()
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val contentImage = lockup["contentImage"].objectOrNull()
        val thumbnailViewModel = contentImage?.get("collectionThumbnailViewModel").objectOrNull()
            ?.get("primaryThumbnail").objectOrNull()
            ?.get("thumbnailViewModel").objectOrNull()
            ?: contentImage?.get("thumbnailViewModel").objectOrNull()
        val sources = thumbnailViewModel?.get("image").objectOrNull()
            ?.get("sources").arrayOrNull()
        var thumbnailUrl: String? = null
        var maxWidth = -1
        if (sources != null) {
            for (source in sources) {
                val sourceObj = source.objectOrNull() ?: continue
                val width = (sourceObj["width"] as? JsonPrimitive)?.intOrNull ?: 0
                if (width >= maxWidth) {
                    maxWidth = width
                    thumbnailUrl = sourceObj["url"].stringOrNull()
                }
            }
        }
        val badges = mutableListOf<JsonObject>()
        findObjectsByKey(lockup, "thumbnailBadgeViewModel", badges)
        val videoCountText = badges.firstNotNullOfOrNull { it["text"].stringOrNull()?.takeIf(String::isNotBlank) }
            .orEmpty()
        RemotePlaylist(
            id = playlistId,
            title = title,
            thumbnail = thumbnailUrl.orEmpty(),
            videoCountText = videoCountText,
        )
    }.distinctBy { it.id }
}

/**
 * Videos of one playlist from a signed VL<playlistId> /browse. Regular
 * playlists come as playlistVideoRenderer items; liked videos ("LL") come as
 * plain video lockupViewModels — parse whichever the response contains.
 */
internal fun JsonElement.toRemotePlaylistVideos(): List<RemotePlaylistVideo> {
    val renderers = mutableListOf<JsonObject>()
    findObjectsByKey(this, "playlistVideoRenderer", renderers)
    if (renderers.isNotEmpty()) {
        return renderers.mapNotNull { renderer ->
            val videoId = renderer["videoId"].stringOrNull()
                ?.takeIf { it.length == 11 } ?: return@mapNotNull null
            if (renderer["isPlayable"].let { (it as? JsonPrimitive)?.booleanOrNull } == false) {
                return@mapNotNull null
            }
            val title = renderer["title"].youtubeText()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val byline = renderer["shortBylineText"].objectOrNull()
            val channelName = byline?.youtubeText()?.takeIf { it.isNotBlank() } ?: "Unknown Channel"
            val channelId = byline?.get("runs").arrayOrNull()
                ?.firstOrNull()?.objectOrNull()
                ?.get("navigationEndpoint").objectOrNull()
                ?.get("browseEndpoint").objectOrNull()
                ?.get("browseId").stringOrNull()
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            val thumbs = renderer["thumbnail"].objectOrNull()
                ?.get("thumbnails").arrayOrNull().orEmpty()
            val thumbnailUrl = thumbs.lastOrNull()?.objectOrNull()
                ?.get("url").stringOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            RemotePlaylistVideo(
                id = videoId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                thumbnail = thumbnailUrl,
            )
        }.distinctBy { it.id }
    }

    val lockups = mutableListOf<JsonObject>()
    findObjectsByKey(this, "lockupViewModel", lockups)
    return lockups.mapNotNull { lockup ->
        val videoId = lockup["contentId"].stringOrNull()
            ?.takeIf { it.length == 11 } ?: return@mapNotNull null
        val metadata = lockup["metadata"].objectOrNull()
            ?.get("lockupMetadataViewModel").objectOrNull()
        val title = metadata?.get("title").objectOrNull()
            ?.get("content").stringOrNull()
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val metadataRows = metadata?.get("metadata").objectOrNull()
            ?.get("contentMetadataViewModel").objectOrNull()
            ?.get("metadataRows").arrayOrNull().orEmpty()
        val channelPart = metadataRows.firstOrNull()?.objectOrNull()
            ?.get("metadataParts").arrayOrNull()
            ?.firstOrNull()?.objectOrNull()
        val channelName = channelPart?.get("text").objectOrNull()
            ?.get("content").stringOrNull()?.takeIf { it.isNotBlank() }
            ?: "Unknown Channel"
        val channelId = channelPart?.get("text").objectOrNull()
            ?.get("runs").arrayOrNull()
            ?.firstOrNull()?.objectOrNull()
            ?.get("navigationEndpoint").objectOrNull()
            ?.get("browseEndpoint").objectOrNull()
            ?.get("browseId").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        val thumbnail = metadata?.get("image").objectOrNull()
            ?.get("decoratedAvatarViewModel").objectOrNull()
            ?.get("avatar").objectOrNull()
            ?.get("avatarViewModel").objectOrNull()
            ?.get("image").objectOrNull()
            ?.get("sources").arrayOrNull()
            ?.maxByOrNull { source ->
                ((source.objectOrNull()?.get("width")) as? JsonPrimitive)?.intOrNull ?: 0
            }?.objectOrNull()?.get("url").stringOrNull()
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        RemotePlaylistVideo(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnail = thumbnail,
        )
    }.distinctBy { it.id }
}

private fun findObjectsByKey(node: JsonElement?, key: String, results: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> {
            node[key].objectOrNull()?.let { results.add(it) }
            node.values.forEach { child ->
                if (child is JsonObject || child is JsonArray) findObjectsByKey(child, key, results)
            }
        }
        is JsonArray -> node.forEach { findObjectsByKey(it, key, results) }
        else -> Unit
    }
}

private fun findContinuationTokens(node: JsonElement?, results: MutableList<String>) {
    if (node is JsonObject) {
        node["nextContinuationData"].objectOrNull()
            ?.get("continuation").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node["continuationEndpoint"].objectOrNull()
            ?.get("continuationCommand").objectOrNull()
            ?.get("token").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node["continuationCommand"].objectOrNull()
            ?.get("token").stringOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                results.add(it)
                return
            }
        node.values.forEach { findContinuationTokens(it, results) }
    } else if (node is JsonArray) {
        node.forEach { findContinuationTokens(it, results) }
    }
}
package com.omersusin.pitube.innertube

import com.omersusin.pitube.innertube.models.AccountInfo
import com.omersusin.pitube.innertube.models.Artist
import com.omersusin.pitube.innertube.models.BrowseEndpoint
import com.omersusin.pitube.innertube.models.StoryboardFrameset
import com.omersusin.pitube.innertube.models.PlaylistItem
import com.omersusin.pitube.innertube.models.Run
import com.omersusin.pitube.innertube.models.Runs
import com.omersusin.pitube.innertube.models.WatchEndpoint
import com.omersusin.pitube.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.omersusin.pitube.innertube.models.YouTubeClient
import com.omersusin.pitube.innertube.models.YouTubeClient.Companion.WEB
import com.omersusin.pitube.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.omersusin.pitube.innertube.models.YouTubeLocale
import com.omersusin.pitube.innertube.models.getContinuation
import com.omersusin.pitube.innertube.models.getItems
import com.omersusin.pitube.innertube.models.response.AccountMenuResponse
import com.omersusin.pitube.innertube.models.response.BrowseResponse
import com.omersusin.pitube.innertube.models.response.ChannelVideosResponse
import com.omersusin.pitube.innertube.models.response.channelVideoCountText
import com.omersusin.pitube.innertube.models.response.GetTranscriptResponse
import com.omersusin.pitube.innertube.models.response.NextResponse
import com.omersusin.pitube.innertube.models.response.PlayerResponse
import com.omersusin.pitube.innertube.pages.CommunityCommentsPage
import com.omersusin.pitube.innertube.pages.CommunityPostsPage
import com.omersusin.pitube.innertube.pages.HistoryPage
import com.omersusin.pitube.innertube.pages.PlaylistContinuationPage
import com.omersusin.pitube.innertube.pages.PlaylistPage
import com.omersusin.pitube.innertube.pages.SearchShortItem
import com.omersusin.pitube.innertube.pages.TranscriptLine
import com.omersusin.pitube.innertube.pages.SearchVideosPage
import com.omersusin.pitube.innertube.pages.ShortsPage
import com.omersusin.pitube.innertube.pages.toSearchShorts
import com.omersusin.pitube.innertube.pages.toSearchVideosPage
import com.omersusin.pitube.innertube.pages.toCommunityCommentsPage
import com.omersusin.pitube.innertube.pages.toCommunityPostsPage
import com.omersusin.pitube.innertube.pages.toShortsPage
import com.omersusin.pitube.innertube.pages.VideoCommentsPage
import com.omersusin.pitube.innertube.pages.RemoteChannel
import com.omersusin.pitube.innertube.pages.RemotePlaylist
import com.omersusin.pitube.innertube.pages.RemotePlaylistVideo
import com.omersusin.pitube.innertube.pages.browseContinuation
import com.omersusin.pitube.innertube.pages.playlistVideoListContinuationToken
import com.omersusin.pitube.innertube.pages.hasSucceededActionResult
import com.omersusin.pitube.innertube.pages.toCreatedVideoComment
import com.omersusin.pitube.innertube.pages.toRemoteChannels
import com.omersusin.pitube.innertube.pages.toRemotePlaylists
import com.omersusin.pitube.innertube.pages.toRemotePlaylistVideos
import com.omersusin.pitube.innertube.pages.toVideoCommentsPage
import com.omersusin.pitube.innertube.pages.toVideoCommentsToken
import com.omersusin.pitube.innertube.pages.NewPipeExtractor
import com.omersusin.pitube.data.model.Comment
import com.omersusin.pitube.data.model.VideoCollaborator
import com.omersusin.pitube.utils.avatarImageIdentityKey
import com.omersusin.pitube.utils.potoken.WebPoTokenSession
import android.net.Uri
import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonArray
import java.net.Proxy
import java.util.Locale
import kotlin.random.Random
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()
    private const val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
    private const val CHANNEL_LIVE_PARAMS = "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"
    private const val CHANNEL_POSTS_PARAMS = "EgVwb3N0c_IGBAoCSgA="

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) {
            innerTube.dataSyncId = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }

    var proxyAuth: String?
        get() = innerTube.proxyAuth
        set(value) {
            innerTube.proxyAuth = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }
    var onCookieRotated: ((String) -> Unit)?
        get() = innerTube.cookieRefreshListener
        set(value) {
            innerTube.cookieRefreshListener = value
        }

    // Long-form search ignores the Shorts shelf; fetch it from the main site (not music).
    suspend fun searchShorts(query: String): Result<List<SearchShortItem>> = runCatching {
        innerTube.webSearch(currentWebClient(), query).body<JsonObject>().toSearchShorts()
    }.onSuccess { Log.d("SearchShorts", "query='$query' shorts=${it.size}") }
        .onFailure { Log.w("SearchShorts", "query='$query' failed: ${it.message}") }

    suspend fun searchByViews(
        query: String,
        searchParams: String,
        continuation: String? = null,
    ): Result<SearchVideosPage> = runCatching {
        ensureVisitorData()
        val searchClient = currentWebClient()
        innerTube.webSearch(
            client = searchClient,
            query = query.takeIf { continuation == null },
            params = searchParams.takeIf { continuation == null },
            continuation = continuation,
            anonymous = true,
            includeVisitorData = true,
        ).body<JsonObject>().toSearchVideosPage()
    }

    private suspend fun ensureVisitorData() {
        if (!visitorData.isNullOrBlank()) return
        visitorData().getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { visitorData = it }
    }

    private suspend fun currentWebClient(): YouTubeClient = withContext(Dispatchers.IO) {
        WEB.copy(
            clientVersion = runCatching { YoutubeParsingHelper.getClientVersion() }
                .getOrDefault(WEB.clientVersion),
        )
    }


    /**
     * Main YouTube search exposes collaboration avatars in a modern entity block:
     * searchVideoResultEntityKey + avatar.avatarStackViewModel. NewPipe only returns
     * the uploader avatar, so callers can merge this lightweight map by video id.
     */
    suspend fun searchVideoAvatarStacks(query: String): Result<Map<String, List<String>>> = runCatching {
        val rawBody = innerTube.webSearch(WEB, query).bodyAsText()
        val root = Json { ignoreUnknownKeys = true; explicitNulls = false }.parseToJsonElement(rawBody)
        buildMap {
            collectSearchVideoAvatarStacks(root, this)
        }
    }

    suspend fun videoAvatarStack(videoId: String): Result<List<String>> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = Json { ignoreUnknownKeys = true; explicitNulls = false }.parseToJsonElement(rawBody)
        root.findVideoOwnerAvatarStackUrls()
    }

    suspend fun videoCollaborators(videoId: String): Result<List<VideoCollaborator>> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = Json { ignoreUnknownKeys = true; explicitNulls = false }.parseToJsonElement(rawBody)
        root.findVideoOwnerCollaborators()
    }

    private fun collectSearchVideoAvatarStacks(
        element: JsonElement,
        result: MutableMap<String, List<String>>,
    ) {
        when (element) {
            is JsonArray -> element.forEach { collectSearchVideoAvatarStacks(it, result) }
            is JsonObject -> {
                if (element.containsKey("searchVideoResultEntityKey") && element.containsKey("avatar")) {
                    val videoId = element.findFirstString("videoId")
                    val avatarUrls = element["avatar"]
                        ?.collectAvatarImageUrls()
                        .orEmpty()

                    if (!videoId.isNullOrBlank() && avatarUrls.isNotEmpty()) {
                        result[videoId] = avatarUrls
                    }
                }
                element.values.forEach { collectSearchVideoAvatarStacks(it, result) }
            }
            else -> Unit
        }
    }

    private fun JsonElement.findFirstString(key: String): String? =
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonPrimitive)?.contentOrNull
                    ?: values.firstNotNullOfOrNull { it.findFirstString(key) }
            }
            is JsonArray -> firstNotNullOfOrNull { it.findFirstString(key) }
            else -> null
        }

    private fun JsonElement.findDirectOrNestedString(key: String): String? =
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonPrimitive)?.contentOrNull
                    ?: values.firstNotNullOfOrNull { it.findDirectOrNestedString(key) }
            }
            is JsonArray -> firstNotNullOfOrNull { it.findDirectOrNestedString(key) }
            else -> null
        }

    private fun JsonElement.collectChannelBrowseIds(): List<String> {
        val ids = mutableListOf<String>()

        fun collect(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::collect)
                is JsonObject -> {
                    val browseId = (element["browseId"] as? JsonPrimitive)?.contentOrNull
                    if (!browseId.isNullOrBlank() && browseId.startsWith("UC")) {
                        ids += browseId
                    }
                    val channelId = (element["channelId"] as? JsonPrimitive)?.contentOrNull
                    if (!channelId.isNullOrBlank() && channelId.startsWith("UC")) {
                        ids += channelId
                    }
                    element.values.forEach(::collect)
                }
                else -> Unit
            }
        }

        collect(this)
        return ids.distinct()
    }

    private fun JsonElement.collectAvatarImageUrls(): List<String> {
        val urls = mutableListOf<String>()

        fun collect(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::collect)
                is JsonObject -> {
                    val url = (element["url"] as? JsonPrimitive)?.contentOrNull
                    if (!url.isNullOrBlank() && url.contains("yt3.ggpht.com")) {
                        urls += url
                    }
                    element.values.forEach(::collect)
                }
                else -> Unit
            }
        }

        collect(this)
        return urls
            .distinctBy { it.avatarImageIdentityKey() }
            .take(5)
    }

    private fun JsonElement.findVideoOwnerAvatarStackUrls(): List<String> =
        when (this) {
            is JsonObject -> {
                val owner = this["videoOwnerRenderer"] as? JsonObject
                val ownerStack = owner?.get("avatarStack")
                    ?.collectAvatarImageUrls()
                    .orEmpty()
                    .take(2)
                if (ownerStack.size > 1) {
                    ownerStack
                } else {
                    values.firstNotNullOfOrNull { child ->
                        child.findVideoOwnerAvatarStackUrls().takeIf { it.size > 1 }
                    }.orEmpty()
                }
            }
            is JsonArray -> firstNotNullOfOrNull { child ->
                child.findVideoOwnerAvatarStackUrls().takeIf { it.size > 1 }
            }.orEmpty()
            else -> emptyList()
        }

    private fun JsonElement.findVideoOwnerCollaborators(): List<VideoCollaborator> =
        when (this) {
            is JsonObject -> {
                val owner = this["videoOwnerRenderer"] as? JsonObject
                val collaborators = owner?.extractCollaboratorDialogRows().orEmpty()
                if (collaborators.size > 1) {
                    collaborators
                } else {
                    values.firstNotNullOfOrNull { child ->
                        child.findVideoOwnerCollaborators().takeIf { it.size > 1 }
                    }.orEmpty()
                }
            }
            is JsonArray -> firstNotNullOfOrNull { child ->
                child.findVideoOwnerCollaborators().takeIf { it.size > 1 }
            }.orEmpty()
            else -> emptyList()
        }

    private fun JsonObject.extractCollaboratorDialogRows(): List<VideoCollaborator> {
        val listItems = getPath(
            "navigationEndpoint",
            "showDialogCommand",
            "panelLoadingStrategy",
            "inlineContent",
            "dialogViewModel",
            "customContent",
            "listViewModel",
            "listItems",
        ) as? JsonArray ?: return emptyList()

        return listItems
            .mapNotNull { item ->
                ((item as? JsonObject)?.get("listItemViewModel") as? JsonObject)
                    ?.toVideoCollaborator()
            }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.channelId.ifBlank { it.name.lowercase(Locale.US) } }
            .take(5)
    }

    private fun JsonObject.toVideoCollaborator(): VideoCollaborator? {
        val channelId = collectChannelBrowseIds().firstOrNull().orEmpty()
        val avatarUrl = collectAvatarImageUrls().firstOrNull().orEmpty()
        val title = (getPath("title", "content") as? JsonPrimitive)?.contentOrNull
        val subtitle = (getPath("subtitle", "content") as? JsonPrimitive)?.contentOrNull
        val label = ((getPath("rendererContext", "accessibilityContext", "label") as? JsonPrimitive)?.contentOrNull)
            ?: findDirectOrNestedString("label")
        val parsedName = title
            ?: label
            ?.substringBefore(". Go to channel")
            ?.substringBefore(" Go to channel")
            ?.substringBefore(" - ")
            ?.substringBefore(" • ")
            ?.substringBefore(" subscribers")
            ?.substringBefore(" subscriber")
            ?.substringBefore(", ")
            ?.takeIf { it.isNotBlank() }
        val subscriberText = label
            ?.substringAfter(" - ", missingDelimiterValue = "")
            ?.substringBefore(". Go to channel")
            ?.takeIf { it.contains("subscriber", ignoreCase = true) }
            ?: subtitle
                ?.substringAfter("•", missingDelimiterValue = "")
                ?.takeIf { it.contains("subscriber", ignoreCase = true) }
            .orEmpty()
            .cleanYouTubeDecoratedText()

        val content = findDirectOrNestedString("content")
            ?.takeIf { !it.contains("@") && !it.contains("subscriber", ignoreCase = true) }
        val name = parsedName ?: content ?: return null
        if (name.isSubscriptionOptionLabel()) return null

        val hasChannelMetadata = channelId.startsWith("UC") && avatarUrl.isNotBlank()
        if (!hasChannelMetadata) return null

        return VideoCollaborator(
            name = name.cleanYouTubeDecoratedText(),
            channelId = channelId,
            thumbnailUrl = avatarUrl,
            subscriberCountText = subscriberText,
        )
    }

    private fun JsonObject.getPath(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { current, key ->
            (current as? JsonObject)?.get(key)
        }

    private fun String.cleanYouTubeDecoratedText(): String =
        replace("\u200E", "")
            .replace("\u2068", "")
            .replace("\u2069", "")
            .trim()

    private fun String.isSubscriptionOptionLabel(): Boolean =
        trim().lowercase(Locale.US) in setOf(
            "personalized",
            "all",
            "none",
            "unsubscribe",
            "subscribed",
            "subscribe",
        )

    // ── Channel-scoped video search (YouTube.com WEB API) ─────────────────────

    data class ChannelVideoSearchResult(
        val videos: List<com.omersusin.pitube.data.model.Video>,
        val continuation: String?,
        val channelVideoCountText: String? = null,
    )

    /**
     * Search for videos within [channelId] matching [query].
     * Uses the YouTube.com WEB Innertube endpoint with a channel-scoped params filter.
     */
    suspend fun channelSearch(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        query: String,
    ): Result<ChannelVideoSearchResult> = runCatching {
        val httpResponse = innerTube.channelSearch(currentWebClient(), channelId, query)
        val rawBody = httpResponse.bodyAsText()
        val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val response = lenientJson.decodeFromString<com.omersusin.pitube.innertube.models.response.ChannelSearchResponse>(rawBody)
        parseChannelSearchResponse(response, channelId, channelName, channelThumbnailUrl)
    }

    /**
     * Signed-in watch history from the account's real YouTube history page
     * (browseId `FEhistory`), following continuation pages. Reuses the
     * channel-search response model — FEhistory renders the same
     * `twoColumnBrowseResultsRenderer > tabs > sectionListRenderer >
     * itemSectionRenderer > videoRenderer` layout.
     */
    suspend fun history(continuation: String? = null): Result<HistoryPage> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching HistoryPage(emptyList(), null)
        val httpResponse =
            innerTube.signedWebBrowse(currentWebClient(), browseId = "FEhistory", continuation = continuation)
        val rawBody = httpResponse.bodyAsText()
        val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val response = lenientJson.decodeFromString<com.omersusin.pitube.innertube.models.response.ChannelSearchResponse>(rawBody)

        val videos = mutableListOf<com.omersusin.pitube.data.model.Video>()
        var nextContinuation: String? = null

        val tabContents = response.contents
            ?.twoColumnBrowseResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
        if (!tabContents.isNullOrEmpty()) {
            tabContents.forEach { section ->
                section.itemSectionRenderer?.contents?.forEach { item ->
                    item.videoRenderer
                        ?.let { parseVideoRenderer(it, "", "", "") }
                        ?.let { videos.add(it) }
                }
                section.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                    ?.let { nextContinuation = it }
            }
        }

        if (nextContinuation == null) {
            nextContinuation = response.continuationContents
                ?.sectionListContinuation?.continuations
                ?.firstOrNull()?.nextContinuationData?.continuation
        }

        HistoryPage(videos = videos.distinctBy { it.id }, continuation = nextContinuation)
    }

    suspend fun channelSearchContinuation(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        continuation: String,
    ): Result<ChannelVideoSearchResult> = runCatching {
        val httpResponse = innerTube.channelSearch(
            currentWebClient(),
            channelId,
            query = "",
            continuation = continuation,
        )
        val rawBody = httpResponse.bodyAsText()
        val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val response = lenientJson.decodeFromString<com.omersusin.pitube.innertube.models.response.ChannelSearchResponse>(rawBody)

        val videos = mutableListOf<com.omersusin.pitube.data.model.Video>()
        var nextContinuation: String? = null

        val appendedItems = response.onResponseReceivedActions
            ?.firstOrNull { it.appendContinuationItemsAction != null }
            ?.appendContinuationItemsAction?.continuationItems.orEmpty()
        if (appendedItems.isNotEmpty()) {
            appendedItems.forEach { richItem ->
                richItem.richItemRenderer?.content?.videoRenderer
                    ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                    ?.let { videos.add(it) }
                richItem.itemSectionRenderer?.contents?.forEach { sectionItem ->
                    sectionItem.videoRenderer
                        ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                        ?.let { videos.add(it) }
                }
                richItem.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                    ?.let { nextContinuation = it }
            }
        }

        if (videos.isEmpty()) {
            val sectionContents = response.continuationContents?.sectionListContinuation?.contents.orEmpty()
            sectionContents.mapNotNull { it.itemSectionRenderer?.contents }
                .flatten()
                .mapNotNull { it.videoRenderer }
                .mapNotNull { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                .forEach { videos.add(it) }
            if (nextContinuation == null) {
                nextContinuation = response.continuationContents
                    ?.sectionListContinuation?.continuations
                    ?.firstOrNull()?.nextContinuationData?.continuation
                    ?: sectionContents.mapNotNull { it.continuationItemRenderer }
                        .firstOrNull()?.continuationEndpoint?.continuationCommand?.token
            }
        }

        if (videos.isEmpty()) {
            response.continuationContents?.richGridContinuation?.contents?.forEach { richItem ->
                richItem.richItemRenderer?.content?.videoRenderer
                    ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                    ?.let { videos.add(it) }
                richItem.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                    ?.let { nextContinuation = it }
            }
        }

        ChannelVideoSearchResult(videos = videos, continuation = nextContinuation)
    }

    suspend fun channelVideos(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<ChannelVideoSearchResult> =
        channelVideosPage(channelId, channelName, channelThumbnailUrl, CHANNEL_VIDEOS_PARAMS, false)

    suspend fun communityPosts(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<CommunityPostsPage> =
        communityPostsPage(channelId, channelName, channelThumbnailUrl)

    suspend fun communityPostsContinuation(
        continuation: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<CommunityPostsPage> =
        communityPostsPage(
            channelId = "",
            channelName = channelName,
            channelThumbnailUrl = channelThumbnailUrl,
            continuation = continuation,
        )

    suspend fun communityPostComments(
        postId: String,
        params: String?,
    ): Result<CommunityCommentsPage> = runCatching {
        val initial = communityPostCommentsPage(postId = postId, params = params)
        if (initial.comments.isNotEmpty() || initial.continuation == null) {
            initial
        } else {
            val commentsPage = communityPostCommentsPage(continuation = initial.continuation)
            commentsPage.copy(
                commentCountText = commentsPage.commentCountText ?: initial.commentCountText,
            )
        }
    }

    // ── Signed-in personalized home feed (YouTube.com WEB) ─────────────────────

    /**
     * The signed-in account's own home feed ("What to watch").
     *
     * Mirrors Koda's approach: a signed /browse on the FEwhat_to_watch browseId
     * through the WEB client, so YouTube returns the same personalized
     * recommendations the account sees on the real site. Requires a stored
     * session cookie; callers should fall back to their discovery feed when
     * this returns empty or fails.
     */
    suspend fun personalizedFeed(): Result<ChannelVideoSearchResult> = runCatching {
        personalizedFeedPage(browseId = "FEwhat_to_watch")
    }

    suspend fun personalizedFeedContinuation(
        continuation: String,
    ): Result<ChannelVideoSearchResult> = runCatching {
        personalizedFeedPage(continuation = continuation)
    }

    private suspend fun personalizedFeedPage(
        browseId: String? = null,
        continuation: String? = null,
    ): ChannelVideoSearchResult {
        val client = currentWebClient()
        val httpResponse = innerTube.signedWebBrowse(
            client = client,
            browseId = browseId,
            continuation = continuation,
        )
        val rawBody = httpResponse.bodyAsText()
        val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val response = lenientJson.decodeFromString<ChannelVideosResponse>(rawBody)
        return parseChannelVideosResponse(response, "", "", "", false)
    }

    // ============================================================
    // Comments (signed-in, Koda port). Reads and writes go through
    // signed POSTs on www.youtube.com so the session cookie and a
    // matching SAPISIDHASH are attached; the minimal webContext pins
    // hl=en so the "Delete" menu label match stays stable.
    // ============================================================

    /**
     * Initial comments continuation token for a video's comment section.
     * Extracted from the signed `next` response's comment-item-section.
     */
    suspend fun videoCommentsToken(videoId: String): Result<String?> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "next",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("videoId", JsonPrimitive(videoId))
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).toVideoCommentsToken()
    }

    /** One page of comments (top-level or replies) from a continuation token. */
    suspend fun videoCommentsPage(token: String): Result<VideoCommentsPage> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "next",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("continuation", JsonPrimitive(token))
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).toVideoCommentsPage()
    }

    /**
     * Post a new top-level comment. [createCommentParams] comes from the first
     * comments page (VideoCommentsPage.createCommentParams). Returns the
     * created comment parsed from the response, or null on failure.
     */
    suspend fun createComment(
        createCommentParams: String,
        text: String,
    ): Result<Comment?> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "comment/create_comment",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("commentText", JsonPrimitive(text))
                put("createCommentParams", JsonPrimitive(createCommentParams))
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).toCreatedVideoComment()
    }

    /** Post a reply to a comment. [replyParams] comes from the parent comment. */
    suspend fun createCommentReply(
        replyParams: String,
        text: String,
    ): Result<Comment?> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "comment/create_comment_reply",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("commentText", JsonPrimitive(text))
                put("createReplyParams", JsonPrimitive(replyParams))
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).toCreatedVideoComment()
    }

    /**
     * Execute a comment toolbar action (like/unlike/delete). The action param
     * comes from the comment's toolbar surface. Requires login.
     */
    suspend fun performCommentAction(action: String): Result<Boolean> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "comment/perform_comment_action",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("actions", buildJsonArray { add(JsonPrimitive(action)) })
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).hasSucceededActionResult()
    }

    private fun commentWebContext(client: YouTubeClient, visitor: String? = null): JsonObject =
        buildJsonObject {
            put(
                "client",
                buildJsonObject {
                    put("clientName", JsonPrimitive("WEB"))
                    put("clientVersion", JsonPrimitive(client.clientVersion))
                    put("hl", JsonPrimitive("en"))
                    put("gl", JsonPrimitive("US"))
                    if (!visitor.isNullOrBlank()) {
                        put("visitorData", JsonPrimitive(visitor))
                    }
                },
            )
        }

    // ============================================================
    // Watch-history reporting (yt-dlp _mark_watched port). Reports the
    // playback to YouTube so the video shows up in the account's watch
    // history: fetch the signed /player response, then fire BOTH the
    // videostatsPlaybackUrl and the videostatsWatchtimeUrl beacons with
    // ver=2 / cpn / cmt / el=detailpage (and st/et on the watchtime URL,
    // which is what actually registers watch time in history).
    // ============================================================

    /**
     * Report a video playback into the signed-in account's YouTube watch
     * history. Returns true when the tracking ping succeeded. Requires login.
     *
     * Mirrors yt-dlp's `_mark_watched`: fetches the signed player response,
     * then fires BOTH tracking URLs from `playbackTracking`:
     *  - `videostatsPlaybackUrl`  marks the video as watched,
     *  - `videostatsWatchtimeUrl` pushes actual watch time (`st`/`et`), which
     *    is what really registers in the account's history.
     * Each ping carries `ver=2`, a fresh `cpn`, `cmt` (position or just
     * before the end) and `el=detailpage`; existing query params are kept.
     *
     * [positionMs] is the watched-to position used for `cmt`/`et`. When 0 the
     * video is marked as watched right before the end (yt-dlp behaviour), so a
     * video is registered in history even if the app is killed mid-playback.
     *
     * One [cpn] covers one playback session; pass the same value on every ping
     * of the same video so YouTube treats the pings as one continuous session
     * (fresh cpns per ping look like repeated restarts in history).
     */
    suspend fun reportVideoPlayback(
        videoId: String,
        positionMs: Long = 0L,
        cpn: String = generateCpn(),
    ): Result<Boolean> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching false
        val tracking = signedPlaybackTracking(videoId, cpn) ?: return@runCatching false

        val playbackUrl = tracking.first ?: return@runCatching false
        val watchtimeUrl = tracking.second

        val lengthSeconds = tracking.third
        val videoLength = (lengthSeconds - 1f).coerceAtLeast(0f)
        // Integer seconds like every working client sends; a zero position
        // reports "started watching at 0:00" instead of marking the video as
        // fully watched (the old behaviour made everything show up fully
        // watched in official history).
        val cmt = when {
            lengthSeconds <= 0f -> 0L
            positionMs <= 0L -> 0L
            else -> (positionMs / 1000L).coerceIn(0L, videoLength.toLong())
        }
        val referer = "https://www.youtube.com/watch?v=$videoId"

        var reported = innerTube.videoStatsPing(
            url = withVideoStatsParams(playbackUrl, cpn, cmt, watchtime = false),
            referer = referer,
        )
        if (watchtimeUrl != null) {
            val watchtimeReported = innerTube.videoStatsPing(
                url = withVideoStatsParams(watchtimeUrl, cpn, cmt, watchtime = true),
                referer = referer,
            )
            reported = reported || watchtimeReported
        }
        reported
    }

    /**
     * Signed player response restricted to the fields needed for history
     * tracking. First tries the light unsigned request; if YouTube does not
     * return `playbackTracking` (bot wall), it retries WITH a signature
     * timestamp and a WebView-minted poToken, like the SABR player path.
     */
    private suspend fun signedPlaybackTracking(
        videoId: String,
        cpn: String,
    ): Triple<String?, String?, Float>? {
        val client = currentWebClient()

        suspend fun fetch(sts: Int?, poToken: String?, visitorData: String?): Triple<String?, String?, Float>? {
            val body = buildJsonObject {
                put("context", commentWebContext(client, visitorData.takeIf { !it.isNullOrBlank() }))
                put("videoId", JsonPrimitive(videoId))
                put("cpn", JsonPrimitive(cpn))
                if (sts != null || poToken != null) {
                    put(
                        "playbackContext",
                        buildJsonObject {
                            put(
                                "contentPlaybackContext",
                                buildJsonObject {
                                    if (sts != null) put("signatureTimestamp", JsonPrimitive(sts))
                                    put("referer", JsonPrimitive("https://www.youtube.com/watch?v=$videoId"))
                                    put("vis", JsonPrimitive(0))
                                    put("splay", JsonPrimitive(false))
                                    put("lactMilliseconds", JsonPrimitive("-1"))
                                    put("html5Preference", JsonPrimitive("HTML5_PREF_WANTS"))
                                },
                            )
                        },
                    )
                }
                if (poToken != null) {
                    put(
                        "serviceIntegrityDimensions",
                        buildJsonObject { put("poToken", JsonPrimitive(poToken)) },
                    )
                }
            }
            val httpResponse = innerTube.signedJsonPost(client = client, endpoint = "player", jsonBody = body)
            val root = Json.parseToJsonElement(httpResponse.bodyAsText())
            val tracking = root.jsonObject["playbackTracking"]?.jsonObject ?: return null
            val playback = tracking["videostatsPlaybackUrl"]?.jsonObject
                ?.get("baseUrl")?.jsonPrimitive?.contentOrNull
            if (playback.isNullOrEmpty()) return null
            val watchtime = tracking["videostatsWatchtimeUrl"]?.jsonObject
                ?.get("baseUrl")?.jsonPrimitive?.contentOrNull
            val length = tracking["videostatsWatchtimeUrl"]?.jsonObject
                ?.get("baseUrl")?.jsonPrimitive?.contentOrNull
                ?.let(::parseLengthFromTrackingUrl)
                ?: root.jsonObject["videoDetails"]?.jsonObject
                    ?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull
                    ?.toFloatOrNull()
                    ?: 0f
            return Triple(playback, watchtime, length)
        }

        // Attempt 1: signed WEB player request with signature timestamp and
        // the session visitor data — the same shape the stream extractor and
        // the original b7594dd reporter used, and what actually registers
        // history for signed-in accounts. The unsent light variant is
        // bot-walled for logged-in sessions.
        val sessionVisitor = visitorData?.takeIf { it.isNotBlank() }
        val sts = runCatching { NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull() }
            .getOrNull()
        fetch(sts, null, sessionVisitor)?.let { return it }

        // Attempt 2: full WEB player request with sts + poToken.
        runCatching {
            val visitorData = WebPoTokenSession.sessionVisitorData()
            if (visitorData.isNullOrEmpty()) return@runCatching null
            val poToken = WebPoTokenSession.mintForVisitorData(videoId, visitorData)
                ?.playerRequestPoToken ?: return@runCatching null
            val sts = NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
            fetch(sts, poToken, visitorData)
        }.getOrNull()?.let { return it }

        return null
    }

    private fun parseLengthFromTrackingUrl(url: String): Float {
        val lenMatch = Regex("(?:^|&)len=([^&]+)").find(url)?.groupValues?.get(1)
        return lenMatch?.toFloatOrNull() ?: 0f
    }

    /**
     * Rewrite a videostats tracking URL's query the way yt-dlp's
     * `_mark_watched` does: keep every existing parameter and set
     * `ver=2`, `cpn`, `cmt`, `c=WEB` and `el=detailpage`. The watchtime URL
     * also gets `st=0` and `et=cmt` so the video registers actual watch time.
     */
    private fun withVideoStatsParams(
        baseUrl: String,
        cpn: String,
        cmtSeconds: Long,
        watchtime: Boolean,
    ): String {
        val uri = Uri.parse(baseUrl)
        val params = LinkedHashMap<String, String>()
        uri.queryParameterNames.forEach { name ->
            params[name] = uri.getQueryParameter(name).orEmpty()
        }
        params["ver"] = "2"
        params["cpn"] = cpn
        params["c"] = "WEB"
        params["cmt"] = cmtSeconds.toString()
        params["el"] = "detailpage"
        if (watchtime) {
            params["st"] = "0"
            params["et"] = cmtSeconds.toString()
        }
        val builder = Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(uri.path)
        params.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build().toString()
    }

    /** Sixteen-char opaque ID tying every beacon of one playback session together. */
    fun newCpn(): String = generateCpn()

    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    // ============================================================
    // Account library on the WEB client (Koda port). The music-host
    // browse feeds stay empty for video-only accounts, so these signed
    // www.youtube.com /browse calls are what actually enumerate the
    // account's subscriptions, playlists and liked videos.
    // ============================================================

    /** All channels the user is subscribed to (FEchannels), following continuations. */
    suspend fun webSubscribedChannels(): Result<List<RemoteChannel>> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching emptyList()
        val client = currentWebClient()
        val channels = mutableListOf<RemoteChannel>()
        var continuation: String? = null
        var pages = 0
        do {
            val response = innerTube.signedWebBrowse(
                client = client,
                browseId = if (continuation == null) "FEchannels" else null,
                continuation = continuation,
            )
            if (!response.status.isSuccess()) {
                Log.w("YouTube", "webSubscribedChannels: HTTP ${response.status.value} on page $pages")
                break
            }
            val root = Json.parseToJsonElement(response.bodyAsText())
            val found = root.toRemoteChannels()
            channels += found
            if (found.isEmpty()) {
                Log.w("YouTube", "webSubscribedChannels: parser returned 0 channels on page $pages (body len ${response.bodyAsText().length})")
            }
            continuation = root.browseContinuation()
            pages++
        } while (continuation != null && pages < 10)
        channels.distinctBy { it.id }
    }

    /** The user's playlists from FEplaylist_aggregation (Watch Later / Liked pinned elsewhere). */
    suspend fun webUserPlaylists(): Result<List<RemotePlaylist>> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching emptyList()
        val client = currentWebClient()
        val response = innerTube.signedWebBrowse(client = client, browseId = "FEplaylist_aggregation")
        if (!response.status.isSuccess()) {
            Log.w("YouTube", "webUserPlaylists: HTTP ${response.status.value}")
            return@runCatching emptyList()
        }
        val playlists = Json.parseToJsonElement(response.bodyAsText()).toRemotePlaylists()
        if (playlists.isEmpty()) Log.w("YouTube", "webUserPlaylists: parser returned 0 playlists (body len ${response.bodyAsText().length})")
        playlists
    }

    /**
     * Videos of one playlist from a signed VL<playlistId> browse (liked
     * videos = "LL"). Follows continuation tokens until the playlist is fully
     * crawled ([maxPages] is just a safety cap), so accounts with thousands of
     * liked videos sync completely instead of stopping at the first ~100.
     */
    suspend fun webPlaylistVideos(playlistId: String, maxPages: Int = 100): Result<List<RemotePlaylistVideo>> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching emptyList()
        val client = currentWebClient()
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val videos = mutableListOf<RemotePlaylistVideo>()
        var continuation: String? = null
        var pages = 0
        do {
            val response = if (continuation == null) {
                innerTube.signedWebBrowse(client = client, browseId = browseId)
            } else {
                innerTube.signedWebBrowse(client = client, continuation = continuation)
            }
            if (!response.status.isSuccess()) {
                Log.w("YouTube", "webPlaylistVideos($playlistId): HTTP ${response.status.value} on page $pages")
                break
            }
            val root = Json.parseToJsonElement(response.bodyAsText())
            val found = root.toRemotePlaylistVideos()
            if (found.isEmpty()) {
                Log.w("YouTube", "webPlaylistVideos($playlistId): parser returned 0 videos on page $pages (body len ${response.bodyAsText().length})")
            }
            videos += found
            continuation = root.playlistVideoListContinuationToken() ?: root.browseContinuation()
            pages++
        } while (continuation != null && pages < maxPages)
        videos.distinctBy { it.id }
    }

    suspend fun communityPostCommentsContinuation(
        continuation: String,
    ): Result<CommunityCommentsPage> = runCatching {
        communityPostCommentsPage(continuation = continuation)
    }

    private suspend fun communityPostsPage(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        continuation: String? = null,
    ): Result<CommunityPostsPage> = runCatching {
        val client = currentWebClient()
        val response = innerTube.channelBrowse(
            client = client,
            channelId = channelId.takeIf { continuation == null },
            params = CHANNEL_POSTS_PARAMS.takeIf { continuation == null },
            continuation = continuation,
        )
        Json.parseToJsonElement(response.bodyAsText()).toCommunityPostsPage(
            fallbackAuthorName = channelName,
            fallbackAuthorAvatarUrl = channelThumbnailUrl,
        )
    }

    private suspend fun communityPostCommentsPage(
        postId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): CommunityCommentsPage {
        val client = currentWebClient()
        val response = innerTube.postCommentsBrowse(
            client = client,
            postId = postId,
            params = params,
            continuation = continuation,
        )
        return Json.parseToJsonElement(response.bodyAsText()).toCommunityCommentsPage()
    }

    private suspend fun channelVideosPage(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        params: String?,
        isLive: Boolean,
        continuation: String? = null,
    ): Result<ChannelVideoSearchResult> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.channelBrowse(
            client = client,
            channelId = if (continuation == null) channelId else null,
            params = params,
            continuation = continuation,
        )
        val rawBody = httpResponse.bodyAsText()
        val lenientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val response = lenientJson.decodeFromString<ChannelVideosResponse>(rawBody)
        parseChannelVideosResponse(response, channelId, channelName, channelThumbnailUrl, isLive)
    }

    private fun parseChannelVideosResponse(
        response: ChannelVideosResponse,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): ChannelVideoSearchResult {
        val metadata = response.metadata?.channelMetadataRenderer
        val resolvedChannelId = metadata?.externalChannelId
            ?: metadata?.externalId
            ?: channelId
        val resolvedChannelName = metadata?.title?.takeIf { it.isNotBlank() } ?: channelName
        val resolvedThumbnail = metadata?.avatar?.thumbnails
            ?.maxByOrNull { it.width ?: 0 }
            ?.url
            ?: channelThumbnailUrl

        val richItems = mutableListOf<ChannelVideosResponse.RichItem>()
        response.onResponseReceivedActions
            ?.flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
            ?.let { richItems += it }

        response.continuationContents?.richGridContinuation?.contents
            ?.let { richItems += it }

        val tabs = response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()
        val selectedTab = tabs.firstOrNull { it.tabRenderer?.selected == true }?.tabRenderer
            ?: tabs.firstOrNull { it.tabRenderer?.content?.richGridRenderer != null }?.tabRenderer
            ?: tabs.firstOrNull { it.expandableTabRenderer?.content?.richGridRenderer != null }?.expandableTabRenderer
        selectedTab?.content?.richGridRenderer?.contents?.let { richItems += it }

        val videos = mutableListOf<com.omersusin.pitube.data.model.Video>()
        var nextContinuation: String? = null
        richItems.forEach { richItem ->
            val content = richItem.richItemRenderer?.content
            content?.lockupViewModel
                ?.let { parseLockupViewModel(it, resolvedChannelId, resolvedChannelName, resolvedThumbnail, isLive) }
                ?.let { videos.add(it) }
            content?.videoRenderer
                ?.let { parseBrowseVideoRenderer(it, resolvedChannelId, resolvedChannelName, resolvedThumbnail, isLive) }
                ?.let { videos.add(it) }
            richItem.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                ?.let { nextContinuation = it }
        }

        return ChannelVideoSearchResult(
            videos = videos.distinctBy { it.id },
            continuation = nextContinuation,
            channelVideoCountText = response.channelVideoCountText(),
        )
    }

    private fun parseLockupViewModel(
        lockup: ChannelVideosResponse.LockupViewModel,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): com.omersusin.pitube.data.model.Video? {
        val videoId = lockup.contentId ?: return null
        if (videoId.length != 11) return null
        val metadata = lockup.metadata?.lockupMetadataViewModel
        val title = metadata?.title?.content?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail = lockup.contentImage?.thumbnailViewModel?.image?.sources
            ?.maxByOrNull { it.width ?: 0 }
            ?.url
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val durationText = lockup.contentImage?.thumbnailViewModel?.overlays
            ?.firstNotNullOfOrNull { overlay ->
                overlay.thumbnailBottomOverlayViewModel
                    ?.badges
                    ?.firstNotNullOfOrNull { it.thumbnailBadgeViewModel?.text }
            }
        val metadataRows = metadata?.metadata?.contentMetadataViewModel?.metadataRows.orEmpty()
        val channelPart = metadataRows.firstOrNull()?.metadataParts?.firstOrNull()
        val resolvedChannelId = channelPart?.runs
            ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint?.browseId }
            ?: channelId
        val resolvedChannelName =
            if (resolvedChannelId != channelId) {
                channelPart?.text?.content?.takeIf { it.isNotBlank() } ?: channelName
            } else {
                channelName
            }
        val resolvedThumbnail = metadata?.image
            ?.decoratedAvatarViewModel?.avatar?.avatarViewModel?.image?.sources
            ?.maxByOrNull { it.width ?: 0 }
            ?.url
            ?: channelThumbnailUrl
        val parts = mutableListOf<String>()
        metadataRows.forEachIndexed { rowIndex, row ->
            row.metadataParts?.forEachIndexed { partIndex, part ->
                if (rowIndex == 0 && partIndex == 0 && resolvedChannelId != channelId) return@forEachIndexed
                part.text?.content?.takeIf(String::isNotBlank)?.let { parts += it }
            }
        }
        val segments = parts
            .flatMap { it.split("•").map(String::trim).filter(String::isNotBlank) }
        // View counts and upload dates are localized (e.g. Turkish "görüntüleme"
        // / "önce"), so classify by shape instead of English keywords: the views
        // segment contains a number but no relative-time unit, the date segment
        // contains a relative-time unit. This also stops the channel name from
        // leaking into the "views"/"date" slots when no segment matches.
        val viewsText = segments.firstOrNull { segment ->
            segment.any(Char::isDigit) && !looksLikeRelativeDate(segment)
        }
        val uploadText = segments.firstOrNull { looksLikeRelativeDate(it) }.orEmpty()

        return com.omersusin.pitube.data.model.Video(
            id = videoId,
            title = title,
            channelName = resolvedChannelName,
            channelId = resolvedChannelId,
            thumbnailUrl = thumbnail,
            duration = parseLengthText(durationText),
            viewCount = parseViewCountText(viewsText),
            uploadDate = uploadText,
            timestamp = parseRelativeUploadDate(uploadText) ?: 0L,
            channelThumbnailUrl = resolvedThumbnail,
            isLive = isLive || viewsText?.contains("watching", ignoreCase = true) == true
                || viewsText?.contains("izliyor", ignoreCase = true) == true,
        )
    }

    private fun parseBrowseVideoRenderer(
        r: ChannelVideosResponse.VideoRenderer,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): com.omersusin.pitube.data.model.Video? {
        val videoId = r.videoId ?: return null
        val title = r.title?.textValue()?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail = r.thumbnail?.thumbnails?.maxByOrNull { it.width ?: 0 }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val uploadText = r.publishedTimeText?.textValue().orEmpty()
        val viewsText = r.viewCountText?.textValue()
        val resolvedChannelName = r.ownerText?.textValue()?.takeIf { it.isNotBlank() } ?: channelName
        val avatarUrls = r.channelAvatarUrls(channelThumbnailUrl)
        return com.omersusin.pitube.data.model.Video(
            id = videoId,
            title = title,
            channelName = resolvedChannelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = parseLengthText(r.lengthText?.textValue()),
            viewCount = parseViewCountText(viewsText),
            uploadDate = uploadText,
            timestamp = parseRelativeUploadDate(uploadText) ?: 0L,
            channelThumbnailUrl = avatarUrls.firstOrNull().orEmpty(),
            channelThumbnailUrls = avatarUrls,
            isLive = isLive || viewsText?.contains("watching", ignoreCase = true) == true,
        )
    }

    private fun ChannelVideosResponse.SimpleText.textValue(): String? =
        simpleText ?: runs?.joinToString("") { it.text.orEmpty() }

    private fun ChannelVideosResponse.VideoRenderer.channelAvatarUrls(fallback: String): List<String> {
        val supported = channelThumbnailSupportedRenderers
        val stackAvatars = listOfNotNull(
            avatarStackViewModel,
            supported?.avatarStackViewModel,
            supported?.channelThumbnailWithLinkRenderer?.avatarStack?.avatarStackViewModel,
        ).flatMap { stack ->
            stack.avatars.orEmpty().mapNotNull { avatar ->
                avatar.avatarViewModel?.image?.sources
                    ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                    ?.url
            }
        }
        val linkedAvatar = supported?.channelThumbnailWithLinkRenderer
            ?.thumbnail
            ?.thumbnails
            ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
            ?.url

        return (stackAvatars + linkedAvatar + fallback)
            .filter { !it.isNullOrBlank() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(2)
            .filterNotNull()
    }

    private fun parseChannelSearchResponse(
        response: com.omersusin.pitube.innertube.models.response.ChannelSearchResponse,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): ChannelVideoSearchResult {
        val tabs = response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()
        Log.d("ChannelSearch", "tabs=${tabs.size}")
        tabs.forEachIndexed { i, tab ->
            val url = tab.tabRenderer?.endpoint?.commandMetadata?.webCommandMetadata?.url
            Log.d("ChannelSearch", "tab[$i]: url=$url, selected=${tab.tabRenderer?.selected}, hasSection=${tab.tabRenderer?.content?.sectionListRenderer != null}, hasRichGrid=${tab.tabRenderer?.content?.richGridRenderer != null}, isExpandable=${tab.expandableTabRenderer != null}")
        }

        val tabContent =
            tabs.firstOrNull { it.tabRenderer?.selected == true && it.tabRenderer.endpoint?.commandMetadata?.webCommandMetadata?.url?.contains("/search") == true }?.tabRenderer?.content
            ?: tabs.firstOrNull { it.expandableTabRenderer?.content != null }?.expandableTabRenderer?.content
            ?: tabs.firstOrNull { it.tabRenderer?.selected == true }?.tabRenderer?.content

        Log.d("ChannelSearch", "tabContent=${tabContent != null}, hasSection=${tabContent?.sectionListRenderer != null}, hasRichGrid=${tabContent?.richGridRenderer != null}")

        val videos = mutableListOf<com.omersusin.pitube.data.model.Video>()
        var continuation: String? = null

        tabContent?.richGridRenderer?.contents?.forEach { richItem ->
            richItem.richItemRenderer?.content?.videoRenderer
                ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                ?.let { videos.add(it) }
            richItem.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                ?.let { continuation = it }
        }

        if (videos.isEmpty()) {
            val sectionContents = tabContent?.sectionListRenderer?.contents.orEmpty()
            sectionContents.mapNotNull { it.itemSectionRenderer?.contents }
                .flatten()
                .mapNotNull { it.videoRenderer }
                .mapNotNull { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                .forEach { videos.add(it) }
            if (continuation == null) {
                continuation = sectionContents.mapNotNull { it.continuationItemRenderer }
                    .firstOrNull()?.continuationEndpoint?.continuationCommand?.token
            }
        }

        Log.d("ChannelSearch", "videos=${videos.size}, hasContinuation=${continuation != null}")
        return ChannelVideoSearchResult(videos = videos, continuation = continuation)
    }

    private fun parseVideoRenderer(
        r: com.omersusin.pitube.innertube.models.response.ChannelSearchResponse.VideoRenderer,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): com.omersusin.pitube.data.model.Video? {
        val videoId = r.videoId ?: return null
        val title = r.title?.runs?.joinToString("") { it.text ?: "" }?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail = r.thumbnail?.thumbnails?.maxByOrNull { it.width ?: 0 }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val duration = parseLengthText(r.lengthText?.simpleText)
        val viewCount = parseViewCountText(r.viewCountText?.simpleText)
        val avatarUrls = r.channelAvatarUrls(channelThumbnailUrl)
        return com.omersusin.pitube.data.model.Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = duration,
            viewCount = viewCount,
            uploadDate = r.publishedTimeText?.simpleText ?: "",
            channelThumbnailUrl = avatarUrls.firstOrNull().orEmpty(),
            channelThumbnailUrls = avatarUrls,
        )
    }

    private fun com.omersusin.pitube.innertube.models.response.ChannelSearchResponse.VideoRenderer.channelAvatarUrls(
        fallback: String
    ): List<String> {
        val supported = channelThumbnailSupportedRenderers
        val stackAvatars = listOfNotNull(
            avatarStackViewModel,
            supported?.avatarStackViewModel,
            supported?.channelThumbnailWithLinkRenderer?.avatarStack?.avatarStackViewModel,
        ).flatMap { stack ->
            stack.avatars.orEmpty().mapNotNull { avatar ->
                avatar.avatarViewModel?.image?.sources
                    ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                    ?.url
            }
        }
        val linkedAvatar = supported?.channelThumbnailWithLinkRenderer
            ?.thumbnail
            ?.thumbnails
            ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
            ?.url

        return (stackAvatars + linkedAvatar + fallback)
            .filter { !it.isNullOrBlank() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(2)
            .filterNotNull()
    }

    private fun parseLengthText(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.split(":").map { it.trim().toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }

    private fun parseViewCountText(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val normalized = text.lowercase(Locale.US)
            .replace("views", "")
            .replace("view", "")
            .replace("watching", "")
            .replace("izliyor", "")
            .trim()
        val match = Regex("""(\d[\d.,]*\d|\d)""").find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?: return 0L
        val separatorCount = match.count { it == '.' || it == ',' }
        val value: Double =
            if (separatorCount >= 2) {
                // Thousands separators ("1,234,567 views" / Turkish "1.234.567")
                (match.replace(".", "").replace(",", "").toLongOrNull() ?: return 0L).toDouble()
            } else {
                // Decimal ("1.2M views" / Turkish "1,2 Mn görüntüleme")
                match.replace(',', '.').toDoubleOrNull() ?: return 0L
            }
        val hasTurkishDecimal = match.contains(',')
        val suffix = normalized.substring(match.lastIndex + 1).trim()
        val multiplier = when {
            suffix.startsWith("milyar") || suffix.startsWith("million") ||
                suffix.startsWith("milliard") || suffix.startsWith("bn") ||
                suffix.startsWith("mr") -> 1_000_000_000.0
            suffix.startsWith("milyon") || suffix.startsWith("million") ||
                suffix.startsWith("mn") || suffix.startsWith("m") -> 1_000_000.0
            suffix.startsWith("bin") || suffix.startsWith("thousand") ||
                suffix.startsWith("k") -> 1_000.0
            // Bare "b": English billion ("1.2B views") vs Turkish bin
            // ("1,2 B görüntüleme" / "123 B görüntüleme")
            suffix.startsWith("b") && (hasTurkishDecimal || suffix.length > 2) -> 1_000.0
            suffix.startsWith("b") -> 1_000_000_000.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    /** Relative-time units in English and Turkish (suffix "ago"/"önce" optional). */
    private val relativeTimeRegex = Regex(
        """(\d+)\s*(saniye|dakika|saat|gün|hafta|ay|yıl|second|minute|hour|day|week|month|year|sec|min|hr)s?\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun looksLikeRelativeDate(text: String): Boolean =
        relativeTimeRegex.containsMatchIn(text)

    private fun parseRelativeUploadDate(text: String?): Long? {
        val normalized = text?.lowercase(Locale.US)
            ?.replace("streamed", "")
            ?.replace("premiered", "")
            ?.replace("live", "")
            ?.replace("ago", "")
            ?.replace("önce", "")
            ?.replace("sonra", "")
            ?.trim()
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.contains("just now") || normalized.contains("today") ||
            normalized.contains("az önce") || normalized.contains("şimdi")
        ) return System.currentTimeMillis()
        if (normalized.contains("yesterday") || normalized.contains("dün")) {
            return System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        }

        val match = relativeTimeRegex.find(normalized) ?: run {
            // Short forms ("2d", "3h", "5m", "1y"…)
            val short = Regex("""(\d+)\s*(s|m|h|d|w|mo|y)\b""", RegexOption.IGNORE_CASE)
                .find(normalized) ?: return null
            val shortValue = short.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
            return System.currentTimeMillis() - shortValue * when (short.groupValues[2].lowercase(Locale.US)) {
                "s" -> 1_000L
                "m" -> 60_000L
                "h" -> 3_600_000L
                "d" -> 86_400_000L
                "w" -> 7L * 86_400_000L
                "mo" -> 30L * 86_400_000L
                else -> 365L * 86_400_000L
            }
        }
        val value = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val unitMillis = when (match.groupValues.getOrNull(2)?.lowercase(Locale.US)) {
            "saniye", "second", "sec" -> 1_000L
            "dakika", "minute", "min" -> 60_000L
            "saat", "hour", "hr" -> 3_600_000L
            "gün", "day" -> 86_400_000L
            "hafta", "week" -> 7L * 86_400_000L
            "ay", "month" -> 30L * 86_400_000L
            "yıl", "year" -> 365L * 86_400_000L
            else -> return null
        }

        return System.currentTimeMillis() - (value * unitMillis)
    }


    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = true
        ).body<BrowseResponse>()
        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header?.title?.runs?.firstOrNull()?.text!!,
                author = header.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
                playEndpoint = null,
                shuffleEndpoint = header.buttons.lastOrNull()?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,
                radioEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = editable
            ),
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                } ?: emptyList(),
            songsContinuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation(),
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            browseId = "",
            setLogin = true
        ).body<BrowseResponse>()

        val mainContents = response.continuationContents?.sectionListContinuation?.contents
            ?.flatMap { it.musicPlaylistShelfRenderer?.contents.orEmpty() }
            ?: emptyList()

        val appendedContents = response.onResponseReceivedActions
            ?.firstOrNull()
            ?.appendContinuationItemsAction
            ?.continuationItems
            .orEmpty()

        val allContents = mainContents + appendedContents

        val songs = allContents
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }

        val nextContinuation = response.continuationContents
            ?.sectionListContinuation
            ?.continuations
            ?.getContinuation()
            ?: response.continuationContents
                ?.musicPlaylistShelfContinuation
                ?.continuations
                ?.getContinuation()
            ?: response.continuationContents
                ?.musicShelfContinuation
                ?.continuations
                ?.getContinuation()
            ?: response.onResponseReceivedActions
                ?.firstOrNull()
                ?.appendContinuationItemsAction
                ?.continuationItems
                ?.getContinuation()

        PlaylistContinuationPage(
            songs = songs,
            continuation = nextContinuation
        )
    }



    suspend fun player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null, poToken: String? = null, localeOverride: YouTubeLocale? = null, apiUrl: String? = null): Result<PlayerResponse> = runCatching {
        innerTube.player(client, videoId, playlistId, signatureTimestamp, poToken, localeOverride, apiUrl).body<PlayerResponse>()
    }

    suspend fun playerWeb(
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
        visitorData: String?,
        locale: YouTubeLocale,
        cpn: String?,
        reloadToken: String? = null,
    ): Result<PlayerResponse> = runCatching {
        innerTube.playerWeb(videoId, signatureTimestamp, poToken, visitorData, locale, cpn, reloadToken).body<PlayerResponse>()
    }

    suspend fun liveChatContinuation(videoId: String): Result<String?> = runCatching {
        innerTube.nextForLiveChat(videoId)
            .body<com.omersusin.pitube.innertube.models.response.LiveChatSeedResponse>()
            .seedContinuation()
    }

    suspend fun liveChat(
        continuation: String,
        offsetMs: Long? = null,
    ): Result<com.omersusin.pitube.innertube.models.response.GetLiveChatResponse> = runCatching {
        innerTube.getLiveChat(continuation, offsetMs)
            .body<com.omersusin.pitube.innertube.models.response.GetLiveChatResponse>()
    }

    suspend fun watchMetadata(
        videoId: String,
    ): Result<com.omersusin.pitube.innertube.models.response.WatchMetadataResponse> = runCatching {
        val primary = innerTube.next(WEB, videoId, null, null, null, null, null)
            .body<com.omersusin.pitube.innertube.models.response.WatchMetadataResponse>()
        if (primary.relatedVideos().isNotEmpty()) {
            primary
        } else {
            val webWatch = runCatching {
                innerTube.nextForLiveChat(videoId)
                    .body<com.omersusin.pitube.innertube.models.response.WatchMetadataResponse>()
            }.getOrNull()
            if (webWatch != null && webWatch.relatedVideos().size > primary.relatedVideos().size) {
                webWatch
            } else {
                primary
            }
        }
    }

    suspend fun watchMetadataLite(
        videoId: String,
    ): Result<com.omersusin.pitube.innertube.models.response.WatchMetadataResponse> = runCatching {
        innerTube.next(WEB, videoId, null, null, null, null, null)
            .body<com.omersusin.pitube.innertube.models.response.WatchMetadataResponse>()
    }

    suspend fun lyricsEndpoint(videoId: String): Result<BrowseEndpoint?> = runCatching {
        innerTube.next(WEB_REMIX, videoId, null, null, null, null, null)
            .body<NextResponse>()
            .contents
            .singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs
            ?.getOrNull(1)
            ?.tabRenderer
            ?.endpoint
            ?.browseEndpoint
    }


    suspend fun transcript(videoId: String): Result<List<TranscriptLine>> = runCatching {
        innerTube.getTranscript(WEB_REMIX, videoId).body<GetTranscriptResponse>()
            .actions
            .orEmpty()
            .firstNotNullOfOrNull { it.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups }
            .orEmpty()
            .mapNotNull { group ->
                group.transcriptCueGroupRenderer?.cues
                    ?.firstOrNull()
                    ?.transcriptCueRenderer
                    ?.let { cue ->
                        cue.cue?.runs
                            ?.joinToString("") { it.text }
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { text -> TranscriptLine(startMs = cue.startOffsetMs, text = text) }
                    }
            }
    }

    suspend fun lyrics(endpoint: BrowseEndpoint?): Result<String> = runCatching {
        if (endpoint == null) return@runCatching ""
        innerTube.browse(WEB_REMIX, browseId = endpoint.browseId, params = endpoint.params)
            .body<BrowseResponse>()
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?.firstNotNullOfOrNull { it.musicDescriptionShelfRenderer }
            ?.description
            ?.runs
            ?.joinToString("") { it.text }
            ?.trim()
            ?: ""
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }
            .jsonPrimitive.content
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        val response = innerTube.accountMenu(WEB_REMIX)
        val body = response.bodyAsText()
        val parsed =
            try {
                Json.parseToJsonElement(body).jsonObject
                    .toAccountMenuResponseOrNull()
            } catch (e: Exception) {
                null
            }
        val fromModel =
            parsed?.actions?.firstOrNull()?.openPopupAction?.popup?.multiPageMenuRenderer
                ?.header?.activeAccountHeaderRenderer
                ?.toAccountInfo()
        if (fromModel?.thumbnailUrl?.isNotBlank() == true) {
            fromModel
        } else {
            // Fallback: some accounts / client shapes return the avatar under a
            // different key or outside thumbnails. Regex-scan the raw body for a
            // Google avatar host (yt3.ggpht.com / lh*.googleusercontent.com) like
            // Koda does, upgrade to a high-res size and merge with the model data.
            val name = fromModel?.name.orEmpty().ifBlank { AccountMenuResponse.parseNameFromRaw(body) }
            val email = fromModel?.email
            val handle = fromModel?.channelHandle
            val regex =
                "\"url\"\\s*:\\s*\"(https?://(?:yt3\\.ggpht\\.com|[a-z0-9-]+\\.(?:googleusercontent\\.com|ggpht\\.com))/[^\"]+?=s\\d+[^\"]*)\""
                    .toRegex()
            var avatar = regex.find(body)?.groupValues?.get(1)
            if (avatar == null) {
                avatar =
                    Regex("\"url\"\\s*:\\s*\"(https?://(?:yt3\\.ggpht\\.com|[a-z0-9-]+\\.(?:googleusercontent\\.com|ggpht\\.com))/[^\"]+)\"")
                        .find(body)?.groupValues?.get(1)
            }
            AccountInfo(
                name = name,
                email = email,
                channelHandle = handle,
                thumbnailUrl = avatar?.replace(Regex("=s\\d+"), "=s512"),
            )
        }
    }

    /**
     * Fetches the video storyboard (scrubber preview frames). Uses the ANDROID
     * player response, which is the most reliable surface for storyboards.
     * Returns an empty list when YouTube does not serve a storyboard spec.
     */
    suspend fun getStoryboards(videoId: String): Result<List<StoryboardFrameset>> = runCatching {
        val response =
            innerTube.player(
                client = YouTubeClient.ANDROID,
                videoId = videoId,
                playlistId = null,
                signatureTimestamp = null,
            ).body<PlayerResponse>()
        val spec = response.storyboards?.playerStoryboardSpecRenderer?.spec ?: return@runCatching emptyList()
        StoryboardFrameset.parseSpec(spec)
    }

    suspend fun shorts(sequenceParams: String? = null): Result<ShortsPage> = runCatching {
        innerTube.reel(
            client = YouTubeClient.ANDROID,
            sequenceParams = sequenceParams ?: "CA8%3D"
        ).toShortsPage()
    }

    /**
     * Fetch a Shorts reel sequence starting from a specific video.
     * Uses 'params' to seed the sequence from a particular video ID.
     */
    suspend fun shortsFromVideo(videoId: String): Result<ShortsPage> = runCatching {
        val seedParams = buildShortsParams(videoId)
        innerTube.reel(
            client = YouTubeClient.ANDROID,
            params = seedParams,
            sequenceParams = null
        ).toShortsPage()
    }

    /**
     * Resolve stream URLs for a Short using the ANDROID client.
     * The ANDROID client is required for Shorts-compatible stream formats.
     */
    suspend fun shortsPlayer(videoId: String): Result<PlayerResponse> = runCatching {
        innerTube.player(
            client = YouTubeClient.ANDROID,
            videoId = videoId,
            playlistId = null,
            signatureTimestamp = null
        ).body<PlayerResponse>()
    }

    /**
     * Build InnerTube params string for seeding a reel sequence from a video ID.
     */
    private fun buildShortsParams(videoId: String): String {
        val bytes = byteArrayOf(0x12) + videoId.length.toByte() + videoId.toByteArray(Charsets.UTF_8)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> {
        return com.omersusin.pitube.innertube.pages.NewPipeExtractor.newPipePlayer(videoId)
    }

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
}

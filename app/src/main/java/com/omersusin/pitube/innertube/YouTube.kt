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
import com.omersusin.pitube.innertube.models.response.ResolveUrlResponse
import com.omersusin.pitube.innertube.pages.MusicArtistContent
import com.omersusin.pitube.innertube.pages.MusicArtistResponse
import com.omersusin.pitube.innertube.pages.MusicSearchPage
import com.omersusin.pitube.innertube.pages.MusicSearchResponse
import com.omersusin.pitube.innertube.pages.toMusicArtistContent
import com.omersusin.pitube.innertube.pages.toMusicSearchPage
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
import com.omersusin.pitube.innertube.pages.RemoteChannelCrawl
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
import com.omersusin.pitube.innertube.pages.NotificationPage
import com.omersusin.pitube.innertube.pages.NewPipeExtractor
import com.omersusin.pitube.innertube.pages.toNotificationPage
import com.omersusin.pitube.data.model.Comment
import com.omersusin.pitube.data.model.VideoCollaborator
import com.omersusin.pitube.FlowApplication
import com.omersusin.pitube.utils.avatarImageIdentityKey
import com.omersusin.pitube.utils.potoken.WebPoTokenSession
import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
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
import java.io.File
import java.net.Proxy
import java.util.Locale
import kotlin.random.Random
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val innerTube = InnerTube()
    private const val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
    private const val CHANNEL_LIVE_PARAMS = "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"
    private const val CHANNEL_POSTS_PARAMS = "EgVwb3N0c_IGBAoCSgA="
    /** Max FEchannels browse pages per crawl (safety cap; ~94 channels/page). */
    private const val CHANNEL_PAGE_CAP = 50
    /** Signed-out marker YouTube embeds in authenticated response bodies. */
    private val LOGGED_OUT_REGEX = Regex("\"loggedIn\"\\s*:\\s*(false|0)|\"logged_in\"\\s*,\\s*\"value\"\\s*:\\s*\"0\"")

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
    var sessionStateListener: ((Boolean) -> Unit)?
        get() = innerTube.sessionStateListener
        set(value) {
            innerTube.sessionStateListener = value
        }
    var dataSyncIdListener: ((String) -> Unit)?
        get() = innerTube.dataSyncIdListener
        set(value) {
            innerTube.dataSyncIdListener = value
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

    /**
     * YouTube Music search (WEB_REMIX against the music host, which is the
     * InnerTube default base URL). Experimental opt-in surface — callers gate
     * this behind the music-search-categories preference so no request leaves
     * the device while it is off.
     */
    /**
     * YouTube Music search (WEB_REMIX against the music host, which is the
     * InnerTube default base URL). Experimental opt-in surface — callers gate
     * this behind the music-search-categories preference so no request leaves
     * the device while it is off.
     *
     * [filterParams] optionally applies a YT Music search filter chip
     * (e.g. the static artists token) to widen one category beyond what the
     * mixed default response carries.
     */
    suspend fun musicSearch(
        query: String,
        continuation: String? = null,
        filterParams: String? = null,
    ): Result<MusicSearchPage> = runCatching {
        val response = innerTube.search(
            client = WEB_REMIX,
            query = query.takeIf { continuation == null },
            params = filterParams.takeIf { continuation == null },
            continuation = continuation,
        ).body<MusicSearchResponse>()
        response.toMusicSearchPage()
    }.onSuccess { Log.d("MusicSearch", "query='$query' songs=${it.songs.size} artists=${it.artists.size} cont=${it.continuation != null}") }
        .onFailure { Log.w("MusicSearch", "query='$query' failed: ${it.message}") }

    /**
     * Content from a YT Music artist page: the "Fans might also like" carousel
     * (~10 related artists) plus the largest two-row video carousel. Shelves
     * are located structurally (artist-majority / video-majority), so this
     * works in any display language. Works anonymously on topic channels too.
     */
    suspend fun musicArtistContent(browseId: String): Result<MusicArtistContent> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = browseId,
        ).body<MusicArtistResponse>()
        response.toMusicArtistContent()
    }.onSuccess { Log.d("MusicSearch", "artist page $browseId: related=${it.relatedArtists.size} videos=${it.videos.size}") }
        .onFailure { Log.w("MusicSearch", "artist page $browseId failed: ${it.message}") }

    /**
     * Music-artist page id → real www channel id. Music search results point
     * at auto-generated "- Topic" pages whose id differs from the artist's
     * actual channel. Two-step bridge (both anonymous): the music browse
     * carries a canonical handle URL in its microformat, and Koda's
     * `resolveChannelId` pattern turns that handle into the real UC id via
     * `/navigation/resolve_url` on the www host.
     */
    suspend fun resolveRealChannelId(musicArtistId: String): String? =
        runCatching {
            val canonical =
                innerTube.browse(client = WEB_REMIX, browseId = musicArtistId)
                    .body<MusicArtistResponse>()
                    .microformat?.microformatDataRenderer?.urlCanonical
            val handle =
                Regex("/(@[^/?]+)").find(canonical.orEmpty())?.groupValues?.get(1)
            if (handle == null) {
                Log.d("MusicSearch", "resolveRealChannelId: no handle for $musicArtistId")
                return@runCatching null
            }
            val resolved =
                innerTube.resolveUrl("https://www.youtube.com/$handle")
                    .body<ResolveUrlResponse>()
                    .endpoint?.browseEndpoint?.browseId
                    ?.takeIf { it.startsWith("UC") && it != musicArtistId }
            Log.d(
                "MusicSearch",
                "resolveRealChannelId: $musicArtistId -> ${resolved ?: "null"} ($handle)",
            )
            resolved
        }.getOrNull()

    private suspend fun ensureVisitorData() {        if (!visitorData.isNullOrBlank()) return
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
        val root = json.parseToJsonElement(rawBody)
        buildMap {
            collectSearchVideoAvatarStacks(root, this)
        }
    }

    suspend fun videoAvatarStack(videoId: String): Result<List<String>> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = json.parseToJsonElement(rawBody)
        root.findVideoOwnerAvatarStackUrls()
    }

    suspend fun videoCollaborators(videoId: String): Result<List<VideoCollaborator>> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = json.parseToJsonElement(rawBody)
        root.findVideoOwnerCollaborators()
    }

    /** Watch-page owner payload used to fill blank YT Music song rows. */
    data class VideoOwnerInfo(
        val channelId: String,
        val avatarUrls: List<String>,
        val viewCountText: String?,
        val publishedTimeText: String?,
    )

    /**
     * Owner channel id, avatar (single is enough here — [videoAvatarStack]
     * deliberately filters size-1 stacks for collab detection), view count and
     * publish date from ONE WEB /next call. Covers music videoIds whose rows
     * ship without any of this metadata.
     */
    suspend fun videoOwnerInfo(videoId: String): Result<VideoOwnerInfo?> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = json.parseToJsonElement(rawBody)
        val owner = root.findFirstJsonObject("videoOwnerRenderer")
        val avatars =
            owner?.get("avatarStack")?.collectAvatarImageUrls().orEmpty()
                .ifEmpty { owner?.collectAvatarImageUrls().orEmpty() }
                .take(2)
        VideoOwnerInfo(
            channelId = owner?.findFirstString("browseId").orEmpty(),
            avatarUrls = avatars,
            viewCountText = root.findFirstJsonObject("videoViewCountRenderer")
                ?.get("viewCount")?.textNode(),
            publishedTimeText = root.findFirstJsonObject("dateText")?.textNode()
                ?: root.findFirstJsonObject("relativeDateText")?.textNode(),
        )
    }

    /** simpleText / first-run text out of a renderer value node. */
    private fun JsonElement?.textNode(): String? {
        when (this) {
            is JsonObject -> {
                (this["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
                (((this["runs"] as? JsonArray)?.firstOrNull()) as? JsonObject)?.let { run ->
                    (run["text"] as? JsonPrimitive)?.contentOrNull?.let { return it }
                }
            }
            is JsonPrimitive -> contentOrNull?.let { return it }
            else -> Unit
        }
        return null
    }

    /**
     * The signed-in user's like state for a video, parsed from the /next
     * frameworkUpdates (likeStatusEntity). Values: "LIKE" | "DISLIKE" |
     * "INDIFFERENT"; null when the entity is absent (e.g. anonymous requests).
     * Display-only: callers must never persist this into local like-state.
     */
    suspend fun getVideoLikeStatus(videoId: String): Result<String?> = runCatching {
        val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
        val root = json.parseToJsonElement(rawBody)
        root.findFirstJsonObject("likeStatusEntity")
            ?.get("likeStatus")
            ?.jsonPrimitive
            ?.contentOrNull
    }

    private fun JsonElement.findFirstJsonObject(key: String): JsonObject? =
        when (this) {
            is JsonObject ->
                (this[key] as? JsonObject)
                    ?: values.firstNotNullOfOrNull { it.findFirstJsonObject(key) }
            is JsonArray -> firstNotNullOfOrNull { it.findFirstJsonObject(key) }
            else -> null
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
        val lenientJson = json
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
        val lenientJson = json
        val response = lenientJson.decodeFromString<com.omersusin.pitube.innertube.models.response.ChannelSearchResponse>(rawBody)

        val videos = mutableListOf<com.omersusin.pitube.data.model.Video>()
        var nextContinuation: String? = null

        // Prefer the SELECTED tab; some FEhistory responses lead with a
        // placeholder/expandable tab whose content is null. Support both the
        // classic sectionListRenderer layout and the newer richGridRenderer.
        val tabs = response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()
        val historyTab = tabs.firstOrNull { it.tabRenderer?.selected == true } ?: tabs.firstOrNull()
        val tabContent = historyTab?.tabRenderer?.content

        when {
            tabContent?.sectionListRenderer?.contents != null -> {
                tabContent.sectionListRenderer.contents.forEach { section ->
                    section.itemSectionRenderer?.contents?.forEach { item ->
                        item.videoRenderer
                            ?.let { parseVideoRenderer(it, "", "", "") }
                            ?.let { videos.add(it) }
                    }
                    section.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                        ?.let { nextContinuation = it }
                }
            }
            tabContent?.richGridRenderer?.contents != null -> {
                tabContent.richGridRenderer.contents.forEach { item ->
                    item.richItemRenderer?.content?.videoRenderer
                        ?.let { parseVideoRenderer(it, "", "", "") }
                        ?.let { videos.add(it) }
                    item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                        ?.let { nextContinuation = it }
                }
            }
        }

        if (nextContinuation == null) {
            nextContinuation = response.continuationContents
                ?.sectionListContinuation?.continuations
                ?.firstOrNull()?.nextContinuationData?.continuation
        }

        if (videos.isEmpty()) {
            Log.w("YouTube", "FEhistory parsed 0 videos — rawBody=${rawBody.length}B selectedTab=${historyTab != null}")
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
        val lenientJson = json
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

    /**
     * Second personal lane: FEmusic_home on the WEB_REMIX client against
     * music.youtube.com — the exact combination upstream Flow uses, which
     * tolerates app clients where www-WEB FEwhat_to_watch gets bot-walled.
     * DATA SOURCE ONLY: there is no separate Music tab/UI; results feed the
     * regular home grid when the primary lane comes back empty.
     */
    suspend fun musicHomeFeed(): Result<ChannelVideoSearchResult> = runCatching {
        val httpResponse = innerTube.browse(
            client = YouTubeClient.WEB_REMIX,
            browseId = "FEmusic_home",
            setLogin = true,
        )
        val rawBody = httpResponse.bodyAsText()
        innerTube.noteResponseState(rawBody)
        val response = json.decodeFromString<ChannelVideosResponse>(rawBody)
        parseChannelVideosResponse(response, "", "", "", false)
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
        innerTube.noteResponseState(rawBody)
        val lenientJson = json
        val response = lenientJson.decodeFromString<ChannelVideosResponse>(rawBody)
        val parsed = parseChannelVideosResponse(response, "", "", "", false)
        if (parsed.videos.isEmpty() && continuation == null) {
            // Empty first page is THE symptom this lane exists to fix. Surface
            // enough context for the diagnostics report to distinguish a
            // bot-wall/login banner from a genuinely empty grid.
            val marker = when {
                "signin" in rawBody || "LOGIN_REQUIRED" in rawBody -> "login-required banner"
                "consistency" in rawBody || "botguard" in rawBody.lowercase() -> "bot-guard interstitial"
                rawBody.length < 500 -> "suspiciously tiny body (${rawBody.length} chars)"
                else -> "parsed-empty (body=${rawBody.length} chars)"
            }
            Log.w("YouTube", "personalizedFeed($browseId): EMPTY response — $marker")
        } else if (continuation == null) {
            Log.w("YouTube", "personalizedFeed($browseId): ${parsed.videos.size} videos, cont=${parsed.continuation != null}")
        }
        return parsed
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

    /**
     * The signed-in user's notification inbox
     * (`notification/get_notification_menu`, inbox tab). Requires login;
     * returns an empty page otherwise.
     */
    suspend fun getNotificationInbox(): Result<NotificationPage> = runCatching {
        val client = currentWebClient()
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = "notification/get_notification_menu",
            jsonBody = buildJsonObject {
                put("context", commentWebContext(client))
                put("notificationsMenuRequestType", JsonPrimitive("NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"))
            },
        )
        Json.parseToJsonElement(httpResponse.bodyAsText()).toNotificationPage()
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
    // Account write-back (Koda port): like/dislike and subscribe.
    // These are the toolbar actions that make listen/subscribe taps
    // take effect on the real Google account, not just the device.
    // They are fire-and-forget best-effort: the caller already wrote
    // the local/optimistic state, so a failed network write keeps the
    // local library consistent rather than rolling both back. They
    // require a non-blank session cookie (see [InnerTube.cookie]).
    // ============================================================

    /**
     * Full signed-in context for account writes, matching how Koda/Metrolist
     * build their like/subscribe bodies. The session cookie is the account
     * authority (no `user.onBehalfOfUser` — a stale datasyncId there makes
     * YouTube reject the request with HTTP 401, verified live Aug 2026).
     */
    private fun signedWriteContext(client: YouTubeClient): JsonObject =
        json
            .encodeToJsonElement(
                client.toContext(innerTube.locale, innerTube.visitorData, dataSyncId),
            )
            .jsonObject

    /** Like a video on the signed-in account. [status] is `"LIKE"`, `"DISLIKE"` or null (clear). */
    suspend fun setLikeStatus(videoId: String, status: String?): Result<Boolean> = runCatching {
        val client = currentWebClient()
        val endpoint =
            when (status) {
                "LIKE" -> "like/like"
                "DISLIKE" -> "like/dislike"
                else -> "like/removelike"
            }
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = endpoint,
            jsonBody = buildJsonObject {
                put("context", signedWriteContext(client))
                put("target", buildJsonObject { put("videoId", JsonPrimitive(videoId)) })
            },
        )
        if (!httpResponse.status.isSuccess()) {
            Log.w("YouTube", "setLikeStatus($status): HTTP ${httpResponse.status.value}")
            return@runCatching false
        }
        // YouTube can answer 200 without applying the action (BotGuard gate,
        // dead/stale session). Confirm from the body rather than trusting the
        // status code: a real engagement response reports `loggedIn: true` in
        // its responseContext and usually carries an `actions` list. A session
        // YouTube explicitly marks signed-out never applied anything.
        val body = httpResponse.bodyAsText()
        val loggedOut = Regex("\"loggedIn\"\\s*:\\s*(false|0)").containsMatchIn(body)
        if (loggedOut) {
            Log.w("YouTube", "setLikeStatus($status): YouTube reports signed-out — write not applied")
            return@runCatching false
        }
        val hasActions = Regex("\"actions\"\\s*:").containsMatchIn(body)
        // `actions` missing is acceptable (some client variants omit it) as long
        // as the session is alive; explicit signed-out is the only hard failure.
        if (!hasActions) {
            Log.d("YouTube", "setLikeStatus($status): HTTP 200, logged-in response without actions list")
        }
        true
    }

    /**
     * Subscribe to / unsubscribe from a channel on the signed-in account.
     * [channelId] must be a canonical `UC...` id.
     */
    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Result<Boolean> = runCatching {
        if (!channelId.startsWith("UC") || channelId.length <= 10 ||
            channelId.contains("/") || channelId.contains("@")
        ) {
            Log.w("YouTube", "setSubscribed($subscribe): non-canonical channel id '$channelId' — remote write skipped")
            return@runCatching false
        }
        val client = currentWebClient()
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        val httpResponse = innerTube.signedJsonPost(
            client = client,
            endpoint = endpoint,
            jsonBody = buildJsonObject {
                put("context", signedWriteContext(client))
                put("channelIds", buildJsonArray { add(JsonPrimitive(channelId)) })
            },
        )
        if (!httpResponse.status.isSuccess()) {
            Log.w("YouTube", "setSubscribed($subscribe): HTTP ${httpResponse.status.value}")
            return@runCatching false
        }
        val body = httpResponse.bodyAsText()
        val loggedOut = Regex("\"loggedIn\"\\s*:\\s*(false|0)").containsMatchIn(body)
        if (loggedOut) {
            Log.w("YouTube", "setSubscribed($subscribe): YouTube reports signed-out — write not applied")
            return@runCatching false
        }
        val hasActions = Regex("\"actions\"\\s*:").containsMatchIn(body)
        if (!hasActions) {
            Log.d("YouTube", "setSubscribed($subscribe): HTTP 200, logged-in response without actions list")
        }
        true
    }

    /**
     * Add / remove a video on the signed-in account's Watch Later playlist.
     *
     * Uses `browse/edit_playlist` against the music origin with the WEB_REMIX
     * client (Koda's playlist-edit path): the `WL` playlist id is YouTube's
     * reserved Watch Later list, and the actions array carries
     * `ACTION_ADD_VIDEO`/`ACTION_REMOVE_VIDEO_BY_VIDEO_ID`. A successful edit
     * reports `status: STATUS_SUCCEEDED`.
     */
    suspend fun setVideoInWatchLater(videoId: String, add: Boolean): Result<Boolean> =
        editPlaylist("WL", videoId, add)

    /**
     * Add / remove a video on one of the signed-in account's playlists.
     *
     * Same `browse/edit_playlist` path as Watch Later (Koda's playlist-edit
     * implementation, ported into piTube): any `PL…`/`VL…` playlist id works.
     * Like/removal on the liked lists is out of scope here — use
     * [setLikeStatus] for "LL".
     */
    suspend fun editPlaylist(playlistId: String, videoId: String, add: Boolean): Result<Boolean> =
        runCatching {
            if (cookie.isNullOrBlank() || videoId.isBlank()) return@runCatching false
            val client = WEB_REMIX
            val httpResponse = innerTube.signedMusicJsonPost(
                client = client,
                endpoint = "browse/edit_playlist",
                jsonBody = buildJsonObject {
                    put("context", signedWriteContext(client))
                    // Playlist ids sometimes carry the VL browse prefix — edit
                    // calls need it stripped (Koda's normalizePlaylistId).
                    put("playlistId", JsonPrimitive(playlistId.removePrefix("VL")))
                    put(
                        "actions",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("action", JsonPrimitive(if (add) "ACTION_ADD_VIDEO" else "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"))
                                    put(
                                        if (add) "addedVideoId" else "removedVideoId",
                                        JsonPrimitive(videoId),
                                    )
                                },
                            )
                        },
                    )
                },
            )
            if (!httpResponse.status.isSuccess()) {
                Log.w("YouTube", "editPlaylist($playlistId, add=$add): HTTP ${httpResponse.status.value}")
                return@runCatching false
            }
            val body = httpResponse.bodyAsText()
            val statusOk = Regex("\"status\"\\s*:\\s*\"STATUS_SUCCEEDED\"").containsMatchIn(body)
            if (!statusOk) {
                Log.w("YouTube", "editPlaylist($playlistId, add=$add): edit_playlist did not report STATUS_SUCCEEDED")
            }
            statusOk
        }

    /**
     * Create a playlist on the signed-in account and return its id, or null on
     * failure / when signed out. Ported from Koda's `createYouTubePlaylist`
     * (`playlist/create`), the music-origin client used for all playlist edits.
     */
    suspend fun createPlaylist(title: String, videoIds: List<String> = emptyList()): Result<String?> =
        runCatching {
            if (cookie.isNullOrBlank() || title.isBlank()) return@runCatching null
            val client = WEB_REMIX
            val httpResponse = innerTube.signedMusicJsonPost(
                client = client,
                endpoint = "playlist/create",
                jsonBody = buildJsonObject {
                    put("context", signedWriteContext(client))
                    put("title", JsonPrimitive(title))
                    if (videoIds.isNotEmpty()) {
                        put("videoIds", buildJsonArray { videoIds.forEach { add(JsonPrimitive(it)) } })
                    }
                },
            )
            if (!httpResponse.status.isSuccess()) {
                Log.w("YouTube", "createPlaylist($title): HTTP ${httpResponse.status.value}")
                return@runCatching null
            }
            val body = httpResponse.bodyAsText()
            Regex("\"playlistId\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
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
     * A playback session's minted beacon pair, obtained once per video/cpn and
     * replayed on every ping of that session. Reusing a single pair (instead of
     * re-fetching the player and minting a brand-new baseUrl per ping) is how
     * yt-dlp/youthub/YouTube.js make partial pings accumulate into one history
     * entry: the same `plid`/`rid`/session and a progressing `st`→`et` window.
     *
     * [scheduledFlushSeconds]/[defaultFlushSeconds] are YouTube's own advertised
     * watchtime heartbeat cadence from the player response
     * (`videostatsScheduledFlushWalltimeSeconds` = 10/20/30 s, then
     * `videostatsDefaultFlushIntervalSeconds` = 40 s) — the reporter follows
     * them so in-progress updates land as promptly as the official player's.
     */
    class PlaybackTracking(
        val playbackUrl: String,
        val watchtimeUrl: String?,
        val lengthSeconds: Float,
        val scheduledFlushSeconds: List<Long> = emptyList(),
        val defaultFlushSeconds: Long = 40L,
    )

    /**
     * Mint (once) the beacon pair for a playback session. Callers should cache
     * the result and pass it to every [reportVideoPlayback] for the same video
     * instead of re-minting per ping. Returns null when not signed in or the
     * tracking mint failed (bot-wall/stale cookie).
     */
    suspend fun getPlaybackTracking(videoId: String, cpn: String): PlaybackTracking? {
        if (cookie.isNullOrBlank()) return null
        return signedPlaybackTracking(videoId, cpn)
    }

    /**
     * Report a playback position into the signed-in account's YouTube watch
     * history. Returns true when the tracking ping succeeded. Requires login.
     *
     * Mirrors yt-dlp's `_mark_watched` + youthub's watchstats: fires BOTH
     * tracking URLs from `playbackTracking`:
     *  - `videostatsPlaybackUrl`  marks the video as watched,
     *  - `videostatsWatchtimeUrl` pushes actual watch time (`st`/`et`), which
     *    is what really registers in the account's history.
     *
     * [tracking] is the [PlaybackTracking] minted once per session (prefer
     * passing it; when null one is fetched per call for simple call-sites).
     * [previousPositionMs] is the position of the previous ping and becomes the
     * `st` of this one, so partial progress chains into one in-progress entry.
     * [final] marks the last ping (video ended or left) with `state=ended`, the
     * signal that "fully watched"/commits the entry.
     *
     * [positionMs] is the watched-to position used for `cmt`/`et`. When 0 the
     * video is marked as watched right before the end (yt-dlp behaviour).
     * [relativeTimeSeconds] is the wall-clock seconds elapsed since the first
     * beacon of this session — the `rt` param youthub sends on watchtime pings
     * so YouTube can compute real watch-time independent of seek position.
     */
    suspend fun reportVideoPlayback(
        videoId: String,
        positionMs: Long = 0L,
        cpn: String = generateCpn(),
        tracking: PlaybackTracking? = null,
        previousPositionMs: Long = 0L,
        final: Boolean = false,
        relativeTimeSeconds: Long = 0L,
        paused: Boolean = false,
        fmt: Int? = null,
        rtn: Long = 0L,
    ): Result<Boolean> = runCatching {
        val status = reportVideoPlaybackStatus(
            videoId, positionMs, cpn, tracking, previousPositionMs,
            final, relativeTimeSeconds, paused, fmt, rtn,
        )
        status in 200..299
    }

    /**
     * Like [reportVideoPlayback] but returns the beacon HTTP status so callers
     * can distinguish success (2xx) from throttling (429) and a dead session
     * (401/403) instead of a plain boolean.
     */
    suspend fun reportVideoPlaybackStatus(
        videoId: String,
        positionMs: Long = 0L,
        cpn: String = generateCpn(),
        tracking: PlaybackTracking? = null,
        previousPositionMs: Long = 0L,
        final: Boolean = false,
        relativeTimeSeconds: Long = 0L,
        paused: Boolean = false,
        fmt: Int? = null,
        rtn: Long = 0L,
    ): Int {
        if (cookie.isNullOrBlank()) return 0
        val tracking = tracking ?: signedPlaybackTracking(videoId, cpn)
        if (tracking == null) {
            Log.w(
                "YouTube",
                "reportVideoPlaybackStatus: playback tracking unavailable for $videoId — history ping dropped (bot wall or dead session)",
            )
            return 0
        }

        val playbackUrl = tracking.playbackUrl
        val watchtimeUrl = tracking.watchtimeUrl
        if (watchtimeUrl == null) {
            Log.w(
                "YouTube",
                "reportVideoPlaybackStatus: no watchtime URL in tracking for $videoId — only the playback beacon will fire",
            )
        }

        val lengthSeconds = tracking.lengthSeconds
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
        val stSeconds = (previousPositionMs / 1000L).coerceAtLeast(0L)
        val referer = "https://www.youtube.com/watch?v=$videoId"

        val playbackStatus = pingStats(playbackUrl, cpn, cmt, watchtime = false, stSeconds = stSeconds, final = final, referer = referer, relativeTimeSeconds = relativeTimeSeconds, paused = paused, fmt = fmt, rtn = rtn)
        var status = playbackStatus
        if (watchtimeUrl != null) {
            val watchtimeStatus = pingStats(watchtimeUrl, cpn, cmt, watchtime = true, stSeconds = stSeconds, final = final, referer = referer, relativeTimeSeconds = relativeTimeSeconds, paused = paused, fmt = fmt, rtn = rtn)
            // Prefer a success from either beacon; keep the more informative
            // failure status (429/401) over a plain transport failure (0).
            status = when {
                status in 200..299 -> status
                watchtimeStatus in 200..299 -> watchtimeStatus
                watchtimeStatus == 429 || watchtimeStatus == 401 || watchtimeStatus == 403 -> watchtimeStatus
                else -> status
            }
        }
        return status
    }

    /** Fires one videostats beacon and interprets its HTTP status. */
    private suspend fun pingStats(
        url: String,
        cpn: String,
        cmtSeconds: Long,
        watchtime: Boolean,
        stSeconds: Long,
        final: Boolean,
        referer: String,
        relativeTimeSeconds: Long = 0L,
        paused: Boolean = false,
        fmt: Int? = null,
        rtn: Long = 0L,
    ): Int {
        val status = innerTube.videoStatsPing(
            url = withVideoStatsParams(
                url, cpn, cmtSeconds,
                watchtime = watchtime,
                stSeconds = stSeconds,
                final = final,
                relativeTimeSeconds = relativeTimeSeconds,
                paused = paused,
                fmt = fmt,
                rtn = rtn,
            ),
            referer = referer,
        )
        return when {
            status in 200..299 -> {
                Log.d("YouTube", "Watch-history beacon OK ($status, watchtime=$watchtime, cmt=${cmtSeconds}s)")
                status
            }
            status == 401 || status == 403 -> {
                Log.w("YouTube", "Watch-history beacon rejected ($status) — session cookie dead, request re-auth")
                status
            }
            status == 429 -> {
                Log.w("YouTube", "Watch-history beacon throttled (429) — backing off")
                status
            }
            else -> {
                Log.w("YouTube", "Watch-history beacon failed with HTTP $status")
                status
            }
        }
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
    ): PlaybackTracking? {
        val client = currentWebClient()

        suspend fun fetch(sts: Int?, poToken: String?, visitorData: String?): PlaybackTracking? {
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
                                    // Without el the mint context counts as a
                                    // Shorts view and the beacon pair attributes
                                    // to the wrong surface (yt-dlp does the same).
                                    put("el", JsonPrimitive("detailpage"))
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
            // YouTube's own advertised heartbeat schedule for watchtime flushes.
            val scheduledFlushes = tracking["videostatsScheduledFlushWalltimeSeconds"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.toLongOrNull() }
                .orEmpty()
            val defaultFlush = tracking["videostatsDefaultFlushIntervalSeconds"]
                ?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: 40L
            return PlaybackTracking(playback, watchtime, length, scheduledFlushes, defaultFlush)
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

    internal fun parseLengthFromTrackingUrl(url: String): Float {
        val lenMatch = Regex("(?:^|&)len=([^&]+)").find(url)?.groupValues?.get(1)
        return lenMatch?.toFloatOrNull() ?: 0f
    }

    /**
     * Rewrite a videostats tracking URL's query the way yt-dlp's
     * `_mark_watched` and youthub's watchstats do: keep every existing
     * parameter byte-for-byte and append `ver=2`, `cpn`, `cmt`, `c=WEB` and
     * `el=detailpage`. The watchtime URL also gets `st`/`et` (a progressing
     * window from the previous ping to the current one) plus `rt` (wall-clock
     * seconds since the session's first beacon). Every non-final beacon carries
     * `state=playing` so intermediate pings register watch time instead of
     * being discounted; only the last ping of a session carries `state=ended`.
     *
     * The URL is rewritten with plain string concatenation on purpose: an
     * Android `Uri` round-trip would form-decode `+`, dedupe repeated params
     * and re-encode spaces as `%20`, all of which corrupt the signed (`s`,
     * `ip`, `sprops`) query params and make YouTube reject the beacon.
     */
    private fun withVideoStatsParams(
        baseUrl: String,
        cpn: String,
        cmtSeconds: Long,
        watchtime: Boolean,
        stSeconds: Long = 0L,
        final: Boolean = false,
        relativeTimeSeconds: Long = 0L,
        paused: Boolean = false,
        fmt: Int? = null,
        rtn: Long = 0L,
    ): String {
        val suffix = buildString {
            append(if ('?' in baseUrl) "&" else "?")
            append("ver=2")
            append("&cpn=$cpn")
            append("&c=WEB")
            append("&cmt=$cmtSeconds")
            append("&el=detailpage")
            if (watchtime) {
                append("&st=$stSeconds")
                append("&et=$cmtSeconds")
            }
            // rt = wall-clock since the session's first beacon (youthub).
            if (relativeTimeSeconds > 0L) {
                append("&rt=$relativeTimeSeconds")
            }
            // Start-ping signal: the played format and the ping sequence number
            // (YouTube.js sends fmt=251 + rtn=0 on its playback-URL ping).
            if (fmt != null) append("&fmt=$fmt")
            if (rtn >= 0L && !watchtime) append("&rtn=$rtn")
            when {
                final -> {
                    append("&state=ended")
                    // YouTube.js-style commit marker on the last watchtime ping.
                    if (watchtime) append("&final=1")
                }
                paused -> append("&state=paused")
                else -> append("&state=playing")
            }
        }
        return baseUrl + suffix
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
    suspend fun webSubscribedChannels(): Result<RemoteChannelCrawl> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching RemoteChannelCrawl(emptyList(), complete = false)
        val client = currentWebClient()
        val channels = mutableListOf<RemoteChannel>()
        val seenContinuations = mutableSetOf<String>()
        var continuation: String? = null
        var pages = 0
        var complete = false
        var stopReason = "no-continuation-token"
        do {
            // One in-place retry: a transient 429/5xx mid-crawl must not abort
            // an otherwise healthy run (withRetry only re-tries transport
            // failures, not rejected statuses).
            var response: HttpResponse
            var attempts = 0
            while (true) {
                response = innerTube.signedWebBrowse(
                    client = client,
                    browseId = if (continuation == null) "FEchannels" else null,
                    continuation = continuation,
                )
                if (response.status.isSuccess() || ++attempts > 1) break
                delay(1_000)
            }
            if (!response.status.isSuccess()) {
                stopReason = "http-${response.status.value}"
                Log.w(
                    "YouTube",
                    "webSubscribedChannels: HTTP ${response.status.value} on page $pages " +
                        "(channels so far ${channels.size})",
                )
                break
            }
            val bodyText = response.bodyAsText()
            val loggedOut = LOGGED_OUT_REGEX.containsMatchIn(bodyText)
            if (loggedOut) {
                // A dead session answers the browse anonymously: that is a
                // re-login problem, not an account with zero channels.
                Log.w("YouTube", "webSubscribedChannels: YouTube answered signed-out — session dead")
                return@runCatching RemoteChannelCrawl(emptyList(), complete = false, sessionExpired = true)
            }
            val root = Json.parseToJsonElement(bodyText)
            val found = root.toRemoteChannels()
            channels += found
            if (found.isEmpty()) {
                // Diagnostic hook: a 0-channel page is either a dead session
                // or a parser-shape regression — never "the account has
                // nothing subscribed". Dump the raw page so the real shape
                // can be inspected on-device alongside the HTTP and session
                // state.
                val dumpPath = runCatching {
                    val rootDir = FlowApplication.appContext.getExternalFilesDir(null)
                        ?: return@runCatching null
                    val dir = File(rootDir, "session_dump").apply { mkdirs() }
                    val dumpFile = File(dir, "fechannels_page${pages}_${System.currentTimeMillis()}.json")
                    dumpFile.writeText(bodyText)
                    dumpFile.absolutePath
                }.getOrNull()
                Log.w(
                    "YouTube",
                    "webSubscribedChannels: 0 channels on page $pages http=${response.status.value} " +
                        "loggedOut=$loggedOut dump=${dumpPath ?: "dump-failed"}",
                )
            }
            val next = root.browseContinuation()
            pages++
            when {
                next == null -> {
                    complete = true
                    stopReason = "no-continuation-token"
                }
                !seenContinuations.add(next) -> {
                    // The server echoed the same token twice: further requests
                    // would only re-serve this page, so the crawl is complete.
                    complete = true
                    stopReason = "repeated-continuation"
                }
                pages >= CHANNEL_PAGE_CAP -> {
                    complete = false
                    stopReason = "page-cap-$CHANNEL_PAGE_CAP"
                }
                else -> {
                    complete = false
                    stopReason = "continuing"
                }
            }
            continuation = next
            Log.i(
                "YouTube",
                "webSubscribedChannels: page=${pages - 1} found=${found.size} total=${channels.size} " +
                    "complete=$complete stop=$stopReason",
            )
        } while (!complete && pages < CHANNEL_PAGE_CAP)
        Log.i(
            "YouTube",
            "webSubscribedChannels: done pages=$pages channels=${channels.size} complete=$complete stop=$stopReason",
        )
        RemoteChannelCrawl(channels.distinctBy { it.id }, complete = complete)
    }

    /**
     * The user's subscriptions feed in one aggregate request: the same
     * FEsubscriptions page the web Subscriptions tab shows. InnerTube returns a
     * rich grid (lockupViewModel / videoRenderer items, newest first, a proper
     * continuation token for the next page), which is far better than crawling
     * every channel's uploads individually — and it is genuinely *your* feed,
     * not a blend rebuilt from trending.
     */
    suspend fun webSubscriptionsFeed(continuation: String? = null): Result<ChannelVideoSearchResult> = runCatching {
        if (cookie.isNullOrBlank()) return@runCatching ChannelVideoSearchResult(emptyList(), null, null)
        val client = currentWebClient()
        val httpResponse = innerTube.signedWebBrowse(
            client = client,
            browseId = if (continuation == null) "FEsubscriptions" else null,
            continuation = continuation,
        )
        if (!httpResponse.status.isSuccess()) {
            Log.w("YouTube", "webSubscriptionsFeed: HTTP ${httpResponse.status.value}")
            return@runCatching ChannelVideoSearchResult(emptyList(), null, null)
        }
        val lenientJson = json
        val response = lenientJson.decodeFromString<ChannelVideosResponse>(httpResponse.bodyAsText())
        parseChannelVideosResponse(response, "", "", "", false)
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
        if (playlists.isEmpty()) {
            // Empty playlist aggregation is the "playlists stay blank" symptom —
            // surface the response shape for the diagnostics report.
            val body = response.bodyAsText()
            val marker = when {
                "signin" in body || "LOGIN_REQUIRED" in body -> "login-required banner"
                body.length < 500 -> "tiny body (${body.length} chars)"
                else -> "parsed-empty (body=${body.length} chars)"
            }
            Log.w("YouTube", "webUserPlaylists: EMPTY — $marker")
        }
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
        val lenientJson = json
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
        // The subscriptions feed (FEsubscriptions) nests its grid one level
        // deeper: sectionListRenderer > itemSectionRenderer > richGridRenderer.
        selectedTab?.content?.sectionListRenderer?.contents
            ?.mapNotNull { it.itemSectionRenderer }
            ?.flatMap { it.contents.orEmpty() }
            ?.mapNotNull { it.richGridRenderer }
            ?.flatMap { it.contents.orEmpty() }
            ?.let { richItems += it }

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
        val overlays = lockup.contentImage?.thumbnailViewModel?.overlays.orEmpty()
        // FEwhat_to_watch / FEsubscriptions grids carry the duration badge in
        // either the modern bottom overlay or the legacy top overlay — read
        // both (Koda/NewPipe do the same) and treat a `:`-shaped text as the
        // duration, never a "LIVE"/"MIX" text badge.
        val badgeViewModels = overlays.flatMap { overlay ->
            overlay.thumbnailBottomOverlayViewModel?.badges.orEmpty() +
                overlay.thumbnailOverlayBadgeViewModel?.thumbnailBadges.orEmpty()
        }.mapNotNull { it.thumbnailBadgeViewModel }
        val durationText = badgeViewModels
            .mapNotNull { it.text }
            .firstOrNull { it.contains(":") }
        val liveBadge = badgeViewModels.firstOrNull { badge ->
            badge.badgeStyle?.contains("LIVE", ignoreCase = true) == true ||
                badge.text?.contains("LIVE", ignoreCase = true) == true ||
                badge.animatedText?.text?.contains("LIVE", ignoreCase = true) == true
        }
        val isShortBadge = badgeViewModels.any { badge ->
            badge.text?.contains("Shorts", ignoreCase = true) == true ||
                badge.badgeStyle?.contains("SHORTS", ignoreCase = true) == true ||
                badge.text?.equals("SHORTS", ignoreCase = true) == true
        }
        val metadataRows = metadata.metadata?.contentMetadataViewModel?.metadataRows.orEmpty()
        val channelPart = metadataRows.firstOrNull()?.metadataParts?.firstOrNull()
        val resolvedChannelId = channelPart?.runs
            ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint?.browseId }
            ?: channelId
        val rawChannelName = if (resolvedChannelId != channelId) {
            channelPart?.text?.content?.takeIf { it.isNotBlank() } ?: channelName
        } else {
            channelName
        }
        val resolvedChannelName = rawChannelName.ifBlank { channelName.ifBlank { resolvedChannelId.takeIf { it.startsWith("UC") } ?: channelId } }
        val resolvedThumbnail = metadata.image
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
            isLive = isLive || liveBadge != null
                || viewsText?.contains("watching", ignoreCase = true) == true
                || viewsText?.contains("izliyor", ignoreCase = true) == true,
            isShort = isShortBadge,
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

    /**
     * Rotate the anonymous visitor identity so personalized/dossier endpoints
     * (home discovery, trending) stop returning the same pinned items forever.
     * Fetches a fresh `visitorData` and swaps the app-wide value; returns the
     * new value, or null when the fetch failed (caller keeps the old one).
     */
    suspend fun rotateVisitorData(): String? {
        val fresh = visitorData().getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        visitorData = fresh
        return fresh
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        val response = innerTube.accountMenu(WEB_REMIX)
        val body = response.bodyAsText()
        innerTube.noteResponseState(body)
        val parsed =
            try {
                AccountMenuResponse.toAccountMenuResponseOrNull(
                    Json.parseToJsonElement(body).jsonObject
                )
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
            // Account-menu bodies sometimes escape the avatar URL as
            // `https:\/\/yt3.ggpht.com\/...`. Normalize the escaped backslashes
            // before regex-scanning so the avatar is found either way (Koda-style).
            val scanBody =
                if (body.contains("\\/")) body.replace("\\/", "/") else body
            val regex =
                "\"url\"\\s*:\\s*\"(https?://(?:yt3\\.ggpht\\.com|[a-z0-9-]+\\.(?:googleusercontent\\.com|ggpht\\.com))/[^\"]+?=s\\d+[^\"]*)\""
                    .toRegex()
            var avatar = regex.find(scanBody)?.groupValues?.get(1)
            if (avatar == null) {
                avatar =
                    Regex("\"url\"\\s*:\\s*\"(https?://(?:yt3\\.ggpht\\.com|[a-z0-9-]+\\.(?:googleusercontent\\.com|ggpht\\.com))/[^\"]+)\"")
                        .find(scanBody)?.groupValues?.get(1)
            }
            // YouTube's own account identifier, used to recognise a profile that
            // is re-added after removal (Koda-style datasyncId dedupe).
            val datasyncId = Regex("\"datasyncId\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)?.groupValues?.get(1)
            AccountInfo(
                name = name,
                email = email,
                channelHandle = handle,
                thumbnailUrl = avatar?.replace(Regex("=s\\d+"), "=s512"),
                datasyncId = datasyncId?.takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Fetches the video storyboard (scrubber preview frames). Uses the ANDROID
     * player response, which is the most reliable surface for storyboards.
     * Returns an empty list when YouTube does not serve a storyboard spec.
     */
    /**
     * Storyboard spec for seekbar scrubbing previews. ANDROID is permanently
     * bot-walled on many networks (see extraction ladder) and silently returns
     * no storyboards — try IOS first, then WEB as fallback, logging the outcome.
     */
    suspend fun getStoryboards(videoId: String): Result<List<StoryboardFrameset>> = runCatching {
        val clients = listOf(YouTubeClient.IOS, YouTubeClient.WEB, YouTubeClient.ANDROID)
        var lastSpec: String? = null
        for (client in clients) {
            val response =
                innerTube.player(
                    client = client,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = null,
                ).body<PlayerResponse>()
            val spec = response.storyboards?.playerStoryboardSpecRenderer?.spec
            if (!spec.isNullOrBlank()) {
                val framesets = StoryboardFrameset.parseSpec(spec)
                Log.d(
                    "YouTube",
                    "storyboard fetched via ${client.clientName}: ${framesets.size} framesets — " +
                        framesets.joinToString(" | ") { f ->
                            "L${framesets.indexOf(f)} cell=${f.frameWidth}x${f.frameHeight} " +
                                "grid=${f.framesPerPageX}x${f.framesPerPageY} pages=${f.urls.size} " +
                                "total=${f.totalCount} dpf=${f.durationPerFrame}ms"
                        },
                )
                return@runCatching framesets
            }
            lastSpec = null
        }
        Log.w("YouTube", "storyboard: no playerStoryboardSpecRenderer from any client for $videoId")
        emptyList()
    }

    suspend fun shorts(
        sequenceParams: String? = null,
        continuation: String? = null,
    ): Result<ShortsPage> = runCatching {
        innerTube.reel(
            client = YouTubeClient.ANDROID,
            sequenceParams = sequenceParams ?: "CA8%3D",
            continuation = continuation
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
     * Falls back through signature-timestamped ANDROID and WEB player
     * requests so bot-walled unsigned responses don't strand Shorts playback.
     */
    suspend fun shortsPlayer(videoId: String): Result<PlayerResponse> = runCatching {
        val plain = innerTube.player(
            client = YouTubeClient.ANDROID,
            videoId = videoId,
            playlistId = null,
            signatureTimestamp = null
        ).body<PlayerResponse>()
        if (plain.streamingData?.hasStreams() == true) return@runCatching plain

        // The unsigned ANDROID player response is often bot-walled and comes
        // back without streamingData. Retry with a signature timestamp, and
        // then with the WEB client, so Shorts playback mirrors the main
        // player's resilience instead of failing with "no stream URL".
        val sts = runCatching { NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull() }.getOrNull()
        if (sts != null) {
            val signedAnd = innerTube.player(
                client = YouTubeClient.ANDROID,
                videoId = videoId,
                playlistId = null,
                signatureTimestamp = sts
            ).body<PlayerResponse>()
            if (signedAnd.streamingData?.hasStreams() == true) return@runCatching signedAnd
        }
        innerTube.player(
            client = YouTubeClient.WEB,
            videoId = videoId,
            playlistId = null,
            signatureTimestamp = sts
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

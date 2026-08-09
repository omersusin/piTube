package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InnerTubeClient {
    private const val TAG = "InnerTubeClient"
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val WEB_VERSION = "2.20260114.08.00"
    private const val ANDROID_VR_VERSION = "1.65.10"
    private const val ANDROID_VR_CLIENT_ID = 28
    private const val ANDROID_VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip"
    private const val IOS_VERSION = "21.02.3"
    private const val IOS_CLIENT_ID = 5
    private const val IOS_UA =
        "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    data class PlayerResponse(
        val streamingData: JSONObject?,
        val visitorDataSuspect: Boolean,
        val captionTracks: List<CaptionTrack> = emptyList(),
        val videoDetails: JSONObject? = null
    )

    private fun authHeaders(context: Context): Pair<String, String?> {
        val cookies = AuthManager.getRawCookies(context)
        if (cookies.isBlank()) return "" to null
        return cookies to KodaAuth.authHeader(cookies, "https://www.youtube.com")
    }

    private suspend fun requestJson(
        context: Context,
        url: String,
        body: JSONObject,
        userAgent: String,
        clientNameId: String?,
        clientVersion: String?,
        visitorData: String?,
        withAuth: Boolean,
        origin: String,
        useStreamClient: Boolean = false
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json")
                .addHeader("Origin", origin)
                .addHeader("Referer", "$origin/")
                .addHeader("X-Origin", origin)
            if (clientNameId != null) reqBuilder.addHeader("X-YouTube-Client-Name", clientNameId)
            if (clientVersion != null) reqBuilder.addHeader("X-YouTube-Client-Version", clientVersion)
            reqBuilder.addHeader("X-Goog-Api-Format-Version", "2")
            if (visitorData != null && visitorData.isNotBlank()) {
                reqBuilder.addHeader("X-Goog-Visitor-Id", visitorData)
            }
            if (withAuth) {
                val (cookies, auth) = authHeaders(context)
                if (cookies.isNotBlank()) reqBuilder.addHeader("Cookie", cookies)
                if (auth != null) {
                    reqBuilder.addHeader("Authorization", auth)
                    reqBuilder.addHeader("X-Goog-AuthUser", "0")
                }
            }
            val c = if (useStreamClient) streamClient else client
            val resp = c.newCall(reqBuilder.build()).execute()
            val code = resp.code
            val bodyStr = resp.body?.string().orEmpty()
            resp.close()
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code for $url: ${bodyStr.take(160)}")
                null
            } else if (bodyStr.isEmpty()) {
                null
            } else {
                JSONObject(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: $url: ${e.message}")
            null
        }
    }

    suspend fun player(context: Context, videoId: String): PlayerResponse {
        val visitorData = VisitorDataManager.get()
        val first = playerCall(context, videoId, visitorData)
        first.streamingData?.let { return first }
        if (!first.visitorDataSuspect) return first
        Log.w(TAG, "visitorData flagged, reminting for $videoId")
        val fresh = VisitorDataManager.remint(visitorData.takeIf { it.isNotBlank() }) ?: return first
        if (fresh == visitorData) return first
        return playerCall(context, videoId, fresh)
    }

    private suspend fun playerCall(
        context: Context,
        videoId: String,
        visitorData: String
    ): PlayerResponse {
        val vr = fetchPlayerResponse(
            context = context,
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = ANDROID_VR_VERSION,
            clientNameId = ANDROID_VR_CLIENT_ID.toString(),
            userAgent = ANDROID_VR_UA,
            visitorData = visitorData,
            extraClientFields = JSONObject().apply {
                put("androidSdkVersion", 32)
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("osName", "Android")
                put("osVersion", "12L")
            }
        )
        vr.streamingData?.let { return vr }

        val ios = fetchPlayerResponse(
            context = context,
            videoId = videoId,
            clientName = "IOS",
            clientVersion = IOS_VERSION,
            clientNameId = IOS_CLIENT_ID.toString(),
            userAgent = IOS_UA,
            visitorData = visitorData,
            extraClientFields = JSONObject().apply {
                put("deviceMake", "Apple")
                put("deviceModel", "iPhone16,2")
                put("osName", "iPhone")
                put("osVersion", "18.1.0.22B83")
            }
        )
        return PlayerResponse(
            streamingData = ios.streamingData,
            visitorDataSuspect = vr.visitorDataSuspect || ios.visitorDataSuspect,
            captionTracks = vr.captionTracks.ifEmpty { ios.captionTracks },
            videoDetails = vr.videoDetails ?: ios.videoDetails
        )
    }

    private suspend fun fetchPlayerResponse(
        context: Context,
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientNameId: String,
        userAgent: String,
        visitorData: String,
        extraClientFields: JSONObject
    ): PlayerResponse = withContext(Dispatchers.IO) {
        try {
            val clientObj = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                put("utcOffsetMinutes", 0)
                if (visitorData.isNotBlank()) put("visitorData", visitorData)
                val keys = extraClientFields.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, extraClientFields.get(k))
                }
            }
            val contextObj = JSONObject().put("client", clientObj)
            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", contextObj)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val url = "https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY&prettyPrint=false"
            val reqBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("User-Agent", userAgent)
                .addHeader("X-Goog-Api-Format-Version", "2")
                .addHeader("X-YouTube-Client-Name", clientNameId)
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("Accept", "application/json")
            if (visitorData.isNotBlank()) reqBuilder.addHeader("X-Goog-Visitor-Id", visitorData)

            val resp = streamClient.newCall(reqBuilder.build()).execute()
            val code = resp.code
            val json = resp.body?.string().orEmpty()
            resp.close()
            if (code !in 200..299) {
                Log.w(TAG, "player[$clientName] HTTP $code body=${json.take(160)}")
                return@withContext PlayerResponse(null, false)
            }
            val root = JSONObject(json)
            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            if (status.isNotEmpty() && status != "OK") {
                Log.w(TAG, "player[$clientName] playability=$status reason=${playability?.optString("reason")}")
                return@withContext PlayerResponse(null, status == "LOGIN_REQUIRED")
            }
            val captions = parseCaptionTracks(root)
            val streaming = root.optJSONObject("streamingData")
            if (streaming == null) {
                return@withContext PlayerResponse(null, true, captions)
            }
            PlayerResponse(
                streamingData = streaming,
                visitorDataSuspect = false,
                captionTracks = captions,
                videoDetails = root.optJSONObject("videoDetails")
            )
        } catch (e: Exception) {
            Log.e(TAG, "player[$clientName] exception: ${e.message}")
            PlayerResponse(null, false)
        }
    }

    private fun parseCaptionTracks(root: JSONObject): List<CaptionTrack> {
        return try {
            val arr = root.optJSONObject("captions")
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks") ?: return emptyList()
            val tracks = mutableListOf<CaptionTrack>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val baseUrl = t.optString("baseUrl")
                val lang = t.optString("languageCode")
                val name = t.optJSONObject("name")?.optString("simpleText") ?: lang
                if (baseUrl.isNotBlank()) {
                    tracks.add(CaptionTrack(lang, name, baseUrl, t.optString("kind") == "asr"))
                }
            }
            tracks
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun browse(
        context: Context,
        browseId: String,
        params: String? = null,
        continuation: String? = null
    ): JSONObject? {
        val body = JSONObject().apply {
            put("context", webContext())
            if (continuation != null) put("continuation", continuation)
            else {
                put("browseId", browseId)
                if (params != null) put("params", params)
            }
        }
        return requestJson(
            context = context,
            url = "https://www.youtube.com/youtubei/v1/browse?key=$API_KEY&prettyPrint=false",
            body = body,
            userAgent = BROWSER_UA,
            clientNameId = "1",
            clientVersion = WEB_VERSION,
            visitorData = VisitorDataManager.get().takeIf { it.isNotBlank() },
            withAuth = true,
            origin = "https://www.youtube.com"
        )
    }

    suspend fun search(context: Context, query: String, filter: String? = null): JSONObject? {
        val body = JSONObject().apply {
            put("context", webContext())
            put("query", query)
        }
        return requestJson(
            context = context,
            url = "https://www.youtube.com/youtubei/v1/search?key=$API_KEY&prettyPrint=false",
            body = body,
            userAgent = BROWSER_UA,
            clientNameId = "1",
            clientVersion = WEB_VERSION,
            visitorData = VisitorDataManager.get().takeIf { it.isNotBlank() },
            withAuth = true,
            origin = "https://www.youtube.com"
        )
    }

    suspend fun searchVideos(context: Context, query: String): List<VideoItem> {
        val root = search(context, query) ?: return emptyList()
        return parseFeedItems(root)
    }

    suspend fun related(context: Context, videoId: String): List<VideoItem> {
        val root = watchNext(context, videoId) ?: return emptyList()
        val secondary = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("secondaryResults")
            ?: return emptyList()
        val lockups = mutableListOf<JSONObject>()
        findObjectsByKey(secondary, "lockupViewModel", lockups)
        return lockups.mapNotNull { parseLockupViewModel(it) }
            .distinctBy { it.videoId }
    }

    suspend fun comments(
        context: Context,
        videoId: String,
        continuation: String? = null
    ): CommentsResponse {
        val body = JSONObject().apply {
            put("context", webContext())
            if (continuation != null) put("continuation", continuation)
            else put("videoId", videoId)
        }
        val root = requestJson(
            context = context,
            url = "https://www.youtube.com/youtubei/v1/next?key=$API_KEY&prettyPrint=false",
            body = body,
            userAgent = BROWSER_UA,
            clientNameId = "1",
            clientVersion = WEB_VERSION,
            visitorData = VisitorDataManager.get().takeIf { it.isNotBlank() },
            withAuth = true,
            origin = "https://www.youtube.com"
        ) ?: return CommentsResponse(emptyList())
        return parseComments(root)
    }

    suspend fun watchNext(context: Context, videoId: String): JSONObject? {
        val body = JSONObject().apply {
            put("context", webContext())
            put("videoId", videoId)
        }
        return requestJson(
            context = context,
            url = "https://www.youtube.com/youtubei/v1/next?key=$API_KEY&prettyPrint=false",
            body = body,
            userAgent = BROWSER_UA,
            clientNameId = "1",
            clientVersion = WEB_VERSION,
            visitorData = VisitorDataManager.get().takeIf { it.isNotBlank() },
            withAuth = true,
            origin = "https://www.youtube.com"
        )
    }

    // ============================================================
    // Engagement: like/dislike, subscribe, comments (WEB, signed in)
    // ============================================================

    private suspend fun postWatchApi(
        context: Context,
        endpoint: String,
        body: JSONObject
    ): JSONObject? = requestJson(
        context = context,
        url = "https://www.youtube.com/youtubei/v1/$endpoint?key=$API_KEY&prettyPrint=false",
        body = body,
        userAgent = BROWSER_UA,
        clientNameId = "1",
        clientVersion = WEB_VERSION,
        visitorData = VisitorDataManager.get().takeIf { it.isNotBlank() },
        withAuth = true,
        origin = "https://www.youtube.com"
    )

    suspend fun rateVideo(context: Context, videoId: String, status: LikeStatus): Boolean {
        if (!AuthManager.isLoggedIn(context)) return false
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val body = JSONObject().apply {
            put("context", webContext())
            put("target", JSONObject().put("videoId", videoId))
        }
        return postWatchApi(context, endpoint, body) != null
    }

    suspend fun setSubscribed(context: Context, channelId: String, subscribe: Boolean): Boolean {
        if (!AuthManager.isLoggedIn(context)) return false
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        val body = JSONObject().apply {
            put("context", webContext())
            put("channelIds", JSONArray().put(channelId))
        }
        return postWatchApi(context, endpoint, body) != null
    }

    suspend fun createComment(context: Context, createCommentParams: String, text: String): Boolean {
        if (!AuthManager.isLoggedIn(context)) return false
        val body = JSONObject().apply {
            put("context", webContext())
            put("commentText", text)
            put("createCommentParams", createCommentParams)
        }
        return postWatchApi(context, "comment/create_comment", body) != null
    }

    /**
     * Like count, the signed-in user's like state and subscription state for a
     * video, parsed from the watch-next response (likeCountEntity /
     * likeStatusEntity frameworkUpdates and the subscribe button).
     */
    suspend fun engagement(context: Context, videoId: String): VideoEngagementInfo? {
        val root = watchNext(context, videoId) ?: return null
        return parseEngagement(root)
    }

    private fun parseEngagement(root: JSONObject): VideoEngagementInfo? {
        return try {
            val likeCountEntities = mutableListOf<JSONObject>()
            findObjectsByKey(root, "likeCountEntity", likeCountEntities)
            var likeCount = likeCountEntities.firstOrNull()
                ?.optJSONObject("likeCountIfIndifferent")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { parseCount(it) }
            if (likeCount == null) {
                val likeButtons = mutableListOf<JSONObject>()
                findObjectsByKey(root, "segmentedLikeDislikeButtonViewModel", likeButtons)
                val title = likeButtons.firstOrNull()
                    ?.optJSONObject("likeButtonViewModel")?.optJSONObject("likeButtonViewModel")
                    ?.optJSONObject("toggleButtonViewModel")?.optJSONObject("toggleButtonViewModel")
                    ?.optJSONObject("defaultButtonViewModel")?.optJSONObject("buttonViewModel")
                    ?.optString("title")
                likeCount = title?.takeIf { it.isNotBlank() && it.any { c -> c.isDigit() } }?.let { parseCount(it) }
            }

            val likeStatusEntities = mutableListOf<JSONObject>()
            findObjectsByKey(root, "likeStatusEntity", likeStatusEntities)
            val likeStatus = when (likeStatusEntities.firstOrNull()?.optString("likeStatus")) {
                "LIKE" -> LikeStatus.LIKE
                "DISLIKE" -> LikeStatus.DISLIKE
                else -> LikeStatus.INDIFFERENT
            }

            val subButtons = mutableListOf<JSONObject>()
            findObjectsByKey(root, "subscribeButtonRenderer", subButtons)
            val subButton = subButtons.firstOrNull()
            val isSubscribed = subButton?.optBoolean("subscribed", false) ?: false

            val owners = mutableListOf<JSONObject>()
            findObjectsByKey(root, "videoOwnerRenderer", owners)
            val owner = owners.firstOrNull()
            val channelId = subButton?.optString("channelId")?.takeIf { it.isNotBlank() }
                ?: owner?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
            val subscriberCountText = owner?.optJSONObject("subscriberCountText")
                ?.let { textObj ->
                    textObj.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: textObj.optString("simpleText")
                }

            VideoEngagementInfo(
                likeCount = likeCount,
                likeStatus = likeStatus,
                channelId = channelId,
                isSubscribed = isSubscribed,
                subscriberCountText = subscriberCountText
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseEngagement: ${e.message}")
            null
        }
    }

    // ============================================================
    // Channel browse: profile (channelMetadataRenderer) + Videos tab
    // ============================================================

    private const val CHANNEL_VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

    suspend fun resolveChannelId(context: Context, urlOrHandle: String): String? {
        val raw = urlOrHandle.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("UC") && raw.length >= 24) return raw
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("@") -> "https://www.youtube.com/$raw"
            else -> "https://www.youtube.com/${raw.trimStart('/')}"
        }
        val body = JSONObject().apply {
            put("context", webContext())
            put("url", url)
        }
        return try {
            postWatchApi(context, "navigation/resolve_url", body)
                ?.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith("UC") }
        } catch (e: Exception) {
            Log.w(TAG, "resolveChannelId failed for $url: ${e.message}")
            null
        }
    }

    suspend fun channelProfile(context: Context, channelId: String): ChannelProfileInfo? {
        val root = browse(context, channelId) ?: return null
        return try {
            val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            val id = metadata?.optString("externalId")?.takeIf { it.isNotBlank() } ?: channelId
            val name = metadata?.optString("title")?.takeIf { it.isNotBlank() } ?: return null
            val thumbs = metadata.optJSONObject("avatar")?.optJSONArray("thumbnails")
            val avatarUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                ?.optString("url")?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val handle = metadata.optString("vanityChannelUrl")
                .substringAfterLast('/')
                .takeIf { it.startsWith("@") }

            val bannerThumbs = metadata.optJSONObject("banner")?.optJSONArray("thumbnails")
            val bannerUrl = bannerThumbs?.optJSONObject((bannerThumbs.length() - 1).coerceAtLeast(0))
                ?.optString("url")?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("//")) "https:$it" else it }

            val subscriberCountText = runCatching {
                val header = root.optJSONObject("header") ?: return@runCatching null
                val texts = mutableListOf<JSONObject>()
                findObjectsByKey(header, "text", texts)
                texts.mapNotNull { it.optString("content").takeIf { c -> c.isNotBlank() } }
                    .firstOrNull { it.contains("subscriber", ignoreCase = true) }
            }.getOrNull()

            ChannelProfileInfo(id, name, avatarUrl, handle, subscriberCountText, bannerUrl)
        } catch (e: Exception) {
            Log.w(TAG, "channelProfile failed for $channelId: ${e.message}")
            null
        }
    }

    /**
     * Latest uploads of a channel (Videos tab). Channel-page lockups omit the
     * channel row, so the channel identity is stitched in from [profile].
     */
    suspend fun channelVideos(context: Context, profile: ChannelProfileInfo): List<VideoItem> {
        val root = browse(context, profile.channelId, params = CHANNEL_VIDEOS_TAB_PARAMS) ?: return emptyList()
        return try {
            val richItems = mutableListOf<JSONObject>()
            findObjectsByKey(root, "richItemRenderer", richItems)
            richItems.mapNotNull { item ->
                val content = item.optJSONObject("content") ?: return@mapNotNull null
                val parsed = parseLockupViewModel(content.optJSONObject("lockupViewModel"))
                    ?: parseVideoRenderer(content.optJSONObject("videoRenderer"))
                    ?: return@mapNotNull null
                parsed.copy(
                    uploaderName = profile.name,
                    uploaderUrl = "https://www.youtube.com/channel/${profile.channelId}",
                    uploaderAvatar = profile.avatarUrl,
                    channelId = profile.channelId,
                    channelIconUrl = profile.avatarUrl ?: parsed.channelIconUrl
                )
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            Log.e(TAG, "channelVideos failed: ${e.message}")
            emptyList()
        }
    }

    private fun webContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", WEB_VERSION)
            put("hl", "en")
            put("gl", "US")
        }
    )

    fun parseFeedItems(root: JSONObject?): List<VideoItem> {
        if (root == null) return emptyList()
        val renderers = mutableListOf<JSONObject>()
        findObjectsByKey(root, "videoRenderer", renderers)
        findObjectsByKey(root, "lockupViewModel", renderers)
        return renderers.mapNotNull { renderer ->
            if (renderer.has("videoId")) parseVideoRenderer(renderer)
            else parseLockupViewModel(renderer)
        }.distinctBy { it.videoId }
    }

    fun feedContinuation(root: JSONObject?): String? {
        if (root == null) return null
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?: root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
        val contents = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("richGridRenderer")
            ?.optJSONArray("contents")
            ?: return null
        for (i in 0 until contents.length()) {
            val token = contents.optJSONObject(i)
                ?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }

    fun parseShortsShelf(root: JSONObject?): List<VideoItem> {
        if (root == null) return emptyList()
        val lockups = mutableListOf<JSONObject>()
        findObjectsByKey(root, "shortsLockupViewModel", lockups)
        if (lockups.isNotEmpty()) {
            return lockups.mapNotNull { parseShortsLockup(it) }.distinctBy { it.videoId }
        }
        val reels = mutableListOf<JSONObject>()
        findObjectsByKey(root, "reelItemRenderer", reels)
        return reels.mapNotNull { parseReelItemRenderer(it) }.distinctBy { it.videoId }
    }

    private fun parseReelItemRenderer(reel: JSONObject): VideoItem? {
        return try {
            val id = reel.optString("videoId").takeIf { it.isNotBlank() } ?: return null
            val titleText = reel.optJSONObject("headline")?.optString("simpleText")
                ?: reel.optJSONObject("headline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: reel.optJSONObject("title")?.optString("simpleText")
                ?: reel.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            val thumb = reel.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            val views = reel.optJSONObject("viewCountText")?.optString("simpleText")
                ?: reel.optJSONObject("viewCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            VideoItem(
                url = "https://www.youtube.com/shorts/$id",
                title = titleText ?: id,
                thumbnailUrl = thumb,
                uploaderName = reel.optString("ownerText", "YouTube") ?: "YouTube",
                uploaderAvatar = null,
                duration = 0,
                views = parseCount(views ?: ""),
                uploadedDate = null,
                isShort = true
            )
        } catch (e: Exception) { null }
    }

    private fun parseComments(root: JSONObject): CommentsResponse {
        return try {
            val entities = mutableMapOf<String, JSONObject>()
            val mutations = root.optJSONObject("frameworkUpdates")
                ?.optJSONObject("entityBatchUpdate")
                ?.optJSONArray("mutations")
            if (mutations != null) {
                for (i in 0 until mutations.length()) {
                    val payload = mutations.optJSONObject(i)?.optJSONObject("payload") ?: continue
                    payload.optJSONObject("commentEntityPayload")?.let { entity ->
                        val id = entity.optJSONObject("properties")?.optString("commentId")
                        if (!id.isNullOrBlank()) entities[id] = entity
                    }
                }
            }

            val comments = mutableListOf<Comment>()
            var nextToken: String? = null
            val threadItems = mutableListOf<JSONObject>()

            // Params for posting a new top-level comment (present on first pages only)
            val createEndpoints = mutableListOf<JSONObject>()
            findObjectsByKey(root, "createCommentEndpoint", createEndpoints)
            val createCommentParams = createEndpoints.firstOrNull()
                ?.optString("createCommentParams")?.takeIf { it.isNotBlank() }

            // First page: comments live in itemSectionRenderer(comment-item-section).contents
            val sections = mutableListOf<JSONObject>()
            findObjectsByKey(root, "itemSectionRenderer", sections)
            for (section in sections) {
                if (section.optString("sectionIdentifier") != "comment-item-section") continue
                val sectionContents = section.optJSONArray("contents") ?: continue
                for (j in 0 until sectionContents.length()) {
                    sectionContents.optJSONObject(j)?.let { threadItems.add(it) }
                }
                val commands = mutableListOf<JSONObject>()
                findObjectsByKey(section, "continuationCommand", commands)
                nextToken = commands.maxByOrNull { it.optString("token").length }
                    ?.optString("token")?.takeIf { it.isNotBlank() } ?: nextToken
            }

            // Continuation pages: onResponseReceivedEndpoints -> appendContinuationItemsAction / reloadContinuationItemsCommand
            val endpoints = root.optJSONArray("onResponseReceivedEndpoints") ?: JSONArray()
            for (i in 0 until endpoints.length()) {
                val ep = endpoints.optJSONObject(i) ?: continue
                val items = (ep.optJSONObject("reloadContinuationItemsCommand")
                    ?: ep.optJSONObject("appendContinuationItemsAction"))
                    ?.optJSONArray("continuationItems") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    threadItems.add(item)
                }
            }

            for (item in threadItems) {
                val thread = item.optJSONObject("commentThreadRenderer") ?: item.optJSONObject("commentRenderer")
                val commentRenderer = thread?.optJSONObject("commentRenderer") ?: thread
                if (commentRenderer != null) {
                    parseClassicComment(commentRenderer)?.let { comments.add(it) }
                    continue
                }
                val viewModel = item.optJSONObject("commentThreadRenderer")
                    ?.optJSONObject("commentViewModel")
                    ?.let { vm -> vm.optJSONObject("commentViewModel") ?: vm }
                val commentId = viewModel?.optString("commentId")
                val entity = commentId?.let { entities[it] }
                if (entity != null) {
                    parseEntityComment(entity, viewModel)?.let { comments.add(it) }
                }
                val commands = mutableListOf<JSONObject>()
                findObjectsByKey(item, "continuationCommand", commands)
                val token = commands.maxByOrNull { it.optString("token").length }
                    ?.optString("token")?.takeIf { it.isNotBlank() }
                if (token != null) nextToken = token
            }
            CommentsResponse(comments = comments, nextpage = nextToken, createCommentParams = createCommentParams)
        } catch (e: Exception) {
            Log.e(TAG, "parseComments: ${e.message}")
            CommentsResponse(emptyList())
        }
    }

    private fun parseClassicComment(cr: JSONObject): Comment? {
        return try {
            val author = cr.optJSONObject("authorText")?.optString("simpleText")
                ?: cr.optString("author")
            val content = cr.optJSONObject("contentText")?.optString("simpleText")
                ?: cr.optJSONObject("contentText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            val likes = cr.optJSONObject("likeCount")?.optString("simpleText")
                ?.let { parseCount(it) } ?: 0L
            val time = cr.optJSONObject("publishedTimeText")?.optString("simpleText")
                ?: ""
            val thumb = cr.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            Comment(
                author = author ?: "Unknown",
                commentText = content ?: "",
                likes = likes,
                commentedTime = time,
                authorThumbnail = thumb ?: ""
            )
        } catch (e: Exception) { null }
    }

    private fun parseEntityComment(entity: JSONObject, viewModel: JSONObject?): Comment? {
        return try {
            val props = entity.optJSONObject("properties")
            val author = entity.optJSONObject("author")
            Comment(
                author = author?.optString("displayName").orEmpty(),
                commentText = props?.optJSONObject("content")?.optString("simpleText")
                    ?: props?.optJSONObject("content")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: "",
                likes = entity.optJSONObject("toolbar")?.optString("likeCountNotliked")
                    ?.let { parseCount(it) } ?: 0L,
                commentedTime = props?.optString("publishedTime").orEmpty(),
                authorThumbnail = author?.optString("avatarThumbnailUrl").orEmpty()
            )
        } catch (e: Exception) { null }
    }

    private fun parseCount(text: String): Long {
        return try {
            val digits = Regex("[\\d.]+[KMB]?").find(text)?.value ?: return 0
            when {
                digits.endsWith("B") -> (digits.dropLast(1).toDouble() * 1_000_000_000).toLong()
                digits.endsWith("M") -> (digits.dropLast(1).toDouble() * 1_000_000).toLong()
                digits.endsWith("K") -> (digits.dropLast(1).toDouble() * 1_000).toLong()
                else -> digits.toLongOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }

    fun findRenderers(node: Any, key: String, results: MutableList<JSONObject>) {
        findObjectsByKey(node, key, results)
    }

    private fun findObjectsByKey(node: Any, key: String, results: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val it = node.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k == key) node.optJSONObject(key)?.let { r -> results.add(r) }
                    else findObjectsByKey(node.opt(k), key, results)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) findObjectsByKey(node.opt(i), key, results)
            }
        }
    }

    private fun parseVideoRenderer(videoRenderer: JSONObject?): VideoItem? {
        if (videoRenderer == null) return null
        return try {
            val videoId = videoRenderer.optString("videoId").takeIf { it.isNotBlank() } ?: return null
            val titleObj = videoRenderer.optJSONObject("title")
            val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: titleObj?.optString("simpleText")
                ?: "Unknown Title"
            val channelObj = videoRenderer.optJSONObject("ownerText")
                ?: videoRenderer.optJSONObject("shortBylineText")
                ?: videoRenderer.optJSONObject("longBylineText")
            val channelName = channelObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: "Unknown Channel"
            val viewCountText = videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
                ?: videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText")
                ?: videoRenderer.optJSONObject("shortViewCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""
            val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "0:00"
            val durationSeconds = parseDurationToSeconds(durationText)
            val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnailUrl = thumbnails?.let {
                var bestUrl: String? = null
                var maxWidth = 0
                for (i in 0 until it.length()) {
                    val thumb = it.optJSONObject(i)
                    val width = thumb?.optInt("width", 0) ?: 0
                    if (width >= maxWidth) {
                        maxWidth = width
                        bestUrl = thumb?.optString("url")
                    }
                }
                bestUrl ?: it.optJSONObject(it.length() - 1)?.optString("url")
            }
            var channelId: String? = null
            try {
                val runs = channelObj?.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val browseEndpoint = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    channelId = browseEndpoint?.optString("browseId")
                }
            } catch (e: Exception) {}
            val publishedText = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText")
            VideoItem(
                url = "https://www.youtube.com/watch?v=$videoId",
                title = title,
                thumbnailUrl = thumbnailUrl,
                uploaderName = channelName,
                uploaderAvatar = null,
                uploaderUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
                channelId = channelId,
                duration = durationSeconds,
                views = parseCount(viewCountText),
                uploadedDate = publishedText,
                isShort = false,
                isLive = durationSeconds <= 0 && viewCountText.contains("watching", ignoreCase = true)
            )
        } catch (e: Exception) { null }
    }

    private fun parseLockupViewModel(lockup: JSONObject?): VideoItem? {
        if (lockup == null) return null
        return try {
            val contentId = lockup.optString("contentId")
            if (contentId.length != 11) return null
            val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val title = metadata?.optJSONObject("title")?.optString("content") ?: "Unknown Title"
            val details = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
            val rows = details?.optJSONArray("metadataRows")
            var channelName = "Unknown Channel"
            var channelId: String? = null
            var viewCount = ""
            var uploadDate = ""
            if (rows != null && rows.length() > 0) {
                val firstParts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                if (firstParts != null && firstParts.length() > 0) {
                    val textObj = firstParts.optJSONObject(0)?.optJSONObject("text")
                    channelName = textObj?.optString("content") ?: channelName
                    if (textObj?.has("runs") == true) {
                        val runs = textObj.optJSONArray("runs")
                        if (runs != null && runs.length() > 0) {
                            channelId = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")?.optString("browseId")
                        }
                    }
                }
                if (rows.length() > 1) {
                    val second = rows.optJSONObject(1)?.optJSONArray("metadataParts")
                    if (second != null) {
                        for (i in 0 until second.length()) {
                            val part = second.optJSONObject(i)?.optJSONObject("text")?.optString("content") ?: ""
                            if (part.contains("view", ignoreCase = true)) viewCount = part
                            else if (part.isNotBlank() && uploadDate.isBlank()) uploadDate = part
                        }
                    }
                }
            }
            val channelIconUrl = metadata
                ?.optJSONObject("image")
                ?.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
                ?.let { sources ->
                    var bestUrl: String? = null
                    var maxWidth = -1
                    for (i in 0 until sources.length()) {
                        val source = sources.optJSONObject(i)
                        val width = source?.optInt("width", 0) ?: 0
                        if (width >= maxWidth) {
                            maxWidth = width
                            bestUrl = source?.optString("url")
                        }
                    }
                    bestUrl?.takeIf { it.isNotBlank() }
                }
            val contentImage = lockup.optJSONObject("contentImage")
            val thumbnailViewModel = contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
                ?: contentImage?.optJSONObject("thumbnailViewModel")
            val thumbnailUrl = thumbnailViewModel?.optJSONObject("image")?.optJSONArray("sources")?.let { sources ->
                var bestUrl: String? = null
                var maxWidth = -1
                for (i in 0 until sources.length()) {
                    val source = sources.optJSONObject(i)
                    val width = source?.optInt("width", 0) ?: 0
                    if (width >= maxWidth) {
                        maxWidth = width
                        bestUrl = source?.optString("url")
                    }
                }
                bestUrl?.takeIf { it.isNotBlank() }
            }
            val durationText = lockup.optJSONObject("contentImage")?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailOverlayBadges")?.toString()
                ?.let { Regex("(\\d+):(\\d+)").find(it)?.value }
            VideoItem(
                url = "https://www.youtube.com/watch?v=$contentId",
                title = title,
                thumbnailUrl = thumbnailUrl,
                uploaderName = channelName,
                uploaderAvatar = channelIconUrl,
                uploaderUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
                channelId = channelId,
                duration = durationText?.let { parseDurationToSeconds(it) } ?: 0,
                views = parseCount(viewCount),
                uploadedDate = uploadDate,
                isShort = false
            )
        } catch (e: Exception) { null }
    }

    private fun parseDurationToSeconds(duration: String): Int {
        if (duration.isBlank()) return 0
        val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0].toInt()
            2 -> (parts[0] * 60 + parts[1]).toInt()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toInt()
            else -> 0
        }
    }

    private fun parseShortsLockup(lockup: JSONObject): VideoItem? {
        return try {
            val reel = lockup.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("reelWatchEndpoint") ?: return null
            val videoId = reel.optString("videoId").takeIf { it.length == 11 } ?: return null
            val overlay = lockup.optJSONObject("overlayMetadata")
            val title = overlay?.optJSONObject("primaryText")?.optString("content")
                ?.takeIf { it.isNotBlank() }
            val viewCount = overlay?.optJSONObject("secondaryText")?.optString("content")
                ?.takeIf { it.isNotBlank() }
            val reelThumb = reel.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
            val sources = lockup.optJSONObject("thumbnailViewModel")?.optJSONObject("image")?.optJSONArray("sources")
            val lockupThumb = sources?.optJSONObject(sources.length() - 1)?.optString("url")
                ?.takeIf { it.isNotBlank() }
            VideoItem(
                url = "https://www.youtube.com/shorts/$videoId",
                title = title ?: videoId,
                thumbnailUrl = lockupThumb ?: reelThumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                uploaderName = "YouTube",
                uploaderAvatar = null,
                duration = 0,
                views = parseShortsViewCount(viewCount),
                uploadedDate = null,
                isShort = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseShortsViewCount(text: String?): Long {
        if (text == null) return 0
        return try {
            val digits = Regex("[\\d.]+[KMB]?").find(text)?.value ?: return 0
            when {
                digits.endsWith("B") -> (digits.dropLast(1).toDouble() * 1_000_000_000).toLong()
                digits.endsWith("M") -> (digits.dropLast(1).toDouble() * 1_000_000).toLong()
                digits.endsWith("K") -> (digits.dropLast(1).toDouble() * 1_000).toLong()
                else -> digits.toLongOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}

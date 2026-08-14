package com.omersusin.pitube.innertube

import android.util.Log
import com.omersusin.pitube.FlowApplication
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.innertube.models.Context
import com.omersusin.pitube.innertube.models.YouTubeClient
import com.omersusin.pitube.innertube.models.YouTubeLocale
import com.omersusin.pitube.innertube.models.body.*
import com.omersusin.pitube.innertube.models.normalizeYouTubeHostLanguage
import com.omersusin.pitube.innertube.models.response.NextResponse
import com.omersusin.pitube.innertube.models.response.PlayerResponse
import com.omersusin.pitube.innertube.models.response.ReelWatchSequenceResponse
import com.omersusin.pitube.innertube.utils.parseCookieString
import com.omersusin.pitube.innertube.utils.sha1
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.net.Proxy
import java.util.*
import kotlin.io.encoding.Base64

private const val TAG = "InnerTube"

/**
 * `logged_in` verdict in a response's responseContext tracking params.
 * Koda uses the same shape to flag a dead session (`logged_in: 0` while we
 * sent cookies still means the stored session was rejected as anonymous).
 */
private val LOGGED_IN_TRACKING_PARAM =
    Regex("\"logged_in\"\\s*,\\s*\"value\"\\s*:\\s*\"([01])\"")

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
class InnerTube {
    private var httpClient = createClient()

    var locale =
        sanitizeLocale(
            YouTubeLocale(
                gl = Locale.getDefault().country,
                hl = Locale.getDefault().toLanguageTag(),
            ),
        )
        set(value) {
            field = sanitizeLocale(value)
        }
    var visitorData: String? = null
    var dataSyncId: String? = null
    var cookie: String? = null
        set(value) {
            field = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
        }
    private var cookieMap = emptyMap<String, String>()

    var proxy: Proxy? = null
        set(value) {
            if (field == value) return
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var proxyAuth: String? = null
        set(value) {
            if (field == value) return
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    var useLoginForBrowse: Boolean = false

    /** Invoked whenever a response rotated the session cookie to a new value. */
    var cookieRefreshListener: ((String) -> Unit)? = null

    /**
     * Invoked with YouTube's own session verdict from a response body: true
     * when `logged_in` is 1, false when it is 0. Only fires when the body
     * actually carries the tracking param, and only for bodies the callers
     * route through [noteResponseState].
     */
    var sessionStateListener: ((Boolean) -> Unit)? = null

    /**
     * Read YouTube's verdict on the session out of a response body.
     *
     * Every InnerTube response reports `logged_in` in its responseContext
     * tracking params. When the app sent cookies and a SAPISIDHASH and still
     * gets `0` back, the stored session is dead - which used to surface only as
     * empty account screens with no hint that signing in again was the fix.
     * A `1` clears the flag, so a session revived by a cookie rotation heals
     * itself without a round trip through the login screen.
     */
    fun noteResponseState(body: String) {
        val match = LOGGED_IN_TRACKING_PARAM.find(body) ?: return
        sessionStateListener?.invoke(match.groupValues[1] == "1")
    }

    private fun sanitizeLocale(value: YouTubeLocale): YouTubeLocale =
        YouTubeLocale(
            gl = sanitizeCountryCode(value.gl),
            hl = sanitizeLanguageCode(value.hl),
        )

    private fun sanitizeCountryCode(value: String): String {
        val normalized = value.trim().uppercase(Locale.US)
        return if (normalized.matches(Regex("[A-Z]{2}"))) {
            normalized
        } else {
            Locale
                .getDefault()
                .country
                .trim()
                .uppercase(Locale.US)
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
                ?: "US"
        }
    }

    private fun sanitizeLanguageCode(value: String): String = normalizeYouTubeHostLanguage(value)

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() =
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            // PERFORMANCE OPTIMIZED: Enhanced network configuration
            engine {
                config {
                    // Keep the stored session fresh as Google rotates cookies
                    // mid-session (SIDTS pairs etc.); only ever merges, never clears.
                    addInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val setCookies = response.headers("Set-Cookie")
                        if (!setCookies.isEmpty()) {
                            val host = response.request.url.host
                            if (host.endsWith("youtube.com") || host.endsWith("google.com")) {
                                val currentCookie = cookie
                                if (!currentCookie.isNullOrBlank()) {
                                    val merged = com.omersusin.pitube.data.local.CookieRotation.mergeCookies(
                                        currentCookie,
                                        setCookies,
                                    )
                                    if (merged != currentCookie) {
                                        cookie = merged
                                        cookieRefreshListener?.invoke(merged)
                                    }
                                }
                            }
                        }
                        response
                    }

                    // Aggressive connection pool for faster connection reuse
                    connectionPool(
                        okhttp3.ConnectionPool(
                            15, // Increased from 10 - more connections available
                            5, // keepAliveDuration
                            java.util.concurrent.TimeUnit.MINUTES,
                        ),
                    )

                    // Faster timeout configurations - fail fast, retry smart
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)

                    // Enable HTTP/2 for multiplexing (parallel streams on single connection)
                    protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))

                    // Retry on connection failure
                    retryOnConnectionFailure(true)

                    // High concurrency dispatcher
                    dispatcher(
                        okhttp3.Dispatcher().apply {
                            maxRequests = 48
                            maxRequestsPerHost = 8
                        },
                    )

                    // Cache configuration for better performance
                    cache(
                        okhttp3.Cache(
                            directory = java.io.File(System.getProperty("java.io.tmpdir"), "http_cache"),
                            maxSize = 50L * 1024L * 1024L, // 50 MB
                        ),
                    )
                }

                proxy?.let { proxy = this@InnerTube.proxy }

                // Fix proxy auth
                proxyAuth?.let { auth ->
                    config {
                        proxyAuthenticator { _, response ->
                            response.request
                                .newBuilder()
                                .header("Proxy-Authorization", auth)
                                .build()
                        }
                    }
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 45000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 30000
            }

            defaultRequest {
                url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
                // Add common headers for better compatibility
                header("Accept", "application/json")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Cache-Control", "no-cache")
            }
        }

    /**
     * Simple retry wrapper for transient IO errors (socket aborts, timeouts).
     * Retries the given block up to [maxAttempts] times with exponential backoff.
     * Cancellation is respected since [delay] will throw if the coroutine is cancelled.
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        apiUrl: String? = null,
    ) {
        val useMainSite = apiUrl != null && apiUrl != YouTubeClient.API_URL_YOUTUBE_MUSIC
        val origin = if (useMainSite) YouTubeClient.ORIGIN_YOUTUBE else YouTubeClient.ORIGIN_YOUTUBE_MUSIC
        val referer = if (useMainSite) YouTubeClient.REFERER_YOUTUBE else YouTubeClient.REFERER_YOUTUBE_MUSIC
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", origin)
            append("Referer", referer)
            visitorData?.let { append("X-Goog-Visitor-Id", it) }
            if (setLogin && client.loginSupported) {
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} $origin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = withRetry {
        httpClient.post("search") {
            ytClient(client, setLogin = useLoginForBrowse)
            setBody(
                SearchBody(
                    context =
                        client.toContext(
                            locale,
                            visitorData,
                            if (useLoginForBrowse) dataSyncId else null,
                        ),
                    query = query,
                    params = params,
                ),
            )
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
        }
    }

    /**
     * Search the main YouTube site (www.youtube.com), not the music endpoint. Needed to
     * reach renderers the music search omits, e.g. the Shorts shelf.
     */
    suspend fun webSearch(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        anonymous: Boolean = false,
        includeVisitorData: Boolean = !anonymous,
    ) = withRetry {
        withVisitorDataFallback(includeVisitorData) { requestVisitorData ->
            httpClient.post("https://www.youtube.com/youtubei/v1/search") {
                headers {
                    append("X-YouTube-Client-Name", client.clientId)
                    append("X-YouTube-Client-Version", client.clientVersion)
                    if (anonymous) {
                        append(HttpHeaders.Origin, YouTubeClient.ORIGIN_YOUTUBE)
                        append("Referer", YouTubeClient.ORIGIN_YOUTUBE)
                        append(HttpHeaders.Cookie, "SOCS=CAE=")
                        append(HttpHeaders.AcceptLanguage, "${locale.hl}, en;q=0.9")
                    } else {
                        append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE)
                        append("Referer", YouTubeClient.REFERER_YOUTUBE)
                    }
                    requestVisitorData?.let { append("X-Goog-Visitor-Id", it) }
                }
                contentType(io.ktor.http.ContentType.Application.Json)
                userAgent(client.userAgent)
                parameter("prettyPrint", false)
                setBody(
                    SearchBody(
                        context = client.toContext(locale, requestVisitorData, null),
                        query = query,
                        params = params,
                        continuation = continuation,
                    ),
                )
            }
        }
    }

    private suspend fun webBrowse(
        client: YouTubeClient,
        body: (String?) -> BrowseBody,
    ) = withRetry {
        withVisitorDataFallback { requestVisitorData ->
            httpClient.post("https://www.youtube.com/youtubei/v1/browse") {
                headers {
                    append("X-YouTube-Client-Name", client.clientId)
                    append("X-YouTube-Client-Version", client.clientVersion)
                    append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE)
                    append("Referer", YouTubeClient.REFERER_YOUTUBE)
                    requestVisitorData?.let { append("X-Goog-Visitor-Id", it) }
                }
                contentType(ContentType.Application.Json)
                userAgent(client.userAgent)
                parameter("prettyPrint", false)
                setBody(body(requestVisitorData))
            }
        }
    }

    /**
     * Search for videos within a specific YouTube channel using the /browse endpoint.
     * Uses the channel's Search Tab (params = "EgZzZWFyY2jyBgQKAloA") with the query as a
     * top-level body field.
     *
     * @param channelId  The channel's browse ID, e.g. "UCxxxxxxxxxxxxxxxxxx"
     * @param query      The search term
     * @param continuation  Pagination token returned from a previous search call
     */
    suspend fun channelSearch(
        client: YouTubeClient,
        channelId: String,
        query: String,
        continuation: String? = null,
    ) = webBrowse(client) { requestVisitorData ->
        BrowseBody(
            context = client.toContext(locale, requestVisitorData, null),
            browseId = if (continuation == null) channelId else null,
            params = if (continuation == null) "EgZzZWFyY2jyBgQKAloA" else null,
            query = if (continuation == null) query else null,
            continuation = continuation,
        )
    }

    /**
     * Browse a YouTube channel tab through the YouTube.com WEB /browse endpoint.
     *
     * Initial calls pass [channelId] and [params]; continuation calls pass only
     * [continuation].
     */
    suspend fun channelBrowse(
        client: YouTubeClient,
        channelId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = webBrowse(client) { requestVisitorData ->
        BrowseBody(
            context = client.toContext(locale, requestVisitorData, null),
            browseId = if (continuation == null) channelId else null,
            params = if (continuation == null) params else null,
            continuation = continuation,
        )
    }

    /**
     * Signed /browse against the YouTube.com WEB endpoint, used for account
     * personalized surfaces (the home feed's FEwhat_to_watch and its rich-grid
     * continuation pages).
     *
     * Unlike [webBrowse] this goes through [HttpRequestBuilder.ytClient] with
     * [setLogin], so the stored session cookie and the matching SAPISIDHASH
     * (signed for the www.youtube.com origin) are attached to every request.
     */
    suspend fun signedWebBrowse(
        client: YouTubeClient,
        browseId: String? = null,
        continuation: String? = null,
    ) = withRetry {
        httpClient.post("https://www.youtube.com/youtubei/v1/browse") {
            ytClient(client, setLogin = true, apiUrl = YouTubeClient.API_URL_YOUTUBE)
            setBody(
                BrowseBody(
                    context =
                        client.toContext(
                            locale,
                            visitorData,
                            dataSyncId,
                        ),
                    browseId = if (continuation == null) browseId else null,
                    params = null,
                    continuation = continuation,
                ),
            )
        }
    }

    /**
     * Signed JSON POST to an arbitrary InnerTube endpoint on www.youtube.com.
     * Same auth as [signedWebBrowse] (cookie + SAPISIDHASH for the
     * www.youtube.com origin) — used for comment writes, which reject the
     * unsigned variant. Returns the raw [HttpResponse] so callers can decide
     * how to interpret the body (status, frameworkUpdates, …).
     */
    suspend fun signedJsonPost(
        client: YouTubeClient,
        endpoint: String,
        jsonBody: JsonObject,
    ) = withRetry {
        httpClient.post("https://www.youtube.com/youtubei/v1/$endpoint") {
            ytClient(client, setLogin = true, apiUrl = YouTubeClient.API_URL_YOUTUBE)
            setBody(jsonBody)
        }
    }

    /**
     * Cookie-authenticated GET for YouTube's videostats beacon URLs
     * (watch-history reporting). Sent like a real browser playback signal:
     * the signed-in session cookie, a SAPISIDHASH Authorization (same shape
     * Koda/ViMusic use for their working history pings), full browser-ish
     * headers and the watch page Referer. Returns the HTTP status code, so the
     * caller can tell a success (2xx) from a throttled (429), bot-walled or
     * session-dead (401/403) response instead of swallowing it.
     */
    suspend fun videoStatsPing(
        url: String,
        referer: String? = null,
    ): Int = withRetry {
        try {
            httpClient.get(url) {
                headers {
                    append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    append("Accept-Language", "en-US,en;q=0.9")
                    append("Origin", YouTubeClient.ORIGIN_YOUTUBE)
                    referer?.let { append("Referer", it) }
                    userAgent(YouTubeClient.WEB.userAgent)
                    cookie?.let { cookie ->
                        append("cookie", cookie)
                        if ("SAPISID" in cookieMap) {
                            val currentTime = System.currentTimeMillis() / 1000
                            val sapisidHash =
                                sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE}")
                            append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                            append("X-Goog-AuthUser", "0")
                        }
                    }
                }
            }.status.value
        } catch (error: ClientRequestException) {
            // expectSuccess=true turns 4xx into an exception; surface its status
            // so history callers can detect 401/403 (session dead) / 429 (back off).
            error.response.status.value
        } catch (_: ServerResponseException) {
            HttpStatusCode.InternalServerError.value
        }
    }

    private suspend fun <T> withVisitorDataFallback(
        includeVisitorData: Boolean = true,
        block: suspend (String?) -> T,
    ): T {
        val requestVisitorData =
            visitorData?.takeIf { includeVisitorData && it.isNotBlank() }
                ?: return block(null)
        return try {
            block(requestVisitorData)
        } catch (error: ClientRequestException) {
            if (error.response.status != HttpStatusCode.BadRequest) throw error
            val response = block(null)
            if (visitorData == requestVisitorData) {
                visitorData = null
            }
            Log.w(TAG, "InnerTube rejected visitor data; request succeeded without it")
            response
        }
    }

    suspend fun postCommentsBrowse(
        client: YouTubeClient,
        postId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = webBrowse(client) { requestVisitorData ->
        BrowseBody(
            context = client.toContext(locale, requestVisitorData, null),
            browseId = if (continuation == null) "FEpost_detail" else null,
            params = if (continuation == null) params else null,
            continuation = continuation,
            canonicalBaseUrl =
                if (continuation == null && params == null) {
                    postId?.let { "/post/$it" }
                } else {
                    null
                },
        )
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
        localeOverride: YouTubeLocale? = null,
        apiUrl: String? = null,
    ) = withRetry {
        httpClient.post(apiUrl?.let { "${it}player" } ?: "player") {
            ytClient(client, setLogin = true, apiUrl = apiUrl)
            setBody(
                PlayerBody(
                    context =
                        client.toContext(localeOverride ?: locale, visitorData, dataSyncId).let {
                            if (client.isEmbedded) {
                                it.copy(
                                    thirdParty =
                                        Context.ThirdParty(
                                            embedUrl = "https://www.youtube.com/watch?v=$videoId",
                                        ),
                                )
                            } else {
                                it
                            }
                        },
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext =
                        if (client.useSignatureTimestamp && signatureTimestamp != null) {
                            PlayerBody.PlaybackContext(
                                PlayerBody.PlaybackContext.ContentPlaybackContext(
                                    signatureTimestamp,
                                ),
                            )
                        } else {
                            null
                        },
                    serviceIntegrityDimensions = poToken?.let { PlayerBody.ServiceIntegrityDimensions(it) },
                ),
            )
        }
    }

    suspend fun playerWeb(
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
        visitorData: String?,
        locale: YouTubeLocale,
        cpn: String?,
        reloadToken: String? = null,
    ) = withRetry {
        val client = YouTubeClient.WEB
        httpClient.post("https://www.youtube.com/youtubei/v1/player") {
            headers {
                append("X-Goog-Api-Format-Version", "1")
                append("X-YouTube-Client-Name", client.clientId)
                append("X-YouTube-Client-Version", client.clientVersion)
                append("X-Origin", "https://www.youtube.com")
                append("Referer", "https://www.youtube.com/")
                visitorData?.let { append("X-Goog-Visitor-Id", it) }
            }
            contentType(ContentType.Application.Json)
            userAgent(client.userAgent)
            parameter("prettyPrint", false)
            setBody(
                PlayerBody(
                    context = client.toContext(locale, visitorData, null),
                    videoId = videoId,
                    playlistId = null,
                    playbackContext =
                        if (signatureTimestamp != null || reloadToken != null) {
                            PlayerBody.PlaybackContext(
                                contentPlaybackContext =
                                    signatureTimestamp?.let {
                                        PlayerBody.PlaybackContext.ContentPlaybackContext(
                                            signatureTimestamp = it,
                                            referer = "https://www.youtube.com/watch?v=$videoId",
                                            vis = 0,
                                            splay = false,
                                            lactMilliseconds = "-1",
                                            html5Preference = "HTML5_PREF_WANTS",
                                        )
                                    },
                                reloadPlaybackContext =
                                    reloadToken?.let {
                                        PlayerBody.PlaybackContext.ReloadPlaybackContext(
                                            PlayerBody.PlaybackContext.ReloadPlaybackContext.ReloadPlaybackParams(it),
                                        )
                                    },
                            )
                        } else {
                            null
                        },
                    serviceIntegrityDimensions = poToken?.let { PlayerBody.ServiceIntegrityDimensions(it) },
                    contentCheckOk = true,
                    racyCheckOk = true,
                    cpn = cpn,
                ),
            )
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = withRetry {
        httpClient.post("browse") {
            ytClient(client, setLogin = setLogin || useLoginForBrowse)
            setBody(
                BrowseBody(
                    context =
                        client.toContext(
                            locale,
                            visitorData,
                            if (setLogin || useLoginForBrowse) dataSyncId else null,
                        ),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    suspend fun reel(
        client: YouTubeClient,
        params: String? = null,
        sequenceParams: String? = "CA8%3D", // Default for initial fetch
        continuation: String? = null, // Continuation token for load-more pages
        setLogin: Boolean = false,
    ) = withRetry {
        httpClient
            // Shorts sequences only resolve on the www host (the music host
            // serves empty/blocked reel responses) — same override the stream
            // extractor applies to every player() call.
            .post("https://www.youtube.com/youtubei/v1/reel/reel_watch_sequence") {
                ytClient(client, setLogin = setLogin, apiUrl = YouTubeClient.API_URL_YOUTUBE)
                setBody(
                    ReelBody(
                        context =
                            client.toContext(
                                locale,
                                visitorData,
                                if (setLogin) dataSyncId else null,
                            ),
                        params = params,
                        sequenceParams = sequenceParams,
                        continuation = continuation,
                    ),
                )
            }.body<ReelWatchSequenceResponse>()
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = withRetry {
        httpClient.post("next") {
            ytClient(client, setLogin = true)
            setBody(
                NextBody(
                    context = client.toContext(locale, visitorData, dataSyncId),
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    private fun HttpRequestBuilder.webYouTubeHeaders(client: YouTubeClient) {
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", "https://www.youtube.com")
            append("Referer", "https://www.youtube.com/")
            visitorData?.let { append("X-Goog-Visitor-Id", it) }
        }
        contentType(ContentType.Application.Json)
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    suspend fun nextForLiveChat(videoId: String) =
        withRetry {
            val client = YouTubeClient.WEB
            httpClient.post("https://www.youtube.com/youtubei/v1/next") {
                webYouTubeHeaders(client)
                setBody(
                    NextBody(
                        context = client.toContext(locale, visitorData, null),
                        videoId = videoId,
                        playlistId = null,
                        playlistSetVideoId = null,
                        index = null,
                        params = null,
                        continuation = null,
                    ),
                )
            }
        }

suspend fun getLiveChat(
        continuation: String,
        offsetMs: Long? = null,
    ) = withRetry {
        val client = YouTubeClient.WEB
        httpClient.post("https://www.youtube.com/youtubei/v1/live_chat/get_live_chat") {
            webYouTubeHeaders(client)
            setBody(
                GetLiveChatBody(
                    context = client.toContext(locale, visitorData, null),
                    continuation = continuation,
                    currentPlayerState =
                        offsetMs?.let {
                            GetLiveChatBody.CurrentPlayerState(playerOffsetMs = it.toString())
                        },
                ),
            )
        }
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
        parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
        headers {
            append("Content-Type", "application/json")
        }
        setBody(
            GetTranscriptBody(
                context = client.toContext(locale, null, null),
                params = Base64.Default.encode("\n${11.toChar()}$videoId".toByteArray()),
            ),
        )
    }

    suspend fun getSwJsData() = httpClient.get("https://music.youtube.com/sw.js_data")

    suspend fun accountMenu(client: YouTubeClient) =
        httpClient.post("account/account_menu") {
            ytClient(client, setLogin = true)
            setBody(AccountMenuBody(client.toContext(locale, visitorData, dataSyncId)))
        }

}

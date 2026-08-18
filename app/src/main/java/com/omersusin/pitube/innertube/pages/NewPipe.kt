package com.omersusin.pitube.innertube.pages

import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.models.YouTubeClient
import com.omersusin.pitube.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Proxy

class NewPipeDownloaderImpl(
    proxy: Proxy?,
    proxyAuth: String? = null,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .proxyAuthenticator { _, response ->
                proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)

        // Only fall back to the WEB user agent when the extractor request does
        // not already carry a client-specific one. NewPipe resolves the
        // ANDROID/IOS/WEB UA itself per-request; naively forcing USER_AGENT_WEB
        // on top mismatches the client body and raises bot-detection walls.
        var hasExplicitUserAgent = false
        headers.forEach { (headerName, headerValueList) ->
            if (headerName.equals("User-Agent", ignoreCase = true) && headerValueList.isNotEmpty()) {
                hasExplicitUserAgent = true
            }
        }
        if (!hasExplicitUserAgent) {
            requestBuilder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
        }

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body.string()
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

class NewPipeUtils(
    downloader: Downloader,
) {
    init {
        NewPipe.init(downloader)
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> =
        runCatching {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        }

    fun deobfuscateThrottling(videoId: String, url: String): String? =
        try {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        } catch (e: Exception) {
            android.util.Log.w("NewPipeUtils", "nsig deobfuscation threw: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
    ): String? =
        try {
            val url =
                format.url ?: format.signatureCipher?.let { signatureCipher ->
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature =
                        params["s"]
                            ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam =
                        params["sp"]
                            ?: throw ParsingException("Could not parse cipher signature parameter")
                    val url =
                        params["url"]?.let { URLBuilder(it) }
                            ?: throw ParsingException("Could not parse cipher url")
                    url.parameters[signatureParam] =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            videoId,
                            obfuscatedSignature,
                        )
                    url.buildString()
                } ?: throw ParsingException("Could not find format url")

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}

object NewPipeExtractor {
    private var newPipeDownloader: NewPipeDownloaderImpl? = null
    private var newPipeUtils: NewPipeUtils? = null
    private var isInitialized = false
    private var lastProxySignature: String? = null

    fun init() {
        val currentSignature = listOf(YouTube.proxy?.address(), YouTube.proxyAuth).joinToString(separator = "|")
        if (!isInitialized || lastProxySignature != currentSignature) {
            newPipeDownloader = NewPipeDownloaderImpl(
                proxy = YouTube.proxy,
                proxyAuth = YouTube.proxyAuth
            )
            newPipeUtils = NewPipeUtils(newPipeDownloader!!)
            isInitialized = true
            lastProxySignature = currentSignature
        }
    }

    fun invalidateClient() {
        newPipeDownloader = null
        newPipeUtils = null
        isInitialized = false
        lastProxySignature = null
        nsigSuppressedUntil = 0L
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> {
        init()
        return newPipeUtils?.getSignatureTimestamp(videoId)
            ?: Result.failure(Exception("NewPipeUtils not initialized"))
    }

    /**
     * NewPipe's throttling (`n` parameter) deobfuscation is the strongest
     * decoder we have, but it used to be permanently disabled for the whole
     * app session after a single failure. One transient failure (bad player JS
     * fetch, network blip) silently crippled every later video. Now a failure
     * only backs NewPipe off for a short cooldown; after that it re-probes so
     * the best decoder keeps working whenever YouTube recovered.
     */
    private const val NSIG_SUPPRESSION_COOLDOWN_MS = 60_000L

    @Volatile
    private var nsigSuppressedUntil = 0L

    fun deobfuscateThrottling(videoId: String, url: String): String? {
        init()
        if (System.currentTimeMillis() < nsigSuppressedUntil) {
            return null
        }
        val result = newPipeUtils?.deobfuscateThrottling(videoId, url)
        if (result == null || (url.contains("n=") && result == url)) {
            nsigSuppressedUntil = System.currentTimeMillis() + NSIG_SUPPRESSION_COOLDOWN_MS
            android.util.Log.w(
                "NewPipeExtractor",
                "NewPipe nsig ineffective (${if (result == null) "threw" else "unchanged"}) — backing off for $NSIG_SUPPRESSION_COOLDOWN_MS ms, re-probing after"
            )
        }
        return result
    }

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        init()
        return newPipeUtils?.getStreamUrl(format, videoId)
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        init()
        return try {
            val streamInfo = StreamInfo.getInfo(
                NewPipe.getService(0),
                "https://www.youtube.com/watch?v=$videoId"
            )
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList.mapNotNull {
                (it.itagItem?.id ?: return@mapNotNull null) to it.content
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

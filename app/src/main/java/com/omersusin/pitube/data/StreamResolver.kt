package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@UnstableApi
object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    data class Resolved(
        val title: String,
        val description: String,
        val uploader: String,
        val uploaderUrl: String,
        val videoUrl: String?,
        val audioUrl: String?,
        val hlsUrl: String?
    ) {
        val playUrl: String? get() = videoUrl ?: hlsUrl
        val downloadUrl: String? get() = videoUrl
    }

    private data class ClientConfig(
        val clientName: String,
        val clientVersion: String,
        val apiKey: String,
        val deviceModel: String? = null,
        val userAgent: String,
        val osName: String,
        val osVersion: String,
        val gl: String = "US",
        val hl: String = "en"
    )

    private val ANDROID_VR_CLIENT = ClientConfig(
        clientName = "ANDROID_VR",
        clientVersion = "1.60.19",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA",
        deviceModel = "Quest 3",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12L"
    )

    private val IOS_CLIENT = ClientConfig(
        clientName = "IOS",
        clientVersion = "19.45.4",
        apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
        deviceModel = "iPhone16,2",
        userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
        osName = "iOS",
        osVersion = "18.1.0"
    )

    private val WEB_CREATOR_CLIENT = ClientConfig(
        clientName = "WEB_CREATOR",
        clientVersion = "1.20241205.01.00",
        apiKey = "AIzaSyBUPetSUmoZL-OhlxA7wSac5XinrygCqMo",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        osName = "Windows",
        osVersion = "10.0.0"
    )

    fun resolve(videoId: String, context: Context): Resolved? {
        val rawCookies = AuthManager.getRawCookies(context)
        val cookieString = if (rawCookies.isNotBlank()) rawCookies else run {
            val cookies = AuthManager.getCookies(context)
            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        val authHeader = if (rawCookies.isNotBlank()) KodaAuth.authHeader(rawCookies) else null

        // ANDROID_VR first - most reliable, no cipher/PO token needed
        Log.d(TAG, "Trying ANDROID_VR for $videoId")
        val vrResult = tryClient(videoId, ANDROID_VR_CLIENT, cookieString, authHeader, context)
        if (vrResult != null && vrResult.playUrl != null) return vrResult

        // IOS fallback
        Log.d(TAG, "Trying IOS for $videoId")
        val iosResult = tryClient(videoId, IOS_CLIENT, cookieString, authHeader, context)
        if (iosResult != null && iosResult.playUrl != null) return iosResult

        // WEB_CREATOR fallback (requires auth)
        Log.d(TAG, "Trying WEB_CREATOR for $videoId")
        val webResult = tryClient(videoId, WEB_CREATOR_CLIENT, cookieString, authHeader, context)
        if (webResult != null && webResult.playUrl != null) return webResult

        Log.e(TAG, "All clients failed for $videoId")
        return null
    }

    private fun tryClient(
        videoId: String,
        client: ClientConfig,
        cookieString: String,
        authHeader: String?,
        context: Context
    ): Resolved? {
        val requestBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", client.clientName)
                    put("clientVersion", client.clientVersion)
                    if (client.deviceModel != null) put("deviceModel", client.deviceModel)
                    put("osName", client.osName)
                    put("osVersion", client.osVersion)
                    put("hl", client.hl)
                    put("gl", client.gl)
                    put("timeZone", "UTC")
                    put("utcOffsetMinutes", 0)
                })
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val clientNumber = when (client.clientName) {
            "IOS" -> "5"
            "ANDROID_VR" -> "28"
            "WEB_CREATOR" -> "62"
            else -> "0"
        }

        val requestBuilder = Request.Builder()
            .url("$INNERTUBE_API_URL?key=${client.apiKey}")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-YouTube-Client-Name", clientNumber)
            .addHeader("X-YouTube-Client-Version", client.clientVersion)
            .addHeader("Accept-Language", "${client.hl},en;q=0.9")

        if (cookieString.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookieString)
        }
        if (authHeader != null) {
            requestBuilder.addHeader("Authorization", authHeader)
        }

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "${client.clientName} HTTP ${resp.code} for $videoId")
                    return null
                }
                KodaAuth.refreshFromResponse(context, resp)

                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)

                val playabilityStatus = json.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status")
                if (status != "OK") {
                    val reason = playabilityStatus?.optString("reason", "")
                    Log.w(TAG, "${client.clientName} status=$status reason=$reason for $videoId")
                    return null
                }

                val videoDetails = json.optJSONObject("videoDetails") ?: return null
                val title = videoDetails.optString("title", "")
                val description = videoDetails.optString("shortDescription", "")
                val uploader = videoDetails.optString("author", "")
                val uploaderUrl = "https://www.youtube.com/channel/${videoDetails.optString("channelId", "")}"

                val streamingData = json.optJSONObject("streamingData") ?: return null
                var videoUrl: String? = null
                var audioUrl: String? = null
                val hlsUrl: String? = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

                // Prefer adaptive formats (separate video+audio for best quality)
                // Sort by quality: prefer 1080p/720p, then by bitrate
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    // Collect all video and audio formats with quality info
                    val videoFormats = mutableListOf<Pair<Int, String>>() // quality to URL
                    val audioFormats = mutableListOf<Pair<Int, String>>() // bitrate to URL
                    
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        val mimeType = format.optString("mimeType", "")
                        val url = format.optString("url", "")
                        val signatureCipher = format.optString("signatureCipher", "")

                        if (url.isBlank()) continue
                        if (signatureCipher.isNotBlank()) continue

                        if (mimeType.startsWith("video/") && !mimeType.contains("audio")) {
                            // Extract quality from label like "1080p60" or "720p"
                            val qualityLabel = format.optString("qualityLabel", "")
                            val quality = qualityLabel.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                            videoFormats.add(quality to url)
                        } else if (mimeType.startsWith("audio/")) {
                            val bitrate = format.optInt("bitrate", 0)
                            audioFormats.add(bitrate to url)
                        }
                    }
                    
                    // Pick best video (highest quality <= 1080p)
                    videoUrl = videoFormats
                        .filter { it.first <= 1080 }
                        .maxByOrNull { it.first }?.second
                        ?: videoFormats.maxByOrNull { it.first }?.second
                    
                    // Pick best audio (highest bitrate)
                    audioUrl = audioFormats.maxByOrNull { it.first }?.second
                }

                // Fallback to regular formats (combined audio+video)
                if (videoUrl == null && audioUrl == null) {
                    val formats = streamingData.optJSONArray("formats")
                    if (formats != null && formats.length() > 0) {
                        for (i in 0 until formats.length()) {
                            val format = formats.getJSONObject(i)
                            val url = format.optString("url", "")
                            if (url.isNotBlank()) {
                                videoUrl = url
                                break
                            }
                        }
                    }
                }

                Log.d(TAG, "${client.clientName} resolved $videoId: video=${videoUrl != null} audio=${audioUrl != null} hls=${hlsUrl != null}")
                Resolved(title, description, uploader, uploaderUrl, videoUrl, audioUrl, hlsUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "${client.clientName} error for $videoId: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun buildMediaSource(context: Context, resolved: Resolved): MediaSource? {
        val videoUrl = resolved.videoUrl
        val audioUrl = resolved.audioUrl
        val hlsUrl = resolved.hlsUrl

        return try {
            val dataSourceFactory = ChunkedStreamDataSource.Factory()

            // If we have both video and audio URLs, merge them
            if (!videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                return MergingMediaSource(videoSource, audioSource)
            }

            // If we have only video URL (combined format or video-only)
            if (!videoUrl.isNullOrBlank()) {
                val mediaItem = MediaItem.fromUri(videoUrl)
                return ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            // Fallback to HLS
            if (!hlsUrl.isNullOrBlank()) {
                val mediaItem = MediaItem.fromUri(hlsUrl)
                return ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

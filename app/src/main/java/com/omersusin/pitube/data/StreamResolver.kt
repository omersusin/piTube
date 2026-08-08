package com.omersusin.pitube.data

import android.content.Context
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
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"

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
        val osName: String = "iOS",
        val osVersion: String = "18.0.0"
    )

    private val IOS_CLIENT = ClientConfig(
        clientName = "IOS",
        clientVersion = "20.05.5",
        apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
        deviceModel = "iPhone16,2",
        userAgent = "com.google.ios.youtube/20.05.5 (iPhone16,2; U; CPU iOS 18_0_0 like Mac OS X;)",
        osName = "iOS",
        osVersion = "18.0.0"
    )

    private val WEB_CREATOR_CLIENT = ClientConfig(
        clientName = "WEB_CREATOR",
        clientVersion = "1.20241205.01.00",
        apiKey = "AIzaSyBUPetSUmoZL-OhlxA7wSac5XinrygCqMo",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        osName = "Windows",
        osVersion = "10.0.0"
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

    fun resolve(videoId: String, context: Context): Resolved? {
        val cookies = AuthManager.getCookies(context)
        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val rawCookies = AuthManager.getRawCookies(context)
        val authHeader = if (rawCookies.isNotBlank()) KodaAuth.authHeader(rawCookies) else null

        // Try IOS client first (most reliable, no signatureCipher)
        val iosResult = tryClient(videoId, IOS_CLIENT, cookieString, authHeader, context)
        if (iosResult != null) return iosResult

        // Fallback to WEB_CREATOR
        val webResult = tryClient(videoId, WEB_CREATOR_CLIENT, cookieString, authHeader, context)
        if (webResult != null) return webResult

        // Fallback to ANDROID_VR
        val vrResult = tryClient(videoId, ANDROID_VR_CLIENT, cookieString, authHeader, context)
        if (vrResult != null) return vrResult

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
                })
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val requestBuilder = Request.Builder()
            .url("$INNERTUBE_API_URL?key=${client.apiKey}")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-YouTube-Client-Name", if (client.clientName == "IOS") "5" else if (client.clientName == "ANDROID_VR") "28" else "62")
            .addHeader("X-YouTube-Client-Version", client.clientVersion)

        if (cookieString.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookieString)
        }
        if (authHeader != null) {
            requestBuilder.addHeader("Authorization", authHeader)
        }

        return try {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return null
            KodaAuth.refreshFromResponse(context, response)

            val json = JSONObject(response.body?.string() ?: return null)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != "OK") return null

            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "")
            val description = videoDetails.optString("shortDescription", "")
            val uploader = videoDetails.optString("author", "")
            val uploaderUrl = "https://www.youtube.com/channel/${videoDetails.optString("channelId", "")}"

            val streamingData = json.optJSONObject("streamingData") ?: return null
            var videoUrl: String? = null
            var audioUrl: String? = null
            val hlsUrl: String? = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

            // Try adaptive formats first
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType", "")
                    val url = format.optString("url", "")

                    if (url.isBlank()) continue

                    if (mimeType.startsWith("video/") && videoUrl == null && !mimeType.contains("audio")) {
                        videoUrl = url
                    } else if (mimeType.startsWith("audio/") && audioUrl == null) {
                        audioUrl = url
                    }

                    if (videoUrl != null && audioUrl != null) break
                }
            }

            // Fallback to regular formats if adaptive didn't work
            if (videoUrl == null && audioUrl == null) {
                val formats = streamingData.optJSONArray("formats")
                if (formats != null && formats.length() > 0) {
                    val format = formats.getJSONObject(0)
                    val url = format.optString("url", "")
                    if (url.isNotBlank()) {
                        videoUrl = url
                    }
                }
            }

            Resolved(title, description, uploader, uploaderUrl, videoUrl, audioUrl, hlsUrl)
        } catch (e: Exception) {
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

            // If we have only video URL
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

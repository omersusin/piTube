package com.omersusin.pitube.data

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@UnstableApi
object StreamResolver {
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"
    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA"

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

    fun resolve(videoId: String, context: Context): Resolved? {
        val cookies = AuthManager.getCookies(context)
        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val requestBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "2.20240101.00.00"
                    }
                },
                "videoId": "$videoId"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$INNERTUBE_API_URL?key=$INNERTUBE_API_KEY")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookieString)
            .build()

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            KodaAuth.refreshFromResponse(context, response)

            val json = JSONObject(response.body?.string() ?: return null)

            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "")
            val description = videoDetails.optString("shortDescription", "")
            val uploader = videoDetails.optString("author", "")
            val uploaderUrl = "https://www.youtube.com/channel/${videoDetails.optString("channelId", "")}"

            val streamingData = json.optJSONObject("streamingData")
            var videoUrl: String? = null
            var audioUrl: String? = null
            val hlsUrl: String? = streamingData?.optString("hlsManifestUrl")

            val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")

            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType")
                    val url = format.optString("url")

                    if (mimeType.startsWith("video/") && videoUrl == null) {
                        videoUrl = url
                    } else if (mimeType.startsWith("audio/") && audioUrl == null) {
                        audioUrl = url
                    }

                    if (videoUrl != null && audioUrl != null) break
                }
            }

            Resolved(title, description, uploader, uploaderUrl, videoUrl, audioUrl, hlsUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun buildMediaSource(context: Context, resolved: Resolved): MediaSource? {
        val url = resolved.playUrl ?: return null

        return try {
            val dataSourceFactory = ChunkedStreamDataSource.Factory()

            val mediaItem = MediaItem.fromUri(url)
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

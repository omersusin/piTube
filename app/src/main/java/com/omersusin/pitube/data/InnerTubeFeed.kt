package com.omersusin.pitube.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InnerTubeFeed {
    private const val TAG = "InnerTubeFeed"
    private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20260114.08.00"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val INNERTUBE_URL = "https://www.youtube.com/youtubei/v1/browse"

    suspend fun fetchFeed(context: android.content.Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val rawCookies = AuthManager.getRawCookies(context)
            if (rawCookies.isBlank()) {
                Log.d(TAG, "No cookies, returning empty")
                return@withContext emptyList()
            }

            val authHeader = KodaAuth.authHeader(rawCookies)
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    })
                })
                put("browseId", browseId)
            }

            val reqBuilder = Request.Builder()
                .url(INNERTUBE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", rawCookies)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-YouTube-Client-Name", "1")
                .addHeader("X-YouTube-Client-Version", CLIENT_VERSION)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("X-Goog-AuthUser", "0")
                .addHeader("X-Goog-Api-Format-Version", "2")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            if (authHeader != null) reqBuilder.addHeader("Authorization", authHeader)

            Log.d(TAG, "Fetching feed for $browseId")
            val resp = client.newCall(reqBuilder.build()).execute()
            resp.use { r ->
                KodaAuth.refreshFromResponse(context, r)

                if (!r.isSuccessful) {
                    Log.w(TAG, "HTTP ${r.code} for $browseId")
                    return@withContext emptyList()
                }

                val json = JSONObject(r.body?.string() ?: return@withContext emptyList())
                val videos = mutableListOf<VideoItem>()

                // Parse the browse response — try multiple formats
                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")

                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val tab = contents.optJSONObject(i)
                            ?.optJSONObject("tabRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("richGridRenderer")
                            ?.optJSONArray("contents") ?: continue

                        for (j in 0 until tab.length()) {
                            val item = tab.optJSONObject(j) ?: continue

                            // Try richItemRenderer -> videoRenderer
                            val vr = item.optJSONObject("richItemRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("videoRenderer")

                            // Try continuationItemRenderer (for pagination)
                            if (vr == null) {
                                item.optJSONObject("continuationItemRenderer")
                                    ?.optJSONObject("continuationEndpoint")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token")
                                    ?.let { token ->
                                        Log.d(TAG, "Found continuation token")
                                    }
                                continue
                            }

                            val videoItem = parseVideoRenderer(vr)
                            if (videoItem != null) videos.add(videoItem)
                        }
                    }
                }

                Log.d(TAG, "Fetched ${videos.size} videos for $browseId")
                videos
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseVideoRenderer(vr: JSONObject): VideoItem? {
        val videoId = vr.optString("videoId", "")
        if (videoId.isBlank()) return null

        val title = vr.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "") ?: ""

        val thumb = vr.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.optJSONObject(0)
            ?.optString("url", "")

        val channelName = vr.optJSONObject("ownerText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "") ?: ""

        val channelAvatar = vr.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.optJSONObject(0)
            ?.optString("url", "")

        val channelId = vr.optJSONObject("longBylineText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId", "") ?: ""

        val duration = vr.optJSONObject("lengthText")
            ?.optString("simpleText", "0:00") ?: "0:00"
        val durationSec = parseDuration(duration)

        val views = vr.optJSONObject("viewCountText")
            ?.optString("simpleText", "0")
            ?.replace(Regex("[^0-9]"), "")
            ?.toLongOrNull() ?: 0L

        val publishedTime = vr.optJSONObject("publishedTimeText")
            ?.optString("simpleText", "") ?: ""

        return VideoItem(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            thumbnailUrl = thumb,
            uploaderName = channelName,
            uploaderAvatar = channelAvatar,
            uploaderUrl = "https://www.youtube.com/channel/$channelId",
            channelId = channelId,
            duration = durationSec,
            views = views,
            uploadedDate = publishedTime,
            isShort = false
        )
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }
}

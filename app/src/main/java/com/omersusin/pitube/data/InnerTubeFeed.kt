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
            val body = """{"context":{"client":{"clientName":"WEB_CREATOR","clientVersion":"1.20241205.01.00","hl":"en","gl":"US","timeZone":"UTC"}},"browseId":"$browseId"}"""
            val req = Request.Builder()
                .url(INNERTUBE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", rawCookies)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .addHeader("X-YouTube-Client-Name", "62")
                .addHeader("X-YouTube-Client-Version", "1.20241205.01.00")
                .apply { if (authHeader != null) addHeader("Authorization", authHeader) }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Fetching feed for $browseId")
            val resp = client.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) {
                    Log.w(TAG, "HTTP ${r.code} for $browseId")
                    return@withContext emptyList()
                }

                val json = JSONObject(r.body?.string() ?: return@withContext emptyList())
                val videos = mutableListOf<VideoItem>()

                // Parse the browse response
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

                            val videoId = vr.optString("videoId", "")
                            if (videoId.isBlank()) continue

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

                            videos.add(VideoItem(
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
                            ))
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

    private fun parseDuration(text: String): Int {
        val parts = text.split(":")
        return when (parts.size) {
            2 -> parts[0].toIntOrNull() ?: 0 * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }
}

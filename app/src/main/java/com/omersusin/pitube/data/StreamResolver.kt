package com.omersusin.pitube.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object StreamResolver {
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"
    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4DEHLAQ9D_042zB78vy3cA"

    fun fetchPlayer(videoId: String, context: android.content.Context): PlayerData? {
        val client = OkHttpClient()
        val cookies = AuthManager.getCookies(context)

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
            .addHeader("Cookie", cookies)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: return null)
            val streamingData = json.optJSONObject("streamingData") ?: return null
            
            val formats = streamingData.optJSONArray("formats")
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            
            var bestVideoUrl: String? = null
            var bestAudioUrl: String? = null
            
            // Find best video stream
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType")
                    val url = format.optString("url")
                    
                    if (mimeType.startsWith("video/") && bestVideoUrl == null) {
                        bestVideoUrl = url
                    } else if (mimeType.startsWith("audio/") && bestAudioUrl == null) {
                        bestAudioUrl = url
                    }
                    
                    if (bestVideoUrl != null && bestAudioUrl != null) break
                }
            }
            
            // Fallback to regular formats
            if (bestVideoUrl == null && formats != null && formats.length() > 0) {
                bestVideoUrl = formats.getJSONObject(0).optString("url")
            }
            
            if (bestVideoUrl != null || bestAudioUrl != null) {
                PlayerData(bestVideoUrl, bestAudioUrl)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class PlayerData(val videoUrl: String?, val audioUrl: String?)
}

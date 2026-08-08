package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeArrowBranding(
    val titles: List<DeArrowTitle>,
    val thumbnails: List<DeArrowThumbnail>
)

data class DeArrowTitle(
    val title: String,
    val votes: Int,
    val locked: Boolean,
    val original: Boolean
)

data class DeArrowThumbnail(
    val timestamp: Float?,
    val votes: Int,
    val locked: Boolean,
    val original: Boolean
)

object DeArrowService {
    private const val BASE_URL = "https://sponsor.ajay.app/api"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun create() = this

    suspend fun getBranding(videoId: String): DeArrowBranding = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/branding?videoID=$videoId"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext DeArrowBranding(emptyList(), emptyList())
            
            val json = response.body?.string() ?: return@withContext DeArrowBranding(emptyList(), emptyList())
            val obj = JSONObject(json)
            
            val titles = mutableListOf<DeArrowTitle>()
            val titlesArray = obj.optJSONArray("titles")
            if (titlesArray != null) {
                for (i in 0 until titlesArray.length()) {
                    val t = titlesArray.optJSONObject(i) ?: continue
                    titles.add(DeArrowTitle(
                        title = t.optString("title", ""),
                        votes = t.optInt("votes", 0),
                        locked = t.optBoolean("locked", false),
                        original = t.optBoolean("original", false)
                    ))
                }
            }
            
            val thumbnails = mutableListOf<DeArrowThumbnail>()
            val thumbsArray = obj.optJSONArray("thumbnails")
            if (thumbsArray != null) {
                for (i in 0 until thumbsArray.length()) {
                    val t = thumbsArray.optJSONObject(i) ?: continue
                    thumbnails.add(DeArrowThumbnail(
                        timestamp = if (t.has("timestamp") && !t.isNull("timestamp")) t.getDouble("timestamp").toFloat() else null,
                        votes = t.optInt("votes", 0),
                        locked = t.optBoolean("locked", false),
                        original = t.optBoolean("original", false)
                    ))
                }
            }
            
            DeArrowBranding(titles, thumbnails)
        } catch (e: Exception) {
            e.printStackTrace()
            DeArrowBranding(emptyList(), emptyList())
        }
    }
}

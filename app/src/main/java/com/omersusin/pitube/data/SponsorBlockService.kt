package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val segment: List<Double>,
    val category: String,
    val actionType: String
)

object SponsorBlockService {
    private const val BASE_URL = "https://sponsor.ajay.app/api"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun create() = this

    suspend fun getSegments(videoId: String, categories: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/skipSegments?videoID=$videoId&categories=$categories"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val json = response.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(json)
            val segments = mutableListOf<SponsorSegment>()
            
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val segArray = obj.getJSONArray("segment")
                val segment = listOf(segArray.getDouble(0), segArray.getDouble(1))
                segments.add(SponsorSegment(
                    segment = segment,
                    category = obj.getString("category"),
                    actionType = obj.getString("actionType")
                ))
            }
            segments
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

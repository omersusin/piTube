package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RydService {
    private const val BASE_URL = "https://returnyoutubedislikeapi.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun create() = this

    suspend fun getVotes(videoId: String): VoteInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/votes?videoId=$videoId"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val json = response.body?.string() ?: return@withContext null
            val obj = JSONObject(json)
            
            VoteInfo(
                likes = obj.optInt("likes", 0),
                dislikes = obj.optInt("dislikes", 0),
                rating = obj.optDouble("rating", 0.0)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

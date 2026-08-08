package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecommendationEngine {
    suspend fun getRecommendations(context: Context, videoId: String, count: Int = 10): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val streamInfo = PipedApiService.create().getStreams(videoId)
            streamInfo.relatedStreams.take(count)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

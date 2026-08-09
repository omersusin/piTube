package com.omersusin.pitube.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InnerTubeFeed {
    private const val TAG = "InnerTubeFeed"

    data class FeedPage(
        val videos: List<VideoItem>,
        val continuation: String?
    )

    suspend fun fetchFeed(context: Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.browse(context, browseId) ?: return@withContext emptyList()
            val videos = InnerTubeClient.parseFeedItems(root)
            Log.d(TAG, "Fetched ${videos.size} videos from $browseId")
            videos
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed $browseId: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchFeedPage(context: Context, browseId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.browse(context, browseId, continuation = continuation) ?: return@withContext FeedPage(emptyList(), null)
            val videos = InnerTubeClient.parseFeedItems(root)
            val next = if (continuation == null) InnerTubeClient.feedContinuation(root) else null
            FeedPage(videos = videos, continuation = next)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed page $browseId: ${e.message}")
            FeedPage(emptyList(), null)
        }
    }

    suspend fun fetchFeedContinuation(context: Context, browseId: String, continuation: String): FeedPage = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.browse(context, browseId, continuation = continuation) ?: return@withContext FeedPage(emptyList(), null)
            val videos = InnerTubeClient.parseFeedItems(root)
            FeedPage(videos = videos, continuation = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed continuation: ${e.message}")
            FeedPage(emptyList(), null)
        }
    }

    suspend fun fetchTrending(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            // FEtrending browseId has been returning HTTP 400 since mid-2025;
            // fall back to the home feed which works signed-out too
            val root = InnerTubeClient.browse(context, "FEwhat_to_watch") ?: return@withContext emptyList()
            InnerTubeClient.parseFeedItems(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending: ${e.message}")
            emptyList()
        }
    }
}

package com.omersusin.pitube.data

import android.util.Log
import com.omersusin.pitube.data.CookieDownloader.Companion.initWithCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object InnerTubeFeed {
    private const val TAG = "InnerTubeFeed"

    suspend fun fetchFeed(context: android.content.Context, browseId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            initWithCookies(context)
            val url = when (browseId) {
                "FEsubscriptions" -> "https://www.youtube.com/feed/subscriptions"
                "FEwhat_to_watch" -> "https://www.youtube.com"
                else -> "https://www.youtube.com/feed/$browseId"
            }

            Log.d(TAG, "Fetching feed: $url")
            val extractor = ServiceList.YouTube.getFeedExtractor(url)
            extractor.fetchPage()

            extractor.initialPage.items.mapNotNull { item ->
                if (item is StreamInfoItem) {
                    VideoItem(
                        url = item.url ?: "",
                        title = item.name ?: "",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        uploaderName = item.uploaderName ?: "",
                        uploaderAvatar = "",
                        uploaderUrl = item.uploaderUrl ?: "",
                        channelId = "",
                        duration = item.duration.toInt(),
                        views = item.viewCount,
                        uploadedDate = item.textualUploadDate ?: "",
                        isShort = false
                    )
                } else null
            }.also { Log.d(TAG, "Fetched ${it.size} videos from $browseId") }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed $browseId: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

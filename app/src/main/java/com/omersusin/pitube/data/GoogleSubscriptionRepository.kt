package com.omersusin.pitube.data

import android.content.Context
import com.omersusin.pitube.data.CookieDownloader.Companion.initWithCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object GoogleSubscriptionRepository {
    data class GoogleChannel(val name: String, val url: String, val thumbnailUrl: String)
    data class GoogleFeedVideo(val name: String, val url: String, val thumbnailUrl: String, val uploaderName: String, val uploaderUrl: String, val duration: Long, val uploadDate: String)

    suspend fun fetchSubscribedChannels(context: Context): List<GoogleChannel> = withContext(Dispatchers.IO) {
        try {
            initWithCookies(context)
            val extractor = ServiceList.YouTube.getFeedExtractor("https://www.youtube.com/feed/subscriptions")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<org.schabi.newpipe.extractor.subscription.SubscriptionItem>().map { item ->
                GoogleChannel(name = item.name, url = item.url, thumbnailUrl = "")
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchSubscriptionFeed(context: Context): List<GoogleFeedVideo> = withContext(Dispatchers.IO) {
        try {
            initWithCookies(context)
            val extractor = ServiceList.YouTube.getFeedExtractor("https://www.youtube.com/feed/subscriptions")
            extractor.fetchPage()
            extractor.initialPage.items.mapNotNull { item ->
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    GoogleFeedVideo(
                        name = item.name,
                        url = item.url,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        uploaderName = item.uploaderName,
                        uploaderUrl = item.uploaderUrl,
                        duration = item.duration,
                        uploadDate = item.textualUploadDate ?: ""
                    )
                } else null
            }
        } catch (e: Exception) { emptyList() }
    }
}

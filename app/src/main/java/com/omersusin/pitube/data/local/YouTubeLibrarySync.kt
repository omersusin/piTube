package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.subscription.SubscriptionItem

/** Counts of what a sync pass actually pulled in, shown to the user after a manual refresh. */
data class LibrarySyncResult(
    val subscribedChannels: Int = 0,
    val feedVideos: Int = 0,
    val notLoggedIn: Boolean = false,
)

/**
 * Pulls the signed-in Google account's real YouTube subscription feed into Flow's existing
 * local repositories (the same ones the Subscriptions and Home screens already read from),
 * using NewPipe's `/feed/subscriptions` extractor with the session cookie injected via
 * [com.omersusin.pitube.data.repository.NewPipeDownloader].
 *
 * This is strictly read/import: it only copies data from the account into Flow's local
 * storage. It never writes back to the real YouTube account, so re-running it is always safe.
 */
object YouTubeLibrarySync {

    private const val TAG = "YouTubeLibrarySync"

    suspend fun sync(context: Context): LibrarySyncResult = withContext(Dispatchers.IO) {
        if (YouTube.cookie.isNullOrEmpty()) {
            return@withContext LibrarySyncResult(notLoggedIn = true)
        }

        var channels = 0
        var videos = 0

        runCatching {
            val extractor = ServiceList.YouTube
                .getFeedExtractor("https://www.youtube.com/feed/subscriptions")
            extractor.fetchPage()
            val items = extractor.initialPage.items

            val subscribed = items.filterIsInstance<SubscriptionItem>()
            if (subscribed.isNotEmpty()) {
                runCatching {
                    SubscriptionRepository.getInstance(context).subscribeAll(
                        subscribed.mapNotNull { item -> item.toSubscription() }
                    )
                }.onFailure { Log.w(TAG, "Subscription sync failed", it) }
                channels = subscribed.size
            }

            val feedVideos = items
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .mapNotNull { it.toVideo() }
            if (feedVideos.isNotEmpty()) {
                runCatching {
                    HomeFeedCacheRepository(context).saveLastFeed(feedVideos)
                }.onFailure { Log.w(TAG, "Feed cache save failed", it) }
                videos = feedVideos.size
            }
        }.onFailure { Log.w(TAG, "Subscriptions feed fetch failed", it) }

        LibrarySyncResult(channels, videos)
    }

    private fun SubscriptionItem.toSubscription(): ChannelSubscription? {
        val channelId = runCatching {
            url?.takeIf { it.isNotBlank() }?.let { raw ->
                when {
                    raw.contains("/channel/") -> raw.substringAfter("/channel/").substringBefore("/")
                    raw.contains("/@") -> {
                        // Resolve @handle later via channel page fetch; keep handle as id stub
                        raw.substringAfterLast("/")
                    }
                    else -> raw.substringAfterLast("/")
                }
            }
        }.getOrNull()?.takeIf { it.startsWith("UC") || it.startsWith("@") } ?: return null
        return ChannelSubscription(
            channelId = channelId,
            channelName = name ?: return null,
            channelThumbnail = ""
        )
    }

    private fun org.schabi.newpipe.extractor.stream.StreamInfoItem.toVideo(): Video? {
        val rawUrl = url ?: return null
        val videoId = when {
            rawUrl.contains("watch?v=") -> rawUrl.substringAfter("watch?v=").substringBefore("&")
            rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?")
            rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
            else -> rawUrl.substringAfterLast("/")
        }
        if (videoId.isBlank() || videoId.length < 8) return null

        val durationSec = when {
            streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM -> 0
            duration > 0 -> duration.toInt()
            else -> 0
        }
        return Video(
            id = videoId,
            title = name ?: return null,
            channelName = uploaderName ?: "",
            channelId = uploaderUrl?.takeIf { it.isNotBlank() }
                ?.let { raw -> raw.substringAfterLast("/channel/").substringBefore("/") }
                ?.takeIf { it.startsWith("UC") } ?: "",
            thumbnailUrl = thumbnails
                ?.sortedByDescending { it.height }
                ?.firstOrNull()
                ?.url
                ?: "",
            duration = durationSec,
            viewCount = 0,
            uploadDate = textualUploadDate ?: "",
            isMusic = false,
            isLive = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,
            isShort = rawUrl.contains("/shorts/"),
            timestamp = System.currentTimeMillis()
        )
    }
}

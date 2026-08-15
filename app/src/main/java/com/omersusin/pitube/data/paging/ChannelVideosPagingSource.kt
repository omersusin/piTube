package com.omersusin.pitube.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omersusin.pitube.data.model.DistinctKeyTracker
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.model.VideoCollaborator
import com.omersusin.pitube.data.model.hasLikelyCollaborationByline
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.utils.avatarImageIdentityKey
import com.omersusin.pitube.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * PagingSource for loading channel videos with infinite scroll support.
 * Uses NewPipe extractor's pagination mechanism for efficient loading.
 */
class ChannelVideosPagingSource(
    private val channelInfo: ChannelInfo,
    private val videosTab: ListLinkHandler?
) : PagingSource<Page, Video>() {
    
    companion object {
        private const val TAG = "ChannelVideosPaging"
    }

    private val loadedVideoKeys = DistinctKeyTracker()
    
    override fun getRefreshKey(state: PagingState<Page, Video>): Page? {
        // Return null to start from the beginning on refresh
        return null
    }
    
    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, Video> {
        return try {
            withContext(Dispatchers.IO) {
                val page = params.key
                
                Log.d(TAG, "Loading page: ${page?.url ?: "initial"}")
                
                val videos = if (page == null) {
                    // Initial load - get from tab info
                    val tabInfo = ChannelTabInfo.getInfo(NewPipe.getService(0), videosTab)
                    nextPage = tabInfo.nextPage

                    // Convert items to videos (avatar-stack resolution runs
                    // concurrently per item instead of one HTTP call at a time)
                    tabInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo(channelInfo) }
                        .withCollabAvatarStacks()
                } else {
                    // Load more - use the page token
                    val moreItems = ChannelTabInfo.getMoreItems(NewPipe.getService(0), videosTab, page)
                    nextPage = moreItems.nextPage

                    moreItems.items
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo(channelInfo) }
                        .withCollabAvatarStacks()
                }
                
                LoadResult.Page(
                    data = loadedVideoKeys.filter(videos, Video::id),
                    prevKey = null, // Only forward pagination
                    nextKey = nextPage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channel videos", e)
            LoadResult.Error(e)
        }
    }
    
    private fun StreamInfoItem.toVideo(channelInfo: ChannelInfo): Video {
        val videoId = extractVideoId(this.url)
        // Use highest resolution thumbnail for better quality
        val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(
            videoId,
            this.thumbnails.maxByOrNull { it.width }?.url
        )
        val absoluteUploadTimestamp = this.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        val textualDate = this.textualUploadDate?.takeIf { it.isNotBlank() }
        val displayUploadDate = textualDate
            ?: com.omersusin.pitube.utils.formatTimeAgo(this.uploadDate?.offsetDateTime()?.toString())
        val uploadTimestamp = absoluteUploadTimestamp
            ?: parseRelativeUploadDate(textualDate)
            ?: 0L
        
        return Video(
            id = videoId,
            title = this.name,
            thumbnailUrl = thumbnail,
            channelName = this.uploaderName ?: channelInfo.name,
            channelId = channelInfo.id,
            channelThumbnailUrl = channelInfo.avatars.maxByOrNull { it.height }?.url
                ?: channelInfo.avatars.firstOrNull()?.url
                ?: "",
            viewCount = this.viewCount,
            duration = this.duration.toInt().coerceAtLeast(0),
            uploadDate = displayUploadDate,
            timestamp = uploadTimestamp,
            description = "",
            isUpcoming = this.streamType == org.schabi.newpipe.extractor.stream.StreamType.NONE,
            isLive = this.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        )
    }

    private suspend fun List<Video>.withCollabAvatarStacks(): List<Video> {
        val pending =
            filter { video ->
                if (video.collaborators.size > 1) return@filter false
                video.channelThumbnailUrls.size < 2 && video.channelName.hasLikelyCollaborationByline()
            }
        // Resolve collaborator stacks concurrently instead of one HTTP call per
        // item at a time (each has its own 4s timeout, so N items ran up to N*4s
        // serially before this).
        val collaboratorsById = ConcurrentHashMap<String, List<VideoCollaborator>>()
        coroutineScope {
            pending
                .map { video ->
                    async {
                        video.id to
                            withTimeoutOrNull(4_000L) {
                                YouTube.videoCollaborators(video.id).getOrNull()
                            }.orEmpty()
                    }
                }
                .awaitAll()
                .forEach { (id, collaborators) -> collaboratorsById[id] = collaborators }
        }
        return map { video ->
            val collaborators = collaboratorsById[video.id].orEmpty()
            if (collaborators.isEmpty() && video.collaborators.isEmpty()) {
                video
            } else {
                val stack =
                    collaborators
                        .map { it.thumbnailUrl }
                        .filter { it.isNotBlank() }
                        .ifEmpty {
                            video.channelThumbnailUrls.takeIf { it.size > 1 }
                                ?: withTimeoutOrNull(4_000L) {
                                    YouTube.videoAvatarStack(video.id).getOrNull()
                                }.orEmpty()
                        }
                if (stack.size <= 1 && collaborators.size <= 1) return@map video
                val merged =
                    (stack + video.channelThumbnailUrls + video.channelThumbnailUrl)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.avatarImageIdentityKey() }
                        .take(2)
                if (merged.size > 1 || collaborators.size > 1) {
                    video.copy(
                        channelName =
                            collaborators
                                .map { it.name }
                                .filter { it.isNotBlank() }
                                .takeIf { it.size > 1 }
                                ?.joinToString(" and ")
                                ?: video.channelName,
                        channelThumbnailUrl = merged.firstOrNull() ?: video.channelThumbnailUrl,
                        channelThumbnailUrls = merged.ifEmpty { video.channelThumbnailUrls },
                        collaborators = collaborators.ifEmpty { video.collaborators },
                    )
                } else {
                    video
                }
            }
        }
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
    }

    private fun parseRelativeUploadDate(text: String?): Long? {
        val normalized = text?.lowercase(Locale.US)
            ?.replace("streamed", "")
            ?.replace("premiered", "")
            ?.replace("live", "")
            ?.replace("ago", "")
            ?.trim()
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
        if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val value = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
        val unitMillis = when {
            normalized.contains("second") || normalized.endsWith("s") -> 1_000L
            normalized.contains("minute") || normalized.endsWith("m") -> 60_000L
            normalized.contains("hour") || normalized.endsWith("h") -> 3_600_000L
            normalized.contains("day") || normalized.endsWith("d") -> 86_400_000L
            normalized.contains("week") || normalized.endsWith("w") -> 7L * 86_400_000L
            normalized.contains("month") || normalized.endsWith("mo") -> 30L * 86_400_000L
            normalized.contains("year") || normalized.endsWith("y") -> 365L * 86_400_000L
            else -> return null
        }

        return System.currentTimeMillis() - (value * unitMillis)
    }
}

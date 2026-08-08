package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object ChannelResolver {
    data class ChannelPage(val name: String, val avatarUrl: String?, val videos: List<VideoItem>)

    suspend fun resolve(channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val url = if (channelIdOrUrl.startsWith("http")) channelIdOrUrl else "https://www.youtube.com/channel/$channelIdOrUrl"
            val ex = ServiceList.YouTube.getChannelExtractor(url)
            ex.fetchPage()
            val name = runCatching { ex.name }.getOrNull() ?: ""
            val avatar = runCatching { ex.avatarUrl }.getOrNull()
            val items = runCatching { ex.initialPage.items }.getOrNull() ?: emptyList()
            val videos = items.filterIsInstance<StreamInfoItem>().map { it ->
                VideoItem(
                    url = it.url ?: "",
                    title = it.name ?: "",
                    thumbnailUrl = runCatching { it.thumbnails?.firstOrNull()?.url }.getOrNull(),
                    uploaderName = it.uploaderName ?: name,
                    uploaderAvatar = avatar,
                    duration = it.duration.toInt(),
                    views = runCatching { it.viewCount }.getOrNull() ?: 0L,
                    uploadedDate = it.textualUploadDate ?: "",
                    isShort = runCatching { it.isShortFormContent }.getOrNull() ?: false
                )
            }
            ChannelPage(name, avatar, videos)
        } catch (e: Exception) { e.printStackTrace(); null }
    }
}

package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ChannelPage(val name: String, val avatarUrl: String?, val videos: List<VideoItem>)

object ChannelResolver {
    suspend fun resolve(channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val url = toUrl(channelIdOrUrl)
            val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
            val avatar = info.avatars?.firstOrNull()?.url
            val videos = info.relatedItems?.mapNotNull { item ->
                (item as? StreamInfoItem)?.let { s ->
                    VideoItem(
                        url = s.url ?: return@let null,
                        title = s.name ?: "",
                        thumbnailUrl = s.thumbnails?.firstOrNull()?.url,
                        uploaderName = s.uploaderName ?: info.name ?: "",
                        uploaderAvatar = avatar,
                        uploaderUrl = s.uploaderUrl ?: url,
                        duration = s.duration.toInt(),
                        views = s.viewCount,
                        uploadedDate = s.textualUploadDate,
                        isShort = s.isShortFormContent
                    )
                }
            } ?: emptyList()
            ChannelPage(info.name ?: "", avatar, videos)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun toUrl(input: String): String = when {
        input.startsWith("http") -> input
        input.startsWith("@") || input.startsWith("user/") || input.startsWith("c/") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/channel/$input"
    }
}

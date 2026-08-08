package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChannelResolver {
    data class ChannelPage(val name: String, val avatarUrl: String?, val videos: List<VideoItem>)

    suspend fun resolve(channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val id = if (channelIdOrUrl.contains("/channel/")) channelIdOrUrl.substringAfter("/channel/") 
                     else if (channelIdOrUrl.contains("/@")) channelIdOrUrl.substringAfter("/@")
                     else if (channelIdOrUrl.startsWith("http")) channelIdOrUrl.substringAfterLast("/") 
                     else channelIdOrUrl
            val channel = PipedApiService.create().getChannel(id)
            ChannelPage(
                name = channel.name ?: "",
                avatarUrl = channel.avatarUrl,
                videos = channel.relatedStreams.filter { !it.isShort }
            )
        } catch (e: Exception) { 
            e.printStackTrace()
            null 
        }
    }
}

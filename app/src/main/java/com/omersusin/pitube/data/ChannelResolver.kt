package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChannelPage(
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String? = null,
    val handle: String? = null,
    val subscriberCountText: String? = null,
    val videos: List<VideoItem>
)

object ChannelResolver {
    suspend fun resolve(context: Context, channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val id = InnerTubeClient.resolveChannelId(context, channelIdOrUrl)
                ?: return@withContext null
            val profile = InnerTubeClient.channelProfile(context, id)
                ?: return@withContext null
            val videos = InnerTubeClient.channelVideos(context, profile)
            ChannelPage(
                name = profile.name,
                avatarUrl = profile.avatarUrl,
                bannerUrl = profile.bannerUrl,
                handle = profile.handle,
                subscriberCountText = profile.subscriberCountText,
                videos = videos
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

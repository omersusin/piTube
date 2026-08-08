package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChannelResolver {
    data class ChannelPage(val name: String, val avatarUrl: String?, val videos: List<VideoItem>)

    suspend fun resolve(channelIdOrUrl: String): ChannelPage? = withContext(Dispatchers.IO) {
        val raw = channelIdOrUrl.trim()
        val id = when {
            raw.contains("/channel/") -> raw.substringAfter("/channel/").trim('/')
            raw.startsWith("UC") -> raw
            else -> raw
        }
        // 1) Piped
        if (id.startsWith("UC")) {
            try {
                val ch = PipedApiService.create().getChannel(id)
                if (ch.relatedStreams.isNotEmpty()) {
                    return@withContext ChannelPage(ch.name ?: "", ch.avatarUrl, ch.relatedStreams.filter { !it.isShort })
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        // 2) InnerTube browse (works without auth)
        if (id.startsWith("UC")) {
            InnerTubeFeed.browseChannel(id)
        } else null
    }
}

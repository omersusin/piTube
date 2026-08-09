package com.omersusin.pitube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GoogleSubscriptionRepository {
    data class GoogleChannel(val name: String, val url: String, val thumbnailUrl: String)
    data class GoogleFeedVideo(val name: String, val url: String, val thumbnailUrl: String, val uploaderName: String, val uploaderUrl: String, val duration: Long, val uploadDate: String)

    suspend fun fetchSubscribedChannels(context: Context): List<GoogleChannel> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.browse(context, "FEsubscriptions") ?: return@withContext emptyList()
            val renderers = mutableListOf<JSONObject>()
            InnerTubeClient.findRenderers(root, "gridChannelRenderer", renderers)
            InnerTubeClient.findRenderers(root, "channelRenderer", renderers)
            renderers.mapNotNull { r ->
                val id = r.optString("channelId").takeIf { it.isNotBlank() }
                    ?: r.optString("externalId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = r.optJSONObject("title")?.optString("simpleText")
                    ?: r.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: return@mapNotNull null
                val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                GoogleChannel(
                    name = name,
                    url = "https://www.youtube.com/channel/$id",
                    thumbnailUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                )
            }.distinctBy { it.url }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchSubscriptionFeed(context: Context): List<GoogleFeedVideo> = withContext(Dispatchers.IO) {
        try {
            val root = InnerTubeClient.browse(context, "FEsubscriptions") ?: return@withContext emptyList()
            InnerTubeClient.parseFeedItems(root).mapNotNull { v ->
                GoogleFeedVideo(
                    name = v.title,
                    url = v.url,
                    thumbnailUrl = v.thumbnailUrl ?: "",
                    uploaderName = v.uploaderName,
                    uploaderUrl = v.uploaderUrl ?: "",
                    duration = v.duration.toLong(),
                    uploadDate = v.uploadedDate ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}

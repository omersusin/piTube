package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object StreamResolver {
    data class Resolved(
        val playUrl: String?,
        val downloadUrl: String?,
        val audioUrl: String?,
        val title: String,
        val description: String,
        val uploader: String,
        val uploaderUrl: String
    )

    private fun qualityOf(res: String?) = res?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    suspend fun resolve(videoId: String): Resolved? = withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val withAudio = runCatching { extractor.videoStreams }.getOrNull() ?: emptyList()
            val videoOnly = runCatching { extractor.videoOnlyStreams }.getOrNull() ?: emptyList()
            val audios = runCatching { extractor.audioStreams }.getOrNull() ?: emptyList()

            val progressive = withAudio.maxByOrNull { qualityOf(it.resolution) }
            val bestOnly = videoOnly.maxByOrNull { qualityOf(it.resolution) }
            val bestAudio = audios.maxByOrNull { it.averageBitrate }

            Resolved(
                playUrl = progressive?.content,
                downloadUrl = bestOnly?.content ?: progressive?.content,
                audioUrl = bestAudio?.content,
                title = runCatching { extractor.name }.getOrNull() ?: "",
                description = runCatching { extractor.description?.content }.getOrNull() ?: "",
                uploader = runCatching { extractor.uploaderName }.getOrNull() ?: "",
                uploaderUrl = runCatching { extractor.uploaderUrl }.getOrNull() ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

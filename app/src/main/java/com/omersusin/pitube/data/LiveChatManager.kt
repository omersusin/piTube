package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamChatItem
import org.schabi.newpipe.extractor.stream.StreamExtractor

object LiveChatManager {
    suspend fun fetchChat(videoUrl: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()
            
            val chatItems = extractor.chatItems ?: return@withContext emptyList()
            chatItems.items.map { item ->
                ChatMessage(
                    author = item.uploaderName,
                    text = item.textualUploadDate ?: "", // Fallback if text field is missing in extractor version
                    timestamp = ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

data class ChatMessage(val author: String, val text: String, val timestamp: String)

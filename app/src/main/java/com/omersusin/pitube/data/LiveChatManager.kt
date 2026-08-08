package com.omersusin.pitube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LiveChatManager {
    suspend fun fetchChat(videoUrl: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        // Live chat requires complex YouTube WebSocket handling.
        // Stubbed for now to ensure stable builds.
        emptyList()
    }
}

data class ChatMessage(val author: String, val text: String, val timestamp: String)

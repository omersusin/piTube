package com.omersusin.pitube.ui.recognition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands a voice/song query from the recognition modal to the (possibly not
 * yet composed) SearchScreen, without coupling the modal to the NavHost.
 * FlowApp: submit(query) → closes modal + navigate("search").
 * SearchScreen: observes [pendingQuery], runs [search], then [consume].
 */
object RecognitionSearchBridge {
    private val _pendingQuery = MutableStateFlow<String?>(null)
    val pendingQuery: StateFlow<String?> = _pendingQuery.asStateFlow()

    fun submit(query: String) {
        _pendingQuery.value = query.trim()
    }

    fun consume() {
        _pendingQuery.value = null
    }
}
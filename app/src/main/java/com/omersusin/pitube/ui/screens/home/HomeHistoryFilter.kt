package com.omersusin.pitube.ui.screens.home

import com.omersusin.pitube.data.local.VideoHistoryEntry
import com.omersusin.pitube.data.local.WatchedThreshold

internal data class HomeHistoryFilterResult(
    val watchedVideoIds: Set<String>,
    val continueWatchingVideos: List<VideoHistoryEntry>
)

internal fun filterHomeHistory(
    history: List<VideoHistoryEntry>,
    hideWatchedVideos: Boolean,
    watchedThreshold: WatchedThreshold,
    continueWatchingEnabled: Boolean
): HomeHistoryFilterResult {
    val watchedVideoIds = if (hideWatchedVideos) {
        history.asSequence()
            .filter { watchedThreshold.isWatched(it.position, it.duration) }
            .mapTo(HashSet()) { it.videoId }
    } else {
        emptySet()
    }

    val continueWatchingVideos = if (continueWatchingEnabled) {
        history.asSequence()
            .filter { !it.isShort && it.progressPercentage >= 3f }
            .filter { entry ->
                if (hideWatchedVideos) {
                    entry.videoId !in watchedVideoIds
                } else {
                    entry.progressPercentage <= 90f
                }
            }
            .sortedByDescending { it.timestamp }
            .take(20)
            .toList()
    } else {
        emptyList()
    }

    return HomeHistoryFilterResult(watchedVideoIds, continueWatchingVideos)
}

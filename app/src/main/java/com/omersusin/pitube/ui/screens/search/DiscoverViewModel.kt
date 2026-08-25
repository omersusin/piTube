package com.omersusin.pitube.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.SessionManager
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.ViewHistory
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Powers the search landing ("Discover") surface with Koda's explore stack:
 * signed-in personal feed → watch-history taste lane → trending.
 */
@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val playerPreferences: PlayerPreferences,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        enum class Source { LOADING, PERSONALIZED, TASTE, TRENDING }

        data class DiscoverState(
            val isLoading: Boolean = true,
            val videos: List<Video> = emptyList(),
            val source: Source = Source.LOADING,
            val endReached: Boolean = false,
        )

        private val _state = MutableStateFlow(DiscoverState())
        val state = _state.asStateFlow()

        private var continuation: String? = null
        private var loadJob: kotlinx.coroutines.Job? = null

        init {
            refresh()
        }

        fun refresh() {
            loadJob?.cancel()
            continuation = null
            loadJob =
                viewModelScope.launch {
                    _state.value = DiscoverState(isLoading = true)
                    // Race the async session restore so the signed-in verdict is
                    // computed from the restored cookie, not the pre-restore null.
                    withTimeoutOrNull(1_500L) { SessionManager.restored.await() }
                    val signedIn = !com.omersusin.pitube.innertube.YouTube.cookie.isNullOrBlank()

                    var videos = emptyList<Video>()
                    var source = Source.TRENDING

                    if (signedIn) {
                        videos = fetchPersonalizedFirstPage()
                        if (videos.isNotEmpty()) source = Source.PERSONALIZED
                    }

                    if (videos.size < 5) {
                        val taste = withTimeoutOrNull(10_000L) { fetchTasteVideos() }.orEmpty()
                        if (taste.size > videos.size) {
                            videos = (taste + videos).distinctBy { it.id }
                            source = Source.TASTE
                        }
                    }

                    if (videos.isEmpty()) {
                        videos =
                            runCatching {
                                repository.getTrendingVideos(playerPreferences.trendingRegion.first()).first
                            }.getOrElse { emptyList() }
                        source = Source.TRENDING
                    }

                    _state.value =
                        DiscoverState(
                            isLoading = false,
                            videos = videos.distinctBy { it.id }.take(30),
                            source = source,
                            endReached = continuation == null && source != Source.PERSONALIZED || videos.isEmpty(),
                        )
                }
        }

        /** Personalized lane pages via the FEwhat_to_watch rich-grid continuation. */
        fun loadMore() {
            val token = continuation ?: return
            if (_state.value.endReached || _state.value.isLoading) return
            viewModelScope.launch {
                val next =
                    runCatching {
                        com.omersusin.pitube.innertube.YouTube.personalizedFeedContinuation(token).getOrNull()
                    }.getOrNull()
                if (next == null || next.videos.isEmpty()) {
                    continuation = null
                    _state.value = _state.value.copy(endReached = true)
                    return@launch
                }
                continuation = next.continuation
                _state.value =
                    _state.value.copy(
                        videos = (_state.value.videos + next.videos).distinctBy { it.id },
                        endReached = next.continuation == null,
                    )
            }
        }

        private suspend fun fetchPersonalizedFirstPage(): List<Video> {
            val primary =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.omersusin.pitube.innertube.YouTube.personalizedFeed().getOrNull()
                }
            val firstLane =
                primary?.videos?.also { continuation = primary.continuation }.orEmpty()
            if (firstLane.isNotEmpty()) return firstLane

            // FEmusic_home / WEB_REMIX second lane when www-WEB is bot-walled empty.
            val fallback =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.omersusin.pitube.innertube.YouTube.musicHomeFeed().getOrNull()
                }
            return fallback?.videos.orEmpty().also { continuation = fallback?.continuation }
        }

        private suspend fun fetchTasteVideos(): List<Video> {
            val viewHistory = ViewHistory.getInstance(context)
            val seedIds =
                viewHistory.getLatestUnfinishedVideo()
                    ?.let { listOf(it.videoId) }
                    .orEmpty()
                    .ifEmpty { viewHistory.getAllHistoryIds().take(6).toList() }
            return seedIds.take(4)
                .flatMap { seedId ->
                    runCatching { repository.getRelatedVideos(seedId) }.getOrElse { emptyList() }
                }
        }
    }

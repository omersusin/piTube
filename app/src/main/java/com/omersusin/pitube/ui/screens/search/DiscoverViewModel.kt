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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

        /** User-ordered / user-hidden Discover topic chips (see PlayerPreferences). */
        val chipOrder: kotlinx.coroutines.flow.StateFlow<List<String>> =
            playerPreferences.discoverChipOrder
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())
        val chipHidden: kotlinx.coroutines.flow.StateFlow<Set<String>> =
            playerPreferences.discoverChipHidden
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())

        private var continuation: String? = null
        private var loadJob: kotlinx.coroutines.Job? = null

        // Per-lane pagination state (only one lane is ever active per refresh).
        private var historyIds: List<String> = emptyList()
        private var tasteSeedOffset = 0
        private var trendingNextPage: org.schabi.newpipe.extractor.Page? = null

        init {
            refresh()
        }

        fun refresh() {
            loadJob?.cancel()
            continuation = null
            tasteSeedOffset = 0
            trendingNextPage = null
            loadJob =
                viewModelScope.launch {
                    _state.value = DiscoverState(isLoading = true)
                    // getAllHistoryIds is suspend + returns a Set — normalize here.
                    historyIds =
                        ViewHistory.getInstance(context)
                            .getAllHistoryIds()
                            .toList()
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
                        runCatching {
                            val (trending, nextPage) =
                                repository.getTrendingVideos(playerPreferences.trendingRegion.first())
                            videos = trending
                            trendingNextPage = nextPage
                        }
                        source = Source.TRENDING
                    }

                    _state.value =
                        DiscoverState(
                            isLoading = false,
                            videos = videos.distinctBy { it.id }.take(30),
                            source = source,
                            endReached = !hasMoreInLane(source),
                        )
                }
        }

        /** Whether the ACTIVE lane can still produce another page. */
        private fun hasMoreInLane(source: Source): Boolean =
            when (source) {
                Source.LOADING -> false
                Source.PERSONALIZED -> continuation != null && _state.value.videos.isNotEmpty()
                Source.TASTE -> tasteSeedOffset < historyIds.size
                Source.TRENDING -> trendingNextPage != null
            }

        /** Page the ACTIVE lane: personalized continuation, more taste seeds, or trending pages. */
        fun loadMore() {
            val source = _state.value.source
            if (source == Source.LOADING || _state.value.endReached || _state.value.isLoading) return
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true)
                when (source) {
                    Source.PERSONALIZED -> {
                        val token = continuation
                        if (token == null) {
                            _state.value = _state.value.copy(endReached = true, isLoading = false)
                            return@launch
                        }
                        val next =
                            runCatching {
                                com.omersusin.pitube.innertube.YouTube.personalizedFeedContinuation(token).getOrNull()
                            }.getOrNull()
                        if (next == null || next.videos.isEmpty()) {
                            continuation = null
                            _state.value = _state.value.copy(endReached = true, isLoading = false)
                            return@launch
                        }
                        continuation = next.continuation
                        _state.value =
                            _state.value.copy(
                                videos = (_state.value.videos + next.videos).distinctBy { it.id },
                                endReached = !hasMoreInLane(source),
                                isLoading = false,
                            )
                    }

                    Source.TASTE -> appendTastePage()

                    Source.TRENDING -> {
                        val page = trendingNextPage
                        if (page == null) {
                            _state.value = _state.value.copy(endReached = true, isLoading = false)
                            return@launch
                        }
                        val result =
                            runCatching {
                                repository.getTrendingVideos(playerPreferences.trendingRegion.first(), page)
                            }.getOrElse { emptyList<Video>() to null }
                        trendingNextPage = result.second
                        _state.value =
                            _state.value.copy(
                                videos = (_state.value.videos + result.first).distinctBy { it.id },
                                endReached = !hasMoreInLane(source),
                                isLoading = false,
                            )
                    }

                    Source.LOADING -> {}
                }
            }
        }

        private suspend fun fetchPersonalizedFirstPage(): List<Video> {
            val primary =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.omersusin.pitube.innertube.YouTube.personalizedFeed().getOrNull()
                }
            return primary?.videos.also { continuation = primary?.continuation }.orEmpty()
        }

        /**
         * Taste lane. Seeds are always BLENDED — the latest unfinished video
         * alone would flood the whole lane with one artist's content (reads as
         * "the feed followed my last search"), so it only ever contributes one
         * seed among several history picks. A per-channel cap then keeps any
         * single channel from dominating the mixed result.
         *
         * [appendPage] false = first batch (uses the blended head); true =
         * next seed window for infinite scroll.
         */
        private suspend fun fetchTasteVideos(): List<Video> {
            val seeds = currentTasteSeeds()
            val collected =
                seeds.flatMap { seedId ->
                    runCatching { repository.getRelatedVideos(seedId) }.getOrElse { emptyList() }
                }
            val perChannel = mutableMapOf<String, Int>()
            return collected.filter { video ->
                val key = video.channelId.ifBlank { video.id }
                val count = perChannel.getOrDefault(key, 0)
                perChannel[key] = count + 1
                count < MAX_TASTE_VIDEOS_PER_CHANNEL
            }
        }

        /** The next SEEDS_PER_PAGE untried history ids (latest unfinished included up front). */
        private suspend fun currentTasteSeeds(): List<String> {
            val viewHistory = ViewHistory.getInstance(context)
            val latest = viewHistory.getLatestUnfinishedVideo()?.videoId
            return buildList {
                if (tasteSeedOffset == 0) latest?.let(::add)
                addAll(historyIds)
            }.distinct().drop(tasteSeedOffset).take(SEEDS_PER_PAGE)
                    .also { tasteSeedOffset += SEEDS_PER_PAGE }
        }

        /** Next taste seed-window, appended with the same diversity cap. */
        private suspend fun appendTastePage() {
            val next = fetchTasteVideos()
            if (next.isEmpty()) {
                _state.value = _state.value.copy(endReached = true, isLoading = false)
                return
            }
            _state.value =
                _state.value.copy(
                    videos = (_state.value.videos + next).distinctBy { it.id },
                    endReached = !hasMoreInLane(Source.TASTE),
                    isLoading = false,
                )
        }

        private companion object {
            /** History seeds consumed per taste page. */
            const val SEEDS_PER_PAGE = 4

            /** One channel can't own more than this slice of the taste lane. */
            const val MAX_TASTE_VIDEOS_PER_CHANNEL = 5
        }
    }

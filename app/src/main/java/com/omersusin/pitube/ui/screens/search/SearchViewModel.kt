@file:Suppress("ktlint:standard:backing-property-naming")

package com.omersusin.pitube.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import com.omersusin.pitube.data.local.ContentType
import com.omersusin.pitube.data.local.SearchFilter
import com.omersusin.pitube.data.local.PlaylistRepository
import com.omersusin.pitube.data.model.Playlist
import com.omersusin.pitube.data.paging.SearchPagingSource
import com.omersusin.pitube.data.paging.SearchResultItem
import com.omersusin.pitube.data.repository.YouTubeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ─────────────────────────────────────────────────────────────────

/** Experimental YouTube Music result categories (opt-in preference gated). */
enum class MusicCategory {
    SONGS,
    ARTISTS,
}

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilter? = null,
    val musicCategory: MusicCategory? = null,
)

/** Paged YouTube Music results for the experimental Songs/Artists tabs. */
data class MusicResults(
    val isLoading: Boolean = false,
    val error: Boolean = false,
    val songs: List<com.omersusin.pitube.data.model.Video> = emptyList(),
    val artists: List<com.omersusin.pitube.data.model.Channel> = emptyList(),
    val endReached: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val playlistRepository: PlaylistRepository,
        playerPreferences: com.omersusin.pitube.data.local.PlayerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        /** Opt-in flag for the YT Music categories — off means zero music-host traffic. */
        val musicCategoriesEnabled: StateFlow<Boolean> =
            playerPreferences.musicSearchCategoriesEnabled
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

        private val _musicResults = MutableStateFlow(MusicResults())
        val musicResults: StateFlow<MusicResults> = _musicResults.asStateFlow()
        private var musicContinuation: String? = null

        private val _savedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())

        /** Ids of remote playlists saved to the library for the active profile. */
        val savedPlaylistIds: StateFlow<Set<String>> = _savedPlaylistIds.asStateFlow()

        init {
            viewModelScope.launch {
                playlistRepository.getSavedVideoPlaylistsFlow().collect { playlists ->
                    _savedPlaylistIds.value = playlists.map { it.id }.toSet()
                }
            }
        }

        /**
         * Internal trigger: emitting a new value here restarts the pager from page 0.
         * Holds (query, contentFilters) so the PagingSource gets fresh arguments.
         */
        private data class SearchKey(
            val query: String,
            val contentFilters: List<String>,
            val searchFilter: SearchFilter?,
        )

        private val _searchKey = MutableStateFlow<SearchKey?>(null)

        /**
         * flatMapLatest restarts the pager whenever [_searchKey] changes (new search
         * or filter change), and cachedIn survives configuration changes.
         */
        val searchResults: Flow<PagingData<SearchResultItem>> =
            _searchKey
                .filterNotNull()
                .filter { it.query.isNotBlank() }
                .flatMapLatest { key ->
                    Pager(
                        config =
                            PagingConfig(
                                pageSize = 20,
                                prefetchDistance = 6,
                                enablePlaceholders = false,
                                initialLoadSize = 20,
                            ),
                        pagingSourceFactory = { SearchPagingSource(key.query, key.contentFilters, key.searchFilter) },
                    ).flow
                }.cachedIn(viewModelScope)

        // ── public API ────────────────────────────────────────────────────────────

        fun search(
            query: String,
            filters: SearchFilter? = null,
        ) {
            if (query.isBlank()) {
                _uiState.value = SearchUiState()
                _searchKey.value = null
                _musicResults.value = MusicResults()
                musicContinuation = null
                return
            }
            val effectiveFilters = filters ?: SearchFilter()
            _uiState.value = SearchUiState(query = query, filters = effectiveFilters)
            _searchKey.value = SearchKey(query, buildContentFilters(effectiveFilters), effectiveFilters)
            if (musicCategoriesEnabled.value) {
                reloadMusicResults()
            }
        }

        fun updateFilters(filters: SearchFilter) {
            val currentQuery = _uiState.value.query
            _uiState.value = _uiState.value.copy(filters = filters, musicCategory = null)
            if (currentQuery.isNotBlank()) {
                _searchKey.value = SearchKey(currentQuery, buildContentFilters(filters), filters)
            }
        }

        /** Switch between the experimental Songs/Artists views (null = regular results). */
        fun selectMusicCategory(category: MusicCategory?) {
            _uiState.value = _uiState.value.copy(musicCategory = category)
            if (category != null && _musicResults.value.songs.isEmpty() && _musicResults.value.artists.isEmpty()) {
                reloadMusicResults()
            }
        }

        fun loadMoreMusicResults() {
            if (musicContinuation == null || _musicResults.value.isLoading) return
            fetchMusicPage()
        }

        private fun reloadMusicResults() {
            musicContinuation = null
            _musicResults.value = MusicResults(isLoading = true)
            fetchMusicPage()
        }

        private fun fetchMusicPage() {
            val query = _uiState.value.query
            if (query.isBlank() || !musicCategoriesEnabled.value) return
            viewModelScope.launch {
                val page =
                    runCatching {
                        com.omersusin.pitube.innertube.YouTube.musicSearch(query, musicContinuation).getOrNull()
                    }.getOrNull()
                if (page == null) {
                    _musicResults.value =
                        _musicResults.value.copy(
                            isLoading = false,
                            error = true,
                            endReached = true,
                        )
                    return@launch
                }
                musicContinuation = page.continuation
                _musicResults.value =
                    MusicResults(
                        songs = (_musicResults.value.songs + page.songs).distinctBy { it.id },
                        artists = (_musicResults.value.artists + page.artists).distinctBy { it.id },
                        endReached = page.continuation == null,
                    )
            }
        }

        fun clearSearch() {
            _uiState.value = SearchUiState()
            _searchKey.value = null
            _musicResults.value = MusicResults()
            musicContinuation = null
        }

        fun hasActiveFilters(filters: SearchFilter?): Boolean {
            if (filters == null) return false
            return filters.contentType != ContentType.ALL ||
                filters.duration != com.omersusin.pitube.data.local.Duration.ANY ||
                filters.uploadDate != com.omersusin.pitube.data.local.UploadDate.ANY ||
                filters.sortType != com.omersusin.pitube.data.local.SortType.RELEVANCE
        }

        suspend fun togglePlaylistSave(playlist: Playlist): Boolean =
            if (playlistRepository.isExternalPlaylistSaved(playlist.id)) {
                playlistRepository.unsaveExternalPlaylist(playlist.id)
                false
            } else {
                playlistRepository.saveExternalVideoPlaylist(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    thumbnailUrl = playlist.thumbnailUrl,
                )
                true
            }

        suspend fun getSearchSuggestions(query: String): List<String> {
            if (query.length < 2) return emptyList()
            return try {
                repository.getSearchSuggestions(query)
            } catch (_: Exception) {
                emptyList()
            }
        }

        // ── helpers ───────────────────────────────────────────────────────────────

        private fun buildContentFilters(filters: SearchFilter?): List<String> {
            val list = mutableListOf<String>()
            if (filters == null) return list

            when (filters.contentType) {
                ContentType.VIDEOS -> {
                    list.add("videos")
                }

                ContentType.CHANNELS -> {
                    list.add("channels")
                }

                ContentType.PLAYLISTS -> {
                    list.add("playlists")
                }

                ContentType.LIVE -> {
                    list.add("videos")
                }

                else -> {}
            }

            return list
        }
    }

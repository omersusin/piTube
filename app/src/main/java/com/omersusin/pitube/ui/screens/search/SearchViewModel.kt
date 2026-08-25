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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    /** The searched artist itself (Top-result card) — shown hero-style above the related list. */
    val mainArtist: com.omersusin.pitube.data.model.Channel? = null,
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

        /** Filter chip the current music page was fetched with (null = unfiltered). */
        private var musicFilterParam: String? = null
        private var refineJob: Job? = null
        private var artistsEnriched = false

        private companion object {
            /** Static YT Music search-filter tokens ("Songs" / "Artists" chips). */
            const val MUSIC_SONGS_FILTER_PARAM = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
            const val MUSIC_ARTISTS_FILTER_PARAM = "EgWKAQIGAWoKEAkQChAFEAMQBA=="

            /** Audience figure a look-alike channel needs to count as legit (collabs). */
            const val COLLAB_AUDIENCE_FLOOR = 50_000L

            /** Up-front artist pages until the list doesn't look broken/empty-ish. */
            const val MIN_ARTIST_RESULTS = 12

            /** "3.57M monthly audience" / "23 subscribers" / "12 B abone" → number+suffix. */
            val AUDIENCE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(mn|mln|bin|[kmb])?""", RegexOption.IGNORE_CASE)
        }

        /** Page-1 filtered fetch used by [ensureRefined]. */
        private suspend fun fetchPage(
            filterParam: String?,
        ): com.omersusin.pitube.innertube.pages.MusicSearchPage? =
            runCatching {
                com.omersusin.pitube.innertube.YouTube.musicSearch(
                    _uiState.value.query,
                    filterParams = filterParam,
                ).getOrNull()
            }.getOrNull()

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
            resetMusicState()
            if (query.isBlank()) {
                _uiState.value = SearchUiState()
                _searchKey.value = null
                return
            }
            val effectiveFilters = filters ?: SearchFilter()
            _uiState.value = SearchUiState(query = query, filters = effectiveFilters)
            _searchKey.value = SearchKey(query, buildContentFilters(effectiveFilters), effectiveFilters)
        }

        fun updateFilters(filters: SearchFilter) {
            val currentQuery = _uiState.value.query
            _uiState.value = _uiState.value.copy(filters = filters, musicCategory = null)
            resetMusicState()
            if (currentQuery.isNotBlank()) {
                _searchKey.value = SearchKey(currentQuery, buildContentFilters(filters), filters)
            }
        }

        /** Fresh query/filter → drop filtered-page bookkeeping and background jobs. */
        private fun resetMusicState() {
            refineJob?.cancel()
            refineJob = null
            musicContinuation = null
            musicFilterParam = null
            artistsEnriched = false
            _musicResults.value = MusicResults()
        }

        /** Switch between the experimental Songs/Artists views (null = regular results). */
        fun selectMusicCategory(category: MusicCategory?) {
            _uiState.value = _uiState.value.copy(musicCategory = category)
            if (category == null) return
            val target = paramFor(category)
            if (_musicResults.value.songs.isEmpty() && _musicResults.value.artists.isEmpty()) {
                // Lazy: nothing is fetched for regular searches — the first
                // request goes STRAIGHT to the server-filtered chip results:
                // one request, clean list immediately (the unfiltered mixed
                // response would pollute Songs with videos/episodes and Artists
                // with fan channels). The hero card arrives in the background
                // via [fetchMainArtistCard].
                musicFilterParam = target
                reloadMusicResults()
            } else if (musicFilterParam != target) {
                ensureRefined(category)
            } else if (category == MusicCategory.ARTISTS) {
                viewModelScope.launch { growArtists() }
            }
        }

        private fun paramFor(category: MusicCategory): String =
            when (category) {
                MusicCategory.SONGS -> MUSIC_SONGS_FILTER_PARAM
                MusicCategory.ARTISTS -> MUSIC_ARTISTS_FILTER_PARAM
            }

        /**
         * Swap a category's list for the server-filtered chip results when the
         * current page was fetched under a different filter (tab switch after
         * another category was open). The other list and the hero are kept.
         */
        private fun ensureRefined(category: MusicCategory) {
            val target = paramFor(category)
            if (!categoryActive(category)) return
            if (musicFilterParam == target) {
                if (category == MusicCategory.ARTISTS) growArtistsWithEnrichment()
                return
            }
            refineJob?.cancel()
            refineJob = viewModelScope.launch {
                val page = fetchPage(target) ?: return@launch
                musicContinuation = page.continuation
                musicFilterParam = target
                artistsEnriched = false
                _musicResults.value =
                    _musicResults.value.copy(
                        songs = if (category == MusicCategory.SONGS) page.songs else _musicResults.value.songs,
                        artists = if (category == MusicCategory.ARTISTS) page.artists else _musicResults.value.artists,
                        endReached = page.continuation == null,
                        isLoading = false,
                        error = false,
                    )
                if (category == MusicCategory.ARTISTS) growArtistsWithEnrichment()
            }
        }

        private fun categoryActive(category: MusicCategory): Boolean =
            _uiState.value.musicCategory == category

        /**
         * Widen the artist list to YT Music parity: pull the "Fans might also
         * like" carousel (~10 entries) from the main artist's page, then drop
         * junk look-alike channels (fan/topic clones of the searched artist)
         * while KEEPING legitimate collab channels (Kx5, REZZMAU5 …) via two
         * signals: names harvested from the songs' artist columns, and a large
         * audience figure in the subtitle.
         */
        private fun growArtistsWithEnrichment() {
            if (!artistsEnriched) enrichArtists() else growArtists()
        }

        private fun enrichArtists() {
            val query = _uiState.value.query
            if (artistsEnriched || query.isBlank()) return
            artistsEnriched = true
            viewModelScope.launch {
                val state = _musicResults.value
                val main = state.mainArtist
                val qNorm = normalizeName(query)
                // No Top-result card? The searched artist may still sit in the
                // list under its exact name — use it to unlock the page browse.
                val mainResolvable =
                    main?.id
                        ?: state.artists.firstOrNull { normalizeName(it.name) == qNorm }?.id
                var merged = state.artists
                if (mainResolvable != null) {
                    val related =
                        runCatching {
                            com.omersusin.pitube.innertube.YouTube.musicArtistContent(mainResolvable).getOrNull()
                        }.getOrNull()?.relatedArtists.orEmpty()
                    if (related.isNotEmpty()) merged = (merged + related).distinctBy { it.id }
                }
                val collabNames =
                    state.songs
                        .flatMap { song ->
                            // Collab videos carry extra channel profiles — use
                            // those plus the "A, B & C" artist column text.
                            splitArtistNames(song.channelName) +
                                song.collaborators.map { it.name }
                        }
                        .mapTo(mutableSetOf()) { normalizeName(it) }
                val mainNorm = main?.name?.let(::normalizeName).orEmpty()
                val cleaned =
                    merged.filter { channel ->
                        val id = channel.id
                        if (main != null && id == main.id) return@filter true
                        val norm = normalizeName(channel.name)
                        val looksLikeTarget =
                            (mainNorm.length >= 4 && (norm.contains(mainNorm) || mainNorm.contains(norm))) ||
                                (qNorm.length >= 4 && (norm.contains(qNorm) || qNorm.contains(norm)))
                        !looksLikeTarget ||
                            normalizeName(channel.name) in collabNames ||
                            audienceCount(channel.description) >= COLLAB_AUDIENCE_FLOOR
                    }
                _musicResults.value = _musicResults.value.copy(artists = cleaned)
                growArtists()
            }
        }

        /**
         * A short artist list reads as broken — users scroll, see 3 cards and
         * think results ended. Keep fetching pages up front until the list has
         * [MIN_ARTIST_RESULTS] entries or the source runs dry.
         */
        private suspend fun growArtists() {
            if (_uiState.value.musicCategory != MusicCategory.ARTISTS) return
            while (
                categoryActive(MusicCategory.ARTISTS) &&
                _musicResults.value.artists.size < MIN_ARTIST_RESULTS &&
                !_musicResults.value.endReached &&
                musicContinuation != null &&
                appendMusicPage()
            ) {
                // loop until enough artists or no more pages
            }
        }

        /** Lowercase, letters+digits only — locale-proof name comparison. */
        private fun normalizeName(name: String): String =
            name.lowercase().filter { it.isLetterOrDigit() }

        /**
         * "Kx5, deadmau5 & Kaskade" → [Kx5, deadmau5, Kaskade] — collaborators
         * harvested from song cards; their channels must survive junk-filtering.
         */
        private fun splitArtistNames(column: String): List<String> =
            column.split(",", "&", "×", "/")
                .map { it.trim() }
                .filter { it.length >= 2 }

        /**
         * "3.57M monthly audience" / "23 subscribers" / "12 B abone" → count.
         * Returns -1 when unparseable. Suffix letters are matched loosely so
         * any display language works ("B" is read as Turkish bin/thousand —
         * English billions are irrelevant at this floor's magnitude).
         */
        private fun audienceCount(description: String): Long {
            val match = AUDIENCE_REGEX.find(description) ?: return -1
            val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1
            val multiplier =
                when (match.groupValues[2].lowercase()) {
                    "m", "mn", "mln" -> 1_000_000L
                    "", "-" -> 1L
                    else -> 1_000L
                }
            return (number * multiplier).toLong()
        }
        fun loadMoreMusicResults() {
            if (musicContinuation == null || _musicResults.value.isLoading) return
            viewModelScope.launch { appendMusicPage() }
        }

        private fun reloadMusicResults() {
            musicContinuation = null
            _musicResults.value = MusicResults(isLoading = true)
            viewModelScope.launch { appendMusicPage() }
        }

        /**
         * One filtered page append. Returns false on error/exhaustion so
         * [growArtists] can stop its up-front loop.
         */
        private suspend fun appendMusicPage(): Boolean {
            val query = _uiState.value.query
            if (query.isBlank() || !musicCategoriesEnabled.value) return false
            val page =
                runCatching {
                    com.omersusin.pitube.innertube.YouTube.musicSearch(
                        query,
                        musicContinuation,
                        filterParams = musicFilterParam,
                    ).getOrNull()
                }.getOrNull()
            if (page == null) {
                _musicResults.value =
                    _musicResults.value.copy(
                        isLoading = false,
                        error = true,
                        endReached = true,
                    )
                return false
            }
            musicContinuation = page.continuation
            var songs = (_musicResults.value.songs + page.songs).distinctBy { it.id }
            val artists = (_musicResults.value.artists + page.artists).distinctBy { it.id }
            songs = decorateSongs(songs, artists, _musicResults.value.mainArtist)
            _musicResults.value =
                MusicResults(
                    songs = songs,
                    artists = artists,
                    endReached = page.continuation == null,
                    mainArtist = page.mainArtist ?: _musicResults.value.mainArtist,
                )
            fetchMainArtistCardIfNeeded()
            return true
        }

        /**
         * YT Music song rows carry no avatar and no view/date metadata — the
         * shared card would render an empty profile circle plus "0 views ·
         * now". Fill avatars from matching artist entries (same channel id or
         * normalized name); the card hides views/date for these via sentinels.
         */
        private fun decorateSongs(
            songs: List<com.omersusin.pitube.data.model.Video>,
            artists: List<com.omersin.pitube.data.model.Channel>,
            mainArtist: com.omersusin.pitube.data.model.Channel?,
        ): List<com.omersusin.pitube.data.model.Video> {
            if (songs.isEmpty()) return songs
            val byId = (artists + listOfNotNull(mainArtist)).associateBy { it.id }
            val byName = (artists + listOfNotNull(mainArtist)).associateBy { normalizeName(it.name) }
            return songs.map { song ->
                val match =
                    byId[song.channelId]
                        ?: byName[normalizeName(song.channelName)]
                        ?: return@map song
                song.copy(
                    channelId = song.channelId.ifBlank { match.id },
                    channelThumbnailUrl = song.channelThumbnailUrl.ifBlank { match.thumbnailUrl },
                )
            }
        }

        /**
         * Filtered chip responses have no Top-result card, so the hero needs a
         * one-shot unfiltered lookup per query. Only the card is taken — the
         * mixed lists are discarded.
         */
        private var mainArtistCardFetchedFor: String? = null

        private fun fetchMainArtistCardIfNeeded() {
            val state = _musicResults.value
            val query = _uiState.value.query
            if (state.mainArtist != null || query.isBlank()) return
            if (mainArtistCardFetchedFor == query) return
            mainArtistCardFetchedFor = query
            viewModelScope.launch {
                val card =
                    runCatching {
                        com.omersusin.pitube.innertube.YouTube.musicSearch(query).getOrNull()?.mainArtist
                    }.getOrNull() ?: return@launch
                val current = _musicResults.value
                if (current.mainArtist == null && _uiState.value.query == query) {
                    _musicResults.value =
                        current.copy(
                            mainArtist = card,
                            songs = decorateSongs(current.songs, current.artists, card),
                        )
                }
            }
        }

        fun clearSearch() {
            _uiState.value = SearchUiState()
            _searchKey.value = null
            resetMusicState()
            mainArtistCardFetchedFor = null
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

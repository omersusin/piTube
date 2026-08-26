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
        private val playerPreferences: com.omersusin.pitube.data.local.PlayerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        /** Opt-in flag for the YT Music categories — off means zero music-host traffic. */
        val musicCategoriesEnabled: StateFlow<Boolean> =
            playerPreferences.musicSearchCategoriesEnabled
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

        /** User-ordered / user-hidden search-strip chips (see PlayerPreferences). */
        val searchChipOrder: StateFlow<List<String>> =
            playerPreferences.searchChipOrder
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())
        val searchChipHidden: StateFlow<Set<String>> =
            playerPreferences.searchChipHidden
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())

        /** Shared grid/list display mode (same key the channel page uses). */
        val searchGridMode: StateFlow<Boolean> =
            playerPreferences.searchIsGridMode
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

        fun toggleSearchGridMode() {
            viewModelScope.launch {
                playerPreferences.setSearchIsGridMode(!searchGridMode.value)
            }
        }

        /**
         * Per-category page bookkeeping — each music tab owns its items and
         * continuation, so switching tabs never refetches and never wipes the
         * other tab's list (the old shared-state design flashed and reloaded).
         */
        private data class SongsPage(
            val items: List<com.omersusin.pitube.data.model.Video> = emptyList(),
            val continuation: String? = null,
            val endReached: Boolean = false,
            val loading: Boolean = false,
            val error: Boolean = false,
            /** Whether an initial load was ever started for this query. */
            val started: Boolean = false,
        )

        private data class ArtistsPage(
            val items: List<com.omersusin.pitube.data.model.Channel> = emptyList(),
            val continuation: String? = null,
            val endReached: Boolean = false,
            val loading: Boolean = false,
            val error: Boolean = false,
            val started: Boolean = false,
        )

        private val _songsPage = MutableStateFlow(SongsPage())
        private val _artistsPage = MutableStateFlow(ArtistsPage())
        private val _mainArtist =
            MutableStateFlow<com.omersusin.pitube.data.model.Channel?>(null)

        /**
         * Combined view the existing UI renders: both lists plus the ACTIVE
         * tab's loading/error flags, so MusicResultsList behaves exactly as
         * before while storage stays per-category underneath.
         */
        val musicResults: StateFlow<MusicResults> =
            kotlinx.coroutines.flow.combine(
                _songsPage,
                _artistsPage,
                _mainArtist,
                _uiState,
            ) { songs, artists, main, ui ->
                val songsActive = ui.musicCategory == MusicCategory.SONGS
                val activeLoading = if (songsActive) songs.loading else artists.loading
                val activeError = if (songsActive) songs.error else artists.error
                val activeEnd = if (songsActive) songs.endReached else artists.endReached
                MusicResults(
                    isLoading = activeLoading,
                    error = activeError,
                    songs = songs.items,
                    artists = artists.items,
                    endReached = activeEnd,
                    mainArtist = main,
                )
            }.stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                MusicResults(),
            )

        private var songsJob: Job? = null
        private var artistsJob: Job? = null

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

        /** Fresh query/filter → drop per-category bookkeeping and jobs. */
        private fun resetMusicState() {
            songsJob?.cancel()
            songsJob = null
            artistsJob?.cancel()
            artistsJob = null
            _songsPage.value = SongsPage()
            _artistsPage.value = ArtistsPage()
            _mainArtist.value = null
        }

        /** Switch between the experimental Songs/Artists views (null = regular results). */
        fun selectMusicCategory(category: MusicCategory?) {
            _uiState.value = _uiState.value.copy(musicCategory = category)
            when (category) {
                MusicCategory.SONGS -> {
                    val page = _songsPage.value
                    when {
                        !page.started ->
                            songsJob =
                                viewModelScope.launch {
                                    loadSongs()
                                }
                        page.error && page.items.isEmpty() ->
                            songsJob =
                                viewModelScope.launch {
                                    loadSongs(retry = true)
                                }
                    }
                }

                MusicCategory.ARTISTS -> {
                    val page = _artistsPage.value
                    when {
                        !page.started || (page.error && page.items.isEmpty()) ->
                            artistsJob =
                                viewModelScope.launch {
                                    loadArtistsWithEnrichment()
                                }
                        else -> growArtistsIfShort()
                    }
                }

                null -> {}
            }
        }

        private fun categoryActive(category: MusicCategory): Boolean =
            _uiState.value.musicCategory == category

        private suspend fun loadSongs(retry: Boolean = false) {
            val query = _uiState.value.query
            if (query.isBlank() || !musicCategoriesEnabled.value) return
            if (!retry) {
                _songsPage.value = SongsPage(loading = true, started = true)
            } else {
                _songsPage.value = _songsPage.value.copy(loading = true, error = false)
            }
            val result =
                runCatching {
                    com.omersusin.pitube.innertube.YouTube.musicSearch(
                        query,
                        filterParams = MUSIC_SONGS_FILTER_PARAM,
                    ).getOrNull()
                }.getOrNull()
            if (result == null) {
                _songsPage.value = _songsPage.value.copy(loading = false, error = true, endReached = true)
                return
            }
            _songsPage.value =
                SongsPage(
                    items = decorateSongs(result.songs),
                    continuation = result.continuation,
                    endReached = result.continuation == null,
                    started = true,
                )
            fetchMainArtistCardIfNeeded()
            refreshSongAvatars(query)
        }

        /**
         * Initial artists page + related-shelf enrichment + up-front growth —
         * all sequential inside one job so writes can't interleave.
         */
        private suspend fun loadArtistsWithEnrichment() {
            val query = _uiState.value.query
            if (query.isBlank() || !musicCategoriesEnabled.value) return
            _artistsPage.value = ArtistsPage(loading = true, started = true)
            val result =
                runCatching {
                    com.omersusin.pitube.innertube.YouTube.musicSearch(
                        query,
                        filterParams = MUSIC_ARTISTS_FILTER_PARAM,
                    ).getOrNull()
                }.getOrNull()
            if (result == null) {
                _artistsPage.value = _artistsPage.value.copy(loading = false, error = true, endReached = true)
                return
            }
            _artistsPage.value =
                ArtistsPage(items = result.artists, continuation = result.continuation, started = true)
            fetchMainArtistCardIfNeeded()
            enrichArtists()
            growArtists()
        }

        /**
         * Widen the artist list to YT Music parity: pull the "Fans might also
         * like" carousel (~10 entries) from the main artist's page, then drop
         * junk look-alike channels (fan/topic clones of the searched artist)
         * while KEEPING legitimate collab channels (Kx5, REZZMAU5 …) via two
         * signals: names harvested from the songs' artist columns, and a large
         * audience figure in the subtitle.
         */
        private suspend fun enrichArtists() {
            val query = _uiState.value.query
            if (query.isBlank()) return
            val page = _artistsPage.value
            val main = _mainArtist.value
            val qNorm = normalizeName(query)
            // No Top-result card? The searched artist may still sit in the
            // list under its exact name — use it to unlock the page browse.
            val mainResolvable =
                main?.id
                    ?: page.items.firstOrNull { normalizeName(it.name) == qNorm }?.id
            var merged = page.items
            if (mainResolvable != null) {
                val related =
                    runCatching {
                        com.omersusin.pitube.innertube.YouTube.musicArtistContent(mainResolvable).getOrNull()
                    }.getOrNull()?.relatedArtists.orEmpty()
                if (related.isNotEmpty()) merged = (merged + related).distinctBy { it.id }
            }
            val collabNames =
                _songsPage.value.items
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
                    // 3+ so short artist names ("Sia") still catch their clones.
                    val looksLikeTarget =
                        (mainNorm.length >= 3 && (norm.contains(mainNorm) || mainNorm.contains(norm))) ||
                            (qNorm.length >= 3 && (norm.contains(qNorm) || qNorm.contains(norm)))
                    !looksLikeTarget ||
                        normalizeName(channel.name) in collabNames ||
                        audienceCount(channel.description) >= COLLAB_AUDIENCE_FLOOR
                }
            _artistsPage.value = page.copy(items = cleaned)
        }

        /**
         * A short artist list reads as broken — users scroll, see 3 cards and
         * think results ended. Keep fetching pages up front until the list has
         * [MIN_ARTIST_RESULTS] entries or the source runs dry. (The artists
         * chip currently returns no continuation at all, so in practice this
         * loop exits after one check unless YouTube restores pagination.)
         */
        private suspend fun growArtists() {
            if (!categoryActive(MusicCategory.ARTISTS)) return
            while (
                categoryActive(MusicCategory.ARTISTS) &&
                _artistsPage.value.items.size < MIN_ARTIST_RESULTS &&
                !_artistsPage.value.endReached &&
                _artistsPage.value.continuation != null &&
                appendArtistsPage()
            ) {
                // loop until enough artists or no more pages
            }
        }

        private fun growArtistsIfShort() {
            val page = _artistsPage.value
            if (!page.loading &&
                !page.endReached &&
                page.continuation != null &&
                page.items.size < MIN_ARTIST_RESULTS
            ) {
                viewModelScope.launch { growArtists() }
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
            when (_uiState.value.musicCategory) {
                MusicCategory.SONGS -> {
                    val page = _songsPage.value
                    if (page.continuation == null || page.loading || page.endReached) return
                    songsJob =
                        viewModelScope.launch {
                            _songsPage.value = _songsPage.value.copy(loading = true)
                            appendSongsPage()
                        }
                }

                MusicCategory.ARTISTS -> growArtistsIfShort()

                null -> {}
            }
        }

        /** One continuation append for the Songs tab. */
        private suspend fun appendSongsPage(): Boolean {
            val query = _uiState.value.query
            val current = _songsPage.value
            if (query.isBlank() || current.continuation == null) return false
            val result =
                runCatching {
                    com.omersusin.pitube.innertube.YouTube.musicSearch(
                        query,
                        current.continuation,
                        filterParams = MUSIC_SONGS_FILTER_PARAM,
                    ).getOrNull()
                }.getOrNull()
            if (result == null) {
                _songsPage.value =
                    current.copy(loading = false, error = true, endReached = true)
                return false
            }
            val merged = (current.items + result.songs).distinctBy { it.id }
            _songsPage.value =
                current.copy(
                    items = decorateSongs(merged),
                    continuation = result.continuation,
                    endReached = result.continuation == null,
                    loading = false,
                )
            refreshSongAvatars(query)
            return true
        }

        /** One continuation append for the Artists tab. */
        private suspend fun appendArtistsPage(): Boolean {
            val query = _uiState.value.query
            val current = _artistsPage.value
            if (query.isBlank() || current.continuation == null) return false
            val result =
                runCatching {
                    com.omersusin.pitube.innertube.YouTube.musicSearch(
                        query,
                        current.continuation,
                        filterParams = MUSIC_ARTISTS_FILTER_PARAM,
                    ).getOrNull()
                }.getOrNull()
            if (result == null) {
                _artistsPage.value =
                    current.copy(endReached = true)
                return false
            }
            val merged = (current.items + result.artists).distinctBy { it.id }
            _artistsPage.value =
                current.copy(
                    items = merged,
                    continuation = result.continuation,
                    endReached = result.continuation == null,
                )
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
        ): List<com.omersusin.pitube.data.model.Video> {
            if (songs.isEmpty()) return songs
            val artists = _artistsPage.value.items
            val main = _mainArtist.value
            val byId = (artists + listOfNotNull(main)).associateBy { it.id }
            val byName = (artists + listOfNotNull(main)).associateBy { normalizeName(it.name) }
            val known = (artists + listOfNotNull(main))
                .map { normalizeName(it.name) }
                .filter { it.length >= 3 }
            return songs.map { song ->
                val songNorm = normalizeName(song.channelName)
                val partial =
                    known.firstOrNull { candidate ->
                        songNorm.contains(candidate) || candidate.contains(songNorm)
                    }?.let { byName[it] }
                val match =
                    byId[song.channelId]
                        ?: byName[songNorm]
                        // Partial match: the artist tab often lists "X" while a
                        // song credits "X & Y" — either direction fills the avatar.
                        ?: partial
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

        /**
         * Avatar pass for song rows (blank by design in YT Music): bulk search
         * stacks + per-video fallbacks run off the critical path, then the list
         * is rewritten through decorateSongs so artist-matching still applies.
         * Loops until no untried blanks remain so continuation pages appended
         * mid-fetch are picked up; results merge by id, never clobbering rows.
         */
        private var songAvatarJob: Job? = null
        private var songAvatarAttemptsFor: String? = null
        private val songAvatarAttemptedIds = mutableSetOf<String>()

        private fun refreshSongAvatars(query: String) {
            if (songAvatarAttemptsFor != query) {
                songAvatarAttemptsFor = query
                songAvatarAttemptedIds.clear()
            }
            android.util.Log.d(
                "DEBUG-songav",
                "vm: refresh q#${query.hashCode()} blanks=${_songsPage.value.items.count { it.channelThumbnailUrl.isBlank() }} active=${songAvatarJob?.isActive == true}",
            )
            if (songAvatarJob?.isActive == true) return
            songAvatarJob =
                viewModelScope.launch {
                    var pass = 0
                    while (_uiState.value.query == query) {
                        val snapshot = _songsPage.value.items
                        val targets =
                            snapshot.filter {
                                it.channelThumbnailUrl.isBlank() && it.id !in songAvatarAttemptedIds
                            }
                        if (targets.isEmpty()) break
                        pass++
                        android.util.Log.d(
                            "DEBUG-songav",
                            "vm: pass=$pass snapshot=${snapshot.size} targets=${targets.size} jobActive=${songAvatarJob?.isActive == true}",
                        )
                        songAvatarAttemptedIds.addAll(targets.map { it.id })
                        val enriched = repository.enrichSongAvatars(query, snapshot)
                        if (_uiState.value.query != query) return@launch
                        val byId = enriched.associateBy { it.id }
                        _songsPage.value =
                            _songsPage.value.copy(
                                items = decorateSongs(_songsPage.value.items.map { byId[it.id] ?: it }),
                            )
                        android.util.Log.d(
                            "DEBUG-songav",
                            "vm: pass=$pass wrote items=${_songsPage.value.items.size} blankLeft=${_songsPage.value.items.count { it.channelThumbnailUrl.isBlank() }}",
                        )
                    }
                }
        }

        private fun fetchMainArtistCardIfNeeded() {
            val query = _uiState.value.query
            if (_mainArtist.value != null || query.isBlank()) return
            if (mainArtistCardFetchedFor == query) return
            mainArtistCardFetchedFor = query
            viewModelScope.launch {
                val card =
                    runCatching {
                        com.omersusin.pitube.innertube.YouTube.musicSearch(query).getOrNull()?.mainArtist
                    }.getOrNull() ?: return@launch
                if (_mainArtist.value == null && _uiState.value.query == query) {
                    _mainArtist.value = card
                    _songsPage.value =
                        _songsPage.value.copy(items = decorateSongs(_songsPage.value.items))
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

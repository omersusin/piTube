package com.omersusin.pitube.ui.screens.channel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.ChannelSubscription
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.model.Comment
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.model.distinctByNonBlankKey
import com.omersusin.pitube.data.model.mergeDistinctByNonBlankKey
import com.omersusin.pitube.data.paging.ChannelPlaylistsPagingSource
import com.omersusin.pitube.data.paging.ChannelVideosPagingSource
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.pages.CommunityPost
import com.omersusin.pitube.ui.youtubeChannelUrl
import com.omersusin.pitube.utils.PerformanceDispatcher
import com.omersusin.pitube.utils.ThumbnailUrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val subscriptionRepository: SubscriptionRepository,
        private val playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ChannelUiState())
        val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()
        private val communityController = ChannelCommunityController(viewModelScope)
        internal val communityUiState: StateFlow<ChannelCommunityUiState> = communityController.state

        // Paging flow for channel videos with infinite scroll
        private val _videosPagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
        val videosPagingFlow: StateFlow<Flow<PagingData<Video>>?> = _videosPagingFlow.asStateFlow()
        private val _shortsPagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
        val shortsPagingFlow: StateFlow<Flow<PagingData<Video>>?> = _shortsPagingFlow.asStateFlow()
        private val _livePagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
        val livePagingFlow: StateFlow<Flow<PagingData<Video>>?> = _livePagingFlow.asStateFlow()
        private val _playlistsPagingFlow = MutableStateFlow<Flow<PagingData<com.omersusin.pitube.data.model.Playlist>>?>(null)
        val playlistsPagingFlow: StateFlow<Flow<PagingData<com.omersusin.pitube.data.model.Playlist>>?> = _playlistsPagingFlow.asStateFlow()

        // Eagerly loaded full video lists (all pages) for filter support
        private val _videosAll = MutableStateFlow<List<Video>>(emptyList())
        val videosAll: StateFlow<List<Video>> = _videosAll.asStateFlow()

        private val _liveAll = MutableStateFlow<List<Video>>(emptyList())
        val liveAll: StateFlow<List<Video>> = _liveAll.asStateFlow()

        private val _isLoadingAllVideos = MutableStateFlow(false)
        val isLoadingAllVideos: StateFlow<Boolean> = _isLoadingAllVideos.asStateFlow()

        var listScrollIndex: Int = 0
            private set
        var listScrollOffset: Int = 0
            private set

        fun saveScrollPosition(
            index: Int,
            offset: Int,
        ) {
            listScrollIndex = index
            listScrollOffset = offset
        }

        private var currentVideosTab: ListLinkHandler? = null
        private var currentShortsTab: ListLinkHandler? = null
        private var currentLiveTab: ListLinkHandler? = null
        private var currentPlaylistsTab: ListLinkHandler? = null

        // Lazy-pagination continuation for the Videos/Live lists
        private var videosChannelInfo: ChannelInfo? = null
        private var videosNextPage: Page? = null
        private var videosPagesLoaded = 0
        private val _isLoadingMoreVideos = MutableStateFlow(false)
        val isLoadingMoreVideos: StateFlow<Boolean> = _isLoadingMoreVideos.asStateFlow()
        private val _hasMoreVideos = MutableStateFlow(false)
        val hasMoreVideos: StateFlow<Boolean> = _hasMoreVideos.asStateFlow()
        private var liveChannelInfo: ChannelInfo? = null
        private var liveNextPage: Page? = null
        private var livePagesLoaded = 0
        private val _isLoadingMoreLive = MutableStateFlow(false)
        val isLoadingMoreLive: StateFlow<Boolean> = _isLoadingMoreLive.asStateFlow()
        private val _hasMoreLive = MutableStateFlow(false)
        val hasMoreLive: StateFlow<Boolean> = _hasMoreLive.asStateFlow()

        companion object {
            private const val TAG = "ChannelViewModel"

            /** Safety cap: stops loading beyond this many pages (~1500 videos) */
            private const val MAX_PAGES = 50
            private const val POSTS_TAB_INDEX = 4
            private const val PLAYLISTS_TAB_INDEX = 3
        }

        /**
         *  PERFORMANCE OPTIMIZED: Load channel with timeout protection
         */
        fun loadChannel(channelUrl: String) {
            if (channelUrl.isBlank()) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_invalid_channel_url), isLoading = false) }
                return
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        channelVideoCountText = null,
                    )
                }

                try {
                    Log.d(TAG, "Loading channel: $channelUrl")

                    // Normalize the URL
                    val normalizedUrl = normalizeChannelUrl(channelUrl)
                    Log.d(TAG, "Normalized URL: $normalizedUrl")

                    val channelInfo =
                        withTimeoutOrNull(20_000L) {
                            withContext(PerformanceDispatcher.networkIO) {
                                // Use NewPipe to fetch channel info
                                ChannelInfo.getInfo(NewPipe.getService(0), normalizedUrl)
                            }
                        }

                    if (channelInfo == null) {
                        _uiState.update {
                            it.copy(
                                error = appContext.getString(R.string.error_channel_loading_timed_out),
                                isLoading = false,
                            )
                        }
                        return@launch
                    }

                    Log.d(TAG, "Channel loaded: ${channelInfo.name}")

                    val channelId = channelInfo.id

                    _uiState.update {
                        it.copy(
                            channelId = channelId,
                            channelInfo = channelInfo,
                            isLoading = false,
                        )
                    }
                    val channelAvatar =
                        channelInfo.avatars.maxByOrNull { it.height }?.url
                            ?: channelInfo.avatars.firstOrNull()?.url
                            ?: ""
                    communityController.reset(channelId, channelInfo.name, channelAvatar)
                    loadChannelVideoCount(channelId, channelInfo.name, channelAvatar)
                    if (_uiState.value.selectedTab == POSTS_TAB_INDEX) {
                        communityController.ensurePostsLoaded()
                    }

                    // Restore the tab the user last left this channel on
                    playerPreferences.channelDefaultTab(channelId).first().let { rememberedTab ->
                        if (rememberedTab != null && rememberedTab != _uiState.value.selectedTab) {
                            _uiState.update { it.copy(selectedTab = rememberedTab) }
                            if (rememberedTab == POSTS_TAB_INDEX) communityController.ensurePostsLoaded()
                        }
                    }

                    // Load subscription state
                    loadSubscriptionState(channelId)

                    // Load channel tabs (Videos, Shorts, Playlists)
                    loadChannelTabs(channelInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load channel", e)
                    _uiState.update {
                        it.copy(
                            error = e.message ?: appContext.getString(R.string.error_failed_to_load_channel),
                            isLoading = false,
                        )
                    }
                }
            }
        }

        private fun normalizeChannelUrl(url: String): String = youtubeChannelUrl(url).orEmpty()

        private fun loadChannelVideoCount(
            channelId: String,
            channelName: String,
            channelThumbnailUrl: String,
        ) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val videoCountText =
                    YouTube
                        .channelVideos(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnailUrl = channelThumbnailUrl,
                        ).getOrNull()
                        ?.channelVideoCountText ?: return@launch
                _uiState.update { state ->
                    if (state.channelId == channelId) {
                        state.copy(channelVideoCountText = videoCountText)
                    } else {
                        state
                    }
                }
            }
        }

        /**
         *  PERFORMANCE OPTIMIZED: Load channel tabs with optimized dispatcher
         */
        private fun loadChannelTabs(channelInfo: ChannelInfo) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    _uiState.update { it.copy(isLoadingVideos = true) }

                    withContext(PerformanceDispatcher.networkIO) {
                        // Find the tabs
                        for (tab in channelInfo.tabs) {
                            try {
                                val tabName = tab.contentFilters.joinToString()
                                val tabUrl = tab.url ?: ""
                                Log.d(TAG, "Checking tab: Name=$tabName, URL=$tabUrl")

                                val isLive =
                                    tabName.contains("live", ignoreCase = true) ||
                                        tabUrl.contains("/streams", ignoreCase = true)

                                val isVideos =
                                    (
                                        tabName.contains("video", ignoreCase = true) ||
                                            tabName.contains("Videos", ignoreCase = true) ||
                                            tabUrl.contains("/videos", ignoreCase = true)
                                    ) && !isLive

                                val isShorts =
                                    tabName.contains("shorts", ignoreCase = true) ||
                                        tabUrl.contains("/shorts", ignoreCase = true)

                                val isPlaylists =
                                    tabName.contains("playlist", ignoreCase = true) ||
                                        tabName.contains("Playlists", ignoreCase = true) ||
                                        tabUrl.contains("/playlists", ignoreCase = true)

                                if (isLive) {
                                    currentLiveTab = tab
                                    Log.d(TAG, "Found live tab")
                                }

                                if (isVideos) {
                                    currentVideosTab = tab
                                    Log.d(TAG, "Found videos tab")
                                }

                                if (isShorts) {
                                    currentShortsTab = tab
                                    Log.d(TAG, "Found shorts tab")
                                }

                                if (isPlaylists) {
                                    currentPlaylistsTab = tab
                                    Log.d(TAG, "Found playlists tab")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error checking tab", e)
                            }
                        }
                    }

                    // Load first page of Videos tab instantly; remaining pages load lazily on scroll
                    val videosTab = currentVideosTab
                    if (videosTab != null) {
                        loadFirstPage(videosTab, channelInfo, _videosAll, isLive = false)
                    } else {
                        // No Videos tab at all: topic channels ("X - Topic") expose a
                        // single Home tab on www, so NewPipe can't list anything.
                        // Their content lives on YT Music — fetch it anonymously.
                        loadTopicChannelVideos(channelInfo)
                    }

                    // Create the paging flow for Shorts
                    if (currentShortsTab != null) {
                        _shortsPagingFlow.value =
                            Pager(
                                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                                pagingSourceFactory = { ChannelVideosPagingSource(channelInfo, currentShortsTab) },
                            ).flow.cachedIn(viewModelScope)
                    }

                    val liveTab = currentLiveTab
                    if (liveTab != null) {
                        loadFirstPage(liveTab, channelInfo, _liveAll, isLive = true)
                    }

                    // Create the paging flow for Playlists
                    if (currentPlaylistsTab != null) {
                        _playlistsPagingFlow.value =
                            Pager(
                                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                                pagingSourceFactory = { ChannelPlaylistsPagingSource(channelInfo, currentPlaylistsTab) },
                            ).flow.cachedIn(viewModelScope)
                    }

                    _uiState.update { it.copy(isLoadingVideos = false) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load channel tabs", e)
                    _uiState.update {
                        it.copy(
                            isLoadingVideos = false,
                            videosError = e.message,
                        )
                    }
                }
            }
        }

        private fun loadSubscriptionState(channelId: String) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.getSubscription(channelId).collect { subscription ->
                    _uiState.update {
                        it.copy(
                            isSubscribed = subscription != null,
                            isNotificationsEnabled = subscription?.isNotificationEnabled ?: false,
                        )
                    }
                }
            }
        }

        fun toggleSubscription() {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                val channelInfo = state.channelInfo ?: return@launch
                val channelName = channelInfo.name
                val channelThumbnail =
                    try {
                        channelInfo.avatars.firstOrNull()?.url ?: ""
                    } catch (e: Exception) {
                        ""
                    }

                if (state.isSubscribed) {
                    // Unsubscribe
                    subscriptionRepository.unsubscribe(channelId)
                    val applied =
                        com.omersusin.pitube.data.local.AccountActions(appContext)
                            .setSubscribed(channelId, false)
                    if (!applied) {
                        // YouTube did not apply the write — restore the row so
                        // the button state matches the real account.
                        subscriptionRepository.subscribe(
                            ChannelSubscription(
                                channelId = channelId,
                                channelName = channelName,
                                channelThumbnail = channelThumbnail,
                                subscribedAt = System.currentTimeMillis(),
                            )
                        )
                        subscriptionWriteFailedMessage()
                    }
                } else {
                    // Subscribe
                    val subscription =
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = channelThumbnail,
                            subscribedAt = System.currentTimeMillis(),
                        )
                    subscriptionRepository.subscribe(subscription)
                    val applied =
                        com.omersusin.pitube.data.local.AccountActions(appContext)
                            .setSubscribed(channelId, true)
                    if (!applied) {
                        subscriptionRepository.unsubscribe(channelId)
                        subscriptionWriteFailedMessage()
                    }
                }
            }
        }

        fun unsubscribe() {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                val channelInfo = state.channelInfo
                subscriptionRepository.unsubscribe(channelId)
                val applied =
                    com.omersusin.pitube.data.local.AccountActions(appContext)
                        .setSubscribed(channelId, false)
                if (!applied && channelInfo != null) {
                    subscriptionRepository.subscribe(
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelInfo.name,
                            channelThumbnail = channelInfo.avatars.firstOrNull()?.url ?: "",
                            subscribedAt = System.currentTimeMillis(),
                        )
                    )
                    subscriptionWriteFailedMessage()
                }
            }
        }

        private fun subscriptionWriteFailedMessage() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(subscriptionError = appContext.getString(R.string.toast_subscribe_write_failed))
                }
            }
        }

        fun clearSubscriptionError() {
            _uiState.update { it.copy(subscriptionError = null) }
        }

        fun setNotificationState(enabled: Boolean) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                subscriptionRepository.updateNotificationState(channelId, enabled)
            }
        }

        fun selectTab(tabIndex: Int) {
            _uiState.update { it.copy(selectedTab = tabIndex) }
            if (tabIndex == POSTS_TAB_INDEX) communityController.ensurePostsLoaded()
            _uiState.value.channelId?.let { channelId ->
                viewModelScope.launch(PerformanceDispatcher.diskIO) {
                    playerPreferences.setChannelDefaultTab(channelId, tabIndex)
                }
            }
        }

        fun openCommunityPostComments(post: CommunityPost) = communityController.openComments(post)

        fun closeCommunityPostComments() = communityController.closeComments()

        fun retryCommunityPosts() = communityController.retryPosts()

        fun loadMoreCommunityPosts() = communityController.loadMorePosts()

        fun loadMoreCommunityPostComments() = communityController.loadMoreComments()

        fun loadCommunityCommentReplies(comment: Comment) = communityController.loadReplies(comment, append = false)

        fun loadMoreCommunityCommentReplies(comment: Comment) = communityController.loadReplies(comment, append = true)

        // ── Channel search ────────────────────────────────────────────────────────

        fun setSearchActive(active: Boolean) {
            _uiState.update {
                it.copy(
                    searchActive = active,
                    searchQuery = if (!active) "" else it.searchQuery,
                    searchResults = if (!active) emptyList() else it.searchResults,
                    searchErrorLog = null,
                )
            }
        }

        fun searchInChannel(query: String) {
            val channelId = _uiState.value.channelId ?: return
            val channelInfo = _uiState.value.channelInfo ?: return
            val trimmed = query.trim()

            _uiState.update {
                it.copy(
                    searchQuery = query,
                    searchErrorLog = null,
                )
            }

            if (trimmed.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isSearching = true) }
                try {
                    val channelThumbnail =
                        try {
                            channelInfo.avatars.maxByOrNull { it.height }?.url
                                ?: channelInfo.avatars.firstOrNull()?.url
                                ?: ""
                        } catch (e: Exception) {
                            ""
                        }

                    val result =
                        com.omersusin.pitube.innertube.YouTube.channelSearch(
                            channelId = channelId,
                            channelName = channelInfo.name,
                            channelThumbnailUrl = channelThumbnail,
                            query = trimmed,
                        )
                    result.fold(
                        onSuccess = { page ->
                            _uiState.update {
                                it.copy(
                                    searchResults = page.videos.distinctByNonBlankKey(Video::id),
                                    searchContinuation = page.continuation,
                                    isSearching = false,
                                    searchErrorLog = null,
                                )
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Channel search failed", e)
                            _uiState.update {
                                it.copy(
                                    isSearching = false,
                                    searchErrorLog =
                                        buildChannelRequestErrorLog(
                                            operation = "channel_search",
                                            channelId = channelId,
                                            query = trimmed,
                                            error = e,
                                        ),
                                )
                            }
                        },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Channel search error", e)
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchErrorLog =
                                buildChannelRequestErrorLog(
                                    operation = "channel_search",
                                    channelId = channelId,
                                    query = trimmed,
                                    error = e,
                                ),
                        )
                    }
                }
            }
        }

        fun loadMoreSearchResults() {
            val state = _uiState.value
            val continuation = state.searchContinuation ?: return
            val channelId = state.channelId ?: return
            val channelInfo = state.channelInfo ?: return
            if (state.isLoadingMoreSearch) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isLoadingMoreSearch = true) }
                try {
                    val channelThumbnail =
                        try {
                            channelInfo.avatars.maxByOrNull { it.height }?.url
                                ?: channelInfo.avatars.firstOrNull()?.url ?: ""
                        } catch (e: Exception) {
                            ""
                        }

                    val result =
                        com.omersusin.pitube.innertube.YouTube.channelSearchContinuation(
                            channelId = channelId,
                            channelName = channelInfo.name,
                            channelThumbnailUrl = channelThumbnail,
                            continuation = continuation,
                        )
                    result.fold(
                        onSuccess = { page ->
                            _uiState.update {
                                it.copy(
                                    searchResults =
                                        it.searchResults.mergeDistinctByNonBlankKey(
                                            page.videos,
                                            Video::id,
                                        ),
                                    searchContinuation = page.continuation,
                                    isLoadingMoreSearch = false,
                                )
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Channel search continuation failed", e)
                            _uiState.update { it.copy(isLoadingMoreSearch = false) }
                        },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Channel search continuation error", e)
                    _uiState.update { it.copy(isLoadingMoreSearch = false) }
                }
            }
        }

        /**
         * Auto-generated topic channels have no Videos tab (single Home tab on
         * www), so NewPipe returns nothing. Fetch their video carousel via the
         * anonymous YT Music artist-page browse instead and populate the Videos
         * tab. Falls back to Playlists only for the rare non-topic channel that
         * lacks uploads but has playlists.
         */
        private fun loadTopicChannelVideos(channelInfo: ChannelInfo) {
            _isLoadingAllVideos.value = true
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val content = YouTube.musicArtistContent(channelInfo.id).getOrNull()
                    val videos = content?.videos.orEmpty()
                    if (videos.isNotEmpty()) {
                        Log.d(TAG, "Topic channel ${channelInfo.id}: ${videos.size} videos from music browse")
                        videosChannelInfo = channelInfo
                        videosNextPage = null
                        videosPagesLoaded = 0
                        _videosAll.value = videos
                        _hasMoreVideos.value = false
                    } else if (currentPlaylistsTab != null && _uiState.value.selectedTab == 0) {
                        _uiState.update { it.copy(selectedTab = PLAYLISTS_TAB_INDEX) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Topic channel music browse failed", e)
                } finally {
                    _isLoadingAllVideos.value = false
                }
            }
        }

        /**
         * Loads the first page of a channel tab instantly and stashes the pagination
         * continuation. Remaining pages are fetched lazily on scroll via
         * [loadMoreVideos]/[loadMoreLive]. Unlike the old eager crawl, no burst of
         * paginated requests (with artificial sleeps) is fired on tab open.
         */
        private fun loadFirstPage(
            tab: ListLinkHandler,
            channelInfo: ChannelInfo,
            target: MutableStateFlow<List<Video>>,
            isLive: Boolean,
        ) {
            if (isLive) {
                liveChannelInfo = channelInfo
                liveNextPage = null
                livePagesLoaded = 0
            } else {
                videosChannelInfo = channelInfo
                videosNextPage = null
                videosPagesLoaded = 0
            }
            _isLoadingAllVideos.value = true
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val service = NewPipe.getService(0)
                    val initial = ChannelTabInfo.getInfo(service, tab)
                    val firstPage =
                        initial.relatedItems
                            .filterIsInstance<StreamInfoItem>()
                            .map { it.toChannelVideo(channelInfo) }
                    target.value = firstPage
                    if (!isLive && target === _videosAll && firstPage.isEmpty() &&
                        currentPlaylistsTab != null && _uiState.value.selectedTab == 0
                    ) {
                        // Topic channels expose a Videos tab that is always empty;
                        // their music lives under Playlists. Fall over once, so the
                        // channel page never looks broken regardless of UI language.
                        _uiState.update { it.copy(selectedTab = PLAYLISTS_TAB_INDEX) }
                    }
                    val nextPage = initial.nextPage
                    if (isLive) {
                        liveNextPage = nextPage
                        livePagesLoaded = 1
                        _hasMoreLive.value = nextPage != null
                    } else {
                        videosNextPage = nextPage
                        videosPagesLoaded = 1
                        _hasMoreVideos.value = nextPage != null
                    }
                } catch (e: Exception) {
                    // Rate-limited or network error — user keeps whatever loaded so far
                    Log.w(TAG, "First page stopped after rate limit or error", e)
                    if (isLive) {
                        liveNextPage = null
                        _hasMoreLive.value = false
                    } else {
                        videosNextPage = null
                        _hasMoreVideos.value = false
                    }
                } finally {
                    _isLoadingAllVideos.value = false
                }
            }
        }

        fun loadMoreVideos() = loadMorePage(isLive = false)

        fun loadMoreLive() = loadMorePage(isLive = true)

        private fun loadMorePage(isLive: Boolean) {
            val tab = (if (isLive) currentLiveTab else currentVideosTab) ?: return
            val channelInfo = (if (isLive) liveChannelInfo else videosChannelInfo) ?: return
            val nextPage = (if (isLive) liveNextPage else videosNextPage) ?: return
            val target = if (isLive) _liveAll else _videosAll
            val loadingFlag = if (isLive) _isLoadingMoreLive else _isLoadingMoreVideos
            val hasMore = if (isLive) _hasMoreLive else _hasMoreVideos
            if (loadingFlag.value) return
            loadingFlag.value = true
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                try {
                    val service = NewPipe.getService(0)
                    val more = ChannelTabInfo.getMoreItems(service, tab, nextPage)
                    val pageVideos =
                        more.items
                            .filterIsInstance<StreamInfoItem>()
                            .map { it.toChannelVideo(channelInfo) }
                    if (pageVideos.isNotEmpty()) {
                        target.value = target.value.mergeDistinctByNonBlankKey(pageVideos, Video::id)
                    }
                    val pagesLoaded =
                        (if (isLive) livePagesLoaded else videosPagesLoaded) + 1
                    val hasMorePages = more.nextPage != null && pagesLoaded < MAX_PAGES
                    if (isLive) {
                        liveNextPage = if (hasMorePages) more.nextPage else null
                        livePagesLoaded = pagesLoaded
                        _hasMoreLive.value = hasMorePages
                    } else {
                        videosNextPage = if (hasMorePages) more.nextPage else null
                        videosPagesLoaded = pagesLoaded
                        _hasMoreVideos.value = hasMorePages
                    }
                } catch (e: Exception) {
                    // Rate-limited or network error — user keeps whatever loaded so far
                    Log.w(TAG, "More pages stopped after rate limit or error", e)
                    if (isLive) {
                        liveNextPage = null
                        _hasMoreLive.value = false
                    } else {
                        videosNextPage = null
                        _hasMoreVideos.value = false
                    }
                } finally {
                    loadingFlag.value = false
                }
            }
        }

        private fun StreamInfoItem.toChannelVideo(channelInfo: ChannelInfo): Video {
            val videoId =
                when {
                    url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                    url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
                    url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
                    else -> url.substringAfterLast("/").substringBefore("?")
                }
            val thumbnail =
                ThumbnailUrlResolver.normalizeVideoThumbnail(
                    videoId,
                    thumbnails.maxByOrNull { it.width }?.url,
                )
            val absoluteUploadTimestamp = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
            val textualDate = textualUploadDate?.takeIf { it.isNotBlank() }
            val displayUploadDate =
                textualDate
                    ?: com.omersusin.pitube.utils
                        .formatTimeAgo(uploadDate?.offsetDateTime()?.toString())
            val uploadTimestamp =
                absoluteUploadTimestamp
                    ?: parseRelativeUploadDate(textualDate)
                    ?: 0L
            return Video(
                id = videoId,
                title = name,
                thumbnailUrl = thumbnail,
                channelName = uploaderName ?: channelInfo.name,
                channelId = channelInfo.id,
                channelThumbnailUrl =
                    channelInfo.avatars.maxByOrNull { it.height }?.url
                        ?: channelInfo.avatars.firstOrNull()?.url
                        ?: "",
                viewCount = viewCount,
                duration = duration.toInt().coerceAtLeast(0),
                uploadDate = displayUploadDate,
                timestamp = uploadTimestamp,
                description = "",
            )
        }

        private fun parseRelativeUploadDate(text: String?): Long? {
            val normalized =
                text
                    ?.lowercase(Locale.US)
                    ?.replace("streamed", "")
                    ?.replace("premiered", "")
                    ?.replace("live", "")
                    ?.replace("ago", "")
                    ?.trim()
                    ?: return null

            if (normalized.isBlank()) return null
            if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
            if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

            val value =
                Regex("(\\d+)")
                    .find(normalized)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?: return null
            val unitMillis =
                when {
                    normalized.contains("second") || normalized.endsWith("s") -> 1_000L
                    normalized.contains("minute") || normalized.endsWith("m") -> 60_000L
                    normalized.contains("hour") || normalized.endsWith("h") -> 3_600_000L
                    normalized.contains("day") || normalized.endsWith("d") -> 86_400_000L
                    normalized.contains("week") || normalized.endsWith("w") -> 7L * 86_400_000L
                    normalized.contains("month") || normalized.endsWith("mo") -> 30L * 86_400_000L
                    normalized.contains("year") || normalized.endsWith("y") -> 365L * 86_400_000L
                    else -> return null
                }

            return System.currentTimeMillis() - (value * unitMillis)
        }
    }

data class ChannelUiState(
    val channelId: String? = null,
    val channelInfo: ChannelInfo? = null,
    val channelVideos: List<Video> = emptyList(),
    val channelVideoCountText: String? = null,
    val isLoading: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val error: String? = null,
    val videosError: String? = null,
    val isSubscribed: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    /** One-shot friendly error when the YouTube account write failed and the state was rolled back. */
    val subscriptionError: String? = null,
    val selectedTab: Int = 0, // 0: Videos, 1: Shorts, 2: Live, 3: Playlists, 4: Posts, 5: About
    // ── Channel search ──────────────────────────────────────────────────────
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Video> = emptyList(),
    val isSearching: Boolean = false,
    val searchErrorLog: String? = null,
    val searchContinuation: String? = null,
    val isLoadingMoreSearch: Boolean = false,
)

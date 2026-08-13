package com.omersusin.pitube.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omersusin.pitube.data.local.CachedHomeVideo
import com.omersusin.pitube.data.local.HomeFeedCacheFilters
import com.omersusin.pitube.data.local.HomeFeedCacheRepository
import com.omersusin.pitube.data.local.LikedVideosRepository
import com.omersusin.pitube.data.local.PlaylistRepository
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.local.ViewHistory
import com.omersusin.pitube.data.local.VideoHistoryEntry
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.model.toVideo
import com.omersusin.pitube.data.repository.YouTubeRepository
import com.omersusin.pitube.data.shorts.ShortsRepository
import com.omersusin.pitube.R
import com.omersusin.pitube.ui.components.FeedInvalidationBus
import com.omersusin.pitube.utils.PerformanceDispatcher
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ln
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Keeps only visible grid keys that map to real feed videos (drops shelf/loader keys). */
internal fun feedImpressionIds(visibleKeys: List<String>, knownIds: Set<String>): List<String> =
    visibleKeys.filter { it in knownIds }

internal enum class FeedSource {
    PERSONAL,
    SUBS,
    RELATED,
    DISCOVERY,
    VIRAL
}

internal data class FeedCandidate(
    val video: Video,
    val source: FeedSource
)

internal data class FeedMixResult(
    val items: List<FeedCandidate>,
    val sourceCounts: Map<FeedSource, Int>
) {
    val videos: List<Video> get() = items.map { it.video }
}

internal fun homeFeedQuotas(
    remaining: Int,
    subCount: Int,
    totalInteractions: Int,
    hasPersonalFeed: Boolean = false
): Map<FeedSource, Int> {
    val slots = remaining.coerceAtLeast(0)
    if (slots == 0) {
        return FeedSource.entries.associateWith { 0 }
    }

    val personal = if (hasPersonalFeed) (slots * 0.30).toInt().coerceAtLeast(0) else 0
    val remainingAfterPersonal = (slots - personal).coerceAtLeast(0)
    val subs = when {
        subCount <= 0 -> 0
        totalInteractions > 50 -> (remainingAfterPersonal * 0.40).toInt()
        else -> (remainingAfterPersonal * 0.35).toInt()
    }.coerceAtLeast(0)
    val related = when {
        subCount <= 0 -> (remainingAfterPersonal * 0.35).toInt()
        totalInteractions > 50 -> (remainingAfterPersonal * 0.25).toInt()
        else -> (remainingAfterPersonal * 0.30).toInt()
    }.coerceAtLeast(0)
    val discovery = when {
        subCount <= 0 -> (remainingAfterPersonal * 0.45).toInt()
        else -> (remainingAfterPersonal * 0.25).toInt()
    }.coerceAtLeast(0)
    val viral = (remainingAfterPersonal - subs - related - discovery).coerceAtLeast(0)

    return mapOf(
        FeedSource.PERSONAL to personal,
        FeedSource.SUBS to subs,
        FeedSource.RELATED to related,
        FeedSource.DISCOVERY to discovery,
        FeedSource.VIRAL to viral
    )
}

internal fun addUniqueVideo(
    video: Video?,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2
): Boolean {
    if (video == null) return false

    val hasChannel = video.channelId.isNotBlank()
    val count = channelCounts[video.channelId] ?: 0
    if (hasChannel && count >= maxPerChannel) return false
    if (!usedVideoIds.add(video.id)) return false
    targetList.add(video)
    if (hasChannel) channelCounts[video.channelId] = count + 1
    return true
}

internal fun addUniquePageVideos(
    candidates: Iterable<Video>,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    targetSize: Int,
    maxPerChannel: Int = 2
): Int {
    var added = 0
    for (candidate in candidates) {
        if (targetList.size >= targetSize) break
        if (addUniqueVideo(candidate, targetList, channelCounts, usedVideoIds, maxPerChannel)) {
            added++
        }
    }
    return added
}

private fun addUniqueCandidate(
    candidate: FeedCandidate?,
    targetList: MutableList<FeedCandidate>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2
): Boolean {
    if (candidate == null) return false
    val temp = mutableListOf<Video>()
    if (!addUniqueVideo(candidate.video, temp, channelCounts, usedVideoIds, maxPerChannel)) return false
    targetList.add(candidate)
    return true
}

internal fun blendFeedSources(
    lanes: Map<FeedSource, List<Video>>,
    quotas: Map<FeedSource, Int>,
    targetSize: Int,
    channelCounts: MutableMap<String, Int> = mutableMapOf(),
    usedVideoIds: MutableSet<String> = mutableSetOf()
): FeedMixResult {
    val target = targetSize.coerceAtLeast(0)
    if (target == 0) return FeedMixResult(emptyList(), emptyMap())

    val queues = FeedSource.entries.associateWith { source ->
        java.util.ArrayDeque(lanes[source].orEmpty().map { FeedCandidate(it, source) })
    }
    val quotaOrder = listOf(
        FeedSource.PERSONAL,
        FeedSource.SUBS,
        FeedSource.RELATED,
        FeedSource.DISCOVERY,
        FeedSource.VIRAL
    )
    val scarcityOrder = listOf(
        FeedSource.RELATED,
        FeedSource.DISCOVERY,
        FeedSource.PERSONAL,
        FeedSource.SUBS,
        FeedSource.VIRAL
    )
    val addedBySource = mutableMapOf<FeedSource, Int>()
    val out = mutableListOf<FeedCandidate>()

    while (out.size < target && queues.any { it.value.isNotEmpty() }) {
        var addedThisRound = false
        for (source in quotaOrder) {
            if (out.size >= target) break
            val added = addedBySource[source] ?: 0
            val quota = quotas[source] ?: 0
            if (added < quota && addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds)) {
                addedBySource[source] = added + 1
                addedThisRound = true
            }
        }

        if (!addedThisRound) {
            val forced = scarcityOrder.any { source ->
                if (out.size >= target) true
                else addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds).also { added ->
                    if (added) addedBySource[source] = (addedBySource[source] ?: 0) + 1
                }
            }
            if (!forced) break
        }
    }

    return FeedMixResult(
        items = out,
        sourceCounts = FeedSource.entries.associateWith { source -> addedBySource[source] ?: 0 }
    )
}

/**
 * Greedy reorder that keeps same-channel items at least `gap` slots apart when possible; order is
 * otherwise preserved. seedRecent primes the cooldown with the prior page's tail to space appends.
 */
internal fun spaceByChannel(
    videos: List<Video>, gap: Int = 1, seedRecent: List<String> = emptyList()
): List<Video> {
    if (videos.size < 2) return videos
    val remaining = videos.toMutableList()
    val out = ArrayList<Video>(videos.size)
    val recent = ArrayDeque<String>()
    seedRecent.takeLast(gap).forEach { recent.addLast(it) }
    while (remaining.isNotEmpty()) {
        val idx = remaining.indexOfFirst { it.channelId.isBlank() || it.channelId !in recent }
            .let { if (it < 0) 0 else it }
        val pick = remaining.removeAt(idx)
        out.add(pick)
        if (pick.channelId.isNotBlank()) {
            recent.addLast(pick.channelId)
            while (recent.size > gap) recent.removeFirst()
        }
    }
    return out
}

private fun Video.withChannelMetadataFrom(enriched: Video): Video {
    val avatarUrl = enriched.channelThumbnailUrl.ifBlank { channelThumbnailUrl }
    return copy(
        channelId = enriched.channelId.ifBlank { channelId },
        channelName = enriched.channelName.ifBlank { channelName },
        channelThumbnailUrl = avatarUrl,
        channelThumbnailUrls = if (avatarUrl.isNotBlank()) {
            (listOf(avatarUrl) +
                enriched.channelThumbnailUrls +
                channelThumbnailUrls).distinct()
        } else {
            channelThumbnailUrls
        }
    )
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: com.omersusin.pitube.data.local.PlayerPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
        private const val HOME_TARGET_SIZE = 40
        private const val FRESH_SUB_WINDOW_MS = 72L * 60L * 60L * 1000L
        private const val HOME_MAX_SUGGESTION_AGE_MS = 365L * 24L * 60L * 60L * 1000L
        private const val MIN_PAGE_SIZE = 8
        private const val DISCOVERY_ORDER_KEY = "discovery_order"
        private const val DISCOVERY_EPOCH_KEY = "discovery_epoch"
    }

    // Broad discovery searches used to fill the home feed when there are no (or few)
    // subscriptions. Plain queries — no engine involved.
    private val DISCOVERY_QUERIES = listOf(
        "viral", "trending now", "popular videos", "new releases", "best of the week",
        "trending today", "viral videos", "music videos", "funny videos", "amazing videos",
        "documentary", "gaming highlights", "science explained", "top 10", "daily news"
    )

    // Saved-interest enrichment sources + per-seed cooldown.
    private val likedVideosRepository by lazy { LikedVideosRepository.getInstance(appContext) }
    private val playlistRepository by lazy { PlaylistRepository(appContext) }
    private val historyRepository by lazy { ViewHistory.getInstance(appContext) }
    private val persistentHomeFeedCache by lazy { HomeFeedCacheRepository(appContext) }
    private val channelMetadataEnrichmentInFlight =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
        .map(HomeUiState::withUniqueLazyContent)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _uiState.value.withUniqueLazyContent()
        )
    
    private var currentPage: Page? = null
    private var isInitialized = false
    private val homePrefetchQueue = HomePrefetchQueue()
    private val homePrefetchWorkerLock = Any()
    private var homePrefetchJob: Job? = null

    private var subsBacklog: List<Video> = emptyList()
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    private var wave2Job: Job? = null

    private var viewHistory: ViewHistory? = null

    private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())

    private val discoveryPrefs by lazy {
        appContext.getSharedPreferences("home_feed_rotation", Context.MODE_PRIVATE)
    }
    private var discoveryRotationEpoch = 0

    /**
     * Fill [discoveryQueries] and reset [currentQueryIndex]. On a forced
     * refresh the query list is reshuffled (and the persisted epoch bumped) so
     * consecutive pulls surface different items; otherwise the last shuffled
     * order is reused to avoid reshuffling mid-session churn.
     */
    private fun seedDiscoveryQueries(shuffle: Boolean) {
        val persisted = discoveryPrefs
            .getString(DISCOVERY_ORDER_KEY, null)
            ?.split(',')
            ?.takeIf { it.size == DISCOVERY_QUERIES.size }
        val seeded =
            if (shuffle) {
                val epoch = discoveryPrefs.getInt(DISCOVERY_EPOCH_KEY, 0)
                discoveryRotationEpoch = epoch
                DISCOVERY_QUERIES.shuffled(Random(epoch.toLong()))
            } else {
                persisted ?: DISCOVERY_QUERIES
            }
        discoveryQueries.clear()
        discoveryQueries.addAll(seeded)
        if (shuffle) {
            discoveryPrefs.edit()
                .putString(DISCOVERY_ORDER_KEY, seeded.joinToString(","))
                .putInt(DISCOVERY_EPOCH_KEY, discoveryRotationEpoch + 1)
                .apply()
        }
        currentQueryIndex = 0
    }

    init {
        if (HomeFeedCache.isFresh()) {
            _uiState.update {
                it.copy(
                    videos = HomeFeedCache.videos,
                    shorts = HomeFeedCache.shorts,
                    isLoading = false,
                    isFlowFeed = true,
                    lastRefreshTime = HomeFeedCache.timestamp
                )
            }
            // Show the cached feed instantly, but still refresh in the
            // background when the cache is older than a minute so consecutive
            // visits don't show the same videos all day.
            if (System.currentTimeMillis() - HomeFeedCache.timestamp > FEED_BACKGROUND_REFRESH_AFTER_MS) {
                loadFlowFeed()
            }
        } else {
            hydratePersistentHomeFeed()
            loadFlowFeed(forceRefresh = true)
            loadHomeShorts()
        }
    }
    

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        viewHistory = ViewHistory.getInstance(context)
        
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            combine(
                viewHistory!!.getVideoHistoryFlow(),
                playerPreferences.hideWatchedVideosFromHome,
                playerPreferences.watchedThreshold,
                playerPreferences.continueWatchingEnabled
            ) { history, hideWatched, threshold, continueWatchingEnabled ->
                filterHomeHistory(
                    history = history,
                    hideWatchedVideos = hideWatched,
                    watchedThreshold = threshold,
                    continueWatchingEnabled = continueWatchingEnabled
                )
            }.collect { result ->
                watchedVideoIds.value = result.watchedVideoIds
                _uiState.update { state ->
                    val videos = state.videos.filterWatched(result.watchedVideoIds)
                    val shorts = state.shorts.filterWatched(result.watchedVideoIds)
                    if (videos != state.videos || shorts != state.shorts) {
                        HomeFeedCache.update(videos, shorts)
                    }
                    state.copy(
                        videos = videos,
                        shorts = shorts,
                        continueWatchingVideos = result.continueWatchingVideos
                    )
                }
            }
        }

        viewModelScope.launch {
            FeedInvalidationBus.events.collect { event ->
                when (event) {
                    is FeedInvalidationBus.Event.ChannelBlocked -> {
                        HomeFeedCache.filterOut(channelId = event.channelId)
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            persistentHomeFeedCache.deleteChannel(event.channelId)
                            persistentHomeFeedCache.deleteVideo(event.videoId)
                        }
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter {
                                    it.id != event.videoId && it.channelId != event.channelId
                                },
                                shorts = state.shorts.filter {
                                    it.id != event.videoId && it.channelId != event.channelId
                                }
                            )
                        }
                        shortsRepository.evictChannel(event.channelId)
                    }
                    is FeedInvalidationBus.Event.MarkedWatched -> {
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            persistentHomeFeedCache.deleteVideo(event.videoId)
                        }
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter { it.id != event.videoId },
                                shorts = state.shorts.filter { it.id != event.videoId }
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            playerPreferences.homeShortsShelfEnabled.collect { enabled ->
                if (!enabled) {
                    _uiState.update { it.copy(shorts = emptyList()) }
                } else if (_uiState.value.shorts.isEmpty()) {
                    loadHomeShorts()
                }
            }
        }

    }

    fun onHomeVisible() {
        val state = _uiState.value
        startHomePrefetch(
            homePrefetchQueue.onVisible(
                currentVideoCount = state.videos.size,
                feedReady = state.isReadyForPrefetch()
            )
        )
    }

    fun onHomeHidden() {
        homePrefetchQueue.onHidden()
        synchronized(homePrefetchWorkerLock) {
            homePrefetchJob?.cancel()
        }

        wave2Job?.cancel()
        _uiState.update { it.copy(isLoadingMore = false) }
    }

    fun onHomeViewportChanged(lastVisibleVideoIndex: Int) {
        val state = _uiState.value
        if (!state.isReadyForPrefetch()) return
        startHomePrefetch(
            homePrefetchQueue.onViewportChanged(
                currentVideoCount = state.videos.size,
                lastVisibleVideoIndex = lastVisibleVideoIndex
            )
        )
    }

    private fun HomeUiState.isReadyForPrefetch(): Boolean =
        videos.isNotEmpty() && !isLoading && isFlowFeed && hasMorePages

    private fun startHomePrefetch(request: HomePrefetchRequest?) {
        request ?: return
        val worker = synchronized(homePrefetchWorkerLock) {
            if (homePrefetchJob?.isCompleted == false) return
            viewModelScope.launch(
                context = PerformanceDispatcher.networkIO,
                start = CoroutineStart.LAZY
            ) {
                drainHomePrefetchQueue(request.generation)
            }.also { homePrefetchJob = it }
        }
        worker.start()
    }

    private suspend fun drainHomePrefetchQueue(generation: Int) {
        var pagesLoaded = 0
        var allowRestart = true
        try {
            wave2Job?.takeIf { it.isActive }?.join()
            while (pagesLoaded < HOME_PREFETCH_MAX_PAGES_PER_RUN) {
                val state = _uiState.value
                val request = homePrefetchQueue.currentRequest(state.videos.size) ?: break
                if (request.generation != generation || !state.hasMorePages) break

                _uiState.update { it.copy(isLoadingMore = true) }
                if (!loadNextPrefetchPage(generation)) {
                    allowRestart = false
                    break
                }
                pagesLoaded++
            }
            if (pagesLoaded >= HOME_PREFETCH_MAX_PAGES_PER_RUN) {
                allowRestart = false
            }
        } catch (cancellation: CancellationException) {
            allowRestart = false
            throw cancellation
        } finally {
            val workerJob = currentCoroutineContext()[Job]
            val ownsLoadingState = synchronized(homePrefetchWorkerLock) {
                if (homePrefetchJob === workerJob) {
                    homePrefetchJob = null
                    true
                } else {
                    false
                }
            }
            if (ownsLoadingState) {
                _uiState.update { it.copy(isLoadingMore = false) }
                if (allowRestart) {
                    startHomePrefetch(homePrefetchQueue.currentRequest(_uiState.value.videos.size))
                }
            }
        }
    }

    private fun resetHomePrefetch() {
        homePrefetchQueue.reset()
        synchronized(homePrefetchWorkerLock) {
            homePrefetchJob?.cancel()
        }
        _uiState.update { it.copy(isLoadingMore = false) }
    }

    fun removeContinueWatchingEntry(videoId: String) {
        viewModelScope.launch {
            viewHistory?.clearVideoHistory(videoId)
        }
    }

    private fun loadHomeShorts() {
        viewModelScope.launch {
            if (!playerPreferences.homeShortsShelfEnabled.first()) return@launch
            try {
                val shorts = shortsRepository.getHomeFeedShorts().map { it.toVideo() }
                if (shorts.isNotEmpty()) {
                    _uiState.update {
                        it.copy(shorts = shorts.filterWatched(watchedVideoIds.value))
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun cacheFilters(): HomeFeedCacheFilters =
        HomeFeedCacheFilters(
            watchedVideoIds = watchedVideoIds.value,
            blockedChannelIds = playerPreferences.blockedChannelIds.first(),
        )

    private fun hydratePersistentHomeFeed() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val cached = runCatching {
                persistentHomeFeedCache.loadLastFeed(cacheFilters())
            }.getOrElse { emptyList() }
            if (cached.isEmpty()) return@launch
            val hydratedCached = repository.enrichLikelyCollabAvatarStacks(cached, limit = 8)

            _uiState.update { state ->
                if (state.videos.isNotEmpty()) return@update state
                val videos = hydratedCached.filterWatched(watchedVideoIds.value)
                HomeFeedCache.update(videos, state.shorts)
                state.copy(
                    videos = videos,
                    isFlowFeed = true,
                    error = null,
                    lastRefreshTime = System.currentTimeMillis()
                )
            }
            enrichVisibleChannelMetadata(hydratedCached)?.let {
                persistentHomeFeedCache.saveLastFeed(it)
            }
        }
    }
    

    private fun updateVideosAndShorts(newVideos: List<Video>, append: Boolean = false) {
        val (newShorts, regularVideos) = newVideos.partition { 
            it.isShort || (it.duration in 1..120) || (it.duration == 0 && !it.isLive)
        }
        
        _uiState.update { state ->
            val watched = watchedVideoIds.value
            val updatedVideos = if (append) (state.videos + regularVideos) else regularVideos
            state.copy(
                videos = updatedVideos.distinctBy { it.id }.filterWatched(watched),
                shorts = (state.shorts + newShorts).distinctBy { it.id }.filterWatched(watched)
                    .sortedByDescending { it.timestamp }
            )
        }
    }

    
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        wave2Job?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                // Rotate the anonymous visitor identity on a forced refresh/pull
                // so the personalized "what to watch" lane stops pinning the same
                // items week after week (the visitor id anchors its response).
                if (forceRefresh) {
                    runCatching { com.omersusin.pitube.innertube.YouTube.rotateVisitorData() }
                    seedDiscoveryQueries(shuffle = true)
                } else {
                    seedDiscoveryQueries(shuffle = false)
                }

                val signedIn = !com.omersusin.pitube.innertube.YouTube.cookie.isNullOrBlank()

                // ── Signed-in lane: the account's own "What to watch" feed ──
                // Fetched in parallel with discovery below; blended into the mix
                // instead of short-circuiting so the feed rotates through fresh
                // items instead of repeating the same personalized set.
                val personalizedPool = if (signedIn) {
                    withTimeoutOrNull(12_000L) {
                        com.omersusin.pitube.innertube.YouTube.personalizedFeed()
                            .getOrNull()
                            ?.videos
                            .orEmpty()
                            .filterValid()
                            .filterWatched(watchedVideoIds.value)
                            .filterRecentHomeSuggestion(System.currentTimeMillis())
                    } ?: emptyList()
                } else {
                    emptyList()
                }

                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = playerPreferences.trendingRegion.first()
                val fetchStart = System.currentTimeMillis()

                // ── Wave 1: first 2 queries + subs + trending ──
                val wave1QueryCount = discoveryQueries.size.coerceAtMost(2)
                val wave1Queries = discoveryQueries.take(wave1QueryCount)
                currentQueryIndex = wave1QueryCount

                val results = supervisorScope {
                    val deferredSubs = async {
                        if (userSubs.isNotEmpty()) {
                            withTimeoutOrNull(8_000L) {
                                runCatching {
                                    repository.getSubscriptionFeed(userSubs.toList())
                                }.getOrElse { emptyList() }
                            } ?: emptyList()
                        } else emptyList()
                    }

                    val deferredDiscovery = async {
                        wave1Queries.map { query ->
                            async { 
                                runCatching { 
                                    repository.searchVideos(query).first
                                }.getOrElse { emptyList() }
                            }
                        }.awaitAll().flatten()
                    }
                    
                    val deferredViral = async {
                        runCatching {
                             repository.getTrendingVideos(region).first
                        }.getOrElse { emptyList() }
                    }

                    // ── Fast first paint ────────────────────────────────────────
                    val viralResult = deferredViral.await()
                    if (viralResult.isNotEmpty() && userSubs.isEmpty()) {
                        val watched = watchedVideoIds.value
                        val quickFeed = viralResult.filterValid()
                            .filterWatched(watched)
                            .filterRecentHomeSuggestion(System.currentTimeMillis())
                            .take(15)
                        if (quickFeed.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    videos = quickFeed.filterWatched(watchedVideoIds.value),
                                    isLoading = true,
                                    isFlowFeed = true
                                )
                            }
                        }
                    }

                    Wave1FeedResults(
                        subs = deferredSubs.await(),
                        discovery = deferredDiscovery.await(),
                        viral = viralResult
                    )
                }

                val rawSubs = results.subs
                val rawDiscovery = results.discovery
                val rawViral = results.viral

                Log.d(TAG, "Wave 1 fetch completed in ${System.currentTimeMillis() - fetchStart}ms")

                val subAvatarMap: Map<String, String> = runCatching {
                    subscriptionRepository.getAllSubscriptions().first()
                        .filter { it.channelThumbnail.isNotEmpty() }
                        .associate { it.channelId to it.channelThumbnail }
                }.getOrElse { emptyMap() }

                fun List<Video>.enrichAvatars(): List<Video> =
                    if (subAvatarMap.isEmpty()) this
                    else map { v ->
                        if (v.channelThumbnailUrl.isEmpty() && subAvatarMap.containsKey(v.channelId))
                            v.copy(
                                channelThumbnailUrl = subAvatarMap.getValue(v.channelId),
                                channelThumbnailUrls = v.channelThumbnailUrls.ifEmpty {
                                    listOf(subAvatarMap.getValue(v.channelId))
                                }
                            )
                        else v
                    }

                val now = System.currentTimeMillis()

                val feedShorts = (rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)
                if (feedShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + feedShorts).distinctBy { it.id })
                    }
                }
                
                // Filter to regular videos for the main feed
                val watched = watchedVideoIds.value
                val subsPool = rawSubs.filterValid().filterWatched(watched).enrichAvatars()
                val discoveryPool = rawDiscovery.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)
                val viralPool = rawViral.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)

                Log.d(
                    TAG,
                    "Flow candidates: subs=${subsPool.size}, discovery=${discoveryPool.size}, viral=${viralPool.size}, subCount=${userSubs.size}"
                )

                val subsByRecency = subsPool.sortedByDescending { it.timestamp }
                val freshSlotTarget = dynamicFreshSubSlots(userSubs.size)
                val freshSubsLane = subsByRecency
                    .filter { isFreshSubscribedCandidate(it, now) }
                    .take(freshSlotTarget)
                val freshIds = freshSubsLane.map { it.id }.toHashSet()

                val bestSubs = subsByRecency
                    .filter { !freshIds.contains(it.id) }
                    .take(15)

                val bestDiscovery = discoveryPool.take(15)
                val bestViral = viralPool.take(6)

                val finalMix = mutableListOf<Video>()
                val usedChannelCounts = mutableMapOf<String, Int>()
                val usedVideoIds = mutableSetOf<String>()
                var freshAdded = 0

                freshSubsLane.forEach { video ->
                    if (addUnique(video, finalMix, usedChannelCounts, usedVideoIds)) freshAdded++
                }

                val remaining = (HOME_TARGET_SIZE - finalMix.size).coerceAtLeast(0)
                val quotas = homeFeedQuotas(
                    remaining,
                    userSubs.size,
                    0,
                    hasPersonalFeed = personalizedPool.isNotEmpty()
                )
                val bestPersonal = personalizedPool.take(12)
                val sourceMix = blendFeedSources(
                    lanes = mapOf(
                        FeedSource.PERSONAL to bestPersonal,
                        FeedSource.SUBS to bestSubs,
                        FeedSource.DISCOVERY to bestDiscovery,
                        FeedSource.VIRAL to bestViral
                    ),
                    quotas = quotas,
                    targetSize = remaining,
                    channelCounts = usedChannelCounts,
                    usedVideoIds = usedVideoIds
                )
                finalMix += sourceMix.videos

                subsBacklog = subsByRecency.filterNot { usedVideoIds.contains(it.id) }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                Log.d(
                    TAG,
                    "Flow mix: freshLane=$freshAdded, final=${finalMix.size}, quotas=${quotas}, selected=${sourceMix.sourceCounts}"
                )

                val spacedMix = repository.enrichLikelyCollabAvatarStacks(
                    spaceByChannel(finalMix),
                    limit = 8
                )
                val renderedIds = spacedMix.mapTo(HashSet()) { it.id }
                val reserveCandidates =
                    cacheCandidates(FeedSource.PERSONAL, bestPersonal, renderedIds) +
                    cacheCandidates(FeedSource.DISCOVERY, bestDiscovery, renderedIds) +
                    cacheCandidates(FeedSource.SUBS, bestSubs, renderedIds) +
                    cacheCandidates(FeedSource.VIRAL, bestViral, renderedIds)
                var visibleFeed = emptyList<Video>()
                _uiState.update { state ->
                    visibleFeed = spacedMix.filterWatched(watchedVideoIds.value)
                    state.copy(
                        videos = visibleFeed,
                        isLoading = false,
                        isRefreshing = false,
                        hasMorePages = true,
                        isFlowFeed = true,
                        feedContinuation = null,
                        lastRefreshTime = now
                    )
                }
                HomeFeedCache.update(visibleFeed, _uiState.value.shorts)
                persistentHomeFeedCache.saveLastFeed(spacedMix)
                persistentHomeFeedCache.saveReserve(reserveCandidates)
                enrichVisibleChannelMetadata(spacedMix)?.let {
                    persistentHomeFeedCache.saveLastFeed(it)
                }

                // ── Wave 2: remaining queries loaded in background ──
                val wave2Queries = discoveryQueries.drop(currentQueryIndex)
                if (wave2Queries.isNotEmpty()) {
                    val wave2FinalMixIds = finalMix.map { it.id }.toHashSet()
                    wave2Job = viewModelScope.launch(PerformanceDispatcher.networkIO) wave2@{
                        try {
                            val wave2Raw = wave2Queries.map { q ->
                                async {
                                    withTimeoutOrNull(6_000L) {
                                        try {
                                            repository.searchVideos(q).first
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (error: Exception) {
                                            Log.d(TAG, "Wave 2 query failed for $q: ${error.message}")
                                            emptyList()
                                        }
                                    } ?: emptyList()
                                }
                            }.awaitAll().flatten()

                            val wave2Watched = watchedVideoIds.value
                            val wave2Valid = wave2Raw.filterValid().filterWatched(wave2Watched)
                                .filter { !wave2FinalMixIds.contains(it.id) }
                            if (wave2Valid.isEmpty()) return@wave2

                            val wave2Ranked = wave2Valid.take(15)

                            if (wave2Ranked.isNotEmpty()) {
                                var updatedSnapshot: List<Video>? = null
                                _uiState.update { state ->
                                    val currentIds = state.videos.map { it.id }.toHashSet()
                                    val uniqueNew = wave2Ranked
                                        .filterWatched(watchedVideoIds.value)
                                        .filter { !currentIds.contains(it.id) }
                                        .distinctBy { it.channelId }
                                    if (uniqueNew.isEmpty()) return@update state
                                    val updated = state.videos + uniqueNew
                                    updatedSnapshot = updated
                                    HomeFeedCache.update(updated, state.shorts)
                                    state.copy(videos = updated)
                                }
                                updatedSnapshot?.let { persistentHomeFeedCache.saveLastFeed(it) }
                                currentQueryIndex = discoveryQueries.size
                                Log.d(TAG, "Wave 2 merged ${wave2Ranked.size} extra candidates")
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            Log.d(TAG, "Wave 2 failed: ${error.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = appContext.getString(R.string.error_failed_to_load_feed)) }
                 loadTrendingFallback() 
            }
        }
    }
    

    private suspend fun loadNextPrefetchPage(generation: Int): Boolean {
        try {
                val now = System.currentTimeMillis()
                val currentIds = _uiState.value.videos.map { it.id }.toHashSet()

                // ── Signed-in lane: next page of the personalized feed ──
                val continuation = _uiState.value.feedContinuation
                if (continuation != null) {
                    val next = runCatching {
                        com.omersusin.pitube.innertube.YouTube.personalizedFeedContinuation(continuation).getOrNull()
                    }.getOrNull()
                    if (next != null && next.videos.isNotEmpty()) {
                        _uiState.update { state ->
                            state.copy(
                                feedContinuation = next.continuation,
                                hasMorePages = next.continuation != null
                            )
                        }
                        val appended = appendLoadMorePage(next.videos, generation)
                        appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                        return appended != null
                    }
                    _uiState.update { state -> state.copy(hasMorePages = false) }
                    return false
                }

                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val page = mutableListOf<Video>()
                val channelCounts = HashMap<String, Int>()
                val pageIds = HashSet<String>(currentIds)

                val reserveVideos = try {
                    persistentHomeFeedCache.loadReservePage(cacheFilters()).map { it.video }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.d(TAG, "Reserve prefetch unavailable: ${error.message}")
                    emptyList()
                }
                    .filterValid()
                    .filterRecentHomeSuggestion(now)
                val reserveAdded = addUniquePageVideos(
                    candidates = reserveVideos,
                    targetList = page,
                    channelCounts = channelCounts,
                    usedVideoIds = pageIds,
                    targetSize = MIN_PAGE_SIZE
                )
                if (page.size >= MIN_PAGE_SIZE) {
                    val appended = appendLoadMorePage(page, generation)
                    appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    Log.d(TAG, "Load-more filled from reserve: +$reserveAdded")
                    return appended != null
                }

                if (currentQueryIndex >= discoveryQueries.size) {
                    discoveryQueries.addAll(DISCOVERY_QUERIES)
                }
                
                val queryA = discoveryQueries.getOrNull(currentQueryIndex++)
                val queryB = discoveryQueries.getOrNull(currentQueryIndex++)
                
                val searchQueries = listOfNotNull(queryA, queryB)
                
                val finalQueries = if (searchQueries.isEmpty()) listOf("Viral") else searchQueries

                val rawVideos = coroutineScope {
                    finalQueries.map { query ->
                        async {
                            withTimeoutOrNull(6_000L) {
                                try {
                                    repository.searchVideos(query).first
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Exception) {
                                    Log.d(TAG, "Prefetch query failed for $query: ${error.message}")
                                    emptyList()
                                }
                            } ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
                if (!homePrefetchQueue.isCurrent(generation)) return false

                // Extract shorts for shelf
                val moreShorts = rawVideos.extractShorts()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + moreShorts).distinctBy { it.id })
                    }
                }
                
                val newVideos = rawVideos.filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)

                if (newVideos.isNotEmpty()) {
                    addUniquePageVideos(
                        candidates = newVideos,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE
                    )
                    persistentHomeFeedCache.saveReserve(
                        cacheCandidates(FeedSource.DISCOVERY, newVideos, pageIds)
                    )
                }

                if (page.size < MIN_PAGE_SIZE && subsBacklog.isNotEmpty()) {
                    addUniquePageVideos(
                        candidates = subsBacklog,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE
                    )
                    subsBacklog = subsBacklog.filterNot { pageIds.contains(it.id) }
                }

                if (page.isNotEmpty()) {
                    val appended = appendLoadMorePage(page, generation)
                    appended?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    return appended != null
                }
                return false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.d(TAG, "Home prefetch page failed: ${error.message}")
            return false
        }
    }

    private suspend fun appendLoadMorePage(
        page: List<Video>,
        generation: Int
    ): List<Video>? {
        if (page.isEmpty() || !homePrefetchQueue.isCurrent(generation)) return null
        var updatedSnapshot: List<Video>? = null
        var appendedPage = emptyList<Video>()
        _uiState.update { state ->
            if (!homePrefetchQueue.isCurrent(generation)) return@update state
            val existingVideoIds = state.videos.mapTo(HashSet()) { it.id }
            appendedPage = page
                .filterWatched(watchedVideoIds.value)
                .filterNot { it.id in existingVideoIds }
            if (appendedPage.isEmpty()) return@update state
            val tailChannels = state.videos.takeLast(2).map { it.channelId }
            val updated = state.videos + spaceByChannel(appendedPage, seedRecent = tailChannels)
            updatedSnapshot = updated
            HomeFeedCache.update(updated, state.shorts)
            state.copy(
                videos = updated,
                hasMorePages = true
            )
        }
        if (appendedPage.isEmpty()) return null
        return enrichVisibleChannelMetadata(appendedPage) ?: updatedSnapshot
    }

    private suspend fun enrichVisibleChannelMetadata(videos: List<Video>): List<Video>? {
        val enriched = repository.enrichMissingChannelMetadata(videos)
        if (enriched == videos) return null

        val originalById = videos.associateBy { it.id }
        val updates = enriched
            .filter { enrichedVideo -> originalById[enrichedVideo.id] != enrichedVideo }
            .associateBy { it.id }
        var updatedSnapshot: List<Video>? = null
        _uiState.update { state ->
            val updated = state.videos.map { current ->
                updates[current.id]?.let(current::withChannelMetadataFrom) ?: current
            }
            if (updated == state.videos) return@update state
            updatedSnapshot = updated
            HomeFeedCache.update(updated, state.shorts)
            state.copy(videos = updated)
        }
        return updatedSnapshot
    }

    fun enrichChannelMetadataIfMissing(videoId: String) {
        val video = _uiState.value.videos.firstOrNull { it.id == videoId } ?: return
        val needsMetadata = video.channelId.isBlank() ||
            !video.channelId.startsWith("UC") ||
            video.channelThumbnailUrl.isBlank()
        if (!needsMetadata || !channelMetadataEnrichmentInFlight.add(videoId)) return

        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val enriched = repository.enrichMissingChannelMetadata(listOf(video), limit = 1)
                    .firstOrNull()
                    ?: return@launch
                if (enriched == video) return@launch

                _uiState.update { state ->
                    val updated = state.videos.map { current ->
                        if (current.id != videoId) {
                            current
                        } else {
                            current.withChannelMetadataFrom(enriched)
                        }
                    }
                    if (updated == state.videos) state else {
                        HomeFeedCache.update(updated, state.shorts)
                        state.copy(videos = updated)
                    }
                }
            } finally {
                channelMetadataEnrichmentInFlight.remove(videoId)
            }
        }
    }
    

    fun loadTrendingVideos() {
        if (_uiState.value.isLoading && _uiState.value.videos.isEmpty()) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val region = playerPreferences.trendingRegion.first()
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage

                updateVideosAndShorts(
                    videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
                    append = false
                )

                _uiState.update { it.copy(
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    isFlowFeed = false
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: appContext.getString(R.string.error_failed_to_load_videos)
                ) }
            }
        }
    }

    private suspend fun loadTrendingFallback() {
        val region = playerPreferences.trendingRegion.first()
        val (videos, nextPage) = repository.getTrendingVideos(region, null)
        currentPage = nextPage

        updateVideosAndShorts(
            videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
            append = false
        )
        _uiState.update { it.copy(
            isLoading = false,
            hasMorePages = nextPage != null,
            isFlowFeed = false,
            error = null
        )}
    }
    
    fun refreshFeed() {
        resetHomePrefetch()
        wave2Job?.cancel()
        HomeFeedCache.clear()
        _uiState.update { it.copy(isRefreshing = true) }
        loadFlowFeed(forceRefresh = true)
    }
    
    fun retry() {
        resetHomePrefetch()
        wave2Job?.cancel()
        loadFlowFeed(forceRefresh = true)
    }

    private fun cacheCandidates(
        source: FeedSource,
        videos: List<Video>,
        excludedIds: Set<String> = emptySet()
    ): List<CachedHomeVideo> =
        videos.asSequence()
            .filterNot { it.id in excludedIds }
            .distinctBy { it.id }
            .map { CachedHomeVideo(it, source.name) }
            .toList()

    private fun addUnique(
        video: Video?, 
        targetList: MutableList<Video>, 
        channelCounts: MutableMap<String, Int>,
        usedVideoIds: MutableSet<String>,
        maxPerChannel: Int = 2
    ): Boolean = addUniqueVideo(video, targetList, channelCounts, usedVideoIds, maxPerChannel)

    // Viewport impressions: count only items actually scrolled into view.
    fun recordImpressions(visibleKeys: List<String>) {
        // Interaction tracking removed with the recommendation engine.
    }

    private fun dynamicFreshSubSlots(subCount: Int): Int {
        return when {
            subCount >= 120 -> 5
            subCount >= 40 -> 4
            subCount >= 5 -> 3
            else -> 2
        }
    }

    private fun isFreshSubscribedCandidate(video: Video, now: Long): Boolean {
        val ageByTimestamp = now - video.timestamp
        if (ageByTimestamp in 0..FRESH_SUB_WINDOW_MS) return true

        val text = video.uploadDate.lowercase()
        if (text.contains("second") || text.contains("minute") || text.contains("hour")) {
            return true
        }

        if (text.contains("day")) {
            val days = text.filter { it.isDigit() }.toIntOrNull() ?: 1
            return days <= 3
        }

        return false
    }
    
    private fun List<Video>.filterValid(): List<Video> {
        return this.filter { 
            !it.isShort && 
            ((it.duration > 120) || (it.duration == 0 && it.isLive)) 
        }
    }

    /**
     * Filter that extracts shorts from a video list for the shelf.
     * Complements filterValid() by capturing what it discards.
     */
    private fun List<Video>.extractShorts(): List<Video> {
        return this.filter { 
            it.isShort || (it.duration in 1..120 && !it.isLive)
        }
    }

    private fun List<Video>.filterRecentHomeSuggestion(now: Long): List<Video> =
        filter { video -> isRecentHomeSuggestion(video, now) }

    private fun isRecentHomeSuggestion(video: Video, now: Long): Boolean {
        val text = video.uploadDate.lowercase()
        if (text.isBlank() || text == "unknown") return video.isLive

        val age = now - video.timestamp
        if (age in 0..HOME_MAX_SUGGESTION_AGE_MS) return true

        val value = text.filter { it.isDigit() }.toIntOrNull() ?: 1
        return when {
            text.contains("second") || text.contains("minute") || text.contains("hour") -> true
            text.contains("day") -> value <= 365
            text.contains("week") -> value <= 52
            text.contains("month") -> value <= 12
            text.contains("year") -> value <= 1
            else -> false
        }
    }

    /**
     * Remove videos the user has already fully watched (≥90 % progress)
     * so they don't re-appear in the home feed.
     */
    private fun List<Video>.filterWatched(watchedIds: Set<String>): List<Video> {
        if (watchedIds.isEmpty()) return this
        return this.filter { !watchedIds.contains(it.id) }
    }
}

private data class Wave1FeedResults(
    val subs: List<Video>,
    val discovery: List<Video>,
    val viral: List<Video>
)

private const val FEED_BACKGROUND_REFRESH_AFTER_MS = 60 * 1000L // 1 minute

/**
 * Process-lifetime in-memory cache for the Home feed.
 *
 * Survives ViewModel recreation (which happens when the user navigates away
 * from Home and comes back via the bottom nav), preventing an unwanted
 * network reload on every tab switch. The cache expires after [CACHE_TTL_MS]
 * (3 minutes — short on purpose so the feed keeps rotating; the UI always
 * refreshes in the background after publishing the cached list).
 */
internal object HomeFeedCache {
    private const val CACHE_TTL_MS = 3 * 60 * 1000L // 3 minutes

    @Volatile var videos: List<Video> = emptyList()
        private set
    @Volatile var shorts: List<Video> = emptyList()
        private set
    @Volatile var timestamp: Long = 0L
        private set

    fun isFresh(): Boolean =
        videos.isNotEmpty() && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS

    fun update(newVideos: List<Video>, newShorts: List<Video>) {
        videos = newVideos
        shorts = newShorts.sortedByDescending { it.timestamp }
        timestamp = System.currentTimeMillis()
    }

    fun clear() {
        videos = emptyList()
        shorts = emptyList()
        timestamp = 0L
    }

    /**
     * Remove videos by blocked channel/topic from the cached feed without
     * requiring a network refetch, keeping the cache TTL alive.
     */
    fun filterOut(channelId: String? = null, videoId: String? = null) {
        if (channelId != null) {
            videos = videos.filter { it.channelId != channelId }
            shorts = shorts.filter { it.channelId != channelId }
        }
        if (videoId != null) {
            videos = videos.filter { it.id != videoId }
            shorts = shorts.filter { it.id != videoId }
        }
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val continueWatchingVideos: List<com.omersusin.pitube.data.local.VideoHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false,
    val lastRefreshTime: Long = 0L,
    val feedContinuation: String? = null
)

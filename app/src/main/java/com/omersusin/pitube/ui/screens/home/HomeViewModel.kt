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
import com.omersusin.pitube.SessionManager
import com.omersusin.pitube.ui.components.FeedInvalidationBus
import com.omersusin.pitube.utils.PerformanceDispatcher
import com.omersusin.pitube.utils.ShortsDetector
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    if (slots == 0) return FeedSource.entries.associateWith { 0 }
    if (hasPersonalFeed) {
        return mapOf(FeedSource.PERSONAL to slots, FeedSource.SUBS to 0, FeedSource.RELATED to 0, FeedSource.DISCOVERY to 0, FeedSource.VIRAL to 0)
    }
    if (subCount > 0) {
        val subs = (slots * 0.70).toInt().coerceAtLeast(8).coerceAtMost(slots)
        val related = (slots * 0.25).toInt().coerceAtMost((slots - subs).coerceAtLeast(0))
        val discovery = (slots - subs - related).coerceAtLeast(0)
        return mapOf(FeedSource.PERSONAL to 0, FeedSource.SUBS to subs, FeedSource.RELATED to related, FeedSource.DISCOVERY to discovery, FeedSource.VIRAL to 0)
    }
    return mapOf(FeedSource.PERSONAL to 0, FeedSource.SUBS to 0, FeedSource.RELATED to (slots * 0.80).toInt(), FeedSource.DISCOVERY to (slots - (slots * 0.80).toInt()).coerceAtLeast(0), FeedSource.VIRAL to 0)
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
    // Personal-first STRICT policy: when quota rounds leave the page short,
    // refill from the user's own lanes FIRST. Discovery/viral may only enter
    // through genuine leftover space — they must never crowd out a strong
    // personal/subscriptions feed ("this home screen isn't mine" regression).
    val scarcityOrder = listOf(
        FeedSource.PERSONAL,
        FeedSource.SUBS,
        FeedSource.RELATED,
        FeedSource.DISCOVERY,
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
            // The per-channel cap is relaxed for the PERSONAL lane: capping a
            // user's own subscriptions at 2 used to starve the personal feed
            // and let discovery content leak into its slots.
            val channelCap = if (source == FeedSource.PERSONAL) 3 else 2
            if (added < quota && addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds, channelCap)) {
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
        private const val DISCOVERY_EPOCH_TIME_KEY = "discovery_epoch_time"

        // Rotate the non-forced discovery order after this long so the home
        // feed changes even without an explicit pull-to-refresh (12h).
        private const val DISCOVERY_ROTATE_MS = 12L * 60L * 60L * 1000L
    }

    private val MUSIC_DISCOVERY_QUERIES = listOf(
        "pop music", "lofi beats", "jazz classics", "hip hop", "k-pop", "classical music",
        "acoustic covers", "electronic music", "indie music", "country hits", "reggaeton",
        "anime music", "meditation music", "study music", "nightcore", "music videos", "live concert"
    )

    private val VIDEO_DISCOVERY_QUERIES = listOf(
        "viral", "trending now", "popular videos", "new releases", "best of the week",
        "trending today", "viral videos", "funny videos", "amazing videos",
        "documentary", "gaming highlights", "science explained", "top 10", "daily news",
        "movie trailers", "how to", "cooking recipes",
        "fitness workout", "travel vlog", "tech reviews", "AI explained", "history explained",
        "space science", "nature documentary", "short films", "comedy skits", "animation",
        "football highlights", "basketball", "e-sports", "reaction videos", "unboxing",
        "car reviews", "diy projects", "gardening",
        "news analysis", "crypto explained", "true crime", "pets and animals",
        "learning english", "photography", "gadget comparisons", "vintage music",
        "challenges", "pranks", "street food", "architecture", "psychology explained",
        "film analysis", "book reviews", "coding tutorial", "3d printing", "wildlife",
        "ocean documentary", "ancient history", "philosophy", "economics explained", "investing",
        "design inspiration", "street photography", "home decor", "tiny house", "urban exploration"
    )

    private val DISCOVERY_QUERIES = VIDEO_DISCOVERY_QUERIES + MUSIC_DISCOVERY_QUERIES

    // Saved-interest enrichment sources + per-seed cooldown.
    private val likedVideosRepository by lazy { LikedVideosRepository.getInstance(appContext) }
    private val playlistRepository by lazy { PlaylistRepository(appContext) }
    private val historyRepository by lazy { ViewHistory.getInstance(appContext) }
    private val persistentHomeFeedCache by lazy { HomeFeedCacheRepository(appContext) }
    private val channelMetadataEnrichmentInFlight =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val channelMetadataEnrichmentSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
        .map(HomeUiState::withUniqueLazyContent)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _uiState.value.withUniqueLazyContent()
        )
    
    private var currentPage: Page? = null
    private var isInitialized = false
    private val homePrefetchQueue = HomePrefetchQueue()
    private val homePrefetchWorkerLock = Any()
    private var homePrefetchJob: Job? = null

    private var subsBacklog: List<Video> = emptyList()

    private val shownVideoIds = LinkedHashSet<String>()
    private fun rememberShown(ids: Collection<String>) {
        shownVideoIds.addAll(ids)
        while (shownVideoIds.size > 400) {
            val it = shownVideoIds.iterator()
            it.next()
            it.remove()
        }
    }
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    private var wave2Job: Job? = null

    // Continuation token of the signed-in personalized "what to watch" feed,
    // captured on every loadFlowFeed so scroll-to-load-more can pull the next
    // personalized page instead of looping the same discovery queries.
    private var personalizedContinuation: String? = null

    private var viewHistory: ViewHistory? = null

    private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())

    private val unplayableVideoIds = MutableStateFlow<Set<String>>(emptySet())

    /** Videos marked "Not interested" — always filtered from every lane. */
    private val hiddenVideoIds = MutableStateFlow<Set<String>>(emptySet())

    /** Channels marked "Don't recommend channel" — always filtered from every lane. */
    private val blockedChannelIds = MutableStateFlow<Set<String>>(emptySet())

    private val discoveryPrefs by lazy {
        appContext.getSharedPreferences("home_feed_rotation", Context.MODE_PRIVATE)
    }
    private var discoveryRotationEpoch = 0

    /**
     * Fill [discoveryQueries] and reset [currentQueryIndex]. On a forced
     * refresh the query list is reshuffled (and the persisted epoch bumped) so
     * consecutive pulls surface different items. Without a forced refresh the
     * last shuffled order is reused UNLESS it has gone stale (older than
     * [DISCOVERY_ROTATE_MS]) — then it is rotated anyway so the home feed
     * genuinely changes over time instead of pinning the same set for days.
     */
    private fun seedDiscoveryQueries(shuffle: Boolean) {
        val persisted = discoveryPrefs
            .getString(DISCOVERY_ORDER_KEY, null)
            ?.split(',')
            ?.takeIf { it.size == VIDEO_DISCOVERY_QUERIES.size || it.size == DISCOVERY_QUERIES.size }
            ?.let { list -> if (list.size == DISCOVERY_QUERIES.size) list.filterNot { it in MUSIC_DISCOVERY_QUERIES } else list }
            ?.takeIf { it.size == VIDEO_DISCOVERY_QUERIES.size }
        val persistedEpoch = discoveryPrefs.getInt(DISCOVERY_EPOCH_KEY, 0)
        val lastRotated = discoveryPrefs.getLong(DISCOVERY_EPOCH_TIME_KEY, 0L)
        val stale = System.currentTimeMillis() - lastRotated > DISCOVERY_ROTATE_MS
        val seeded =
            if (shuffle || stale || persisted == null) {
                val epoch = persistedEpoch + 1
                discoveryRotationEpoch = epoch
                VIDEO_DISCOVERY_QUERIES.shuffled(Random(epoch.toLong() * 31L + System.currentTimeMillis() % 1_000_000L))
            } else {
                persisted
            }
        discoveryQueries.clear()
        discoveryQueries.addAll(seeded)
        if (shuffle || stale || persisted == null) {
            discoveryPrefs.edit()
                .putString(DISCOVERY_ORDER_KEY, seeded.joinToString(","))
                .putInt(DISCOVERY_EPOCH_KEY, discoveryRotationEpoch)
                .putLong(DISCOVERY_EPOCH_TIME_KEY, System.currentTimeMillis())
                .apply()
        }
        currentQueryIndex = 0
    }

    init {
        if (HomeFeedCache.isFresh() &&
            HomeFeedCache.signedIn == (com.omersusin.pitube.innertube.YouTube.cookie != null)
        ) {
            _uiState.update {
                it.copy(
                    videos = HomeFeedCache.videos,
                    shorts = HomeFeedCache.shorts,
                    isLoading = false,
                    isFlowFeed = true,
                    lastRefreshTime = HomeFeedCache.timestamp
                )
            }
            rememberShown(HomeFeedCache.videos.map { it.id })
            hydratePersistentHomeFeed()
            if (System.currentTimeMillis() - HomeFeedCache.timestamp > FEED_BACKGROUND_REFRESH_AFTER_MS) {
                loadFlowFeed()
            }
        } else {
            hydratePersistentHomeFeed()
            loadFlowFeed(forceRefresh = true)
            loadHomeShorts()
        }
        viewModelScope.launch {
            try {
                com.omersusin.pitube.data.local.ProfileManager(appContext).activeProfileId.drop(1).distinctUntilChanged().collect {
                    shownVideoIds.clear()
                    subsBacklog = emptyList()
                    currentPage = null
                    personalizedContinuation = null
                    resetHomePrefetch()
                    wave2Job?.cancel()
                    HomeFeedCache.clear()
                    _uiState.value = HomeUiState()
                    hydratePersistentHomeFeed()
                    loadFlowFeed(forceRefresh = true)
                    loadHomeShorts()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Home refresh cycle failed: ${e.message}")
            }
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
            }.distinctUntilChanged().collect { result ->
                watchedVideoIds.value = result.watchedVideoIds
                _uiState.update { state ->
                    val videos = state.videos.filterWatched(result.watchedVideoIds)
                    val shorts = state.shorts.filterWatched(result.watchedVideoIds)
                    if (videos != state.videos || shorts != state.shorts) {
                        HomeFeedCache.update(videos, shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
                    }
                    state.copy(
                        videos = videos,
                        shorts = shorts,
                        continueWatchingVideos = result.continueWatchingVideos
                    )
                }
            }
        }

        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            combine(
                playerPreferences.unplayableVideoIds,
                playerPreferences.hideUnplayableVideosFromSubscriptions
            ) { ids, hideUnplayable ->
                if (hideUnplayable) ids else emptySet()
            }
                .distinctUntilChanged()
                .collect { ids ->
                    unplayableVideoIds.value = ids
                    _uiState.update { state ->
                        val videos = state.videos.filterUnplayable(ids)
                        val shorts = state.shorts.filterUnplayable(ids)
                        if (videos != state.videos || shorts != state.shorts) {
                            HomeFeedCache.update(videos, shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
                        }
                        state.copy(videos = videos, shorts = shorts)
                    }
                }
        }

        // "Not interested" and "Don't recommend channel" — kept in memory for
        // the live blend and applied to every lane on the next refresh.
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            combine(playerPreferences.hiddenVideoIds, playerPreferences.blockedChannelIds) { hidden, blocked ->
                hidden to blocked
            }
                .distinctUntilChanged()
                .collect { (hidden, blocked) ->
                    hiddenVideoIds.value = hidden
                    blockedChannelIds.value = blocked
                    _uiState.update { state ->
                        val videos = state.videos.filterSuppressed(hidden, blocked)
                        val shorts = state.shorts.filterSuppressed(hidden, blocked)
                        if (videos != state.videos || shorts != state.shorts) {
                            HomeFeedCache.update(videos, shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
                        }
                        state.copy(videos = videos, shorts = shorts)
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
                    is FeedInvalidationBus.Event.VideoHidden -> {
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
                    is FeedInvalidationBus.Event.ProfileSwitched -> {
                        shownVideoIds.clear()
                        subsBacklog = emptyList()
                        currentPage = null
                        personalizedContinuation = null
                        resetHomePrefetch()
                        wave2Job?.cancel()
                        HomeFeedCache.clear()
                        _uiState.value = HomeUiState()
                        hydratePersistentHomeFeed()
                        loadFlowFeed(forceRefresh = true)
                        loadHomeShorts()
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
                        it.copy(shorts = shorts.filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value).filterUnplayable(unplayableVideoIds.value))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Home shorts shelf load failed: ${e.message}")
            }
        }
    }

    private suspend fun cacheFilters(): HomeFeedCacheFilters =
        HomeFeedCacheFilters(
            watchedVideoIds = watchedVideoIds.value,
            suppressedVideoIds = hiddenVideoIds.value,
            suppressedChannelIds = blockedChannelIds.value,
            blockedChannelIds = blockedChannelIds.value,
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
                val videos = hydratedCached.filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value).filterUnplayable(unplayableVideoIds.value)
                HomeFeedCache.update(videos, state.shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
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
            it.isShort || (it.duration in 1..120 && !it.isLive)
        }
        
        _uiState.update { state ->
            val watched = watchedVideoIds.value
            val unplayable = unplayableVideoIds.value
            val updatedVideos = if (append) (state.videos + regularVideos) else regularVideos
            state.copy(
                videos = updatedVideos.distinctBy { it.id }.filterWatched(watched).filterUnplayable(unplayable),
                shorts = (state.shorts + newShorts).distinctBy { it.id }.filterWatched(watched)
                    .filterUnplayable(unplayable)
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
                // Rotate the ANONYMOUS visitor identity on a forced refresh so the
                // discovery lane stops pinning the same items week after week.
                // NEVER rotate while signed in: re-pairing a fresh visitor id with
                // the account cookies is exactly the combination YouTube answers
                // with an empty FEwhat_to_watch (log evidence: fetched=0).
                val rotationAllowed = com.omersusin.pitube.innertube.YouTube.cookie.isNullOrBlank()
                if (forceRefresh && rotationAllowed) {
                    runCatching { com.omersusin.pitube.innertube.YouTube.rotateVisitorData() }
                    seedDiscoveryQueries(shuffle = true)
                } else {
                    seedDiscoveryQueries(shuffle = false)
                }

                val musicEnabled = runCatching { playerPreferences.musicSearchCategoriesEnabled.first() }.getOrDefault(false)

                // Cold starts race the application's async session restore; wait
                withTimeoutOrNull(8000L) { SessionManager.restored.await() }

                val signedIn = !com.omersusin.pitube.innertube.YouTube.cookie.isNullOrBlank()

                // ── Signed-in lane: the account's own "What to watch" feed ──
                // Fetched in parallel with discovery below; blended into the mix
                // instead of short-circuiting so the feed rotates through fresh
                // items instead of repeating the same personalized set. The
                // continuation token is preserved so scrolling can pull the
                // next personalized page (feedContinuation drives load-more).
                val personalizedPool = if (signedIn) {
                    withTimeoutOrNull(12_000L) {
                        val primary = com.omersusin.pitube.innertube.YouTube.personalizedFeed()
                            .getOrNull()
                            ?.let { result ->
                                if (result.videos.isNotEmpty()) {
                                    personalizedContinuation = result.continuation
                                }
                                result.videos
                            }
                            .orEmpty()
                        val videos = primary
                        // KODA continuation-walk (HomeViewModel model): page 1 is
                        // quasi-static; on a forced refresh keep walking pages
                        // until ≥15 not-recently-shown items are collected.
                        var walked = videos
                        var walkCont = personalizedContinuation
                        if (forceRefresh) {
                            var pages = 0
                            while (walked.size < 15 && walkCont != null && pages < 3) {
                                val next = runCatching {
                                    com.omersusin.pitube.innertube.YouTube.personalizedFeedContinuation(walkCont).getOrNull()
                                }.getOrNull() ?: break
                                if (next.videos.isEmpty()) break
                                walked = walked + next.videos.filter { candidate ->
                                    candidate.id !in shownVideoIds && walked.none { it.id == candidate.id }
                                }
                                personalizedContinuation = next.continuation
                                walkCont = next.continuation
                                pages++
                            }
                            Log.w(TAG, "Feed personal lane: continuation-walk total=${walked.size} (+${walked.size - videos.size}) across $pages extra pages")
                        }
                        walked
                            .filterSignedValid()
                            .also { Log.w(TAG, "Feed personal lane: after signedValid=${it.size} (dropped shorts/≤120s)") }
                            .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                            .also { Log.w(TAG, "Feed personal lane: after watched/suppressed=${it.size}") }
                            .filterUnplayable(unplayableVideoIds.value)
                            // Recency filtering is an UPLOADS concept; YouTube already
                            // curates FEwhat_to_watch, and non-English relative dates
                            // can fail the age check and gut the personal lane. Only
                            // drop items whose date is provably ancient.
                            .let { list ->
                                val filtered = list.filter { v ->
                                    v.isLive || v.timestamp <= 0L ||
                                        (System.currentTimeMillis() - v.timestamp) <= HOME_MAX_SUGGESTION_AGE_MS * 30
                                }
                                Log.w(TAG, "Feed personal lane: after recency-guard=${filtered.size} (was ${list.size})")
                                filtered
                            }
                    } ?: run {
                        Log.w(TAG, "Feed personal lane: fetch timed out (12s)")
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // ── Taste-profile fallback (Koda getTasteBasedVideos pattern) ──
                // A signed-in session whose personalized feed came back weak
                // (bot-walled, fresh account, region without FE data) used to
                // degrade the whole feed to trending/discovery. Seed a lane from
                // the user's own watch history instead: latest watched videos →
                // their related items → interleaved as RELATED content.
                val tastePool = if (personalizedPool.size < 5) {
                    withTimeoutOrNull(10_000L) {
                        runCatching {
                            val seeds = viewHistory?.getLatestUnfinishedVideo()
                                ?.let { listOf(it.videoId) }
                                .orEmpty()
                            val historyIds = seeds.ifEmpty {
                                viewHistory?.getAllHistoryIds()?.take(8).orEmpty()
                            }
                            val seedIds = historyIds.take(6)
                            val perSeed = kotlinx.coroutines.coroutineScope {
                                seedIds.map { seedId ->
                                    async {
                                        runCatching { repository.getRelatedVideos(seedId) }.getOrElse { emptyList() }
                                    }
                                }.awaitAll()
                            }
                            interleaveRoundRobin(perSeed).filterSignedValid()
                        }.getOrElse { emptyList() }
                    } ?: emptyList()
                } else emptyList()

                val hasPersonalFeedEarly = personalizedPool.size >= 3
                val userSubs = subscriptionRepository.getValidSubscriptionIds()
                val region = playerPreferences.trendingRegion.first()
                val fetchStart = System.currentTimeMillis()

                val wave1QueryCount = discoveryQueries.size.coerceAtMost(2)
                val wave1Queries = discoveryQueries.take(wave1QueryCount)
                currentQueryIndex = wave1QueryCount

                val results = supervisorScope {
                    val deferredSubs = async {
                        if (userSubs.isEmpty()) emptyList()
                        else {
                            // Prefer the account's real aggregate subscriptions
                            // feed (FEsubscriptions): one signed request returns
                            // actual subscription uploads newest-first, instead
                            // of crawling per-channel uploads which is slow and
                            // can be partially rate-limited. Fall back to the
                            // per-channel crawl only when the aggregate comes
                            // back empty (bot-walled/unsupported).
                            val aggregate = if (signedIn) {
                                withTimeoutOrNull(10_000L) {
                                    com.omersusin.pitube.innertube.YouTube.webSubscriptionsFeed()
                                        .getOrNull()
                                        ?.videos
                                        .orEmpty()
                                } ?: emptyList()
                            } else emptyList()
                            // The signed FEsubscriptions page is the real
                            // subscription feed; fall back to the per-channel
                            // crawl only when it came back empty (bot-walled /
                            // dead session), never when it merely returned
                            // fewer items than some arbitrary threshold.
                            if (aggregate.isNotEmpty()) aggregate
                            else {
                                // Budget must cover the channel crawl inside
                                // getVideosForChannels (10-wide chunks, 6s/channel,
                                // up to 18 channels = 2 rounds = ~12s worst case).
                                // An 8s cap here silently emptied the subscription
                                // lane and the feed degraded to trending/discovery.
                                withTimeoutOrNull(15_000L) {
                                    runCatching {
                                        repository.getSubscriptionFeed(userSubs.toList())
                                    }.getOrElse { emptyList() }
                                } ?: emptyList()
                            }
                        }
                    }

                    val deferredDiscovery = if (hasPersonalFeedEarly) null else async {
                        wave1Queries.map { query ->
                            async { 
                                runCatching { 
                                    withTimeoutOrNull(6_000L) {
                                        repository.searchVideos(query).first
                                    }.orEmpty()
                                }.getOrElse { emptyList() }
                            }
                        }.awaitAll().flatten()
                    }
                    
                    Wave1FeedResults(
                        subs = deferredSubs.await(),
                        discovery = deferredDiscovery?.await() ?: emptyList(),
                        viral = emptyList()
                    )
                }

                val rawSubs = results.subs
                val rawDiscovery = results.discovery
                val rawViral = results.viral

                Log.d(TAG, "Wave 1 fetch completed in ${System.currentTimeMillis() - fetchStart}ms")

                val subMap: Map<String, com.omersusin.pitube.data.local.ChannelSubscription> = runCatching {
                    subscriptionRepository.getAllSubscriptions().first()
                        .associateBy { it.channelId }
                }.getOrElse { emptyMap() }

                fun List<Video>.enrichAvatars(): List<Video> =
                    if (subMap.isEmpty()) this
                    else map { v ->
                        val sub = subMap[v.channelId] ?: return@map v
                        val needAvatar = v.channelThumbnailUrl.isBlank()
                        val needName = v.channelName.isBlank()
                        if (!needAvatar && !needName) return@map v
                        v.copy(
                            channelName = if (needName) sub.channelName.ifBlank { v.channelName } else v.channelName,
                            channelThumbnailUrl = if (needAvatar) sub.channelThumbnail.ifBlank { v.channelThumbnailUrl } else v.channelThumbnailUrl,
                            channelThumbnailUrls = if (needAvatar && sub.channelThumbnail.isNotBlank()) {
                                v.channelThumbnailUrls.ifEmpty { listOf(sub.channelThumbnail) }
                            } else v.channelThumbnailUrls
                        )
                    }

                fun Video.fallbackChannelName(): String =
                    channelName.ifBlank { channelId.ifBlank { "" }.takeIf { it.startsWith("UC") } ?: channelId }

                fun List<Video>.withFallbackNames(): List<Video> =
                    map { v -> if (v.channelName.isBlank()) v.copy(channelName = v.fallbackChannelName()) else v }

                val now = System.currentTimeMillis()

                // The signed home response's own Shorts shelf (harvested by the
                // home parser) joins the shorts lane so the shelf reflects the
                // account's personalized shorts, not just subs/discovery spill.
                val homeShelfShorts = if (signedIn) {
                    com.omersusin.pitube.innertube.YouTube.lastHomeShortsShelf
                } else emptyList()
                val feedShorts = (homeShelfShorts + rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                    .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                    .filterUnplayable(unplayableVideoIds.value)
                    .filterRecentHomeSuggestion(now)
                if (feedShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + feedShorts).distinctBy { it.id })
                    }
                }
                
                // Filter to regular videos for the main feed
                val watched = watchedVideoIds.value
                val unplayable = unplayableVideoIds.value
                val subsPool = rawSubs.filterSignedValid().filterWatched(watched).filterUnplayable(unplayable).filterNotMusicIfDisabled(musicEnabled).enrichAvatars().withFallbackNames()
                val discoveryPool = rawDiscovery.filterValid().filterWatched(watched).filterUnplayable(unplayable).filterNotMusicIfDisabled(musicEnabled)
                    .filterRecentHomeSuggestion(now).enrichAvatars().withFallbackNames()
                val viralPool = rawViral.filterValid().filterWatched(watched).filterUnplayable(unplayable).filterNotMusicIfDisabled(musicEnabled)
                    .filterRecentHomeSuggestion(now).enrichAvatars().withFallbackNames()

                Log.w(
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
                val tastePoolFiltered = tastePool
                    .filterWatched(watched).filterUnplayable(unplayable)
                    .filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                    .filterRecentHomeSuggestion(now)
                    .enrichAvatars().withFallbackNames()
                    .take(12)

                val finalMix = mutableListOf<Video>()
                val usedChannelCounts = mutableMapOf<String, Int>()
                val usedVideoIds = mutableSetOf<String>()
                var freshAdded = 0

                freshSubsLane.forEach { video ->
                    if (addUnique(video, finalMix, usedChannelCounts, usedVideoIds)) freshAdded++
                }

                val remaining = (HOME_TARGET_SIZE - finalMix.size).coerceAtLeast(0)
                // "Strong" personal feed only when it can actually fill the
                // page; a weak one (bot-walled / fresh account) must fall back
                // to the SUBS/TASTE quota mix instead of hogging all slots.
                val hasPersonalFeed = personalizedPool.size >= 3
                Log.w(
                    TAG,
                    "Feed lane decision: personalized=${personalizedPool.size} " +
                        "(strong=$hasPersonalFeed, signedLane=${_uiState.value.feedContinuation != null}), " +
                        "subsPool=${bestSubs.size}, taste=${tastePoolFiltered.size}"
                )
                val baseQuotas = homeFeedQuotas(
                    remaining,
                    userSubs.size,
                    watched.size,
                    hasPersonalFeed = hasPersonalFeed
                )
                val quotas = if (personalizedPool.size < 5 && tastePoolFiltered.isNotEmpty()) {
                    baseQuotas.toMutableMap().apply {
                        val subsQ = this[FeedSource.SUBS] ?: 0
                        if (subsQ > 4) {
                            this[FeedSource.SUBS] = 4
                            this[FeedSource.RELATED] = (this[FeedSource.RELATED] ?: 0) + (subsQ - 4)
                        }
                    }
                } else baseQuotas
                val bestPersonal = personalizedPool.take(20)
                // When the personalized lane is weak the taste lane takes its
                // place as RELATED content — history-seeded instead of generic.
                val lanes = buildMap {
                    put(FeedSource.PERSONAL, bestPersonal)
                    put(FeedSource.SUBS, bestSubs)
                    if (personalizedPool.size < 5 && tastePoolFiltered.isNotEmpty()) {
                        put(FeedSource.RELATED, tastePoolFiltered)
                    }
                    put(FeedSource.DISCOVERY, bestDiscovery)
                    put(FeedSource.VIRAL, bestViral)
                }
                val sourceMix = blendFeedSources(
                    lanes = lanes,
                    quotas = quotas,
                    targetSize = remaining,
                    channelCounts = usedChannelCounts,
                    usedVideoIds = usedVideoIds
                )
                finalMix += sourceMix.videos

                subsBacklog = subsByRecency.filterNot { usedVideoIds.contains(it.id) }

                if (finalMix.isEmpty()) {
                    if (tastePoolFiltered.isNotEmpty()) {
                        val fallback = tastePoolFiltered.take(HOME_TARGET_SIZE)
                        _uiState.update { it.copy(videos = fallback, isLoading = false, isRefreshing = false, hasMorePages = false, isFlowFeed = true, lastRefreshTime = now) }
                        HomeFeedCache.update(fallback, _uiState.value.shorts, signedIn = signedIn)
                        persistentHomeFeedCache.saveLastFeed(fallback)
                        return@launch
                    }
                    loadTrendingFallback()
                    return@launch
                }

                Log.w(
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
                val dedupedSpacedMix = if (forceRefresh && shownVideoIds.isNotEmpty()) {
                    val fresh = spacedMix.filterNot { it.id in shownVideoIds }
                    if (fresh.size >= 15) fresh else if (fresh.isNotEmpty()) fresh else spacedMix
                } else spacedMix
                _uiState.update { state ->
                    visibleFeed = dedupedSpacedMix.filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value).filterUnplayable(unplayableVideoIds.value)
                    if (visibleFeed.isEmpty()) {
                        state.copy(isLoading = false, isRefreshing = false)
                    } else {
                        rememberShown(visibleFeed.map { it.id })
                        state.copy(
                            videos = visibleFeed,
                            isLoading = false,
                            isRefreshing = false,
                            hasMorePages = true,
                            isFlowFeed = true,
                            feedContinuation = personalizedContinuation,
                            lastRefreshTime = now
                        )
                    }
                }
                if (visibleFeed.isNotEmpty()) {
                    HomeFeedCache.update(visibleFeed, _uiState.value.shorts, signedIn = signedIn)
                }
                persistentHomeFeedCache.saveLastFeed(if (visibleFeed.isNotEmpty()) dedupedSpacedMix else spacedMix)
                persistentHomeFeedCache.saveReserve(reserveCandidates)
                enrichVisibleChannelMetadata(spacedMix)?.let {
                    persistentHomeFeedCache.saveLastFeed(it)
                }

                if (personalizedPool.isNotEmpty()) {
                    Log.d(TAG, "Wave 2 skipped — personalized feed is authoritative, no discovery injection")
                } else {
                    val wave2Queries = discoveryQueries.drop(currentQueryIndex)
                    if (wave2Queries.isNotEmpty()) {
                        val wave2FinalMixIds = finalMix.map { it.id }.toHashSet()
                        wave2Job = viewModelScope.launch(PerformanceDispatcher.networkIO) wave2@{
                            try {
                                if (personalizedContinuation != null || finalMix.any { v -> personalizedPool.any { p -> p.id == v.id } }) {
                                    Log.d(TAG, "Wave2 skipped personal")
                                    return@wave2
                                }
                                val limitedQueries = wave2Queries.take(6)
                                val wave2Raw = PerformanceDispatcher.parallelMap(limitedQueries, 3) { q ->
                                    withTimeoutOrNull(6_000L) {
                                        try {
                                            repository.searchVideos(q).first
                                        } catch (cancellation: CancellationException) {
                                            throw cancellation
                                        } catch (error: Exception) {
                                            Log.d(TAG, "Wave 2 query failed for $q: ${error.message}")
                                            emptyList<Video>()
                                        }
                                    } ?: emptyList<Video>()
                                }.flatten()
                                val wave2Watched = watchedVideoIds.value
                                val wave2Valid = wave2Raw.filterValid().filterWatched(wave2Watched)
                                    .filterUnplayable(unplayableVideoIds.value).filterNotMusicIfDisabled(musicEnabled)
                                    .filter { !wave2FinalMixIds.contains(it.id) }
                                if (wave2Valid.isEmpty()) return@wave2
                                val wave2Ranked = wave2Valid.take(15)
                                if (wave2Ranked.isNotEmpty()) {
                                    var updatedSnapshot: List<Video>? = null
                                    _uiState.update { state ->
                                        val currentIds = state.videos.map { it.id }.toHashSet()
                                        val uniqueNew = wave2Ranked
                                            .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                                            .filterUnplayable(unplayableVideoIds.value)
                                            .filter { !currentIds.contains(it.id) }
                                            .distinctBy { it.channelId }
                                        if (uniqueNew.isEmpty()) return@update state
                                        val updated = state.videos + uniqueNew
                                        updatedSnapshot = updated
HomeFeedCache.update(updated, state.shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
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
                }

            } catch (e: Exception) {
                 Log.w(TAG, "Flow feed failed entirely: ${e.javaClass.simpleName}: ${e.message}")
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

                val userSubs = subscriptionRepository.getValidSubscriptionIds()
                val page = mutableListOf<Video>()
                val channelCounts = HashMap<String, Int>()
                val pageIds = HashSet<String>(currentIds).apply { addAll(shownVideoIds) }

                if (_uiState.value.feedContinuation != null) {
                    Log.d(TAG, "Prefetch skipped — personalized continuation still available")
                    return false
                }
                if (currentQueryIndex >= discoveryQueries.size) {
                    seedDiscoveryQueries(shuffle = true)
                }
                val queryA = discoveryQueries.getOrNull(currentQueryIndex++)
                val queryB = discoveryQueries.getOrNull(currentQueryIndex++)
                val searchQueries = listOfNotNull(queryA, queryB)
                val finalQueries = if (searchQueries.isEmpty()) listOf("Viral") else searchQueries
                val rawVideos = coroutineScope {
                    finalQueries.map { query ->
                        async {
                            withTimeoutOrNull(6_000L) {
                                try { repository.searchVideos(query).first } catch (cancellation: CancellationException) { throw cancellation } catch (error: Exception) { Log.d(TAG, "Prefetch query failed for $query: ${error.message}"); emptyList() }
                            } ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
                if (!homePrefetchQueue.isCurrent(generation)) return false
                val moreShorts = rawVideos.extractShorts()
                    .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                    .filterUnplayable(unplayableVideoIds.value)
                    .filterRecentHomeSuggestion(now)
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + moreShorts).distinctBy { it.id })
                    }
                }
                
                val musicEnabledPrefetch = runCatching { playerPreferences.musicSearchCategoriesEnabled.first() }.getOrDefault(false)
                val newVideos = rawVideos.filterValid()
                    .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                    .filterUnplayable(unplayableVideoIds.value).filterNotMusicIfDisabled(musicEnabledPrefetch)
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
                .filterWatched(watchedVideoIds.value).filterSuppressed(hiddenVideoIds.value, blockedChannelIds.value)
                .filterUnplayable(unplayableVideoIds.value)
                .filterNot { it.id in existingVideoIds }
                .filterNot { it.id in shownVideoIds }
            if (appendedPage.isEmpty()) return@update state
            rememberShown(appendedPage.map { it.id })
            val tailChannels = state.videos.takeLast(2).map { it.channelId }
            val updated = state.videos + spaceByChannel(appendedPage, seedRecent = tailChannels)
            updatedSnapshot = updated
            HomeFeedCache.update(updated, state.shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
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
            HomeFeedCache.update(updated, state.shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
            state.copy(videos = updated)
        }
        return updatedSnapshot
    }

    fun enrichChannelMetadataIfMissing(videoId: String) {
        val video = _uiState.value.videos.firstOrNull { it.id == videoId } ?: return
        if (video.channelThumbnailUrl.isNotBlank()) return
        if (!channelMetadataEnrichmentInFlight.add(videoId)) return
        if (!channelMetadataEnrichmentSemaphore.tryAcquire()) {
            channelMetadataEnrichmentInFlight.remove(videoId)
            return
        }
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
                        HomeFeedCache.update(updated, state.shorts, signedIn = com.omersusin.pitube.innertube.YouTube.cookie != null)
                        state.copy(videos = updated)
                    }
                }
            } finally {
                channelMetadataEnrichmentSemaphore.release()
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
        return this.filter { !ShortsDetector.isShort(it) }
    }

    private fun List<Video>.filterNotMusicIfDisabled(musicEnabled: Boolean): List<Video> {
        if (musicEnabled) return this
        return this.filter { !it.isMusic }
    }

    /**
     * Lenient validity filter for the signed lanes (FEwhat_to_watch /
     * FEsubscriptions): those grids can omit the duration badge, and dropping
     * every item without a duration is what silently emptied the personalized
     * feed. Only actual Shorts are removed.
     */
    private fun <T> interleaveRoundRobin(lists: List<List<T>>): List<T> {
        val result = mutableListOf<T>()
        val iters = lists.map { it.iterator() }.toMutableList()
        while (iters.any { it.hasNext() }) {
            val it = iters.iterator()
            while (it.hasNext()) {
                val cur = it.next()
                if (cur.hasNext()) result.add(cur.next()) else it.remove()
            }
        }
        return result
    }

    private fun List<Video>.filterSignedValid(): List<Video> {
        return this.filter { !it.isShort }
    }

    /**
     * Filter that extracts shorts from a video list for the shelf.
     * Complements filterValid() by capturing what it discards.
     */
    private fun List<Video>.extractShorts(): List<Video> {
        return this.filter { it.isShort || (it.duration in 1..120 && !it.isLive) }
    }

    private fun List<Video>.filterRecentHomeSuggestion(now: Long): List<Video> =
        filter { video -> isRecentHomeSuggestion(video, now) }

    private fun isRecentHomeSuggestion(video: Video, now: Long): Boolean {
        val text = video.uploadDate.lowercase()
        if (text.isBlank() || text == "unknown") return video.isLive

        // The parser fills a real epoch for relative dates in any language —
        // prefer it over keyword matching, which only understands English.
        if (video.timestamp > 0L) {
            return now - video.timestamp <= HOME_MAX_SUGGESTION_AGE_MS
        }

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

    /**
     * Remove videos the user marked "Not interested" and every video from
     * channels marked "Don't recommend channel" — applied to every lane so
     * dismissed content can never resurface on the next refresh.
     */
    private fun List<Video>.filterSuppressed(
        hiddenIds: Set<String>,
        blockedIds: Set<String>
    ): List<Video> {
        if (hiddenIds.isEmpty() && blockedIds.isEmpty()) return this
        return this.filter { video ->
            video.id !in hiddenIds &&
                (video.channelId.isBlank() || video.channelId !in blockedIds)
        }
    }

    /**
     * Remove videos the player has permanently given up on (age-restricted,
     * private, members-only or removed). Only active when the user opts in.
     */
    private fun List<Video>.filterUnplayable(unplayableIds: Set<String>): List<Video> {
        if (unplayableIds.isEmpty()) return this
        return this.filter { !unplayableIds.contains(it.id) }
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
    private const val CACHE_TTL_MS = 60 * 1000L // 1 minute — keeps the feed
        // genuinely dynamic (Koda-style live sync); a 3-minute TTL served the
        // same snapshot long after account changes landed.

    @Volatile var videos: List<Video> = emptyList()
        private set
    @Volatile var shorts: List<Video> = emptyList()
        private set
    @Volatile var timestamp: Long = 0L
        private set

    /**
     * Whether the cached feed was produced while a YouTube session was
     * present. The cache is only trusted when it matches the current session
     * state; a generic anonymous snapshot must never mask a warmed-up
     * signed-in account (see the init gate and FlowApplication's invalidate).
     */
    @Volatile var signedIn: Boolean = false
        private set

    fun isFresh(): Boolean =
        videos.isNotEmpty() && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS

    fun update(newVideos: List<Video>, newShorts: List<Video>, signedIn: Boolean = false) {
        videos = newVideos
        shorts = newShorts.sortedByDescending { it.timestamp }
        timestamp = System.currentTimeMillis()
        this.signedIn = signedIn
    }

    fun clear() {
        videos = emptyList()
        shorts = emptyList()
        timestamp = 0L
        signedIn = false
    }

    /** Drop the cached feed so the next visit re-fetches from network. */
    fun invalidate() = clear()

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

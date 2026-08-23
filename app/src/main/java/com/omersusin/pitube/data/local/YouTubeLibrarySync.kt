package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.pages.RemotePlaylistVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Counts of what a sync pass actually applied locally, shown to the user after a manual refresh. */
data class LibrarySyncResult(
    val likedVideos: Int = 0,
    val playlists: Int = 0,
    val subscribedChannels: Int = 0,
    val watchLater: Int = 0,
    val notLoggedIn: Boolean = false,
    /** YouTube only returned part of the library (page cap hit) — counts are a lower bound. */
    val partial: Boolean = false,
    /** The account answered as signed-out: a dead session, not an empty library. */
    val sessionExpired: Boolean = false,
    val error: String? = null,
)

/**
 * Pulls the signed-in Google account's real YouTube library into Flow's existing local
 * repositories (the same ones the Liked Videos, Playlists and Subscriptions screens already
 * read from).
 *
 * All reads go through signed www.youtube.com WEB-client calls ([YouTube.webSubscribedChannels],
 * [YouTube.webUserPlaylists], [YouTube.webPlaylistVideos]) — the music-host `YouTube.library()`
 * feeds stay empty for video-only accounts and were the reason sync used to report zeroes.
 *
 * This is strictly read/import: it only copies data from the account into Flow's local
 * database. It never writes back to the real YouTube account (no like/subscribe calls
 * are sent to Google), so re-running it is always safe.
 *
 * The reported counts are the number of items actually applied to the local stores
 * (not raw parser output), so "0 channels" means nothing landed locally — and when
 * YouTube answers as signed-out or the crawl is truncated, the result carries an
 * explicit flag instead of silently persisting fake numbers.
 */
object YouTubeLibrarySync {

    private const val TAG = "YouTubeLibrarySync"

    // Safety caps so a very large account can't turn a "refresh" into an unbounded crawl.
    private const val PLAYLIST_FETCH_CONCURRENCY = 4

    suspend fun sync(context: Context): LibrarySyncResult {
        if (YouTube.cookie.isNullOrEmpty()) {
            return LibrarySyncResult(notLoggedIn = true)
        }

        var likedVideos = 0
        var playlists = 0
        var channels = 0
        var watchLater = 0
        var partial = false
        var sessionExpired = false

        val firstError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        coroutineScope {
            launch {
                runCatching { likedVideos = syncLikedVideos(context) }
                    .onFailure { Log.w(TAG, "Liked videos sync failed", it); firstError.compareAndSet(null, it.message) }
            }
            launch {
                runCatching { watchLater = syncWatchLater(context) }
                    .onFailure { Log.w(TAG, "Watch Later sync failed", it); firstError.compareAndSet(null, it.message) }
            }
            launch {
                runCatching { playlists = syncPlaylists(context) }
                    .onFailure { Log.w(TAG, "Playlist sync failed", it); firstError.compareAndSet(null, it.message) }
            }
            launch {
                runCatching {
                    val outcome = syncSubscriptions(context)
                    if (outcome.failed) {
                        firstError.compareAndSet(null, "subscription crawl failed")
                    }
                    channels = outcome.applied
                    partial = !outcome.complete
                    sessionExpired = outcome.sessionExpired
                }.onFailure { Log.w(TAG, "Subscription sync failed", it); firstError.compareAndSet(null, it.message) }
            }
        }

        if (sessionExpired) {
            Log.w(TAG, "Account sync hit a signed-out session — counts not persisted")
            firstError.compareAndSet(null, "session expired")
        }
        if (likedVideos == 0 && playlists == 0 && channels == 0 && watchLater == 0 && !firstError.get().isNullOrBlank()) {
            Log.w(TAG, "Account sync returned all-zero with error: ${firstError.get()}")
        }

        if (firstError.get().isNullOrBlank()) {
            PlayerPreferences(context).setYoutubeLibrarySyncedAt()
            PlayerPreferences(context).setYoutubeLibrarySyncCounts(likedVideos, playlists, channels)
        }

        return LibrarySyncResult(
            likedVideos = likedVideos,
            playlists = playlists,
            subscribedChannels = channels,
            watchLater = watchLater,
            partial = partial && firstError.get().isNullOrBlank(),
            sessionExpired = sessionExpired,
            error = firstError.get(),
        )
    }

    /**
     * Pull the account's Watch Later playlist (WL) into the local scoped
     * watch-later playlist. The ACCOUNT is authoritative for items that exist
     * on both sides; local-only entries are kept (they may predate login or
     * be pending a write-through), but remote items always land locally.
     */
    private suspend fun syncWatchLater(context: Context): Int {
        val playlistRepository = PlaylistRepository(context)
        val videos = YouTube.webPlaylistVideos("WL").getOrNull().orEmpty()
        if (videos.isEmpty()) {
            Log.w(TAG, "Watch Later: remote returned 0 items (empty account or parser miss)")
            return 0
        }
        // No unique index backs the cross-ref table, so dedupe locally before
        // applying — repeated syncs must never multiply Watch Later rows.
        val existing = playlistRepository.getWatchLaterVideosFlow().first().mapTo(HashSet()) { it.id }
        var applied = 0
        videos.forEach { video ->
            if (video.id in existing) return@forEach
            runCatching {
                playlistRepository.addToWatchLater(video.toSyncVideo())
                existing.add(video.id)
                applied++
            }.onFailure { Log.w(TAG, "Watch Later apply failed for ${video.id}: ${it.message}") }
        }
        Log.d(TAG, "Watch Later synced: $applied new / ${videos.size} remote items")
        return applied
    }

    private suspend fun syncLikedVideos(context: Context): Int {
        val repository = LikedVideosRepository.getInstance(context)
        val videos = YouTube.webPlaylistVideos("LL").getOrNull().orEmpty()
        var applied = 0
        videos.forEach { video ->
            runCatching {
                repository.likeVideo(
                    LikedVideoInfo(
                        videoId = video.id,
                        title = video.title,
                        thumbnail = video.thumbnail,
                        channelName = video.channelName,
                        isMusic = false
                    )
                )
            }.onSuccess { applied++ }
        }
        return applied
    }

    private suspend fun syncPlaylists(context: Context): Int {
        val playlistRepository = PlaylistRepository(context)
        val remotePlaylists = YouTube.webUserPlaylists().getOrNull().orEmpty()
        val semaphore = Semaphore(PLAYLIST_FETCH_CONCURRENCY)

        val appliedCounts = coroutineScope {
            remotePlaylists.map { playlist ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            playlistRepository.saveExternalMusicPlaylist(
                                id = playlist.id,
                                name = playlist.title,
                                description = "",
                                thumbnailUrl = playlist.thumbnail
                            )
                            val videos = YouTube.webPlaylistVideos(playlist.id).getOrNull().orEmpty()
                            if (videos.isNotEmpty()) {
                                playlistRepository.syncSavedPlaylistVideos(playlist.id, videos.map { it.toSyncVideo() })
                            }
                            1
                        }.onFailure { Log.w(TAG, "Failed syncing playlist ${playlist.id}", it) }
                            .getOrDefault(0)
                    }
                }
            }.awaitAll()
        }

        return appliedCounts.sum()
    }

    private data class SubscriptionsSyncOutcome(
        val applied: Int,
        val complete: Boolean,
        val sessionExpired: Boolean,
        val failed: Boolean = false,
    )

    private suspend fun syncSubscriptions(context: Context): SubscriptionsSyncOutcome {
        val repository = SubscriptionRepository.getInstance(context)
        val crawl = YouTube.webSubscribedChannels().getOrNull()
            ?: return SubscriptionsSyncOutcome(0, complete = false, sessionExpired = false, failed = true)
        // A dead session answers the browse anonymously — that is a re-login
        // problem, never "the account has 0 channels".
        if (crawl.sessionExpired) {
            return SubscriptionsSyncOutcome(0, complete = false, sessionExpired = true)
        }
        val channels = crawl.channels
        val remoteIds = channels.mapTo(HashSet()) { it.id }
        var applied = 0
        channels.forEach { channel ->
            runCatching {
                repository.subscribe(
                    ChannelSubscription(
                        channelId = channel.id,
                        channelName = channel.name,
                        channelThumbnail = channel.thumbnail,
                        isMusic = false
                    )
                )
            }.onSuccess { applied++ }
        }
        // The account is authoritative: drop local-only rows that are NOT in the
        // remote list. Without this, recommendation-shelf channels that slipped
        // into the local library before the parser scoping fix would stay
        // "subscribed" forever and drown out real subscriptions in the feed.
        // The prune only runs when the crawl exhausted its continuation tokens
        // (complete) and every fetched channel applied cleanly — a truncated,
        // rate-limited or partially-failed fetch must never be treated as
        // authoritative, or a large account would lose most of its channels in
        // one sync.
        if (crawl.complete && channels.size >= 10 && applied == channels.size) {
            runCatching {
                repository.getAllSubscriptionIds()
                    .filter { it !in remoteIds }
                    .forEach { repository.unsubscribe(it) }
            }
        }
        return SubscriptionsSyncOutcome(applied, crawl.complete, sessionExpired = false)
    }

    /** Incremental like-refresh used right after an in-app like/unlike. */
    suspend fun syncLikedVideosOnly(context: Context) {
        if (YouTube.cookie.isNullOrEmpty()) return
        runCatching { syncLikedVideos(context) }
    }

    /** Incremental subscription-refresh used right after an in-app (un)subscribe. */
    suspend fun syncSubscriptionsOnly(context: Context) {
        if (YouTube.cookie.isNullOrEmpty()) return
        runCatching { syncSubscriptions(context) }
    }

    private fun RemotePlaylistVideo.toSyncVideo(): Video {
        return Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = 0,
            viewCount = 0,
            uploadDate = "",
            isMusic = false
        )
    }
}

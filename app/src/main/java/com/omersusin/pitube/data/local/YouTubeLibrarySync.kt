package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.pages.RemotePlaylistVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Counts of what a sync pass actually pulled in, shown to the user after a manual refresh. */
data class LibrarySyncResult(
    val likedVideos: Int = 0,
    val playlists: Int = 0,
    val subscribedChannels: Int = 0,
    val notLoggedIn: Boolean = false,
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

        val firstError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        coroutineScope {
            launch {
                runCatching { likedVideos = syncLikedVideos(context) }
                    .onFailure { Log.w(TAG, "Liked videos sync failed", it); firstError.compareAndSet(null, it.message) }
            }
            launch {
                runCatching { playlists = syncPlaylists(context) }
                    .onFailure { Log.w(TAG, "Playlist sync failed", it); firstError.compareAndSet(null, it.message) }
            }
            launch {
                runCatching { channels = syncSubscriptions(context) }
                    .onFailure { Log.w(TAG, "Subscription sync failed", it); firstError.compareAndSet(null, it.message) }
            }
        }

        if (likedVideos == 0 && playlists == 0 && channels == 0 && !firstError.get().isNullOrBlank()) {
            Log.w(TAG, "Account sync returned all-zero with error: ${firstError.get()}")
        }

        if (firstError.get().isNullOrBlank()) {
            PlayerPreferences(context).setYoutubeLibrarySyncedAt()
            PlayerPreferences(context).setYoutubeLibrarySyncCounts(likedVideos, playlists, channels)
        }

        return LibrarySyncResult(likedVideos, playlists, channels, error = firstError.get())
    }

    private suspend fun syncLikedVideos(context: Context): Int {
        val repository = LikedVideosRepository.getInstance(context)
        val videos = YouTube.webPlaylistVideos("LL").getOrNull().orEmpty()
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
            }
        }
        return videos.size
    }

    private suspend fun syncPlaylists(context: Context): Int {
        val playlistRepository = PlaylistRepository(context)
        val remotePlaylists = YouTube.webUserPlaylists().getOrNull().orEmpty()
        val semaphore = Semaphore(PLAYLIST_FETCH_CONCURRENCY)

        coroutineScope {
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
                        }.onFailure { Log.w(TAG, "Failed syncing playlist ${playlist.id}", it) }
                    }
                }
            }.awaitAll()
        }

        return remotePlaylists.size
    }

    private suspend fun syncSubscriptions(context: Context): Int {
        val repository = SubscriptionRepository.getInstance(context)
        val channels = YouTube.webSubscribedChannels().getOrNull().orEmpty()
        val remoteIds = channels.mapTo(HashSet()) { it.id }
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
            }
        }
        // The account is authoritative: drop local-only rows that are NOT in the
        // remote list. Without this, recommendation-shelf channels that slipped
        // into the local library before the parser scoping fix would stay
        // "subscribed" forever and drown out real subscriptions in the feed.
        // The prune only runs when the remote list is plausibly complete (a
        // handful or more) so a truncated/rate-limited fetch can't wipe the
        // library.
        if (channels.size >= 10) {
            runCatching {
                repository.getAllSubscriptionIds()
                    .filter { it !in remoteIds }
                    .forEach { repository.unsubscribe(it) }
            }
        }
        return channels.size
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
package com.omersusin.pitube.data.local

import android.content.Context
import android.util.Log
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.models.ArtistItem
import com.omersusin.pitube.innertube.models.PlaylistItem
import com.omersusin.pitube.innertube.models.SongItem
import com.omersusin.pitube.innertube.models.YTItem
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
)

/**
 * Pulls the signed-in Google account's real YouTube library into Flow's existing local
 * repositories (the same ones the Liked Videos, Playlists and Subscriptions screens already
 * read from), using `YouTube.library()` — an InnerTube call that already existed in this
 * codebase but was never invoked from any screen.
 *
 * This is strictly read/import: it only copies data from the account into Flow's local
 * database. It never writes back to the real YouTube account (no like/subscribe calls
 * are sent to Google), so re-running it is always safe.
 */
object YouTubeLibrarySync {

    private const val TAG = "YouTubeLibrarySync"

    // Safety caps so a very large account can't turn a "refresh" into an unbounded crawl.
    private const val MAX_CONTINUATION_PAGES = 15
    private const val PLAYLIST_FETCH_CONCURRENCY = 4

    suspend fun sync(context: Context): LibrarySyncResult {
        if (YouTube.cookie.isNullOrEmpty()) {
            return LibrarySyncResult(notLoggedIn = true)
        }

        var likedVideos = 0
        var playlists = 0
        var channels = 0

        coroutineScope {
            launch {
                runCatching { likedVideos = syncLikedVideos(context) }
                    .onFailure { Log.w(TAG, "Liked videos sync failed", it) }
            }
            launch {
                runCatching { playlists = syncPlaylists(context) }
                    .onFailure { Log.w(TAG, "Playlist sync failed", it) }
            }
            launch {
                runCatching { channels = syncSubscriptions(context) }
                    .onFailure { Log.w(TAG, "Subscription sync failed", it) }
            }
        }

        return LibrarySyncResult(likedVideos, playlists, channels)
    }

    /** Walks every continuation page for a library tab and returns the combined item list. */
    private suspend fun fetchAllLibraryItems(browseId: String): List<YTItem> {
        val first = YouTube.library(browseId).getOrNull() ?: return emptyList()
        val items = first.items.toMutableList()
        var continuation = first.continuation
        var page = 0
        while (continuation != null && page < MAX_CONTINUATION_PAGES) {
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            items += next.items
            continuation = next.continuation
            page++
        }
        return items
    }

    private suspend fun syncLikedVideos(context: Context): Int {
        val repository = LikedVideosRepository.getInstance(context)
        val videos = fetchAllLibraryItems("FElikedvideos").filterIsInstance<SongItem>()
        videos.forEach { song ->
            runCatching {
                repository.likeVideo(
                    LikedVideoInfo(
                        videoId = song.id,
                        title = song.title,
                        thumbnail = song.thumbnail,
                        channelName = song.artists.joinToString(", ") { it.name },
                        isMusic = false
                    )
                )
            }
        }
        return videos.size
    }

    private suspend fun syncPlaylists(context: Context): Int {
        val playlistRepository = PlaylistRepository(context)
        val remotePlaylists = fetchAllLibraryItems("FEplaylist_aggregation").filterIsInstance<PlaylistItem>()
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
                                thumbnailUrl = playlist.thumbnail.orEmpty()
                            )
                            val songs = YouTube.playlist(playlist.id).getOrNull()?.songs.orEmpty()
                            if (songs.isNotEmpty()) {
                                playlistRepository.syncSavedPlaylistVideos(playlist.id, songs.map { it.toSyncVideo() })
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
        val channels = fetchAllLibraryItems("FEsubscriptions").filterIsInstance<ArtistItem>()
        channels.forEach { artist ->
            runCatching {
                repository.subscribe(
                    ChannelSubscription(
                        channelId = artist.id,
                        channelName = artist.title,
                        channelThumbnail = artist.thumbnail.orEmpty(),
                        isMusic = false
                    )
                )
            }
        }
        return channels.size
    }

    private fun SongItem.toSyncVideo(): Video {
        val artistNames = artists.joinToString(", ") { it.name }
        return Video(
            id = id,
            title = title,
            channelName = artistNames,
            channelId = artists.firstOrNull()?.id ?: "",
            thumbnailUrl = thumbnail,
            duration = duration ?: 0,
            viewCount = 0,
            uploadDate = "",
            isMusic = false
        )
    }
}

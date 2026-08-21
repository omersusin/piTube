package com.omersusin.pitube.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.omersusin.pitube.data.local.LikedVideosRepository
import com.omersusin.pitube.data.local.PlaylistRepository
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.local.ViewHistory
import com.omersusin.pitube.data.video.VideoDownloadManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    playlistRepository: PlaylistRepository,
    videoDownloadManager: VideoDownloadManager,
    subscriptionRepository: SubscriptionRepository = SubscriptionRepository.getInstance(context)
) : ViewModel() {

    private val likedVideosRepository = LikedVideosRepository.getInstance(context)
    private val viewHistory = ViewHistory.getInstance(context)
    val subscriptions = subscriptionRepository.getAllSubscriptions()
        .map { it.take(20) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)

    internal val history = viewHistory.getRecentLibraryHistory(LIBRARY_SHELF_ITEM_LIMIT)
        .map { history ->
            history.asSequence()
                .map { it.toLibraryMediaItem() }
                .toList()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())

    internal val likes = likedVideosRepository.getAllLikedVideos()
        .map { likes ->
            likes.take(LIBRARY_SHELF_ITEM_LIMIT).map { it.toLibraryMediaItem() }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())

    internal val playlists = playlistRepository.getAllPlaylistsFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val watchLater = playlistRepository.getVideoOnlyWatchLaterFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val savedShorts = playlistRepository.getVideoOnlySavedShortsFlow()
        .map { it.take(LIBRARY_SHELF_ITEM_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, sharing, emptyList())

    internal val downloads = videoDownloadManager.downloadedVideos
        .map { videos ->
            videos.map { LibraryMediaItem.DownloadedVideoItem(it) }
                .sortedByDescending { it.download.downloadedAt }
                .take(LIBRARY_SHELF_ITEM_LIMIT)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, emptyList())
}

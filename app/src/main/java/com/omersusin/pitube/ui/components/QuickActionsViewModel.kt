package com.omersusin.pitube.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import com.omersusin.pitube.data.local.ChannelSubscription
import com.omersusin.pitube.data.local.PlaylistRepository
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.repository.YouTubeRepository
import com.omersusin.pitube.utils.ThumbnailUrlResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.omersusin.pitube.data.video.VideoDownloadManager
import com.omersusin.pitube.data.local.entity.DownloadItemStatus
import com.omersusin.pitube.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lightweight singleton event bus for feed-visible state changes.
 * Emitted by QuickActionsViewModel, observed by HomeViewModel / ShortsViewModel
 * to instantly strip blocked/disliked content from the cached feed.
 */
object FeedInvalidationBus {
    sealed class Event {
        data class ChannelBlocked(val channelId: String, val videoId: String) : Event()
        data class MarkedWatched(val videoId: String) : Event()
        data class VideoHidden(val videoId: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) { _events.tryEmit(event) }
}

@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerPreferences: com.omersusin.pitube.data.local.PlayerPreferences,
    private val videoDownloadManager: VideoDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val subscriptionRepository = SubscriptionRepository.getInstance(context)

    val watchLaterIds = playlistRepository.getWatchLaterIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** In-memory set of video IDs manually marked as watched this session */
    private val _watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedVideoIds = _watchedVideoIds.asStateFlow()

    /** Per-video subscription state cache: channelId -> Boolean */
    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannelIds = _subscribedChannelIds.asStateFlow()

    val downloadedVideoIds = videoDownloadManager.allDownloads
        .map { list ->
            list.filter { it.overallStatus == DownloadItemStatus.COMPLETED && it.items.isNotEmpty() }
                .map { it.download.videoId }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun loadSubscriptionState(channelId: String) {
        viewModelScope.launch {
            subscriptionRepository.isSubscribed(channelId).collect { subscribed ->
                if (subscribed) {
                    _subscribedChannelIds.update { it + channelId }
                } else {
                    _subscribedChannelIds.update { it - channelId }
                }
            }
        }
    }

    fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
        viewModelScope.launch {
            try {
                val isCurrentlySubscribed = _subscribedChannelIds.value.contains(channelId)
                if (isCurrentlySubscribed) {
                    subscriptionRepository.unsubscribe(channelId)
                    _subscribedChannelIds.update { it - channelId }
                    val applied =
                        com.omersusin.pitube.data.local.AccountActions(context)
                            .setSubscribed(channelId, false)
                    if (!applied) {
                        subscriptionRepository.subscribe(
                            com.omersusin.pitube.data.local.ChannelSubscription(
                                channelId = channelId,
                                channelName = channelName,
                                channelThumbnail = channelThumbnail
                            )
                        )
                        _subscribedChannelIds.update { it + channelId }
                        Toast.makeText(context, context.getString(R.string.toast_subscribe_write_failed), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    Toast.makeText(context, context.getString(R.string.toast_unsubscribed_from, channelName), Toast.LENGTH_SHORT).show()
                } else {
                    val resolvedThumbnail = channelThumbnail
                        .takeUnless { ThumbnailUrlResolver.isYoutubeVideoThumbnail(it) }
                        ?.takeIf { it.isNotBlank() }
                        ?: withContext(Dispatchers.IO) {
                            repository.fetchChannelAvatarById(channelId)
                        }
                    subscriptionRepository.subscribe(
                        com.omersusin.pitube.data.local.ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = resolvedThumbnail,
                            subscribedAt = System.currentTimeMillis()
                        )
                    )
                    _subscribedChannelIds.update { it + channelId }
                    val applied =
                        com.omersusin.pitube.data.local.AccountActions(context)
                            .setSubscribed(channelId, true)
                    if (!applied) {
                        subscriptionRepository.unsubscribe(channelId)
                        _subscribedChannelIds.update { it - channelId }
                        Toast.makeText(context, context.getString(R.string.toast_subscribe_write_failed), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    Toast.makeText(context, context.getString(R.string.toast_subscribed_to, channelName), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.quick_actions_error_template, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun toggleWatchLater(video: Video) {
        viewModelScope.launch {
            try {
                android.util.Log.d("QuickActionsViewModel", "Toggling Watch Later for video: ${video.id}")
                val isInWatchLater = playlistRepository.isInWatchLater(video.id)
                android.util.Log.d("QuickActionsViewModel", "Is currently in Watch Later: $isInWatchLater")

                if (isInWatchLater) {
                    playlistRepository.removeFromWatchLater(video.id)
                } else {
                    playlistRepository.addToWatchLater(video)
                }
                // Mirror the toggle onto the real account's Watch Later when
                // signed in; the local Room entry above is the source of truth
                // for the UI and works offline. When YouTube answers but does
                // not apply the write, roll the local state back so the UI and
                // the account agree.
                val applied =
                    com.omersusin.pitube.data.local.AccountActions(context)
                        .setVideoInWatchLater(video.id, !isInWatchLater)
                if (!applied) {
                    if (isInWatchLater) {
                        playlistRepository.addToWatchLater(video)
                    } else {
                        playlistRepository.removeFromWatchLater(video.id)
                    }
                    Toast.makeText(context, context.getString(R.string.toast_watch_later_write_failed), Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (isInWatchLater) {
                    Toast.makeText(context, context.getString(R.string.toast_removed_from_watch_later), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_added_to_watch_later), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("QuickActionsViewModel", "Error toggling Watch Later", e)
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.quick_actions_error_template, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Block the channel of a video — the channel will never appear in the feed again.
     */
    fun blockChannel(video: Video) {
        viewModelScope.launch {
            try {
                val metadata = if (video.channelId.startsWith("UC")) {
                    null
                } else {
                    withContext(Dispatchers.IO) { repository.getLiveWatchMetadata(video.id) }
                }
                val channelId = metadata?.channelId.orEmpty().ifBlank { video.channelId }
                check(channelId.isNotBlank()) {
                    context.getString(com.omersusin.pitube.R.string.channel_metadata_unavailable)
                }
                playerPreferences.addBlockedChannel(channelId)
                FeedInvalidationBus.emit(
                    FeedInvalidationBus.Event.ChannelBlocked(channelId, video.id)
                )
                Toast.makeText(
                    context,
                    context.getString(
                        com.omersusin.pitube.R.string.channel_blocked_toast,
                        metadata?.channelName.orEmpty().ifBlank { video.channelName }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.quick_actions_error_template, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Mark a video as "Watched" - records it in watch history so it leaves the feed.
     * Useful for clearing videos without replaying the whole video.
     */
    fun markAsWatched(video: Video) {
        viewModelScope.launch {
            try {
                val durationMs = if (video.duration > 0) video.duration * 1000L else 1000L
                val thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg"
                com.omersusin.pitube.data.local.ViewHistory.getInstance(context).savePlaybackPosition(
                    videoId = video.id,
                    position = durationMs,
                    duration = durationMs,
                    title = video.title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    isMusic = false,
                    isShort = video.isShort
                )

                _watchedVideoIds.update { it + video.id }
                FeedInvalidationBus.emit(FeedInvalidationBus.Event.MarkedWatched(video.id))
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.mark_as_watched_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.quick_actions_error_template, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Mark a video as "Not interested" — it is removed from every feed lane
     * immediately and never appears again (stored in the same preferences as
     * blocked channels; the Content settings screen can undo it).
     */
    fun markNotInterested(video: Video) {
        viewModelScope.launch {
            try {
                playerPreferences.addHiddenVideo(video.id)
                FeedInvalidationBus.emit(FeedInvalidationBus.Event.VideoHidden(video.id))
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.not_interested_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(com.omersusin.pitube.R.string.quick_actions_error_template, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Insert [video] immediately after the current position (Play Next).
     */
    fun playVideoNext(video: Video) {
        com.omersusin.pitube.player.EnhancedPlayerManager.getInstance().addVideoToQueueNext(video)
    }

    /**
     * Append [video] to the end of the current queue.
     */
    fun addVideoToQueue(video: Video) {
        com.omersusin.pitube.player.EnhancedPlayerManager.getInstance().addVideoToQueue(video)
    }

    /**
     * Start radio: play [video] now with its watch-next related items
     * (shuffled) queued behind it — ArchiveTune/Koda's SongMenu radio action.
     */
    fun startRadio(video: Video) {
        viewModelScope.launch {
            val related =
                runCatching { repository.getRelatedVideos(video.id) }
                    .getOrElse { emptyList() }
                    .filter { it.id != video.id }
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)
            if (related.isEmpty()) {
                Toast.makeText(context, R.string.radio_unavailable_toast, Toast.LENGTH_SHORT).show()
                return@launch
            }
            com.omersusin.pitube.player.EnhancedPlayerManager.getInstance()
                .setQueue(listOf(video) + related, 0, video.title)
            Toast.makeText(context, context.getString(R.string.radio_started_toast), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hand the video URL to the user's configured external downloader app
     * (Seal / YTDLnis style handoff). Resolution chain (strictest → loosest):
     * launch-intent for the configured package → SEND-filtered activity in
     * that package → system chooser. Many download managers (e.g. AB Download
     * Manager) don't export a text/plain SEND handler, so a package hit alone
     * must not produce "not installed" — only a total resolution failure does.
     */
    fun openWithExternalDownloader(video: Video) {
        viewModelScope.launch {
            val enabled =
                runCatching { playerPreferences.externalDownloaderEnabled.first() }.getOrDefault(false)
            if (!enabled) return@launch
            val packageName =
                runCatching { playerPreferences.externalDownloaderPackage.first() }.getOrDefault("").trim()
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.id}")
                }
            val pm = context.packageManager
            var launched = false
            if (packageName.isNotBlank()) {
                runCatching {
                    pm.getLaunchIntentForPackage(packageName)?.let { launch ->
                        launch.putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.id}")
                        context.startActivity(launch)
                        launched = true
                    }
                }
                if (!launched) {
                    runCatching {
                        val target = Intent(shareIntent).setPackage(packageName)
                        if (pm.queryIntentActivities(target, 0).isNotEmpty()) {
                            context.startActivity(target)
                            launched = true
                        }
                    }
                }
            }
            if (!launched) {
                try {
                    context.startActivity(Intent.createChooser(shareIntent, null))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.downloader_not_installed_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun isExternalDownloaderEnabled(): Boolean =
        runCatching { playerPreferences.externalDownloaderEnabled.first() }.getOrDefault(false)
}

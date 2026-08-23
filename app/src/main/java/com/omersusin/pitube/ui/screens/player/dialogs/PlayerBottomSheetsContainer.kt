package com.omersusin.pitube.ui.screens.player.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.model.Comment
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.utils.ChannelIdResolver
import com.omersusin.pitube.player.EnhancedPlayerManager
import com.omersusin.pitube.player.SleepTimerManager
import com.omersusin.pitube.ui.components.FlowChaptersBottomSheet
import com.omersusin.pitube.ui.components.FlowCommentsBottomSheet
import com.omersusin.pitube.ui.components.FlowDescriptionBottomSheet
import com.omersusin.pitube.ui.components.FlowLiveChatBottomSheet
import com.omersusin.pitube.ui.components.FlowLyricsBottomSheet
import com.omersusin.pitube.ui.components.FlowPlaylistQueueBottomSheet
import com.omersusin.pitube.ui.components.SleepTimerSheet
import com.omersusin.pitube.ui.components.VideoQuickActionsBottomSheet
import com.omersusin.pitube.ui.components.commentTimestampToMs
import com.omersusin.pitube.ui.components.sortCommentsByFilter
import com.omersusin.pitube.ui.screens.player.VideoPlayerUiState
import com.omersusin.pitube.ui.screens.player.LyricsUiState
import com.omersusin.pitube.ui.screens.player.state.PlayerScreenState

@Composable
fun PlayerBottomSheetsContainer(
    screenState: PlayerScreenState,
    uiState: VideoPlayerUiState,
    video: Video,
    completeVideo: Video,
    disableShortsPlayer: Boolean,
    showShortsPlayerPrompt: Boolean,
    comments: List<Comment>,
    commentsEnabled: Boolean = true,
    isLoadingComments: Boolean,
    isLoadingMoreComments: Boolean = false,
    hasMoreComments: Boolean = false,
    onLoadMoreComments: (videoId: String) -> Unit = {},
    lyricsState: LyricsUiState = LyricsUiState.Idle,
    lyricsTranslations: Map<Long, String> = emptyMap(),
    initialSearchTitle: String = "",
    initialSearchArtist: String = "",
    onSearchLyricsCandidates: (title: String, artist: String, onResult: (List<Pair<String, String>>) -> Unit) -> Unit = { _, _, onResult -> onResult(emptyList()) },
    onApplyManualLyrics: (String) -> Unit = {},
    onRequestLyrics: (videoId: String) -> Unit = {},
    mediaSheetExpandedHeight: Dp? = null,
    mediaSheetCollapsedHeight: Dp = 0.dp,
    context: Context,
    onPlayAsShort: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit = {},
    onLoadMoreReplies: (Comment) -> Unit = {},
    isSignedIn: Boolean = false,
    isPostingComment: Boolean = false,
    onPostComment: (String) -> Unit = {},
    onPostReply: (Comment, String) -> Unit = { _, _ -> },
    onToggleLike: (Comment) -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    onNavigateToChannel: ((String) -> Unit)? = null,
    renderChaptersSheet: Boolean = true,
    renderSleepTimerSheet: Boolean = true,
    onMediaSheetProgressChange: (Float) -> Unit = {},
) {
    val shareWithoutText by remember { PlayerPreferences(context).shareWithoutText }
        .collectAsStateWithLifecycle(initialValue = false)

    val queueSwipeToRemoveEnabled by remember { PlayerPreferences(context).queueSwipeToRemoveEnabled }
        .collectAsStateWithLifecycle(initialValue = true)

    val lyricsPrefs = remember { PlayerPreferences(context) }
    val lyricsShowPlayPause by lyricsPrefs.lyricsShowPlayPauseOnThumbnail
        .collectAsStateWithLifecycle(initialValue = true)
    val lyricsSwipeToChangeSong by lyricsPrefs.lyricsSwipeToChangeSong
        .collectAsStateWithLifecycle(initialValue = false)
    val playerIsPlaying by EnhancedPlayerManager.getInstance().playerState
        .collectAsStateWithLifecycle()

    val sortedComments =
        remember(comments, screenState.commentSortFilter) {
            sortCommentsByFilter(comments, screenState.commentSortFilter)
        }

    val handleTimestampClick: (String) -> Unit =
        remember {
            { timestamp ->
                EnhancedPlayerManager.getInstance().seekTo(commentTimestampToMs(timestamp))
            }
        }

    LaunchedEffect(Unit) {
        SleepTimerManager.attachToPlayer(
            player = EnhancedPlayerManager.getInstance().getPlayer(),
        ) {
            EnhancedPlayerManager.getInstance().pause()
        }
        SleepTimerManager.attachExitCallback {
            EnhancedPlayerManager.getInstance().pause()
            context.stopService(
                android.content.Intent(context, com.omersusin.pitube.service.VideoPlayerService::class.java),
            )
            (context as? android.app.Activity)?.finishAndRemoveTask()
        }
    }

    // Quick actions sheet
    if (screenState.showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = completeVideo,
            onDismiss = { screenState.showQuickActions = false },
            onShare = {
                screenState.showQuickActions = false
                val shareText =
                    if (shareWithoutText) {
                        context.getString(R.string.share_link_only_template, completeVideo.id)
                    } else {
                        context.getString(R.string.check_out_video_template, completeVideo.title, completeVideo.id)
                    }
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, completeVideo.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
            },
            onDownload = {
                screenState.showQuickActions = false
                screenState.showDownloadDialog = true
            },
            onChannelClick = onNavigateToChannel,
        )
    }

    // Comments Bottom Sheet
    if (screenState.showCommentsSheet && commentsEnabled) {
        FlowCommentsBottomSheet(
            comments = sortedComments,
            isLoading = isLoadingComments,
            selectedFilter = screenState.commentSortFilter,
            onFilterChanged = { filter ->
                screenState.commentSortFilter = filter
            },
            onLoadReplies = onLoadReplies,
            onLoadMoreReplies = onLoadMoreReplies,
            isSignedIn = isSignedIn,
            isPostingComment = isPostingComment,
            onPostComment = onPostComment,
            onPostReply = onPostReply,
            onToggleLike = onToggleLike,
            onDeleteComment = onDeleteComment,
            onTimestampClick = handleTimestampClick,
            channelAvatar = completeVideo.channelThumbnailUrl,
            isLoadingMore = isLoadingMoreComments,
            hasMore = hasMoreComments,
            onLoadMore = { onLoadMoreComments(video.id) },
            onAuthorClick = { authorChannelRef ->
                screenState.showCommentsSheet = false
                onNavigateToChannel?.invoke(authorChannelRef)
            },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showCommentsSheet = false },
        )
    }

    if (screenState.showLiveChatSheet && uiState.isLiveChatAvailable) {
        FlowLiveChatBottomSheet(
            messages = uiState.liveChatMessages,
            isLoading = uiState.isLiveChatLoading,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showLiveChatSheet = false },
        )
    }

    // Lyrics Bottom Sheet
    if (screenState.showLyricsSheet) {
        FlowLyricsBottomSheet(
            videoId = video.id,
            lyricsState = lyricsState,
            currentPosition = screenState.currentPosition,
            onLyricsLineClick = { positionMs ->
                EnhancedPlayerManager.getInstance().seekTo(positionMs)
            },
            onSwipeNextTrack = if (lyricsSwipeToChangeSong) { { EnhancedPlayerManager.getInstance().playNext() } } else null,
            onSwipePrevTrack = if (lyricsSwipeToChangeSong) { { EnhancedPlayerManager.getInstance().playPrevious() } } else null,
            translations = lyricsTranslations,
            onPickedManualLyrics = onApplyManualLyrics,
            onManualSearch = onSearchLyricsCandidates,
            initialSearchTitle = initialSearchTitle,
            initialSearchArtist = initialSearchArtist,
            showPlayPauseControl = lyricsShowPlayPause,
            isPlaying = playerIsPlaying.isPlaying,
            onTogglePlayPause = {
                val p = EnhancedPlayerManager.getInstance().getPlayer()
                if (p != null) { if (p.isPlaying) p.pause() else p.play() }
            },
            onRequestLyrics = { onRequestLyrics(video.id) },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showLyricsSheet = false },
        )
    }

    // Description Bottom Sheet
    if (screenState.showDescriptionSheet) {
        val currentVideo =
            remember(uiState.streamInfo, video) {
                val streamInfo = uiState.streamInfo
                if (streamInfo != null) {
                    Video(
                        id = streamInfo.id ?: video.id,
                        title = streamInfo.name ?: video.title,
                        channelName = streamInfo.uploaderName ?: video.channelName,
                        channelId = ChannelIdResolver.resolve(video.channelId, streamInfo.uploaderUrl),
                        thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
                        duration = streamInfo.duration.toInt(),
                        viewCount = streamInfo.viewCount,
                        likeCount = streamInfo.likeCount,
                        uploadDate =
                            streamInfo.textualUploadDate ?: streamInfo.uploadDate?.run {
                                try {
                                    val date = java.util.Date.from(offsetDateTime().toInstant())
                                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    sdf.format(date)
                                } catch (e: Exception) {
                                    video.uploadDate
                                }
                            } ?: video.uploadDate,
                        description = streamInfo.description?.content ?: video.description,
                        channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
                    )
                } else {
                    video
                }
            }

        FlowDescriptionBottomSheet(
            video = currentVideo,
            tags = uiState.streamInfo?.tags ?: emptyList(),
            onTimestampClick = handleTimestampClick,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showDescriptionSheet = false },
        )
    }

    // Chapters Bottom Sheet
    if (screenState.showChaptersSheet && renderChaptersSheet) {
        val chaptersPositionMs by remember {
            derivedStateOf { (screenState.currentPosition / 1_000L) * 1_000L }
        }
        FlowChaptersBottomSheet(
            chapters = uiState.chapters,
            currentPosition = chaptersPositionMs,
            durationMs = screenState.duration,
            onChapterClick = { newPosition ->
                EnhancedPlayerManager.getInstance().seekTo(newPosition)
            },
            thumbnailUrl = video.thumbnailUrl,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showChaptersSheet = false },
        )
    }

    // Playlist Queue Bottom Sheet
    if (screenState.showPlaylistQueueSheet) {
        val queueVideos by EnhancedPlayerManager.getInstance().queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
        val currentQueueIndex by EnhancedPlayerManager.getInstance().currentQueueIndexState.collectAsStateWithLifecycle(initialValue = -1)
        val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()

        FlowPlaylistQueueBottomSheet(
            queueVideos = queueVideos,
            currentQueueIndex = currentQueueIndex,
            playlistTitle = playerState.queueTitle,
            isLooping = playerState.isQueueLooping,
            isShuffled = playerState.isQueueShuffled,
            onLoopToggle = EnhancedPlayerManager.getInstance()::toggleQueueLoop,
            onShuffleToggle = EnhancedPlayerManager.getInstance()::toggleQueueShuffle,
            onPlayVideoAtIndex = { index ->
                EnhancedPlayerManager.getInstance().playVideoAtIndex(index, loadStreamsInPlayer = false)
            },
            onRemoveVideoAtIndex = EnhancedPlayerManager.getInstance()::removeVideoAtIndex,
            onMoveVideoAtIndex = EnhancedPlayerManager.getInstance()::moveVideoAtIndex,
            onDismiss = { screenState.showPlaylistQueueSheet = false },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            swipeToRemoveEnabled = queueSwipeToRemoveEnabled,
            onSheetProgressChange = onMediaSheetProgressChange,
        )
    }

    if (screenState.showSleepTimerSheet && renderSleepTimerSheet) {
        SleepTimerSheet(
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.showSleepTimerSheet = false },
        )
    }

    // Shorts Suggestion Dialog
    if (screenState.showShortsPrompt && !disableShortsPlayer && showShortsPlayerPrompt) {
        ShortsSuggestionDialog(
            onPlayAsShort = {
                screenState.showShortsPrompt = false
                onPlayAsShort(completeVideo.id)
            },
            onDismiss = { screenState.showShortsPrompt = false },
        )
    }
}

/**
 * Dialog suggesting to play short video as Shorts
 */
@Composable
fun ShortsSuggestionDialog(
    onPlayAsShort: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SmartDisplay, null) },
        title = {
            Text(
                text = stringResource(R.string.play_mode_suggestion_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(stringResource(R.string.play_mode_suggestion_body))
        },
        confirmButton = {
            TextButton(onClick = onPlayAsShort) {
                Text(stringResource(R.string.shorts_player))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

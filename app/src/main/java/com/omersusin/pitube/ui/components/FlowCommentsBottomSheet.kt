package com.omersusin.pitube.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.model.Comment
import com.omersusin.pitube.data.model.distinctByNonBlankKey
import com.omersusin.pitube.ui.translation.rememberTranslatedText
import com.omersusin.pitube.utils.formatLikeCount
import com.omersusin.pitube.utils.formatRichText
import com.omersusin.pitube.utils.formatTimeAgo
import kotlinx.coroutines.launch

enum class CommentSortFilter {
    TOP,
    POPULAR,
    NEWEST,
    OLDEST,
}

private fun relativeTimeToSeconds(timeStr: String): Long {
    val lower = timeStr.lowercase().trim()
    val number = Regex("\\d+").find(lower)?.value?.toLongOrNull() ?: 0L
    return when {
        "second" in lower -> number
        "minute" in lower -> number * 60L
        "hour" in lower -> number * 3_600L
        "day" in lower -> number * 86_400L
        "week" in lower -> number * 604_800L
        "month" in lower -> number * 2_592_000L
        "year" in lower -> number * 31_536_000L
        else -> Long.MAX_VALUE
    }
}

/** Sorts comments for the given filter, keeping pinned comments first. */
fun sortCommentsByFilter(
    comments: List<Comment>,
    filter: CommentSortFilter,
): List<Comment> {
    val pinned = comments.filter { it.isPinned }
    val unpinned = comments.filterNot { it.isPinned }
    val sortedUnpinned =
        when (filter) {
            CommentSortFilter.TOP -> unpinned.sortedByDescending { it.likeCount }
            CommentSortFilter.POPULAR -> unpinned.sortedByDescending { it.likeCount + it.replyCount }
            CommentSortFilter.NEWEST -> unpinned.sortedBy { relativeTimeToSeconds(it.publishedTime) }
            CommentSortFilter.OLDEST -> unpinned.sortedByDescending { relativeTimeToSeconds(it.publishedTime) }
        }
    return pinned + sortedUnpinned
}

/** Converts a "H:MM:SS" / "MM:SS" comment timestamp into milliseconds. */
fun commentTimestampToMs(timestamp: String): Long {
    val parts = timestamp.split(":").map { it.toLongOrNull() ?: 0L }
    val seconds =
        when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0L
        }
    return seconds * 1000L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowCommentsBottomSheet(
    comments: List<Comment>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTimestampClick: (String) -> Unit = {},
    onFilterChanged: (CommentSortFilter) -> Unit = {},
    onLoadReplies: (Comment) -> Unit = {},
    onLoadMoreReplies: (Comment) -> Unit = {},
    selectedFilter: CommentSortFilter = CommentSortFilter.TOP,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    hasMore: Boolean = false,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    isSignedIn: Boolean = false,
    isPostingComment: Boolean = false,
    onPostComment: (String) -> Unit = {},
    onPostReply: (Comment, String) -> Unit = { _, _ -> },
    onToggleLike: (Comment) -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    channelAvatar: String? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetExpandedHeight = expandedHeight ?: (configuration.screenHeightDp.dp * 0.75f)
    val expandedHeightPx = with(density) { sheetExpandedHeight.toPx() }
    val collapsedHeightPx = with(density) { collapsedHeight.toPx() }.coerceIn(0f, expandedHeightPx)
    val sheetProgressRangePx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1f)
    val dismissThresholdPx = collapsedHeightPx + sheetProgressRangePx * 0.55f
    val sheetHeightPx = remember { Animatable(0f) }
    var isAnimatingOut by remember { mutableStateOf(false) }
    val sheetProgress =
        if (expandedHeightPx > 0f) {
            ((sheetHeightPx.value - collapsedHeightPx) / sheetProgressRangePx).coerceIn(0f, 1f)
        } else {
            0f
        }
    SideEffect {
        onSheetProgressChange(sheetProgress)
    }

    val commentsListState = rememberLazyListState()

    fun animateToExpanded() {
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = expandedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
        }
    }

    fun animateToDismiss() {
        if (isAnimatingOut) return
        isAnimatingOut = true
        coroutineScope.launch {
            sheetHeightPx.animateTo(
                targetValue = collapsedHeightPx,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(expandedHeightPx, collapsedHeightPx) {
        isAnimatingOut = false
        sheetHeightPx.updateBounds(lowerBound = collapsedHeightPx, upperBound = expandedHeightPx)
        if (sheetHeightPx.value == 0f || sheetHeightPx.value < collapsedHeightPx) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
        sheetHeightPx.animateTo(
            targetValue = expandedHeightPx,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )
    }

    LaunchedEffect(selectedFilter) {
        commentsListState.scrollToItem(0)
    }

    BackHandler(onBack = ::animateToDismiss)

    val headerDragModifier =
        Modifier.pointerInput(expandedHeightPx, collapsedHeightPx, dismissThresholdPx, isAnimatingOut) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (isAnimatingOut) return@detectVerticalDragGestures
                    velocityTracker.addPointerInputChange(change)
                    coroutineScope.launch {
                        val nextValue = (sheetHeightPx.value - dragAmount).coerceIn(collapsedHeightPx, expandedHeightPx)
                        sheetHeightPx.snapTo(nextValue)
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    if (!isAnimatingOut) animateToExpanded()
                },
                onDragEnd = {
                    val velocityY = velocityTracker.calculateVelocity().y
                    velocityTracker.resetTracking()
                    when {
                        velocityY > 1200f || sheetHeightPx.value < dismissThresholdPx -> animateToDismiss()
                        else -> animateToExpanded()
                    }
                },
            )
        }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { sheetHeightPx.value.toDp() }),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(headerDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.comments),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = ::animateToDismiss,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                    CommentSortFilterChips(
                        selectedFilter = selectedFilter,
                        onFilterChanged = onFilterChanged,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                FlowCommentsList(
                    comments = comments,
                    isLoading = isLoading,
                    listState = commentsListState,
                    selectedFilter = selectedFilter,
                    onTimestampClick = onTimestampClick,
                    onLoadReplies = onLoadReplies,
                    onLoadMoreReplies = onLoadMoreReplies,
                    onAuthorClick = onAuthorClick,
                    onAvatarClick = onAvatarClick,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                    hasMore = hasMore,
                    isSignedIn = isSignedIn,
                    canComment = isSignedIn,
                    isPostingComment = isPostingComment,
                    onPostComment = onPostComment,
                    onPostReply = onPostReply,
                    onToggleLike = onToggleLike,
                    onDeleteComment = onDeleteComment,
                    channelAvatar = channelAvatar,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
        }
    }
}

/**
 * Input row pinned under the comments list (Koda-style). Shows a "Replying
 * to" banner when [replyTarget] is active and focuses the field so the
 * keyboard comes up together with the banner.
 */
@Composable
private fun CommentComposer(
    replyTarget: Comment?,
    isPosting: Boolean,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSend = text.isNotBlank() && !isPosting

    LaunchedEffect(replyTarget) {
        if (replyTarget != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (replyTarget != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.replying_to_template, replyTarget.author),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancelReply) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel_reply),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            stringResource(
                                if (replyTarget != null) R.string.reply_placeholder else R.string.comment_hint,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    enabled = !isPosting,
                )
                if (isPosting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            if (canSend) {
                                onSend(text.trim())
                                text = ""
                            }
                        },
                        enabled = canSend,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.post),
                            tint =
                                if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentSortFilterChips(
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == CommentSortFilter.TOP,
            onClick = { onFilterChanged(CommentSortFilter.TOP) },
            label = { Text(stringResource(R.string.filter_top)) },
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.POPULAR,
            onClick = { onFilterChanged(CommentSortFilter.POPULAR) },
            label = { Text(stringResource(R.string.filter_popular)) },
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.NEWEST,
            onClick = { onFilterChanged(CommentSortFilter.NEWEST) },
            label = { Text(stringResource(R.string.filter_newest)) },
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.OLDEST,
            onClick = { onFilterChanged(CommentSortFilter.OLDEST) },
            label = { Text(stringResource(R.string.filter_oldest)) },
        )
    }
}

@Composable
fun FlowCommentsList(
    comments: List<Comment>,
    isLoading: Boolean,
    listState: LazyListState,
    selectedFilter: CommentSortFilter,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit,
    onAvatarClick: (String) -> Unit,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isSignedIn: Boolean = false,
    canComment: Boolean = false,
    isPostingComment: Boolean = false,
    onPostComment: (String) -> Unit = {},
    onPostReply: (Comment, String) -> Unit = { _, _ -> },
    onToggleLike: (Comment) -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    channelAvatar: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp),
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val uniqueComments =
        remember(comments) {
            comments.distinctByNonBlankKey(Comment::id)
        }
    var replyTarget by remember { mutableStateOf<Comment?>(null) }
    var replyThreadParent by remember { mutableStateOf<Comment?>(null) }

    Column(modifier = modifier.imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        ) {
            if (isLoading) {
                item(key = "loading") {
                    Column(Modifier.padding(16.dp)) {
                        repeat(6) { CommentSkeleton() }
                    }
                }
            } else if (uniqueComments.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier =
                            Modifier
                                .fillParentMaxWidth()
                                .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.no_comments_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = uniqueComments,
                    key = { comment -> "${selectedFilter.name}_${comment.id}" },
                ) { comment ->
                    FlowCommentItem(
                        comment = comment,
                        onTimestampClick = onTimestampClick,
                        onLoadReplies = onLoadReplies,
                        onLoadMoreReplies = onLoadMoreReplies,
                        onAuthorClick = onAuthorClick,
                        onAvatarClick = onAvatarClick,
                        isSignedIn = isSignedIn,
                        isPostingComment = isPostingComment,
                        onPostReply = onPostReply,
                        onToggleLike = onToggleLike,
                        onDeleteComment = onDeleteComment,
                        channelAvatar = channelAvatar,
                        onReplyRequested = { target, threadParent ->
                            replyTarget = target
                            replyThreadParent = threadParent
                        },
                    )
                }
                if (hasMore) {
                    item(key = "load_more_trigger") {
                        LaunchedEffect(comments.size) {
                            latestOnLoadMore()
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        if (canComment) {
            CommentComposer(
                replyTarget = replyTarget,
                isPosting = isPostingComment,
                onCancelReply = {
                    replyTarget = null
                    replyThreadParent = null
                },
                onSend = { text ->
                    val parent = replyThreadParent
                    if (parent != null) {
                        onPostReply(parent, text)
                    } else {
                        onPostComment(text)
                    }
                    replyTarget = null
                    replyThreadParent = null
                },
            )
        }
    }
}

@Composable
fun FlowCommentItem(
    comment: Comment,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    isSignedIn: Boolean = false,
    isPostingComment: Boolean = false,
    onPostReply: (Comment, String) -> Unit = { _, _ -> },
    onToggleLike: (Comment) -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    channelAvatar: String? = null,
    onReplyRequested: (Comment, Comment?) -> Unit = { _, _ -> },
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isRepliesVisible by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    var isLoadingReplies by remember { mutableStateOf(false) }
    var commentTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current
    val commentContext = LocalContext.current
    val commentPrefs = remember { PlayerPreferences(commentContext) }
    val commentState = rememberTranslatedText(comment.text, commentPrefs.translateComments)

    LaunchedEffect(comment.replies) {
        isLoadingReplies = false
    }

    // Process text — cached so it isn't rebuilt on every recomposition.
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotatedText =
        remember(comment.text, primaryColor) {
            formatRichText(
                text = comment.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }

    // Full-size image viewer
    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(comment.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    // Delete confirmation for own comments
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_comment)) },
            text = { Text(stringResource(R.string.delete_comment_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteComment(comment)
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        ChannelAvatarImage(
            url = comment.authorThumbnail,
            contentDescription = null,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        onAvatarClick(comment.authorThumbnail)
                        showFullSizeImage = true
                    },
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Pinned indicator
            if (comment.isPinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.pinned_comment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pinned_by_creator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(comment.author),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onAuthorClick(commentAuthorChannelRef(comment)) },
                            ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = localizedCommentPublishedTime(comment.publishedTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment Body with "Read More" logic
            Box(modifier = Modifier.animateContentSize()) {
                SelectionContainer {
                    BasicText(
                        text = if (commentState.translated != null) {
                            androidx.compose.ui.text.AnnotatedString(commentState.displayText)
                        } else {
                            annotatedText
                        },
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                            ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            commentTextLayoutResult = result
                            if (result.hasVisualOverflow) isOverflowing = true
                        },
                        modifier =
                            Modifier.pointerInput(annotatedText) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        commentTextLayoutResult?.let { result ->
                                            val offset = result.getOffsetForPosition(tapOffset)
                                            val ts =
                                                annotatedText
                                                    .getStringAnnotations("TIMESTAMP", offset, offset)
                                                    .firstOrNull()
                                            val url =
                                                annotatedText
                                                    .getStringAnnotations("URL", offset, offset)
                                                    .firstOrNull()
                                            if (ts != null) {
                                                onTimestampClick(ts.item)
                                            } else if (url != null) {
                                                try {
                                                    uriHandler.openUri(url.item)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            } else {
                                                if (!isExpanded && isOverflowing) isExpanded = true
                                            }
                                        }
                                    },
                                )
                            },
                    )
                }
            }

            if (isOverflowing && !isExpanded) {
                Text(
                    text = stringResource(R.string.read_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .clickable { isExpanded = true },
                )
            }

            if (commentState.showOriginalBelow) {
                Text(
                    text = commentState.original,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar (Like, Dislike, Reply)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like
                Icon(
                    imageVector =
                        if (comment.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    tint =
                        if (comment.isLiked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier =
                        if (isSignedIn && comment.likeParams != null) {
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggleLike(comment) }
                                .padding(2.dp)
                        } else {
                            Modifier.size(20.dp)
                        },
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (comment.likeCount > 0) {
                    Text(
                        text = formatLikeCount(comment.likeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Creator interaction badges: hearted by the channel owner or
                // a reply from the channel owner (LibreTube-style).
                if (comment.isHearted || comment.creatorReplied) {
                    Spacer(modifier = Modifier.width(6.dp))
                    if (comment.isHearted) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.hearted_by_creator),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    CreatorBadgeAvatar(
                        url =
                            comment.creatorThumbnail
                                .ifBlank { channelAvatar.orEmpty() },
                        contentDescription =
                            if (comment.isHearted) {
                                stringResource(R.string.hearted_by_creator)
                            } else {
                                stringResource(R.string.creator_replied)
                            },
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Reply
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        if (isSignedIn) {
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onReplyRequested(comment, null) }
                                .padding(4.dp)
                        } else {
                            Modifier
                        },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = stringResource(R.string.reply),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                    if (isSignedIn) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.reply),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isSignedIn && comment.deleteParams != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_comment),
                        tint = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showDeleteConfirm = true }
                                .padding(4.dp),
                    )
                }
            }

            // View Replies Button
            if (comment.replyCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (!isRepliesVisible && comment.replies.isEmpty()) {
                                    isLoadingReplies = true
                                    onLoadReplies(comment)
                                }
                                isRepliesVisible = !isRepliesVisible
                            }.padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.view_replies),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .rotate(if (isRepliesVisible) 180f else 0f),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (isRepliesVisible) {
                                stringResource(
                                    R.string.hide_replies,
                                )
                            } else {
                                stringResource(R.string.view_replies_template, comment.replyCount)
                            },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isLoadingReplies) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Display Replies
            if (isRepliesVisible && comment.replies.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    comment.replies.forEach { reply ->
                        FlowReplyItem(
                            reply = reply,
                            onTimestampClick = onTimestampClick,
                            onAuthorClick = onAuthorClick,
                            onAvatarClick = onAvatarClick,
                            isSignedIn = isSignedIn,
                            isPostingComment = isPostingComment,
                            onPostReply = onPostReply,
                            onToggleLike = onToggleLike,
                            onDeleteComment = onDeleteComment,
                            channelAvatar = channelAvatar,
                            onReplyRequested = { target -> onReplyRequested(target, comment) },
                        )
                    }

                    if (comment.repliesPage != null || comment.continuationToken != null) {
                        Text(
                            text = stringResource(R.string.load_more_replies),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .padding(top = 8.dp)
                                    .clickable {
                                        isLoadingReplies = true
                                        onLoadMoreReplies(comment)
                                    },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlowReplyItem(
    reply: Comment,
    onTimestampClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    isSignedIn: Boolean = false,
    isPostingComment: Boolean = false,
    onPostReply: (Comment, String) -> Unit = { _, _ -> },
    onToggleLike: (Comment) -> Unit = {},
    onDeleteComment: (Comment) -> Unit = {},
    channelAvatar: String? = null,
    onReplyRequested: (Comment) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val replyContext = LocalContext.current
    val replyPrefs = remember { PlayerPreferences(replyContext) }
    val replyState = rememberTranslatedText(reply.text, replyPrefs.translateComments)
    val annotatedText =
        remember(reply.text, primaryColor) {
            formatRichText(
                text = reply.text,
                primaryColor = primaryColor,
                textColor = onSurface,
            )
        }
    var replyTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showFullSizeImage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showFullSizeImage) {
        FullSizeImageDialog(
            imageUrl = toHighQualityAvatarUrl(reply.authorThumbnail),
            onDismiss = { showFullSizeImage = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_comment)) },
            text = { Text(stringResource(R.string.delete_comment_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteComment(reply)
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        ChannelAvatarImage(
            url = reply.authorThumbnail,
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        onAvatarClick(reply.authorThumbnail)
                        showFullSizeImage = true
                    },
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header: Author + Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAuthorName(reply.author),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onAuthorClick(commentAuthorChannelRef(reply)) },
                            ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = localizedCommentPublishedTime(reply.publishedTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Reply Body
            SelectionContainer {
                Column {
                    BasicText(
                        text = if (replyState.translated != null) {
                            androidx.compose.ui.text.AnnotatedString(replyState.displayText)
                        } else {
                            annotatedText
                        },
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp,
                        ),
                    onTextLayout = { replyTextLayoutResult = it },
                    modifier =
                        Modifier.pointerInput(annotatedText) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    replyTextLayoutResult?.let { result ->
                                        val offset = result.getOffsetForPosition(tapOffset)
                                        val ts =
                                            annotatedText
                                                .getStringAnnotations("TIMESTAMP", offset, offset)
                                                .firstOrNull()
                                        if (ts != null) {
                                            onTimestampClick(ts.item)
                                        } else {
                                            annotatedText
                                                .getStringAnnotations("URL", offset, offset)
                                                .firstOrNull()
                                                ?.let {
                                                    try {
                                                        uriHandler.openUri(it.item)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                        }
                                    }
                                },
                            )
                        },
                )
                    if (replyState.showOriginalBelow) {
                        Text(
                            text = replyState.original,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Bar (Minimal for replies)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        if (reply.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(R.string.like),
                    tint =
                        if (reply.isLiked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier =
                        if (isSignedIn && reply.likeParams != null) {
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggleLike(reply) }
                                .padding(2.dp)
                        } else {
                            Modifier.size(18.dp)
                        },
                )
                if (reply.likeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatLikeCount(reply.likeCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Creator interaction badges on replies too.
                if (reply.isHearted || reply.creatorReplied) {
                    Spacer(modifier = Modifier.width(4.dp))
                    if (reply.isHearted) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.hearted_by_creator),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    CreatorBadgeAvatar(
                        url =
                            reply.creatorThumbnail
                                .ifBlank { channelAvatar.orEmpty() },
                        contentDescription =
                            if (reply.isHearted) {
                                stringResource(R.string.hearted_by_creator)
                            } else {
                                stringResource(R.string.creator_replied)
                            },
                        size = 14.dp,
                    )
                }

                if (isSignedIn) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onReplyRequested(reply) }
                                .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = stringResource(R.string.reply),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.reply),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isSignedIn && reply.deleteParams != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_comment),
                        tint = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showDeleteConfirm = true }
                                .padding(4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Tiny creator avatar shown next to a comment that the channel owner
 * hearted or replied to. Falls back to the video channel avatar.
 */
@Composable
private fun CreatorBadgeAvatar(
    url: String,
    contentDescription: String,
    size: Dp = 18.dp,
) {
    if (url.isBlank()) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size),
        )
    } else {
        ChannelAvatarImage(
            url = url,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.error, CircleShape),
        )
    }
}

@Composable
private fun localizedCommentPublishedTime(publishedTime: String): String {    val editedSuffix = Regex("\\s*\\(?edited\\)?\\s*$", RegexOption.IGNORE_CASE)
    val isEdited = editedSuffix.containsMatchIn(publishedTime)
    val time = formatTimeAgo(publishedTime.replace(editedSuffix, "").trim())
    return if (isEdited) {
        stringResource(R.string.comment_time_edited_template, time, stringResource(R.string.comment_edited))
    } else {
        time
    }
}

// ==========================================
// SKELETONS
// ==========================================

@Composable
fun CommentSkeleton() {
    Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(0.2f)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(modifier = Modifier.width(100.dp).height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.width(200.dp).height(12.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
        }
    }
}

// HELPER COMPOSABLES & UTILITIES
@Composable
fun FullSizeImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(toHighQualityAvatarUrl(imageUrl))
                            .crossfade(true)
                            .size(1600, 1600)
                            .scale(coil3.size.Scale.FIT)
                            .allowHardware(false)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tap_to_close),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

fun formatAuthorName(author: String): String {
    val trimmed = author.trim()
    return if (trimmed.startsWith("@")) {
        trimmed
    } else {
        "@$trimmed"
    }
}

/**
 * Channel reference for a comment author, in one of the forms `youtubeChannelUrl` accepts:
 * a `UC…` channel id, an `@handle`, or a bare handle.
 *
 * Callers must forward the value unchanged — re-prefixing it with `@` turns a channel id into a
 * handle that does not exist, which is why comment authors used to open a 404 page.
 */
fun commentAuthorChannelRef(comment: Comment): String = comment.authorChannelId.trim().ifBlank { comment.author.trim().removePrefix("@") }

private fun toHighQualityAvatarUrl(url: String): String {
    if (url.isBlank()) return url

    return url
        .replace(Regex("=s\\d+"), "=s1024")
        .replace(Regex("/s\\d+-"), "/s1024-")
        .replace(Regex("=w\\d+-h\\d+"), "=w1024-h1024")
}

package com.omersusin.pitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.omersusin.pitube.data.PipedApiService

private data class CommentItem(
    val author: String,
    val commentText: String,
    val likes: Long,
    val commentedTime: String,
    val authorThumbnail: String,
    val replyCount: Int = 0,
    val replies: List<CommentItem> = emptyList(),
    val isReply: Boolean = false
)

private class CommentsPagingSource(private val api: PipedApiService, private val videoId: String) : PagingSource<String, CommentItem>() {
    override fun getRefreshKey(state: PagingState<String, CommentItem>): String? = null
    override suspend fun load(params: LoadParams<String>): LoadResult<String, CommentItem> {
        return try {
            val response = if (params.key == null) api.getComments(videoId) else api.getNextComments(videoId, params.key!!)
            val items = response.comments.map { c ->
                CommentItem(
                    author = c.author,
                    commentText = c.commentText,
                    likes = c.likes,
                    commentedTime = c.commentedTime,
                    authorThumbnail = c.authorThumbnail
                )
            }
            LoadResult.Page(data = items, prevKey = null, nextKey = response.nextpage)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

enum class CommentSortMode(val label: String) {
    TOP("Top"), NEWEST("Newest")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowCommentsBottomSheet(
    videoId: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sortMode by remember { mutableStateOf(CommentSortMode.TOP) }
    var showRepliesFor by remember { mutableStateOf<Int?>(null) }

    val api = remember { PipedApiService.create() }
    val comments = remember(videoId, sortMode) {
        Pager(PagingConfig(pageSize = 20)) { CommentsPagingSource(api, videoId) }.flow
    }.collectAsLazyPagingItems()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${comments.itemCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sort tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommentSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Comment list
            when {
                comments.loadState.refresh is LoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                    ) {
                        items(comments.itemCount) { index ->
                            val comment = comments[index]
                            if (comment != null) {
                                FlowCommentItem(
                                    comment = comment,
                                    onReplyClick = { /* TODO: expand replies */ }
                                )
                            }
                        }
                        if (comments.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowCommentItem(
    comment: CommentItem,
    onReplyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = comment.authorThumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.commentText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatCommentCount(comment.likes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = comment.commentedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (comment.replyCount > 0) {
                        Text(
                            text = "${comment.replyCount} replies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onReplyClick() }
                        )
                    }
                }
            }
        }
    }
}

private fun formatCommentCount(likes: Long): String = when {
    likes >= 1_000_000 -> String.format("%.1fM", likes / 1_000_000.0)
    likes >= 1_000 -> String.format("%.1fK", likes / 1_000.0)
    else -> likes.toString()
}

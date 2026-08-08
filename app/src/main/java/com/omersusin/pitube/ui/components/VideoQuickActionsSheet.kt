package com.omersusin.pitube.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.NotInterestedRepository
import com.omersusin.pitube.data.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickActionsBottomSheet(
    video: VideoItem,
    onDismiss: () -> Unit,
    onWatchLater: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onNotInterested: () -> Unit = {},
    onChannelClick: ((String) -> Unit)? = null,
    onBlockChannel: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val notInterestedRepo = remember { NotInterestedRepository(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val maxHeight = configuration.screenHeightDp.dp * 0.65f

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(bottom = 24.dp)
        ) {
            // Video info header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.uploaderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider()
            }

            // Action Grid
            item {
                FlowActionGrid(
                    actions = listOf(
                        FlowAction(
                            icon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                            text = "Save",
                            onClick = { onDismiss() }
                        ),
                        FlowAction(
                            icon = {
                                Icon(
                                    Icons.Outlined.WatchLater,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = "Watch Later",
                            onClick = {
                                onWatchLater?.invoke()
                                onDismiss()
                            }
                        ),
                        FlowAction(
                            icon = { Icon(Icons.Outlined.Share, null) },
                            text = "Share",
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                                    putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.videoId}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Channel Group
            item {
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbUp, null) },
                            title = { Text("Like") },
                            onClick = {
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbDown, null) },
                            title = { Text("Dislike") },
                            onClick = {
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Algorithm Group
            item {
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = { Text("Mark as Watched") },
                            onClick = { onDismiss() }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbUp, null) },
                            title = { Text("I Like This") },
                            onClick = {
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbDown, null) },
                            title = { Text("Not Interested") },
                            onClick = {
                                notInterestedRepo.hideVideo(video)
                                onNotInterested()
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = {
                                Icon(
                                    Icons.Outlined.Block,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            title = {
                                Text(
                                    "Don't Show Channel",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                onBlockChannel?.invoke(video.uploaderName)
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Utility Group
            item {
                FlowMenuGroup(
                    items = listOf(
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Download, null) },
                            title = { Text("Download") },
                            onClick = {
                                onDownload?.invoke()
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Info, null) },
                            title = { Text("Details") },
                            onClick = { onDismiss() }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

data class FlowAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

data class FlowMenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val onClick: (() -> Unit)? = null
)

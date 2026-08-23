package com.omersusin.pitube.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.video.DownloadedVideo
import com.omersusin.pitube.ui.components.ShortsCard

@Composable
internal fun LibraryShelf(
    title: String,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun LibraryMediaShelf(
    title: String,
    items: List<LibraryMediaItem>,
    sourceName: String,
    onTitleClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onDownloadedVideoClick: (List<DownloadedVideo>, Int) -> Unit
) {
    val downloadedVideoQueue = remember(items) {
        items.mapNotNull { (it as? LibraryMediaItem.DownloadedVideoItem)?.download }
    }

    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(
            items = items,
            key = LibraryMediaItem::key,
            contentType = {
                when (it) {
                    is LibraryMediaItem.VideoItem,
                    is LibraryMediaItem.DownloadedVideoItem -> "video"
                }
            }
        ) { item ->
            when (item) {
                is LibraryMediaItem.VideoItem -> LibraryVideoCard(
                    video = item.video,
                    onClick = { onVideoClick(item.video) }
                )
                is LibraryMediaItem.DownloadedVideoItem -> LibraryVideoCard(
                    video = item.download.video,
                    onClick = {
                        val index = downloadedVideoQueue.indexOfFirst {
                            it.video.id == item.download.video.id
                        }
                        if (index >= 0) onDownloadedVideoClick(downloadedVideoQueue, index)
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryShortsShelf(
    title: String,
    shorts: List<Video>,
    onTitleClick: () -> Unit,
    onShortClick: (Video) -> Unit
) {
    LibraryShelf(title = title, onTitleClick = onTitleClick) {
        items(shorts, key = Video::id, contentType = { "short" }) { short ->
            ShortsCard(video = short, onClick = { onShortClick(short) })
        }
    }
}

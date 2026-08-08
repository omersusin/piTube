package com.omersusin.pitube.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.VideoChapter

private fun formatTimestamp(ms: Long): String = String.format("%d:%02d", ms / 60000, (ms / 1000) % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersBottomSheet(
    chapters: List<VideoChapter>,
    currentPositionMs: Long,
    onChapterClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Find current chapter index
    val currentChapterIndex = remember(chapters, currentPositionMs) {
        chapters.indexOfLast { it.startMs <= currentPositionMs }.coerceAtLeast(0)
    }

    // Auto-scroll to current chapter
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val isCurrentChapter = index == currentChapterIndex
                    val progress = if (isCurrentChapter && index < chapters.size - 1) {
                        val nextChapterStart = chapters.getOrNull(index + 1)?.startMs ?: chapter.startMs + 60000
                        val duration = nextChapterStart - chapter.startMs
                        if (duration > 0) ((currentPositionMs - chapter.startMs).toFloat() / duration).coerceIn(0f, 1f) else 0f
                    } else if (isCurrentChapter) {
                        0f
                    } else {
                        0f
                    }

                    ChapterItem(
                        chapter = chapter,
                        index = index,
                        isCurrentChapter = isCurrentChapter,
                        progress = progress,
                        onClick = { onChapterClick(chapter.startMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: VideoChapter,
    index: Int,
    isCurrentChapter: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrentChapter) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chapter number or play indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentChapter) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = formatTimestamp(chapter.startMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Progress indicator for current chapter
        if (isCurrentChapter && progress > 0f) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

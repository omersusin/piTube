package com.omersusin.pitube.ui.screens.likedvideos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.LikedVideoInfo
import com.omersusin.pitube.data.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesScreen(
    onVideoClick: (Video) -> Unit,
    onBackClick: () -> Unit,
    onPlayQueue: (List<Video>, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: LikedVideosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.likes),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.likedVideos.isNotEmpty()) {
                        FilterChip(
                            selected = uiState.typeFilter == LikedTypeFilter.MUSIC,
                            onClick = {
                                viewModel.setTypeFilter(
                                    if (uiState.typeFilter == LikedTypeFilter.MUSIC) {
                                        LikedTypeFilter.ALL
                                    } else {
                                        LikedTypeFilter.MUSIC
                                    },
                                )
                            },
                            label = { Text(stringResource(R.string.liked_filter_music)) },
                        )
                        Box {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.playlist_sort_options),
                                )
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                            ) {
                                LikedSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(likedSortLabel(option)) },
                                        onClick = {
                                            sortExpanded = false
                                            viewModel.setSort(option)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { onPlayQueue(uiState.likedVideos.map { it.toVideo() }, 0) },
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_all))
                        }
                        IconButton(
                            onClick = {
                                val shuffled = uiState.likedVideos.map { it.toVideo() }.shuffled()
                                if (shuffled.isNotEmpty()) onPlayQueue(shuffled, 0)
                            },
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.likedVideos.isEmpty() -> {
                    EmptyLikesState(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = uiState.likedVideos,
                            key = { it.videoId },
                        ) { like ->
                            LikedVideoCard(
                                video = like,
                                onClick = { onVideoClick(like.toVideo()) },
                                onUnlikeClick = { viewModel.removeLike(like.videoId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedVideoCard(
    video: LikedVideoInfo,
    onClick: () -> Unit,
    onUnlikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(156.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                )

                IconButton(
                    onClick = onUnlikeClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = stringResource(R.string.unlike),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (video.channelName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLikesState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.empty_liked),
    body: String = stringResource(R.string.empty_liked_body),
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ThumbUp,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LikedVideoInfo.toVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = "",
        thumbnailUrl = thumbnail,
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        isShort = false,
    )

@Composable
private fun likedSortLabel(sort: LikedSort): String =
    when (sort) {
        LikedSort.NEWEST -> stringResource(R.string.history_sort_newest)
        LikedSort.OLDEST -> stringResource(R.string.history_sort_oldest)
        LikedSort.TITLE -> stringResource(R.string.liked_sort_title)
    }

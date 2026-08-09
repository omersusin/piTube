package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.PlaylistCard

@Composable
fun PlaylistsScreen(
    onBackClick: () -> Unit,
    onVideoPlaylistClick: (PlaylistInfo) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val videoState by viewModel.uiState.collectAsStateWithLifecycle()
    var contentFilter by rememberSaveable { mutableStateOf(PlaylistContentFilter.Videos) }
    var ownershipFilter by rememberSaveable { mutableStateOf(PlaylistOwnershipFilter.All) }
    var creationTarget by remember { mutableStateOf<PlaylistCreationTarget?>(null) }
    var videoToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }

    val visibleVideoPlaylists = remember(
        videoState.playlists,
        videoState.savedPlaylists,
        ownershipFilter
    ) {
        ownershipFilter.select(videoState.playlists, videoState.savedPlaylists)
    }
    val isLoading = videoState.isLoading

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            PlaylistLibraryTopBar(
                title = stringResource(R.string.library_playlists_label),
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            PlaylistCreationFabMenu(
                onCreateVideo = { creationTarget = PlaylistCreationTarget.Video }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            PlaylistLibraryFilterRow(
                selectedContent = contentFilter,
                onContentSelected = { contentFilter = it },
                selectedOwnership = ownershipFilter,
                onOwnershipSelected = { ownershipFilter = it }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (visibleVideoPlaylists.isEmpty()) {
                            item(
                                key = "empty-video-playlists",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "empty"
                            ) {
                                EmptyPlaylistLibraryState()
                            }
                        } else {
                            items(
                                items = visibleVideoPlaylists,
                                key = { "video-${it.id}" },
                                contentType = { "video-playlist" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onVideoPlaylistClick(playlist) },
                                    onDeleteClick = { videoToDelete = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    PlaylistCreationDialogHost(
        target = creationTarget,
        onDismiss = { creationTarget = null },
        onCreateVideo = { name, description, isPrivate ->
            viewModel.createPlaylist(name, description, isPrivate)
        }
    )

    PlaylistManagementDialogHost(
        videoToDelete = videoToDelete,
        onDismissVideoDelete = { videoToDelete = null },
        onConfirmVideoDelete = {
            viewModel.deletePlaylist(it.id)
            videoToDelete = null
        }
    )
}

@Composable
private fun PlaylistLibraryTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyPlaylistLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
        Text(
            text = stringResource(R.string.no_playlists_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

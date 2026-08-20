package com.omersusin.pitube.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omersusin.pitube.data.local.ChannelSubscription
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.ui.components.VideoCardFullWidth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val channels by viewModel.subscribedChannels.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val feed by viewModel.subscriptionFeed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abonelikler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading && feed.isNotEmpty(),
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groups.isNotEmpty()) {
                    item(key = "group-filter") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "group-all") {
                                FilterChip(
                                    selected = selectedGroupId == null,
                                    onClick = { viewModel.selectGroup(null) },
                                    label = { Text("Tümü") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            items(groups, key = { it.name }) { group ->
                                FilterChip(
                                    selected = selectedGroupId == group.name,
                                    onClick = {
                                        viewModel.selectGroup(
                                            if (selectedGroupId == group.name) null else group.name
                                        )
                                    },
                                    label = { Text(group.name) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                progress?.let { (done, total) ->
                    item(key = "progress") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = "$done / $total kanal kontrol ediliyor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearWavyProgressIndicator(
                                progress = { if (total > 0) done.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (channels.isNotEmpty()) {
                    item(key = "channel-rail") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(channels.take(30), key = { it.channelId }) { channel ->
                                ChannelRailItem(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.channelId) }
                                )
                            }
                        }
                    }
                }

                if (isLoading && feed.isEmpty()) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                        }
                    }
                } else if (channels.isEmpty()) {
                    item(key = "empty-no-channels") {
                        EmptySubscriptions(
                            onImportClick = onNavigateToImport,
                            onLoginClick = onNavigateToLogin
                        )
                    }
                } else if (feed.isEmpty()) {
                    item(key = "empty-feed") {
                        FeedEmptyState(
                            isGroupFiltered = selectedGroupId != null,
                            error = error,
                            onRetry = { viewModel.refresh(force = true) },
                            onClearGroup = { viewModel.selectGroup(null) }
                        )
                    }
                } else {
                    items(feed, key = { it.id }) { video ->
                        VideoCardFullWidth(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ChannelRailItem(
    channel: ChannelSubscription,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .width(64.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (channel.channelThumbnail.isNotBlank()) {
                AsyncImage(
                    model = channel.channelThumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = channel.channelName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = channel.channelName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptySubscriptions(
    onImportClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        Text(
            text = "Henüz kanal yok",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Takip ettiğin kanallar burada görünecek. İçe aktar veya giriş yap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("İçe aktar")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Giriş yap")
        }
    }
}

@Composable
private fun FeedEmptyState(
    isGroupFiltered: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onClearGroup: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp)
    ) {
        Text(
            text = when {
                error != null -> error
                isGroupFiltered -> "Bu gruptan son yüklenen video yok."
                else -> "Kanallarından son yüklenen video yok."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        when {
            error != null -> OutlinedButton(onClick = onRetry) { Text("Tekrar dene") }
            isGroupFiltered -> OutlinedButton(onClick = onClearGroup) { Text("Tüm kanalları göster") }
        }
    }
}

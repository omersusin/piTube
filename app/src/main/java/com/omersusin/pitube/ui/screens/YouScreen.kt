package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.PlaylistRepository
import com.omersusin.pitube.data.ProfileKind
import com.omersusin.pitube.data.ProfileManager
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.ui.components.PlaylistCard

@Composable
fun YouScreen(
    account: AccountFetcher.AccountInfo?,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLogin: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    var showLiked by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val profileManager = remember { ProfileManager(context) }
    val profiles by profileManager.profiles.collectAsState()
    val activeProfileId by profileManager.activeProfileId.collectAsState()

    if (showLiked) { LikedVideosScreen(onBack = { showLiked = false }, onVideoClick = onVideoClick); return }
    if (showStats) { StatsScreen(onBack = { showStats = false }); return }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Profile Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        val url = account?.avatarUrl
                        if (url != null) AsyncImage(model = url, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account?.name ?: "Not signed in", style = MaterialTheme.typography.titleLarge)
                        if (!account?.handle.isNullOrBlank()) {
                            Text(account?.handle ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            }
        }

        // Library Section Header
        item {
            Text(
                "Library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Library Items
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showLiked = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Liked Videos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDownloads
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Downloads", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenHistory
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("History", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showStats = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Stats", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Account Section
        if (account == null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenLogin
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Sign In", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Playlists Section
        val playlists = remember { PlaylistRepository.getAll(context) }
        if (playlists.isNotEmpty()) {
            item {
                Text(
                    "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(playlists.size) { index ->
                PlaylistCard(
                    playlist = playlists[index],
                    onClick = { /* TODO: open playlist */ }
                )
            }
        }
    }
}

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.VideoItem

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

    if (showLiked) { LikedVideosScreen(onBack = { showLiked = false }, onVideoClick = onVideoClick); return }
    if (showStats) { StatsScreen(onBack = { showStats = false }); return }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    val url = account?.avatarUrl
                    if (url != null) AsyncImage(model = url, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column { Text(account?.name ?: "Not signed in", style = MaterialTheme.typography.titleLarge); Text(account?.handle ?: "", style = MaterialTheme.typography.bodySmall) }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        item { Text("Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(8.dp)) }
        item { LibraryRow(Icons.Default.Favorite, "Liked Videos") { showLiked = true } }
        item { LibraryRow(Icons.Default.Download, "Downloads") { onOpenDownloads() } }
        item { LibraryRow(Icons.Default.History, "History") { onOpenHistory() } }
        item { LibraryRow(Icons.Default.BarChart, "Stats") { showStats = true } }
    }
}

@Composable
private fun LibraryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(modifier = Modifier.width(16.dp)); Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

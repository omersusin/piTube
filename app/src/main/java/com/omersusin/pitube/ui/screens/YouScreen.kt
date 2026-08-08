package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.HistoryManager
import com.omersusin.pitube.data.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
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
    val history = remember { HistoryManager.getHistory(context) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("You", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            if (AuthManager.isLoggedIn(context)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(96.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        val url = account?.avatarUrl
                        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(contentAlignment = Alignment.Center) { Text((account?.name?.firstOrNull() ?: 'U').toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(account?.name ?: "Google account", style = MaterialTheme.typography.headlineMedium)
                        Text("${account?.handle ?: "@piTube"}  •  member", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column {
                    Text("Sign in to get your feed, subscriptions and profile.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenLogin) { Icon(Icons.Default.AccountCircle, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Sign in with Google") }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (history.isNotEmpty()) {
            item { Text("History", style = MaterialTheme.typography.titleLarge); Spacer(modifier = Modifier.height(8.dp)) }
            item {
                LazyRow {
                    items(history.take(15)) { video ->
                        Column(modifier = Modifier.width(160.dp).clickable { onVideoClick(video) }.padding(end = 8.dp)) {
                            AsyncImage(model = video.safeThumb, contentDescription = null, modifier = Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        item { Text("Library", style = MaterialTheme.typography.titleLarge); Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpenDownloads() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null); Spacer(modifier = Modifier.width(16.dp))
                Text("Downloads", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpenHistory() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null); Spacer(modifier = Modifier.width(16.dp))
                Text("Watch History", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

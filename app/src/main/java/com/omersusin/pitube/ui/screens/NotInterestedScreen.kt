package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.NotInterestedRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotInterestedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { NotInterestedRepository(context) }
    val hiddenVideos by repo.hiddenVideos.collectAsState()
    val blockedChannels by repo.blockedChannels.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Not Interested") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        if (hiddenVideos.isEmpty() && blockedChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing hidden yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (hiddenVideos.isNotEmpty()) {
                    item {
                        Text("Hidden Videos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    }
                    items(hiddenVideos) { video ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, style = MaterialTheme.typography.bodyLarge)
                                Text(video.channelName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { repo.unhideVideo(video.videoId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Unhide")
                            }
                        }
                    }
                }
                if (blockedChannels.isNotEmpty()) {
                    item {
                        Text("Blocked Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    }
                    items(blockedChannels) { channel ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                                Text(channel.channelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { repo.unblockChannel(channel.channelId, channel.name) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}

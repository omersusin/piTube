package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.SavedChannel
import com.omersusin.pitube.data.SubscriptionManager
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf<List<SavedChannel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<SavedChannel?>(null) }
    var channelVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        channels = SubscriptionManager.getSavedChannels(context)
    }

    if (selectedChannel != null) {
        LaunchedEffect(selectedChannel) {
            isLoading = true
            scope.launch {
                try {
                    val api = PipedApiService.create()
                    val info = api.getChannel(selectedChannel!!.channelId)
                    channelVideos = info.relatedStreams
                } catch (e: Exception) {
                    channelVideos = emptyList()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedChannel != null) {
            // Channel Videos View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedChannel = null }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("← Back to Channels", color = MaterialTheme.colorScheme.primary)
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channelVideos) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVideoClick(video) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(video.uploadedDate, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            // Saved Channels View
            if (channels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No subscriptions yet.\nSubscribe to a channel from a video!", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channels) { channel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedChannel = channel }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(channel.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                SubscriptionManager.removeChannel(context, channel.channelId)
                                channels = SubscriptionManager.getSavedChannels(context)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

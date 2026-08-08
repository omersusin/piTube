package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.InnerTubeFeed
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.PrefsManager
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val context = LocalContext.current
    val zenMode = remember { PrefsManager.isZenMode(context) }

    if (zenMode) {
        SubscriptionsScreen(onVideoClick = onVideoClick)
        return
    }

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                var list = emptyList<VideoItem>()
                if (AuthManager.isLoggedIn(context)) {
                    list = InnerTubeFeed.fetchFeed(context, "FEwhat_to_watch")
                }
                if (list.isEmpty()) list = PipedApiService.create().getTrending()
                videos = list
                isLoading = false
            } catch (e: Exception) { error = e.message; isLoading = false }
        }
    }

    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (error != null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error", color = MaterialTheme.colorScheme.error) } }
    else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(videos) { video -> VideoCard(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.url.substringAfter("channel/")) }) }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, onChannelClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(modifier = Modifier.clickable(onClick = onClick)) {
            AsyncImage(model = video.safeThumb, contentDescription = video.title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            AsyncImage(model = video.uploaderAvatar, contentDescription = video.uploaderName, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onChannelClick), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.clickable(onClick = onClick)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text("${video.uploaderName}${video.uploadedDate?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

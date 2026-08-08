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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.PrefsManager
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val zenMode = remember { PrefsManager.isZenMode(androidx.compose.ui.platform.LocalContext.current) }

    if (zenMode) {
        if (AuthManager.isLoggedIn(androidx.compose.ui.platform.LocalContext.current)) {
            SubscriptionsScreen(onVideoClick = onVideoClick)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧘 Zen Mode", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sign in with Google to see only your subscriptions.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try { videos = PipedApiService.create().getTrending(); isLoading = false } 
            catch (e: Exception) { error = e.message; isLoading = false }
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
            AsyncImage(model = video.thumbnailUrl, contentDescription = video.title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            AsyncImage(
                model = video.uploaderAvatar,
                contentDescription = video.uploaderName,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onChannelClick),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.clickable(onClick = onClick)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text("${video.uploaderName} • ${video.uploadedDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

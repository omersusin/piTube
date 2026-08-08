package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.GoogleSubscriptionRepository
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(AuthManager.isLoggedIn(context)) }
    var feedVideos by remember { mutableStateOf<List<GoogleSubscriptionRepository.GoogleFeedVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            isLoading = true
            error = null
            scope.launch {
                try {
                    feedVideos = GoogleSubscriptionRepository.fetchSubscriptionFeed(context)
                    if (feedVideos.isEmpty()) error = "No videos found in your feed"
                } catch (e: Exception) { error = e.message ?: "Failed to load feed" } 
                finally { isLoading = false }
            }
        } else { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isLoggedIn) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sign in to see your subscriptions", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Go to Settings > Sign in with Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { 
                        isLoading = true
                        scope.launch {
                            try { feedVideos = GoogleSubscriptionRepository.fetchSubscriptionFeed(context); error = null } 
                            catch (e: Exception) { error = e.message } 
                            finally { isLoading = false }
                        }
                    }) { Text("Retry") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(feedVideos) { video ->
                    FeedVideoCard(video = video) {
                        val videoItem = VideoItem(
                            url = video.url, title = video.name, thumbnailUrl = video.thumbnailUrl,
                            uploaderName = video.uploaderName, uploaderAvatar = null, duration = video.duration.toInt(),
                            views = 0L, uploadedDate = video.uploadDate, isShort = false
                        )
                        onVideoClick(videoItem)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedVideoCard(video: GoogleSubscriptionRepository.GoogleFeedVideo, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        AsyncImage(model = video.thumbnailUrl, contentDescription = video.name, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column {
                Text(text = video.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "${video.uploaderName} • ${video.uploadDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

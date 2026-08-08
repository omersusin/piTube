package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val trending = PipedApiService.create().getTrending()
                videos = trending.items
                
                val history = WatchHistoryRepository.getRecentWatches(context, 5)
                if (history.isNotEmpty()) {
                    val seed = history.first()
                    val seedVideo = VideoItem(
                        videoId = seed.videoId,
                        title = seed.title,
                        thumbnailUrl = seed.thumbnailUrl,
                        uploaderName = seed.channelName,
                        isShort = false
                    )
                    recommendations = RecommendationEngine.getRecommendations(context, seedVideo, 10)
                }
            } catch (e: Exception) {
                // Fallback to empty
            } finally {
                isLoading = false
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            if (recommendations.isNotEmpty()) {
                item {
                    Text("Recommended for You", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
                items(recommendations) { video ->
                    VideoListItem(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.uploaderName) })
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            item {
                Text("Trending", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            items(videos) { video ->
                VideoListItem(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.uploaderName) })
            }
        }
    }
}

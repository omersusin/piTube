package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.*
import com.omersusin.pitube.ui.components.VideoListItem
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                FlowNeuroEngine.initialize(context)
                val trending = PipedApiService.create().getTrending()
                
                // Use FlowNeuroEngine to rank videos based on user preferences
                val ranked = FlowNeuroEngine.rank(context, trending)
                videos = ranked
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    videos = PipedApiService.create().getTrending()
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                Text(
                    "For You",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    onClick = { 
                        onVideoClick(video)
                        // Record interaction when video is clicked
                        scope.launch {
                            FlowNeuroEngine.recordInteraction(
                                context = context,
                                videoId = video.videoId,
                                title = video.title,
                                channelName = video.uploaderName,
                                channelId = null,
                                type = InteractionType.CLICK
                            )
                        }
                    },
                    onChannelClick = { onChannelClick(video.uploaderName) }
                )
            }
        }
    }
}

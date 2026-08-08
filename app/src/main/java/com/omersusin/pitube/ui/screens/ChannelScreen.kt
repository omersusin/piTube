package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.ChannelResolver
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(channelId: String, onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit) {
    var page by remember { mutableStateOf<ChannelResolver.ChannelPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelId) {
        scope.launch { page = ChannelResolver.resolve(channelId); isLoading = false }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(page?.name ?: "Channel") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (page == null || page!!.videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No videos found") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        page?.avatarUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(96.dp).clip(CircleShape)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(page?.name ?: "", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Videos", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(page!!.videos) { video ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(bottom = 16.dp)) {
                        AsyncImage(model = video.safeThumb, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${video.uploaderName}${video.uploadedDate?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

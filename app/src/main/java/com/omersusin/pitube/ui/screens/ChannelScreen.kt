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
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.omersusin.pitube.data.ChannelResolver
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(channelId: String, onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit) {
    var page by remember { mutableStateOf<com.omersusin.pitube.data.ChannelPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelId) {
        scope.launch {
            page = ChannelResolver.resolve(channelId)
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(page?.name ?: "Channel") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (page == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No videos found") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = page?.avatarUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(page?.name ?: "", style = MaterialTheme.typography.titleLarge)
                    }
                }
                items(page?.videos ?: emptyList()) { video ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = video.safeThumb, contentDescription = null, modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column { Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.*
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val merged = mutableListOf<VideoItem>()
            // Account feed
            try { merged.addAll(InnerTubeFeed.fetchFeed(context, "FEsubscriptions")) } catch (e: Exception) {}
            // Local RSS subs
            for (sub in LocalSubs.getAll(context)) {
                try {
                    val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${sub.channelId}"
                    val client = okhttp3.OkHttpClient()
                    val resp = client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
                    val xml = resp.body?.string() ?: ""
                    val feed = ChannelRssParser.parse(xml)
                    feed.entries.forEach { e ->
                        merged.add(VideoItem(
                            url = "https://www.youtube.com/watch?v=${e.videoId}",
                            title = e.title,
                            thumbnailUrl = e.thumbnailUrl,
                            uploaderName = feed.channelName ?: sub.name,
                            uploaderAvatar = sub.avatarUrl,
                            uploaderUrl = "https://www.youtube.com/channel/${sub.channelId}",
                            duration = 0, views = e.viewCount, uploadedDate = null, isShort = false
                        ))
                    }
                } catch (e: Exception) {}
            }
            videos = merged.distinctBy { it.videoId }
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
    } else if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("No subscription videos.\nSign in or add local subscriptions.") }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(videos) { video ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(bottom = 16.dp)) {
                    AsyncImage(model = video.safeThumb, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(video.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val refresh = {
        scope.launch {
            isRefreshing = true
            val merged = mutableListOf<VideoItem>()
            try { merged.addAll(InnerTubeFeed.fetchFeed(context, "FEsubscriptions")) } catch (e: Exception) {}
            for (sub in LocalSubs.getAll(context)) {
                try {
                    val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${sub.channelId}"
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    resp.use { r ->
                        val xml = r.body?.string() ?: ""
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
                    }
                } catch (e: Exception) {}
            }
            videos = merged.distinctBy { it.videoId }
            loading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            videos.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No subscription videos", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sign in or add local subscriptions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(videos) { video ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(bottom = 16.dp)) {
                        AsyncImage(
                            model = video.safeThumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(video.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

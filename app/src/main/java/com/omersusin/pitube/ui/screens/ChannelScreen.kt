package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.omersusin.pitube.data.ChannelResolver
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.ui.components.ChannelBanner
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(channelId: String, onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    var page by remember { mutableStateOf<com.omersusin.pitube.data.ChannelPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var subscribed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tabs = listOf("Videos", "About")

    LaunchedEffect(channelId) {
        scope.launch {
            page = ChannelResolver.resolve(context, channelId)
            subscribed = com.omersusin.pitube.data.LocalSubscriptionsRepository(context).isSubscribed(page?.videos?.firstOrNull()?.channelId ?: channelId)
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.name ?: "Channel") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { paddingValues ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (page == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Channel not found", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Channel Banner
                ChannelBanner(imageUrl = page?.bannerUrl)

                // Channel Header
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = page?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(page?.name ?: "", style = MaterialTheme.typography.headlineSmall)
                            if (!page?.handle.isNullOrBlank()) {
                                Text(page?.handle ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!page?.subscriberCountText.isNullOrBlank()) {
                                Text(page?.subscriberCountText ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val repo = com.omersusin.pitube.data.LocalSubscriptionsRepository(context)
                                    val cid = page?.videos?.firstOrNull()?.channelId ?: channelId
                                    val ok = if (com.omersusin.pitube.data.AuthManager.isLoggedIn(context)) {
                                        com.omersusin.pitube.data.VideoEngagement.subscribe(context, cid, !subscribed)
                                    } else true
                                    if (ok) {
                                        if (subscribed) repo.unsubscribe(cid)
                                        else repo.subscribe(com.omersusin.pitube.data.LocalSubscription(cid, page?.name ?: "", page?.avatarUrl ?: ""))
                                        subscribed = !subscribed
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error)
                        ) {
                            Text(if (subscribed) "Subscribed" else "Subscribe")
                        }
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = {
                                when (index) {
                                    0 -> Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                    1 -> Icon(Icons.Default.Info, contentDescription = null)
                                }
                            }
                        )
                    }
                }

                // Tab Content
                when (selectedTab) {
                    0 -> { // Videos
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(page?.videos ?: emptyList()) { video ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = video.safeThumb,
                                        contentDescription = null,
                                        modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // About
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                            Column {
                                Text("Channel ID", style = MaterialTheme.typography.titleMedium)
                                Text(channelId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!page?.subscriberCountText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Subscribers", style = MaterialTheme.typography.titleMedium)
                                    Text(page?.subscriberCountText ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

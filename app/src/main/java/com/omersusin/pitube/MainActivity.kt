package com.omersusin.pitube

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.*
import com.omersusin.pitube.service.PlaybackService
import com.omersusin.pitube.ui.screens.*
import com.omersusin.pitube.ui.theme.PiTubeTheme
import com.omersusin.pitube.ui.theme.ThemeMode

object PipState { val inPip = mutableStateOf(false) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, PlaybackService::class.java))
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            PiTubeTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PiTubeApp(themeMode = themeMode, onThemeChange = { themeMode = it })
                }
            }
        }
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerHolder.getPlayer(this).isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }
    override fun onPictureInPictureModeChanged(b: Boolean) { super.onPictureInPictureModeChanged(b); PipState.inPip.value = b }
    override fun onDestroy() { super.onDestroy(); if (isFinishing) PlayerHolder.releasePlayer() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiTubeApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSubsMgmt by remember { mutableStateOf(false) }
    var showNotInterested by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<AccountFetcher.AccountInfo?>(null) }

    LaunchedEffect(showLogin) {
        if (AuthManager.isLoggedIn(context)) account = AccountFetcher.getCached(context) ?: AccountFetcher.fetch(context)?.also { AccountFetcher.cache(context, it) }
        else account = null
    }

    // Resume last session (fresh within 12h)
    LaunchedEffect(Unit) {
        val r = SessionResume.load(context)
        if (r != null && System.currentTimeMillis() - r.timestamp < 12 * 3600 * 1000L && selectedVideo == null) {
            selectedVideo = VideoItem(
                url = "https://www.youtube.com/watch?v=${r.videoId}", title = r.title,
                thumbnailUrl = r.thumbnailUrl, uploaderName = r.channelName, uploaderAvatar = null,
                duration = (r.durationMs / 1000).toInt(), views = 0L, uploadedDate = null, isShort = false
            )
        }
    }

    BackHandler {
        when {
            selectedVideo != null -> { if (PlayerHolder.getPlayer(context).isPlaying) NowPlaying.showMini.value = true; selectedVideo = null }
            selectedChannel != null -> selectedChannel = null
            showLogin -> showLogin = false
            showSettings -> showSettings = false
            showDownloads -> showDownloads = false
            showHistory -> showHistory = false
            showStats -> showStats = false
            showSubsMgmt -> showSubsMgmt = false
            showNotInterested -> showNotInterested = false
            selectedTab == 4 -> selectedTab = 0
            else -> (context as? android.app.Activity)?.finish()
        }
    }

    if (showDownloads) { DownloadsScreen(onBack = { showDownloads = false }); return }
    if (showHistory) { HistoryScreen(onBack = { showHistory = false }, onVideoClick = { selectedVideo = it; NowPlaying.current.value = it }); return }
    if (showStats) { StatsScreen(onBack = { showStats = false }); return }
    if (showSubsMgmt) { SubsManagementScreen(onBack = { showSubsMgmt = false }); return }
    if (showNotInterested) { NotInterestedScreen(onBack = { showNotInterested = false }); return }
    if (showSettings) { SettingsScreen(currentTheme = themeMode, onThemeChange = onThemeChange, onBack = { showSettings = false }, onOpenLogin = { showLogin = true }, onOpenDownloads = { showDownloads = true }, onOpenHistory = { showHistory = true }, onOpenStats = { showStats = true }, onOpenSubsMgmt = { showSubsMgmt = true }, onOpenNotInterested = { showNotInterested = true }, account = account); return }
    if (selectedChannel != null) { ChannelScreen(channelId = selectedChannel!!, onBack = { selectedChannel = null }, onVideoClick = { selectedVideo = it; NowPlaying.current.value = it }); return }
    if (showLogin) { YouTubeLoginScreen(onBack = { showLogin = false }); return }
    if (selectedVideo != null) { VideoPlayerScreen(video = selectedVideo!!, onBack = { if (PlayerHolder.getPlayer(context).isPlaying) NowPlaying.showMini.value = true; selectedVideo = null }, onVideoClick = { selectedVideo = it; NowPlaying.current.value = it }, onChannelClick = { selectedChannel = it }); return }

    val tabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("Shorts", Icons.Default.PlayArrow),
        TabItem("Subs", Icons.Default.Subscriptions),
        TabItem("You", Icons.Default.Person)
    )

    Scaffold(
        topBar = {
            if (selectedTab != 3) {
                TopAppBar(
                    title = { Text("piTube", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { selectedTab = 4 }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                )
            }
        },
        bottomBar = {
            Column {
                if (NowPlaying.showMini.value && selectedVideo == null) {
                    MiniPlayerBar(
                        onOpen = { NowPlaying.showMini.value = false; selectedVideo = NowPlaying.current.value },
                        onClose = { PlayerHolder.getPlayer(context).stop(); NowPlaying.showMini.value = false; NowPlaying.current.value = null }
                    )
                }
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = {
                                if (index == 3) {
                                    Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                        val url = account?.avatarUrl
                                        if (url != null) coil.compose.AsyncImage(model = url, contentDescription = "You", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, "You", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                                    }
                                } else Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(onVideoClick = { selectedVideo = it; NowPlaying.current.value = it }, onChannelClick = { selectedChannel = it })
                1 -> ShortsScreen()
                2 -> SubscriptionsScreen(onVideoClick = { selectedVideo = it; NowPlaying.current.value = it })
                3 -> YouScreen(account = account, onOpenSettings = { showSettings = true }, onOpenSearch = { selectedTab = 4 }, onOpenDownloads = { showDownloads = true }, onOpenHistory = { showHistory = true }, onOpenLogin = { showLogin = true }, onVideoClick = { selectedVideo = it; NowPlaying.current.value = it })
                4 -> SearchScreen(onVideoClick = { selectedVideo = it; NowPlaying.current.value = it }, onChannelClick = { selectedChannel = it })
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

package com.omersusin.pitube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.PlayerHolder
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.service.PlaybackService
import com.omersusin.pitube.ui.screens.*
import com.omersusin.pitube.ui.theme.PiTubeTheme
import com.omersusin.pitube.ui.theme.ThemeMode

object PipState { val inPip = mutableStateOf(false) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, PlaybackService::class.java))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        PipState.inPip.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) PlayerHolder.releasePlayer()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiTubeApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<AccountFetcher.AccountInfo?>(null) }

    LaunchedEffect(showLogin) {
        if (AuthManager.isLoggedIn(context)) {
            account = AccountFetcher.getCached(context) ?: AccountFetcher.fetch(context)?.also { AccountFetcher.cache(context, it) }
        } else account = null
    }

    if (showDownloads) { DownloadsScreen(onBack = { showDownloads = false }); return }
    if (showHistory) { HistoryScreen(onBack = { showHistory = false }, onVideoClick = { selectedVideo = it }); return }
    if (selectedChannel != null) { ChannelScreen(channelId = selectedChannel!!, onBack = { selectedChannel = null }, onVideoClick = { selectedVideo = it }); return }
    if (showLogin) { YouTubeLoginScreen(onBack = { showLogin = false }); return }
    if (selectedVideo != null) { VideoPlayerScreen(video = selectedVideo!!, onBack = { selectedVideo = null }, onVideoClick = { selectedVideo = it }, onChannelClick = { selectedChannel = it }); return }

    val tabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("Shorts", Icons.Default.PlayArrow),
        TabItem("Subs", Icons.Default.Subscriptions),
        TabItem("Settings", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            if (selectedTab != 3) {
                TopAppBar(
                    title = { Text("piTube", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { selectedTab = 4 }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        Surface(modifier = Modifier.size(32.dp).clickable { selectedTab = 3 }, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            val url = account?.avatarUrl
                            if (url != null) AsyncImage(model = url, contentDescription = "Profile", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Box(contentAlignment = Alignment.Center) { Text((account?.name?.firstOrNull() ?: 'U').toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(icon = { Icon(tab.icon, contentDescription = tab.label) }, label = { Text(tab.label) }, selected = selectedTab == index, onClick = { selectedTab = index })
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(onVideoClick = { selectedVideo = it }, onChannelClick = { selectedChannel = it })
                1 -> ShortsScreen()
                2 -> SubscriptionsScreen(onVideoClick = { selectedVideo = it })
                3 -> SettingsScreen(currentTheme = themeMode, onThemeChange = onThemeChange, onBack = { selectedTab = 0 }, onOpenLogin = { showLogin = true }, onOpenDownloads = { showDownloads = true }, onOpenHistory = { showHistory = true }, account = account)
                4 -> SearchScreen(onVideoClick = { selectedVideo = it })
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

package com.omersusin.pitube

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            PiTubeTheme(themeMode = themeMode) {
                PiTubeApp(themeMode = themeMode, onThemeChange = { themeMode = it })
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }

    if (selectedChannel != null) {
        ChannelScreen(
            channelId = selectedChannel!!,
            onBack = { selectedChannel = null },
            onVideoClick = { selectedVideo = it }
        )
        return
    }

    if (showLogin) { YouTubeLoginScreen(onBack = { showLogin = false }); return }

    if (selectedVideo != null) {
        VideoPlayerScreen(
            video = selectedVideo!!,
            onBack = { selectedVideo = null },
            onVideoClick = { newVideo -> selectedVideo = newVideo },
            onChannelClick = { id -> selectedChannel = id }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(currentTheme = themeMode, onThemeChange = onThemeChange, onBack = { showSettings = false }, onOpenLogin = { showLogin = true })
        return
    }

    val tabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("Shorts", Icons.Default.PlayArrow),
        TabItem("Subs", Icons.Default.Subscriptions),
        TabItem("Search", Icons.Default.Search)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("piTube", style = MaterialTheme.typography.titleLarge) },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") } }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(onVideoClick = { selectedVideo = it }, onChannelClick = { selectedChannel = it })
                1 -> ShortsScreen()
                2 -> SubscriptionsScreen(onVideoClick = { selectedVideo = it })
                3 -> SearchScreen(onVideoClick = { selectedVideo = it })
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

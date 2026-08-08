package com.omersusin.pitube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.NowPlaying
import com.omersusin.pitube.data.PlayerHolder
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.service.PlaybackService
import com.omersusin.pitube.ui.screens.*
import com.omersusin.pitube.ui.theme.PiTubeTheme
import com.omersusin.pitube.ui.theme.ThemeMode
import com.omersusin.pitube.ui.components.PiTubeSplashScreen
import com.omersusin.pitube.ui.components.FloatingBottomNavBar
import com.omersusin.pitube.ui.components.NavItem
import kotlinx.coroutines.delay

object PipState { val inPip = mutableStateOf(false) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, PlaybackService::class.java))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            var themeMode by remember { mutableStateOf(loadTheme(this@MainActivity)) }
            PiTubeTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PiTubeApp(themeMode = themeMode, onThemeChange = { themeMode = it; saveTheme(this@MainActivity, it) })
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = PlayerHolder.getPlayer(this)
        if (player.isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

@Composable
fun MiniPlayerBar(onOpen: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val item = NowPlaying.current.value ?: return
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            playing = PlayerHolder.getPlayer(context).isPlaying
            delay(500)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.safeThumb,
                contentDescription = null,
                modifier = Modifier
                    .width(100.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    item.uploaderName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                val p = PlayerHolder.getPlayer(context)
                if (p.isPlaying) p.pause() else p.play()
            }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiTubeApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSubsMgmt by remember { mutableStateOf(false) }
    var showNotInterested by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<AccountFetcher.AccountInfo?>(null) }

    if (showSplash) {
        PiTubeSplashScreen(onAnimationFinished = { showSplash = false })
        return
    }

    LaunchedEffect(showLogin) {
        if (AuthManager.isLoggedIn(context)) {
            account = AccountFetcher.getCached(context) ?: AccountFetcher.fetch(context)
        } else {
            account = null
        }
    }

    // Also refresh account on first composition
    LaunchedEffect(Unit) {
        if (AuthManager.isLoggedIn(context)) {
            account = AccountFetcher.getCached(context) ?: AccountFetcher.fetch(context)
        }
    }

    // Show mini player when video is playing and we're not in video player
    val isPlaying = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val player = PlayerHolder.getPlayer(context)
            isPlaying.value = player.isPlaying
            // Auto-show mini player when video starts playing and we navigate away
            if (player.isPlaying && NowPlaying.current.value != null && selectedVideo == null) {
                NowPlaying.showMini.value = true
            }
            delay(500)
        }
    }

    BackHandler {
        when {
            selectedVideo != null -> {
                if (PlayerHolder.getPlayer(context).isPlaying) {
                    NowPlaying.showMini.value = true
                }
                selectedVideo = null
            }
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

    val navItems = listOf(
        NavItem(0, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
        NavItem(1, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow, "Shorts"),
        NavItem(2, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, "Subs"),
        NavItem(3, Icons.Filled.Person, Icons.Outlined.Person, "You")
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
                if (NowPlaying.showMini.value && selectedVideo == null && NowPlaying.current.value != null) {
                    MiniPlayerBar(
                        onOpen = {
                            NowPlaying.showMini.value = false
                            selectedVideo = NowPlaying.current.value
                        },
                        onClose = {
                            PlayerHolder.getPlayer(context).stop()
                            NowPlaying.showMini.value = false
                            NowPlaying.current.value = null
                        }
                    )
                }
                FloatingBottomNavBar(
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
                    items = navItems
                )
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

data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)


fun loadTheme(context: android.content.Context): com.omersusin.pitube.ui.theme.ThemeMode {
    val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
    val saved = prefs.getString("theme_mode", "system") ?: "system"
    return try {
        com.omersusin.pitube.ui.theme.ThemeMode.valueOf(saved.uppercase())
    } catch (e: Exception) {
        when (saved) {
            "amoled" -> com.omersusin.pitube.ui.theme.ThemeMode.AMOLED
            "system" -> com.omersusin.pitube.ui.theme.ThemeMode.SYSTEM
            else -> com.omersusin.pitube.ui.theme.ThemeMode.SYSTEM
        }
    }
}

fun saveTheme(context: android.content.Context, mode: com.omersusin.pitube.ui.theme.ThemeMode) {
    val prefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putString("theme_mode", mode.name).apply()
}

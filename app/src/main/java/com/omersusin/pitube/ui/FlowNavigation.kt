package com.omersusin.pitube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlaylistRepository
import com.omersusin.pitube.player.GlobalPlayerState
import com.omersusin.pitube.ui.components.PlayerSheetValue
import com.omersusin.pitube.ui.components.PlayerDraggableState
import com.omersusin.pitube.ui.screens.home.HomeScreen
import com.omersusin.pitube.ui.screens.home.HomeViewModel
import com.omersusin.pitube.ui.screens.history.HistoryScreen
import com.omersusin.pitube.ui.screens.library.LibraryScreen
import com.omersusin.pitube.ui.screens.likedvideos.LikesScreen
import com.omersusin.pitube.ui.screens.playlists.PlaylistsScreen
import com.omersusin.pitube.ui.screens.playlists.PlaylistDetailScreen
import com.omersusin.pitube.ui.screens.notifications.NotificationScreen
import com.omersusin.pitube.ui.screens.player.VideoPlayerViewModel
import com.omersusin.pitube.ui.screens.player.VideoPlayerUiState
import com.omersusin.pitube.ui.screens.search.SearchScreen
import com.omersusin.pitube.ui.screens.account.YouTubeLoginScreen
import com.omersusin.pitube.ui.screens.settings.SettingsScreen
import com.omersusin.pitube.ui.screens.shorts.ShortsScreen
import com.omersusin.pitube.ui.screens.channel.ChannelScreen
import com.omersusin.pitube.ui.screens.onboarding.OnboardingScreen
import com.omersusin.pitube.ui.theme.CustomThemePalettes
import com.omersusin.pitube.ui.theme.ThemeMode
import com.omersusin.pitube.ui.theme.ThemeVariant
import androidx.media3.common.util.UnstableApi

@UnstableApi
fun NavGraphBuilder.flowAppGraph(
    navController: NavHostController,
    currentRoute: MutableState<String>,
    showBottomNav: MutableState<Boolean>,
    selectedBottomNavIndex: MutableIntState,
    playerSheetState: PlayerDraggableState,
    homeViewModel: HomeViewModel,
    playerViewModel: VideoPlayerViewModel,
    playerUiStateResult: State<VideoPlayerUiState>, 
    playerVisibleState: MutableState<Boolean>, 
    currentTheme: ThemeMode,
    themeVariant: ThemeVariant,
    customThemePalettes: CustomThemePalettes,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant,
    onThemeChange: (ThemeMode) -> Unit,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    onCustomThemePalettesChange: (CustomThemePalettes) -> Unit,
    onSystemLightThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeChange: (ThemeMode) -> Unit,
    onSystemDarkThemeVariantChange: (ThemeVariant) -> Unit,
    disableShortsPlayer: Boolean = false,
    defaultStartRoute: String = "home",
    /**
     * Opens the Voice/Song recognition modal; wired to the mic icon in the
     * Search screen's search bar (the only path to the modal from the UI).
     */
    onOpenRecognitionModal: () -> Unit = {},
    /**
     * Read lazily inside the destination that needs it. Destination lambdas are captured once
     * when NavHost remembers the graph, so a by-value Dp here is frozen at graph-construction
     * time and never reflects the bar showing or hiding.
     */
    bottomNavOverlayPadding: () -> Dp = { 0.dp }
) {
    // =============================================
    // ONBOARDING (First-time user experience)
    // =============================================
    composable("onboarding") {
        currentRoute.value = "onboarding"
        showBottomNav.value = false
        OnboardingScreen(
            onComplete = {
                // Navigate to the selected default tab and clear the backstack so user can't go back to onboarding
                navController.navigate(defaultStartRoute) {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        )
    }
    
    composable("home") {
        currentRoute.value = "home"
        showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
        selectedBottomNavIndex.intValue = 0
        val density = LocalDensity.current
        val config = LocalConfiguration.current
        // Use miniSizeScale live value: wide = screenWidth * 9/16 height, normal = 0
        val inlinePlayerHeight by remember {
            derivedStateOf {
                val scale = playerSheetState.miniSizeScale.value
                val isMini = playerSheetState.expandFraction.value > 0.5f
                if (isMini && scale > 1.5f) {
                    with(density) { (config.screenWidthDp.dp.toPx() * (9f / 16f)).toDp() }
                } else 0.dp
            }
        }
        HomeScreen(
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    playerViewModel.playVideo(video)
                    GlobalPlayerState.setCurrentVideo(video)
                }
            },
            onShortClick = { video ->
                if (disableShortsPlayer) {
                    playerViewModel.playVideo(video)
                    GlobalPlayerState.setCurrentVideo(video)
                } else {
                    navController.navigate("shorts?startVideoId=${video.id}")
                }
            },
            onNotificationClick = {
                navController.navigate("notifications")
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
            onNavigateToHistory = {
                navController.navigate("history")
            },
            onOpenShortsFeed = {
                navController.navigate("shorts")
            },
            viewModel = homeViewModel
        )
    }

    // Account (YouTube sign-in) Screen. `add=1` forces a fresh login even when
    // an account is already signed in, so "Add account" shows the WebView
    // instead of bouncing to the current account's panel + logout button.
    composable(
        route = "account?add={add}",
        arguments = listOf(
            navArgument("add") {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { backStackEntry ->
        currentRoute.value = "account"
        showBottomNav.value = false
        YouTubeLoginScreen(
            forceNewLogin = backStackEntry.arguments?.getBoolean("add") == true,
            onLoginComplete = {
                navController.popBackStack()
            },
            onNavigateBack = {
                // The "add account" mode clears the shared session cookie to
                // force the login form; backing out without signing in must put
                // the previous profile's session back.
                com.omersusin.pitube.data.local.AccountSwitcher(
                    navController.context.applicationContext
                ).restoreActiveSession()
                navController.popBackStack()
            }
        )
    }

    // Notifications Screen
    composable("notifications") {
        currentRoute.value = "notifications"
        showBottomNav.value = false
        NotificationScreen(
            onBackClick = { navController.popBackStack() },
            onNotificationClick = { videoId ->
                navController.navigate("player/$videoId")
            }
        )
    }

    composable(
        route = "shorts?startVideoId={startVideoId}",
        arguments = listOf(
            navArgument("startVideoId") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        currentRoute.value = "shorts"
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 1
        val startVideoId = backStackEntry.arguments?.getString("startVideoId")
        ShortsScreen(
            startVideoId = startVideoId,
            bottomNavOverlayPadding = bottomNavOverlayPadding(),
            onBack = {
                navController.popBackStack()
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            }
        )
    }

    composable("library") {
        currentRoute.value = "library"
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 4
        val downloadsSourceName = androidx.compose.ui.res.stringResource(
            com.omersusin.pitube.R.string.library_downloads_label
        )
        LibraryScreen(
            onNavigateToHistory = { 
                navController.navigate("history")
            },
            onNavigateToPlaylists = { 
                navController.navigate("playlists")
            },
            onNavigateToLikedVideos = { 
                navController.navigate("likes")
            },
            onNavigateToWatchLater = {
                navController.navigate("playlist/${PlaylistRepository.WATCH_LATER_ID}")
            },
            onNavigateToSavedShorts = {
                navController.navigate("savedShorts")
            },
            onNavigateToDownloads = {
                navController.navigate("downloads")
            },
            onNavigateToLocalMedia = {
                navController.navigate("localMedia")
            },
            onManageData = {
                navController.navigate("settings")
            },
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onPlaylistClick = { playlistId ->
                navController.navigate("playlist/$playlistId")
            },
            onDownloadedVideoClick = { videos, index ->
                val videoList = videos.map { it.video }
                playerViewModel.playPlaylist(videoList, index, downloadsSourceName)
                GlobalPlayerState.setCurrentVideo(videoList[index])
            },
            onSavedShortClick = { video ->
                navController.navigate("savedShortsPlayer/${video.id}")
            }
        )
    }

    composable("search") {
        currentRoute.value = "search"
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 5
        SearchScreen(
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onChannelClick = { channel ->
                navController.navigateToYoutubeChannel(channel.url.ifBlank { channel.id })
            },
            onPlaylistClick = { playlist ->
                navController.navigate("playlist/${playlist.id}")
            },
            onVoiceSearch = onOpenRecognitionModal,
        )
    }

    composable("categories") {
        currentRoute.value = "categories"
        showBottomNav.value = true
        selectedBottomNavIndex.intValue = 6
        com.omersusin.pitube.ui.screens.categories.CategoriesScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            }
        )
    }

    composable("settings") {
        currentRoute.value = "settings"
        showBottomNav.value = false
        SettingsScreen(
            currentTheme = currentTheme,

            onNavigateBack = { navController.popBackStack() },
            onNavigateToAppearance = { navController.navigate("settings/appearance") },
            onNavigateToPlayerAppearance = { navController.navigate("settings/player_appearance") },
            onNavigateToDonations = { navController.navigate("donations") },
            onNavigateToDownloads = { navController.navigate("settings/downloads") },
            onNavigateToTimeManagement = { navController.navigate("settings/time_management") },
            onNavigateToPlayerSettings = { navController.navigate("settings/player") },
            onNavigateToProxySettings = { navController.navigate("settings/proxy") },
            onNavigateToVideoQuality = { navController.navigate("settings/video_quality") },
            onNavigateToShortsQuality = { navController.navigate("settings/shorts_quality") },
            onNavigateToContentSettings = { navController.navigate("settings/content") },
            onNavigateToDateTimeSettings = { navController.navigate("settings/datetime") },
            onNavigateToBufferSettings = { navController.navigate("settings/buffer") },
            onNavigateToSearchHistory = { navController.navigate("settings/search_history") },
            onNavigateToAbout = { navController.navigate("settings/about") },
            onNavigateToNotifications = { navController.navigate("settings/notifications") },
            onNavigateToAppIconPicker = { navController.navigate("settings/app_icon") },
            onNavigateToDiagnostics = { navController.navigate("settings/diagnostics") },
            onNavigateToSyncDevices = { navController.navigate("settings/sync_devices") },
            onNavigateToSponsorBlockSettings = { navController.navigate("settings/sponsorblock") },
            onNavigateToTranslation = { navController.navigate("settings/translation") },
            onNavigateToGoogleLogin = { navController.navigate("account") },
            onAddYouTubeAccount = { navController.navigate("account?add=1") }
        )
    }

    composable("settings/sync_devices") {
        currentRoute.value = "settings/sync_devices"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.sync.SyncScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/player") {
        currentRoute.value = "settings/player"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.PlayerSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/translation") {
        currentRoute.value = "settings/translation"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.TranslationSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/proxy") {
        currentRoute.value = "settings/proxy"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.ProxySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/sponsorblock") {
        currentRoute.value = "settings/sponsorblock"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.SponsorBlockSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/buffer") {
        currentRoute.value = "settings/buffer"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.BufferSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/search_history") {
        currentRoute.value = "settings/search_history"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.SearchHistorySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/video_quality") {
        currentRoute.value = "settings/video_quality"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.VideoQualitySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/shorts_quality") {
        currentRoute.value = "settings/shorts_quality"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.ShortsVideoQualitySettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/content") {
        currentRoute.value = "settings/content"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.ContentSettingsScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    composable("settings/datetime") {
        currentRoute.value = "settings/datetime"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.DateTimeSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable("settings/time_management") {
        currentRoute.value = "settings/time_management"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.TimeManagementScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/about") {
        currentRoute.value = "settings/about"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.AboutScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDonations = { navController.navigate("donations") }
        )
    }

    composable("settings/appearance") {
        currentRoute.value = "settings/appearance"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.AppearanceScreen(
            currentTheme = currentTheme,
            themeVariant = themeVariant,
            customThemePalettes = customThemePalettes,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            systemDarkThemeVariant = systemDarkThemeVariant,
            onThemeChange = onThemeChange,
            onThemeVariantChange = onThemeVariantChange,
            onCustomThemePalettesChange = onCustomThemePalettesChange,
            onSystemLightThemeChange = onSystemLightThemeChange,
            onSystemDarkThemeChange = onSystemDarkThemeChange,
            onSystemDarkThemeVariantChange = onSystemDarkThemeVariantChange,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/player_appearance") {
        currentRoute.value = "settings/player_appearance"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.PlayerAppearanceScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/downloads") {
        currentRoute.value = "settings/downloads"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.DownloadSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/notifications") {
        currentRoute.value = "settings/notifications"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.NotificationSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/app_icon") {
        currentRoute.value = "settings/app_icon"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.AppIconPickerScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("settings/diagnostics") {
        currentRoute.value = "settings/diagnostics"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.DiagnosticsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable("donations") {
        currentRoute.value = "donations"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.settings.DonationsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = "channel?url={channelUrl}",
        arguments = listOf(navArgument("channelUrl") { type = NavType.StringType })
    ) { backStackEntry ->
        currentRoute.value = "channel"
        showBottomNav.value = false
        val channelUrl = backStackEntry.arguments?.getString("channelUrl")?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
        
        ChannelScreen(
            channelUrl = channelUrl,
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            },
            onShortClick = { videoId ->
                if (disableShortsPlayer) {
                    navController.navigate("player/$videoId")
                } else {
                    navController.navigate("shorts?startVideoId=$videoId")
                }
            },
            onPlaylistClick = { playlistId ->
                navController.navigate("playlist/$playlistId")
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // History Screen
    composable("history") {
        currentRoute.value = "history"
        showBottomNav.value = false
        HistoryScreen(
            onVideoClick = { video ->
                val localId = video.id.removePrefix("local_").toLongOrNull()
                if (video.id.startsWith("local_") && localId != null) {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, localId
                    ).toString()
                    val localVideo = com.omersusin.pitube.data.model.Video(
                        id = video.id,
                        title = video.title,
                        channelName = video.channelName,
                        channelId = "local",
                        thumbnailUrl = uri,
                        duration = video.duration,
                        viewCount = 0,
                        uploadDate = "",
                        description = ""
                    )
                    playerViewModel.playLocalVideo(localVideo, uri)
                    GlobalPlayerState.setCurrentVideo(localVideo)
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onShortClick = { videoId ->
                if (disableShortsPlayer) {
                    navController.navigate("player/$videoId")
                } else {
                    navController.navigate("shorts?startVideoId=$videoId")
                }
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // Likes Screen
    composable("likes") {
        currentRoute.value = "likes"
        showBottomNav.value = false
        LikesScreen(
            onVideoClick = { video ->
                navController.navigate("player/${video.id}")
            },
            onBackClick = { navController.popBackStack() },
            onPlayQueue = { videos, startIndex ->
                playerViewModel.playPlaylist(videos, startIndex)
            },
        )
    }

    // Playlists Screen
    composable("playlists") {
        currentRoute.value = "playlists"
        showBottomNav.value = false
        PlaylistsScreen(
            onBackClick = { navController.popBackStack() },
            onVideoPlaylistClick = { playlist ->
                navController.navigate("playlist/${playlist.id}")
            }
        )
    }

    // Playlist Detail Screen
    composable("playlist/{playlistId}") { _ ->
        currentRoute.value = "playlist"
        showBottomNav.value = false
        PlaylistDetailScreen(
            // playlistId is handled by ViewModel via SavedStateHandle
            // playlistRepository is injected by Hilt
            onNavigateBack = { navController.popBackStack() },
            onVideoClick = { video ->
                if (video.isShort && !disableShortsPlayer) {
                    navController.navigate("shorts?startVideoId=${video.id}")
                } else {
                    navController.navigate("player/${video.id}")
                }
            },
            onPlayPlaylist = { videos, index ->
                playerViewModel.playPlaylist(videos, index, navController.context.getString(R.string.playlist))
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            }
        )
    }

    // Saved Shorts Grid
    composable("savedShorts") {
        currentRoute.value = "savedShorts"
        showBottomNav.value = false
        com.omersusin.pitube.ui.screens.library.SavedShortsGridScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { videoId ->
                navController.navigate("savedShortsPlayer/$videoId")
            }
        )
    }

    // Saved Shorts Player
    composable(
        route = "savedShortsPlayer/{startVideoId}",
        arguments = listOf(navArgument("startVideoId") { type = NavType.StringType })
    ) { backStackEntry ->
        currentRoute.value = "savedShortsPlayer"
        showBottomNav.value = false
        val startVideoId = backStackEntry.arguments?.getString("startVideoId")
        ShortsScreen(
            startVideoId = startVideoId,
            isSavedMode = true,
            bottomNavOverlayPadding = 0.dp,
            onBack = {
                navController.popBackStack()
            },
            onChannelClick = { channelId ->
                navController.navigateToYoutubeChannel(channelId)
            }
        )
    }
    composable("downloads") {
        currentRoute.value = "downloads"
        showBottomNav.value = false
        
        com.omersusin.pitube.ui.screens.library.DownloadsScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { videos, index ->
                val videoList = videos.map { it.video }
                playerViewModel.playPlaylist(videoList, index, navController.context.getString(R.string.downloads_title))
                GlobalPlayerState.setCurrentVideo(videoList[index])
            },
            onHomeClick = {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            }
        )
    }
    composable("localMedia") {
        currentRoute.value = "localMedia"
        showBottomNav.value = false

        com.omersusin.pitube.ui.screens.library.LocalMediaScreen(
            onBackClick = { navController.popBackStack() },
            onVideoClick = { item ->
                val video = com.omersusin.pitube.data.model.Video(
                    id = com.omersusin.pitube.ui.screens.library.LocalMediaViewModel.localMediaId(item),
                    title = item.title,
                    channelName = item.subtitle.ifBlank { navController.context.getString(R.string.local_video) },
                    channelId = "local",
                    thumbnailUrl = item.contentUri,
                    duration = (item.durationMs / 1000).toInt(),
                    viewCount = 0,
                    uploadDate = "",
                    description = ""
                )
                playerViewModel.playLocalVideo(video, item.contentUri)
                GlobalPlayerState.setCurrentVideo(video)
            }
        )
    }
    composable(
        route = "player/{videoId}",
        arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "http://www.youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://www.youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "http://youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "http://youtu.be/{videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://youtu.be/{videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "http://m.youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://m.youtube.com/watch?v={videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://www.youtube.com/shorts/{videoId}"
                action = android.content.Intent.ACTION_VIEW
            },
            navDeepLink {
                uriPattern = "https://youtube.com/shorts/{videoId}"
                action = android.content.Intent.ACTION_VIEW
            }
        )
    ) { backStackEntry ->
        val videoId = backStackEntry.arguments?.getString("videoId")
        val effectiveVideoId = when {
            !videoId.isNullOrEmpty() && videoId != "sample" -> videoId
            else -> "jNQXAC9IVRw"
        }

        // Use passed state
        val playerUiState = playerUiStateResult.value
        LaunchedEffect(effectiveVideoId) {
            val isAlreadyPlayingThis = playerUiState.cachedVideo?.id == effectiveVideoId &&
                !playerUiState.isRestoredSession
            if (!isAlreadyPlayingThis) {
                val placeholder = Video(
                    id = effectiveVideoId,
                    title = "",
                    channelName = "",
                    channelId = "",
                    thumbnailUrl = "",
                    duration = 0,
                    viewCount = 0L,
                    uploadDate = "",
                    description = "",
                    channelThumbnailUrl = ""
                )
                playerViewModel.playVideo(placeholder)
                GlobalPlayerState.setCurrentVideo(placeholder)
            } else {
                playerViewModel.showVideoPlayer()
                playerVisibleState.value = true
                playerSheetState.expand()
            }
            withFrameNanos { }
            navController.popTransientRouteOrNavigateStart(defaultStartRoute)
        }
        
        Box(modifier = Modifier.fillMaxSize())
    }
}

private fun NavHostController.popTransientRouteOrNavigateStart(defaultStartRoute: String) {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(defaultStartRoute) {
            launchSingleTop = true
        }
    }
}

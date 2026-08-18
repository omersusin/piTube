package com.omersusin.pitube.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.utils.ChannelIdResolver
import com.omersusin.pitube.player.EnhancedPlayerManager
import com.omersusin.pitube.player.GlobalPlayerState
import com.omersusin.pitube.player.SleepTimerManager
import com.omersusin.pitube.ui.components.DonationPromptHost
import com.omersusin.pitube.ui.components.FloatingBottomNavBar
import com.omersusin.pitube.ui.components.FlowPlaylistQueueBottomSheet
import com.omersusin.pitube.ui.components.PlayerSheetValue
import com.omersusin.pitube.ui.components.rememberPlayerDraggableState
import com.omersusin.pitube.ui.screens.account.AccountSheet
import com.omersusin.pitube.ui.screens.home.HomeViewModel
import com.omersusin.pitube.ui.screens.player.VideoPlayerViewModel
import com.omersusin.pitube.ui.theme.CustomThemePalettes
import com.omersusin.pitube.ui.theme.ThemeMode
import com.omersusin.pitube.ui.theme.ThemeVariant
import com.omersusin.pitube.ui.theme.isEffectivelyDark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

@UnstableApi
@Composable
fun FlowApp(
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
    deeplinkVideoId: String? = null,
    isShort: Boolean = false,
    onDeeplinkConsumed: () -> Unit = {},
    pendingWidgetRoute: String? = null,
    onWidgetRouteConsumed: () -> Unit = {},
    openRecognitionModalRequest: Boolean = false,
    onRecognitionModalRequestConsumed: () -> Unit = {},
    recognitionSearchQueryRequest: String? = null,
    onRecognitionSearchQueryConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val navController = rememberNavController()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)
    val playerUiStateResult = playerViewModel.uiState.collectAsStateWithLifecycle()
    val playerUiState by playerUiStateResult
    val enhancedPlayerManager = remember { EnhancedPlayerManager.getInstance() }
    val hasVideoQueue by enhancedPlayerManager.hasQueue.collectAsStateWithLifecycle(
        initialValue = enhancedPlayerManager.playerState.value.queueTitle != null,
    )

    val preferences = remember { PlayerPreferences(context) }
    val isHomeNavigationEnabled by preferences.homeNavigationEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = com.omersusin.pitube.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val bottomNavHideOnScroll by preferences.bottomNavHideOnScroll.collectAsState(initial = true)
    val sleepTimerCloseAppOnExpiry by preferences.sleepTimerCloseAppOnExpiry.collectAsState(
        initial = SleepTimerManager.preferredCloseAppOnExpiry,
    )
    val navigationVisibility =
        NavigationVisibility(
            home = isHomeNavigationEnabled,
            shorts = isShortsNavigationEnabled,
        )
    val resolvedDefaultNavTabIndex =
        resolveDefaultNavTabIndex(
            preferredIndex = defaultNavTabIndex,
            order = navTabOrder,
            visibility = navigationVisibility,
        )
    val defaultStartRoute = navRouteForIndex(resolvedDefaultNavTabIndex)

    // Mini Player Customizations
    val miniPlayerScale by preferences.miniPlayerScale.collectAsState(initial = 0.45f)
    val miniPlayerShowSkipControls by preferences.miniPlayerShowSkipControls.collectAsState(initial = false)
    val miniPlayerShowNextPrevControls by preferences.miniPlayerShowNextPrevControls.collectAsState(initial = false)

    // Offline Monitoring
    val currentRoute = remember { mutableStateOf(defaultStartRoute) }

    // Onboarding check
    var needsOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        needsOnboarding = !preferences.onboardingComplete.first()
    }

    LaunchedEffect(sleepTimerCloseAppOnExpiry) {
        SleepTimerManager.updatePreferredCloseAppOnExpiry(sleepTimerCloseAppOnExpiry)
    }

    HandleDeepLinks(deeplinkVideoId, isShort, navController, onDeeplinkConsumed)
    OfflineMonitor(context, navController, snackbarHostState, currentRoute)

    val selectedBottomNavIndex = remember { mutableIntStateOf(resolvedDefaultNavTabIndex) }
    val showBottomNav = remember { mutableStateOf(true) }
    val navScrollThresholdPx = with(LocalDensity.current) { 32.dp.toPx() }

    // "You" tab: the account sheet is hosted at the app root so it can be
    // opened from any destination. The active profile's avatar feeds the tab
    // pill (mirroring YouTube), plus the expired badge on the avatar ring.
    val accountSwitcher = remember(context) { com.omersusin.pitube.data.local.AccountSwitcher(context) }
    val activeProfileId by accountSwitcher.activeProfileId.collectAsStateWithLifecycle()
    val profilesState by accountSwitcher.profiles.collectAsStateWithLifecycle()
    val activeProfile = remember(profilesState, activeProfileId) {
        profilesState.firstOrNull { it.id == activeProfileId }
    }
    var showAccountSheet by remember { mutableStateOf(false) }

    // Voice & Song recognition modal, opened from the center nav slot, the
    // recognition notification and the floating overlay button.
    var openRecognitionModal by remember { mutableStateOf(false) }

    // Same Activity-scoped ViewModel the modal uses: every open starts a fresh
    // IDLE session (a stale SUCCESS would otherwise auto-search the old track).
    val recognitionViewModel: com.omersusin.pitube.ui.recognition.RecognitionViewModel =
        hiltViewModel(activity)
    LaunchedEffect(openRecognitionModal) {
        if (openRecognitionModal) {
            recognitionViewModel.reset()
        }
    }

    val closeAccountSheet: () -> Unit = {
        showAccountSheet = false
    }

    LaunchedEffect(resolvedDefaultNavTabIndex) {
        selectedBottomNavIndex.intValue = resolvedDefaultNavTabIndex
        currentRoute.value = navRouteForIndex(resolvedDefaultNavTabIndex)
    }

    LaunchedEffect(isHomeNavigationEnabled, currentRoute.value, defaultStartRoute, needsOnboarding) {
        if (needsOnboarding == false && !isHomeNavigationEnabled && currentRoute.value == "home") {
            selectedBottomNavIndex.intValue = resolvedDefaultNavTabIndex
            currentRoute.value = defaultStartRoute
            navController.navigate(defaultStartRoute) {
                popUpTo("home") { inclusive = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    var isNavScrolledVisible by remember { mutableStateOf(true) }
    var accumulatedNavScroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentRoute.value) {
        isNavScrolledVisible = true
        accumulatedNavScroll = 0f
    }
    // Keep the bar pinned when the user turned hide-on-scroll off.
    LaunchedEffect(bottomNavHideOnScroll) {
        if (!bottomNavHideOnScroll) {
            isNavScrolledVisible = true
            accumulatedNavScroll = 0f
        }
    }
    val nestedScrollConnection =
        remember(navScrollThresholdPx, bottomNavHideOnScroll) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val route = currentRoute.value
                    if (!bottomNavHideOnScroll ||
                        source != NestedScrollSource.UserInput ||
                        route == "shorts" ||
                        route == "savedShortsPlayer"
                    ) {
                        return Offset.Zero
                    }

                    val delta = available.y
                    if (delta == 0f) return Offset.Zero
                    if (accumulatedNavScroll != 0f && (accumulatedNavScroll > 0f) != (delta > 0f)) {
                        accumulatedNavScroll = 0f
                    }
                    accumulatedNavScroll += delta

                    when {
                        accumulatedNavScroll <= -navScrollThresholdPx && isNavScrolledVisible -> {
                            isNavScrolledVisible = false
                            accumulatedNavScroll = 0f
                        }

                        accumulatedNavScroll >= navScrollThresholdPx && !isNavScrolledVisible -> {
                            isNavScrolledVisible = true
                            accumulatedNavScroll = 0f
                        }
                    }
                    return Offset.Zero
                }
            }
        }

    val isInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    val currentVideo by GlobalPlayerState.currentVideo.collectAsState()
    val isShortsPlayerRoute =
        currentRoute.value == "shorts" || currentRoute.value == "savedShortsPlayer"

    LaunchedEffect(isShortsPlayerRoute) {
        if (isShortsPlayerRoute) {
            EnhancedPlayerManager.getInstance().pause()
            GlobalPlayerState.hideMiniPlayer()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()

        val navBarBottomInset = WindowInsets.navigationBars.getBottom(density)

        val bottomNavContentHeightDp = 48.dp

        val playerSheetState = rememberPlayerDraggableState()
        val playerVisibleState = remember { mutableStateOf(false) }
        var playerVisible by playerVisibleState
        var keepMiniOnQueueAutoAdvance by remember { mutableStateOf(false) }

        val miniPlayerHeightDp = 80.dp

        val activeVideo =
            playerUiState.cachedVideo ?: playerUiState.streamInfo?.let { streamInfo ->
                Video(
                    id = streamInfo.id,
                    title = streamInfo.name ?: "",
                    channelName = streamInfo.uploaderName ?: "",
                    channelId = ChannelIdResolver.resolve(null, streamInfo.uploaderUrl),
                    thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: "",
                    duration = streamInfo.duration.toInt(),
                    viewCount = streamInfo.viewCount,
                    uploadDate = "",
                )
            }

        LaunchedEffect(playerSheetState.currentValue, playerSheetState.isDragging) {
            if (!playerSheetState.isDragging) {
                showBottomNav.value = playerSheetState.currentValue != PlayerSheetValue.Expanded
                when (playerSheetState.currentValue) {
                    PlayerSheetValue.Expanded -> {
                        GlobalPlayerState.expandMiniPlayer()
                    }

                    PlayerSheetValue.Collapsed -> {
                        if (playerUiState.isBackgroundPlaybackMode) {
                            GlobalPlayerState.hideMiniPlayer()
                        } else {
                            GlobalPlayerState.collapseMiniPlayer()
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            enhancedPlayerManager.queueAutoAdvanceEvent.collect {
                keepMiniOnQueueAutoAdvance = playerSheetState.currentValue == PlayerSheetValue.Collapsed
            }
        }

        LaunchedEffect(playerViewModel) {
            playerViewModel.expandPlayerRequest.collect {
                playerVisible = true
                playerSheetState.expand()
            }
        }

        LaunchedEffect(playerUiState.cachedVideo, playerUiState.isBackgroundPlaybackMode) {
            if (playerUiState.cachedVideo != null) {
                if (playerUiState.isBackgroundPlaybackMode) {
                    playerSheetState.snapTo(PlayerSheetValue.Collapsed)
                    GlobalPlayerState.hideMiniPlayer()
                    playerVisible = false
                    showBottomNav.value = true
                    return@LaunchedEffect
                }
                GlobalPlayerState.setExplicitBackgroundPlaybackActive(false)
                playerVisible = true
                val isQueueAutoAdvanceInMiniPlayer =
                    keepMiniOnQueueAutoAdvance &&
                        hasVideoQueue &&
                        playerSheetState.currentValue == PlayerSheetValue.Collapsed

                if (
                    playerUiState.isRestoredSession ||
                    playerUiState.resumedInMiniPlayer ||
                    isQueueAutoAdvanceInMiniPlayer
                ) {
                    playerSheetState.collapse()
                } else {
                    playerSheetState.expand()
                }

                keepMiniOnQueueAutoAdvance = false
            }
        }

        LaunchedEffect(pendingWidgetRoute) {
            pendingWidgetRoute?.let { route ->
                navController.currentBackStackEntryFlow.first()
                navController.navigate(route)
                onWidgetRouteConsumed()
            }
        }

        LaunchedEffect(openRecognitionModalRequest) {
            if (openRecognitionModalRequest) {
                openRecognitionModal = true
                onRecognitionModalRequestConsumed()
            }
        }

        LaunchedEffect(recognitionSearchQueryRequest) {
            val query = recognitionSearchQueryRequest ?: return@LaunchedEffect
            onRecognitionSearchQueryConsumed()
            if (currentRoute.value != "search") {
                navController.navigate("search") {
                    launchSingleTop = true
                }
            }
            com.omersusin.pitube.ui.recognition.RecognitionSearchBridge.submit(query)
            if (openRecognitionModal) {
                openRecognitionModal = false
            }
        }

        ApplyStatusBarStyle(
            themeMode = currentTheme,
            themeVariant = themeVariant,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            isFullscreen = playerUiState.isFullscreen,
            isShortsPlayer = currentRoute.value == "shorts" || currentRoute.value == "savedShortsPlayer",
        )

        LaunchedEffect(isInPipMode) {
            if (isInPipMode && !currentRoute.value.startsWith("player") && currentVideo != null) {
                navController.navigate("player/${currentVideo!!.id}")
            }
        }

        val dismissRequested by GlobalPlayerState.dismissRequested.collectAsState()
        LaunchedEffect(dismissRequested) {
            if (dismissRequested) {
                GlobalPlayerState.resetDismiss()
                GlobalPlayerState.hideMiniPlayer()
                playerVisible = false
                if (playerUiState.isRestoredSession) {
                    playerViewModel.dismissContinueWatching()
                }
                playerViewModel.clearVideo()
                if (isInPipMode) {
                    activity.moveTaskToBack(false)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val bottomNavContentPadding by animateDpAsState(
                targetValue =
                    if (
                        !bottomNavHideOnScroll &&
                        !isInPipMode &&
                        showBottomNav.value &&
                        isNavScrolledVisible &&
                        !isShortsPlayerRoute
                    ) {
                        bottomNavContentHeightDp
                    } else {
                        0.dp
                    },
                animationSpec = tween(durationMillis = 220),
                label = "bottomNavContentPadding",
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor =
                    if (isInPipMode) {
                        androidx.compose.ui.graphics.Color.Black
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.background
                    },
                contentWindowInsets = WindowInsets.systemBars,
                bottomBar = {},
            ) { paddingValues ->
                val layoutDirection = LocalLayoutDirection.current
                val contentPadding =
                    if (
                        isShortsPlayerRoute
                    ) {
                        PaddingValues(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            top = 0.dp,
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = paddingValues.calculateBottomPadding(),
                        )
                    } else {
                        paddingValues
                    }
                Box(
                    modifier =
                        Modifier
                            .padding(if (isInPipMode) PaddingValues(0.dp) else contentPadding)
                            .padding(bottom = bottomNavContentPadding)
                            .nestedScroll(nestedScrollConnection),
                ) {
                    if (needsOnboarding != null) {
                        val homeViewModel: HomeViewModel = hiltViewModel(activity)
                        LaunchedEffect(homeViewModel) {
                            homeViewModel.initialize(context.applicationContext)
                        }

                        NavHost(
                            navController = navController,
                            startDestination = if (needsOnboarding == true) "onboarding" else defaultStartRoute,
                            enterTransition = {
                                fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(
                                        initialOffsetX = { (it * 0.06f).toInt() },
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                    )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing))
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
                                    slideOutHorizontally(
                                        targetOffsetX = { (it * 0.06f).toInt() },
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                    )
                            },
                        ) {
                            flowAppGraph(
                                navController = navController,
                                currentRoute = currentRoute,
                                showBottomNav = showBottomNav,
                                selectedBottomNavIndex = selectedBottomNavIndex,
                                playerSheetState = playerSheetState,
                                homeViewModel = homeViewModel,
                                playerViewModel = playerViewModel,
                                playerUiStateResult = playerUiStateResult,
                                playerVisibleState = playerVisibleState,
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
                                disableShortsPlayer = disableShortsPlayer,
                                defaultStartRoute = defaultStartRoute,
                                onOpenRecognitionModal = {
                                    openRecognitionModal = true
                                },
                                bottomNavOverlayPadding = {
                                    if (showBottomNav.value && isNavScrolledVisible) {
                                        bottomNavContentHeightDp
                                    } else {
                                        0.dp
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Floating bottom nav bar overlay ──────────────────────────────────
            AnimatedVisibility(
                visible = !isInPipMode && showBottomNav.value && isNavScrolledVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter =
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
                    ) + fadeIn(animationSpec = tween(160, delayMillis = 40)),
                exit =
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f),
                    ) + fadeOut(animationSpec = tween(120)),
            ) {
                FloatingBottomNavBar(
                    selectedIndex = selectedBottomNavIndex.intValue,
                    isHomeEnabled = isHomeNavigationEnabled,
                    isShortsEnabled = isShortsNavigationEnabled,
                    navOrder = navTabOrder,
                    accountAvatarUrl = activeProfile?.avatarUrl?.takeIf { !activeProfile.isLocal },
                    accountExpired = activeProfile?.expired == true,
                    isAccountSelected = showAccountSheet,
                    onAccountClick = {
                        showAccountSheet = true
                    },
                    onItemSelected = { index ->
                        // The center slot is the Search tab: a normal route.
                        // The recognition modal is opened from the mic icon
                        // inside the Search screen's bar (see onOpenRecognitionModal).
                        val route = navRouteForIndex(index)

                        val activeRoute = navController.currentBackStackEntry?.destination?.route
                        if (activeRoute == route) {
                            TabScrollEventBus.emitScrollToTop(route)
                        } else {
                            selectedBottomNavIndex.intValue = index
                            currentRoute.value = route
                            navController.navigate(route) {
                                popUpTo(defaultStartRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }

        val animatedBottomPaddingRaw by animateDpAsState(
            targetValue =
                if (!isInPipMode && showBottomNav.value && isNavScrolledVisible) {
                    bottomNavContentHeightDp + with(density) { navBarBottomInset.toDp() }
                } else {
                    with(density) { navBarBottomInset.toDp() }
                },
            animationSpec = tween(220),
            label = "globalBottomPadding",
        )
        val animatedBottomPadding = animatedBottomPaddingRaw.coerceAtLeast(0.dp)
        val snackbarBottomPadding = (animatedBottomPadding + 12.dp).coerceAtLeast(12.dp)

        // ===== GLOBAL PLAYER OVERLAY =====
        GlobalPlayerOverlay(
            video = activeVideo,
            isVisible = playerVisible && !isShortsPlayerRoute,
            playerSheetState = playerSheetState,
            bottomPadding = animatedBottomPadding,
            miniPlayerScale = miniPlayerScale,
            miniPlayerShowSkipControls = miniPlayerShowSkipControls,
            miniPlayerShowNextPrevControls = miniPlayerShowNextPrevControls,
            onClose = {
                playerVisible = false
                if (playerUiState.isRestoredSession) {
                    playerViewModel.dismissContinueWatching()
                }
                playerViewModel.clearVideo()
            },
            onMinimize = {
                playerSheetState.snapTo(PlayerSheetValue.Collapsed)
                GlobalPlayerState.hideMiniPlayer()
                playerVisible = false
                showBottomNav.value = true
            },
            onNavigateToChannel = { channelArg ->
                playerSheetState.collapse()
                navController.navigateToYoutubeChannel(channelArg)
            },
            onNavigateToShorts = { videoId ->
                playerSheetState.collapse()
                navController.navigate("shorts?startVideoId=$videoId")
            },
        )

        // ===== GLOBAL MUSIC PLAYER OVERLAY ===== (removed with music feature)

        // ===== GLOBAL QUEUE SHEET HOST =====
        // The queue sheet must be reachable even before any player/mini-player
        // is on screen (video still being opened, or nothing loaded yet): a
        // "Play Next"/"Add to queue" from any feed card always gets visible
        // feedback instead of silently mutating an unseen queue. Rendered last
        // in the root Box so it stacks above the NavHost content.
        GlobalQueueSheetHost()

        // ===== GLOBAL ACCOUNT SHEET =====
        // Opened from the "You" tab in the bottom nav. Hosted at the root so
        // it works from every destination and can push library/settings routes
        // on top of whatever the user was doing.
        if (showAccountSheet) {
            AccountSheet(
                onDismiss = closeAccountSheet,
                onSignIn = {
                    navController.navigate("account")
                },
                onAddYouTubeAccount = {
                    navController.navigate("account?add=1")
                },
                onOpenHistory = {
                    navController.navigate("history")
                },
                onOpenPlaylists = {
                    navController.navigate("playlists")
                },
                onOpenLikedVideos = {
                    navController.navigate("likes")
                },
                onOpenWatchLater = {
                    navController.navigate("playlist/${com.omersusin.pitube.data.local.PlaylistRepository.WATCH_LATER_ID}")
                },
                onOpenSavedShorts = {
                    navController.navigate("savedShorts")
                },
                onOpenDownloads = {
                    navController.navigate("downloads")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onOpenAbout = {
                    navController.navigate("settings/about")
                }
            )
        }

        // ===== GLOBAL RECOGNITION MODAL =====
        // Opened from the center nav slot (voice & song recognition), the
        // recognition notification and the floating overlay button. Hosted at
        // the root so it can start listening immediately, above the player.
        if (openRecognitionModal) {
            com.omersusin.pitube.ui.recognition.RecognitionScreen(
                onDismiss = {
                    recognitionViewModel.reset()
                    openRecognitionModal = false
                },
                onOpenSettings = {
                    recognitionViewModel.reset()
                    openRecognitionModal = false
                    navController.navigate("settings")
                },
                onSearch = { query ->
                    recognitionViewModel.reset()
                    openRecognitionModal = false
                    if (currentRoute.value != "search") {
                        navController.navigate("search") {
                            launchSingleTop = true
                        }
                    }
                    com.omersusin.pitube.ui.recognition.RecognitionSearchBridge.submit(query)
                },
            )
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = snackbarBottomPadding,
                    ),
        )

        DonationPromptHost(
            enabled = needsOnboarding == false && !isInPipMode && !playerVisible,
            onNavigateToDonations = { navController.navigate("donations") },
        )
    }
}

@Composable
private fun ApplyStatusBarStyle(
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    isFullscreen: Boolean,
    isShortsPlayer: Boolean = false,
) {
    val activity = LocalContext.current as? Activity ?: return
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme =
        themeMode.isEffectivelyDark(
            isSystemDark = isSystemDark,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            themeVariant = themeVariant,
        )

    SideEffect {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val shouldDrawBehindStatusBar = isFullscreen || isShortsPlayer

        // API 35 deprecates statusBarColor; edge-to-edge insets are handled elsewhere.
        @Suppress("DEPRECATION")
        window.statusBarColor =
            if (shouldDrawBehindStatusBar) {
                android.graphics.Color.TRANSPARENT
            } else {
                colorScheme.background.toArgb()
            }

        insetsController.isAppearanceLightStatusBars = !isDarkTheme && !shouldDrawBehindStatusBar
    }
}

/**
 * Global host for the playback-queue sheet, composed in FlowApp's root Box so
 * "Play Next"/"Add to queue" always has visible feedback even when no
 * player/mini-player is composed yet. Opens the queue sheet whenever the
 * manager bumps its display trigger, then acknowledges the request so a stale
 * trigger can't re-open the sheet later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalQueueSheetHost() {
    val manager = EnhancedPlayerManager.getInstance()
    val queueDisplayRequested by manager.queueDisplayRequested.collectAsStateWithLifecycle()
    val queueVideos by manager.queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentQueueIndex by manager.currentQueueIndexState.collectAsStateWithLifecycle()
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }

    LaunchedEffect(queueDisplayRequested) {
        if (queueDisplayRequested > 0L) {
            if (!showQueueSheet) showQueueSheet = true
            manager.consumeQueueDisplayRequest()
        }
    }

    // If the queue is emptied (all items removed) the sheet has nothing to
    // show — reset so the next add opens it fresh instead of a stale popup.
    LaunchedEffect(queueVideos) {
        if (queueVideos.isEmpty()) showQueueSheet = false
    }

    if (showQueueSheet && queueVideos.isNotEmpty()) {
        FlowPlaylistQueueBottomSheet(
            queueVideos = queueVideos,
            currentQueueIndex = currentQueueIndex,
            playlistTitle = playerState.queueTitle,
            isLooping = playerState.isQueueLooping,
            isShuffled = playerState.isQueueShuffled,
            onLoopToggle = manager::toggleQueueLoop,
            onShuffleToggle = manager::toggleQueueShuffle,
            onPlayVideoAtIndex = { index ->
                manager.playVideoAtIndex(index, loadStreamsInPlayer = false)
            },
            onRemoveVideoAtIndex = manager::removeVideoAtIndex,
            onMoveVideoAtIndex = manager::moveVideoAtIndex,
            onDismiss = { showQueueSheet = false },
        )
    }
}

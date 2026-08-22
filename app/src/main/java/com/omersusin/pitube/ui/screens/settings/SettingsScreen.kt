package com.omersusin.pitube.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonParser
import com.omersusin.pitube.BuildConfig
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.RecognitionPreferences
import com.omersusin.pitube.data.local.RecognitionProvider
import com.omersusin.pitube.data.local.RecognitionFailureType
import com.omersusin.pitube.data.local.FallbackPolicy
import com.omersusin.pitube.data.local.SttApiKeyStore
import com.omersusin.pitube.data.local.SttProvider
import com.omersusin.pitube.network.AppProxyManager
import com.omersusin.pitube.recognition.RecognitionNotifier
import com.omersusin.pitube.recognition.RecognitionOverlayService
import com.omersusin.pitube.ui.theme.ThemeMode
import com.omersusin.pitube.ui.theme.extendedColors
import com.omersusin.pitube.utils.AppLanguageManager
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayerAppearance: () -> Unit,
    onNavigateToDonations: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToTimeManagement: () -> Unit,
    onNavigateToPlayerSettings: () -> Unit,
    onNavigateToProxySettings: () -> Unit,
    onNavigateToVideoQuality: () -> Unit,
    onNavigateToShortsQuality: () -> Unit,
    onNavigateToContentSettings: () -> Unit,
    onNavigateToSubscriptionTransfer: () -> Unit,
    onNavigateToDateTimeSettings: () -> Unit,
    onNavigateToBufferSettings: () -> Unit,
    onNavigateToSearchHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAppIconPicker: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSyncDevices: () -> Unit,
    onNavigateToSponsorBlockSettings: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToGoogleLogin: () -> Unit,
    onAddYouTubeAccount: () -> Unit,
    onNavigateToLyrics: () -> Unit = {},
    onNavigateToActionRow: () -> Unit = {},
    onNavigateToRecognitionAppearance: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }

    // Dialog state
    var showRegionDialog by remember { mutableStateOf(false) }
    var showAppLanguageDialog by remember { mutableStateOf(false) }

    // ── Song recognition section ──────────────────────────────────────────────
    val recognitionPreferences = remember { RecognitionPreferences(context) }
    val recognitionProvider by recognitionPreferences.provider
        .collectAsStateWithLifecycle(initialValue = RecognitionProvider.SHAZAM)
    val recognitionNotificationsEnabled by recognitionPreferences.notificationsEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val floatingButtonPreferred by recognitionPreferences.floatingButtonEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    var overlayPermissionAvailable by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var showRecognitionProviderDialog by remember { mutableStateOf(false) }
    var showSttProviderDialog by remember { mutableStateOf(false) }
    var showSttApiKeyDialog by remember { mutableStateOf(false) }
    var showRecognitionFallbackDialog by remember { mutableStateOf(false) }
    var pendingNotificationPermission by remember { mutableStateOf(false) }

    // The overlay permission can be granted/revoked in the system screen, so
    // re-check whenever Settings regains focus and keep the switch in sync
    // with reality (Audile-style: the toggle reflects actual capability).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    overlayPermissionAvailable = Settings.canDrawOverlays(context)
                    if (Settings.canDrawOverlays(context) &&
                        floatingButtonPreferred &&
                        !RecognitionOverlayService.isRunning.get()
                    ) {
                        RecognitionOverlayService.start(context)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (pendingNotificationPermission) {
                pendingNotificationPermission = false
                if (granted) {
                    coroutineScope.launch {
                        recognitionPreferences.setNotificationsEnabled(true)
                        RecognitionNotifier.getInstance(context).showEntryNotification()
                    }
                }
            }
        }

    val overlayPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            overlayPermissionAvailable = Settings.canDrawOverlays(context)
            if (overlayPermissionAvailable) {
                coroutineScope.launch {
                    recognitionPreferences.setFloatingButtonEnabled(true)
                    RecognitionOverlayService.start(context)
                }
            } else {
                coroutineScope.launch { recognitionPreferences.setFloatingButtonEnabled(false) }
            }
        }

    fun onRecognitionNotificationsToggle(enabled: Boolean) {
        coroutineScope.launch {
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingNotificationPermission = true
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return@launch
            }
            recognitionPreferences.setNotificationsEnabled(enabled)
            val notifier = RecognitionNotifier.getInstance(context)
            if (enabled) {
                notifier.showEntryNotification()
            } else {
                notifier.cancelEntryNotification()
            }
        }
    }

    fun onRecognitionFloatingToggle(enabled: Boolean) {
        if (enabled) {
            if (Settings.canDrawOverlays(context)) {
                coroutineScope.launch {
                    recognitionPreferences.setFloatingButtonEnabled(true)
                    RecognitionOverlayService.start(context)
                }
            } else {
                coroutineScope.launch { recognitionPreferences.setFloatingButtonEnabled(false) }
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        } else {
            coroutineScope.launch {
                recognitionPreferences.setFloatingButtonEnabled(false)
                RecognitionOverlayService.stop(context)
            }
        }
    }

    val recognitionProviderLabel: String =
        when (recognitionProvider) {
            RecognitionProvider.SHAZAM -> stringResource(R.string.recognition_provider_shazam)
            RecognitionProvider.AUDD -> stringResource(R.string.recognition_provider_audd)
            RecognitionProvider.ACRCLOUD -> stringResource(R.string.recognition_provider_acrcloud)
        }
    val sttProvider by recognitionPreferences.sttProvider
        .collectAsStateWithLifecycle(initialValue = SttProvider.CIHAZ)
    val sttApiKeys = remember { SttApiKeyStore(context) }
    val sttProviderLabel: String =
        when (sttProvider) {
            SttProvider.CIHAZ -> stringResource(R.string.stt_provider_cihaz)
            SttProvider.GROQ -> stringResource(R.string.stt_provider_groq)
            SttProvider.IBM_WATSON -> stringResource(R.string.stt_provider_ibm_watson)
            SttProvider.AZURE -> stringResource(R.string.stt_provider_azure)
            SttProvider.GOOGLE_CLOUD -> stringResource(R.string.stt_provider_google_cloud)
        }
    val sttApiKeyLabel: String =
        when (sttProvider) {
            SttProvider.CIHAZ -> stringResource(R.string.stt_api_key_not_required)
            SttProvider.AZURE -> {
                val region = sttApiKeys.getAzureRegion()
                val key = sttApiKeys.getApiKey(SttProvider.AZURE)
                listOfNotNull(region?.let { stringResource(R.string.stt_azure_region_summary, it) }, key?.let { sttApiKeys.maskedKey(SttProvider.AZURE) }).joinToString(" · ")
            }
            SttProvider.IBM_WATSON -> {
                val url = sttApiKeys.getIbmInstanceUrl()
                val key = sttApiKeys.getApiKey(SttProvider.IBM_WATSON)
                listOfNotNull(url?.let { stringResource(R.string.stt_ibm_url_summary, it) }, key?.let { sttApiKeys.maskedKey(SttProvider.IBM_WATSON) }).joinToString(" · ")
            }
            else -> sttApiKeys.maskedKey(sttProvider) ?: stringResource(R.string.stt_api_key_missing)
        }
    val recognitionFallbackBadInternet by recognitionPreferences.fallbackBadInternet
        .collectAsStateWithLifecycle(initialValue = FallbackPolicy.IGNORE)
    val recognitionFallbackNoMatch by recognitionPreferences.fallbackNoMatch
        .collectAsStateWithLifecycle(initialValue = FallbackPolicy.IGNORE)
    val recognitionFallbackOther by recognitionPreferences.fallbackOther
        .collectAsStateWithLifecycle(initialValue = FallbackPolicy.IGNORE)
    val recognitionFallbackState =
        remember(recognitionFallbackBadInternet, recognitionFallbackNoMatch, recognitionFallbackOther) {
            com.omersusin.pitube.data.local.RecognitionFallbackState(
                badInternet = recognitionFallbackBadInternet,
                noMatch = recognitionFallbackNoMatch,
                other = recognitionFallbackOther,
            )
        }
    val floatingButtonShown = floatingButtonPreferred && overlayPermissionAvailable

    // Google account state
    val youtubeAccountName by playerPreferences.youtubeAccountName.collectAsStateWithLifecycle(initialValue = null)
    val youtubeAccountEmail by playerPreferences.youtubeAccountEmail.collectAsStateWithLifecycle(initialValue = null)
    val youtubeAccountThumbnail by playerPreferences.youtubeAccountThumbnail.collectAsStateWithLifecycle(initialValue = null)
    val isGoogleSignedIn by playerPreferences.youtubeCookie
        .map { !it.isNullOrEmpty() }
        .collectAsStateWithLifecycle(initialValue = false)
    // YouTube answered an authenticated request as anonymous; the stored
    // session is dead until the user signs in again.
    val sessionExpired by com.omersusin.pitube.data.local.SessionManager.sessionExpired
        .collectAsStateWithLifecycle(initialValue = false)
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var isSyncingLibrary by remember { mutableStateOf(false) }
    var librarySyncResultText by remember { mutableStateOf<String?>(null) }

    // Shared by the "Sync now" button and the auto-sync below. The sync itself
    // runs on an app-scoped launcher: navigating away mid-sync must never cancel
    // the crawl (composition scopes die with the screen and aborted the crawl).
    val performLibrarySync: () -> Unit = {
        if (!isSyncingLibrary) {
            isSyncingLibrary = true
            librarySyncResultText = null
            com.omersusin.pitube.sync.LibrarySyncLauncher.syncInBackground(context) { result ->
                isSyncingLibrary = false
                coroutineScope.launch {
                    playerPreferences.setYoutubeLibrarySyncCounts(
                        result.likedVideos,
                        result.playlists,
                        result.subscribedChannels
                    )
                }
                librarySyncResultText = when {
            result.notLoggedIn -> context.getString(R.string.settings_google_sign_in_subtitle)
            result.sessionExpired && result.subscribedChannels == 0 -> context.getString(
                R.string.settings_google_sync_zero_invalid
            )
            result.sessionExpired -> context.getString(R.string.account_switcher_expired)
            !result.error.isNullOrBlank() -> context.getString(
                R.string.settings_google_sync_error,
                result.error
            )
            result.subscribedChannels == 0 -> context.getString(
                R.string.settings_google_sync_zero_valid
            )
                else -> {
                    val base = context.getString(
                        R.string.settings_google_sync_result,
                        result.likedVideos,
                        result.playlists,
                        result.subscribedChannels
                    )
                    if (result.partial) {
                        "$base\n${context.getString(R.string.settings_google_sync_partial)}"
                    } else {
                        base
                    }
                }
                }
            }
        }
    }

    // Auto-sync: once per day (or right after login) the account library is
    // refreshed silently so liked videos / playlists / subscriptions stay in
    // sync with official YouTube without opening the screen and tapping.
    val youtubeLibrarySyncedAt by playerPreferences.youtubeLibrarySyncedAt
        .collectAsStateWithLifecycle(initialValue = 0L)

    // Persisted last-sync counts so the result survives navigating away and
    // back; falls back to the text already shown when the last pass errored.
    val syncCounts by playerPreferences.youtubeLibrarySyncCounts
        .collectAsStateWithLifecycle(initialValue = Triple(0, 0, 0))
    val (lastLiked, lastPlaylists, lastChannels) = syncCounts
    val showPersistedSyncResult =
        librarySyncResultText == null && isGoogleSignedIn && youtubeLibrarySyncedAt > 0L

    LaunchedEffect(isGoogleSignedIn, youtubeLibrarySyncedAt, isSyncingLibrary) {
        if (isGoogleSignedIn && !isSyncingLibrary && librarySyncResultText == null &&
            System.currentTimeMillis() - youtubeLibrarySyncedAt > AUTO_SYNC_INTERVAL_MS
        ) {
            performLibrarySync()
        }
    }

    // Profile picture: always re-fetch the account info when Settings opens so the
    // avatar and name stay fresh (and show up even if login landed without the
    // profile fetch); retries a few times on transient failures. The avatar
    // regex in YouTube.accountInfo now also handles JSON-escaped URLs.
    LaunchedEffect(isGoogleSignedIn) {
        if (isGoogleSignedIn) {
            repeat(3) { attempt ->
                val info = runCatching {
                    com.omersusin.pitube.innertube.YouTube.accountInfo().getOrNull()
                }.getOrNull() ?: return@repeat
                if (info.name.isNotBlank() ||
                    !info.email.isNullOrBlank() ||
                    !info.thumbnailUrl.isNullOrBlank()
                ) {
                    playerPreferences.updateYoutubeAccountInfo(info.name, info.email, info.thumbnailUrl)
                    val sessionManager = com.omersusin.pitube.data.local.SessionManager(context)
                    sessionManager.saveUserName(info.name)
                    info.email?.takeIf { it.isNotBlank() }?.let { sessionManager.saveUserEmail(it) }
                    info.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { sessionManager.saveUserAvatar(it) }
                    return@LaunchedEffect
                }
                if (attempt < 2) kotlinx.coroutines.delay(1_500L * (attempt + 1))
            }
        }
    }

    // Update checker state (github flavor only)
    var isCheckingUpdate by remember { mutableStateOf(false) }
    // null = no dialog; non-null = tag string of the available update
    var updateAvailableTag by remember { mutableStateOf<String?>(null) }

    // Player preferences states
    val currentRegion by playerPreferences.trendingRegion.collectAsState(initial = "US")
    val currentAppLanguage by playerPreferences.appLanguage.collectAsState(initial = AppLanguageManager.SYSTEM_DEFAULT)

    // Optimize Region Dialog: compute list only once
    val regionList = remember { REGION_NAMES.toList() }
    val appLanguageOptions = remember { AppLanguageManager.getSupportedLanguages() }
    val currentAppLanguageLabel =
        remember(currentAppLanguage, appLanguageOptions) {
            val normalizedLanguage = AppLanguageManager.normalizeLanguageTag(currentAppLanguage)
            if (normalizedLanguage == AppLanguageManager.SYSTEM_DEFAULT) {
                context.getString(R.string.settings_language_system_default)
            } else {
                appLanguageOptions.firstOrNull { it.tag == normalizedLanguage }?.localizedName
                    ?: AppLanguageManager.getLanguageLabel(normalizedLanguage)
            }
        }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) runCatching { searchFocusRequester.requestFocus() }
    }
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val onCheckForUpdatesClick: () -> Unit = {
        if (BuildConfig.UPDATER_ENABLED && !isCheckingUpdate) {
            isCheckingUpdate = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val client = AppProxyManager.applyTo(OkHttpClient.Builder()).build()
                    val request =
                        Request
                            .Builder()
                            .url("https://api.github.com/repos/omersusin/piTube/releases/latest")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                    val response = client.newCall(request).execute()
                    withContext(Dispatchers.Main) {
                        isCheckingUpdate = false
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val json = JsonParser.parseString(body).asJsonObject
                            val latestTag = json.get("tag_name").asString
                            val cleanLatest = latestTag.removePrefix("v")
                            val cleanCurrent = BuildConfig.VERSION_NAME.removePrefix("v")
                            val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
                            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
                            var isNewer = false
                            val size = maxOf(latestParts.size, currentParts.size)
                            for (i in 0 until size) {
                                val l = latestParts.getOrNull(i) ?: 0
                                val c = currentParts.getOrNull(i) ?: 0
                                if (l > c) {
                                    isNewer = true
                                    break
                                }
                                if (l < c) break
                                if (isNewer) {
                                    updateAvailableTag = latestTag
                                } else {
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.flow_is_up_to_date),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        } else {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.update_check_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdate = false
                        android.widget.Toast
                            .makeText(
                                context,
                                context.getString(R.string.update_check_failed),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }
    }

    // Section label strings for the search index
    val secAppearance = stringResource(R.string.settings_header_appearance)
    val secContentPlayback = stringResource(R.string.settings_header_content_playback)
    val secNotifications = stringResource(R.string.settings_header_notifications)
    val secRecognition = stringResource(R.string.settings_header_recognition)
    val secDataManagement = stringResource(R.string.settings_header_data_management)
    val secAbout = stringResource(R.string.settings_header_about)

    val allSettingsEntries =
        listOf(
            SettingSearchEntry(
                Icons.Outlined.Palette,
                stringResource(R.string.settings_item_theme),
                "",
                secAppearance,
                onNavigateToAppearance,
            ),
            SettingSearchEntry(
                Icons.Outlined.Language,
                stringResource(R.string.settings_item_app_language),
                currentAppLanguageLabel,
                secAppearance,
            ) { showAppLanguageDialog = true },
            SettingSearchEntry(
                Icons.Outlined.AppShortcut,
                stringResource(R.string.settings_item_app_icon),
                stringResource(R.string.settings_item_app_icon_subtitle),
                secAppearance,
                onNavigateToAppIconPicker,
            ),
            SettingSearchEntry(
                Icons.Outlined.Tune,
                stringResource(R.string.settings_item_player_appearance),
                stringResource(R.string.settings_item_player_appearance_subtitle),
                secAppearance,
                onNavigateToPlayerAppearance,
            ),
            SettingSearchEntry(
                Icons.Outlined.GridView,
                stringResource(R.string.settings_item_content_display),
                stringResource(R.string.settings_item_content_display_subtitle),
                secAppearance,
                onNavigateToContentSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.SwapHoriz,
                stringResource(R.string.settings_item_subscription_transfer),
                stringResource(R.string.settings_item_subscription_transfer_subtitle),
                secAppearance,
                onNavigateToSubscriptionTransfer,
            ),
            SettingSearchEntry(
                Icons.Outlined.Schedule,
                stringResource(R.string.settings_item_datetime),
                stringResource(R.string.settings_item_datetime_subtitle),
                secAppearance,
                onNavigateToDateTimeSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.PlayCircle,
                stringResource(R.string.settings_item_player),
                stringResource(R.string.settings_item_player_subtitle),
                secContentPlayback,
                onNavigateToPlayerSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Public,
                stringResource(R.string.settings_item_proxy),
                stringResource(R.string.settings_item_proxy_subtitle),
                secContentPlayback,
                onNavigateToProxySettings,
            ),
            SettingSearchEntry(
                R.drawable.ic_block,
                stringResource(R.string.sb_settings_title),
                stringResource(R.string.sb_settings_subtitle),
                secContentPlayback,
                onNavigateToSponsorBlockSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.HighQuality,
                stringResource(R.string.settings_item_quality),
                stringResource(R.string.settings_item_quality_subtitle),
                secContentPlayback,
                onNavigateToVideoQuality,
            ),
            SettingSearchEntry(
                Icons.Outlined.Slideshow,
                stringResource(R.string.shorts_quality_settings_title),
                stringResource(R.string.shorts_quality_settings_subtitle),
                secContentPlayback,
                onNavigateToShortsQuality,
            ),
            SettingSearchEntry(
                Icons.Outlined.Speed,
                stringResource(R.string.settings_item_buffer),
                stringResource(R.string.settings_item_buffer_subtitle),
                secContentPlayback,
                onNavigateToBufferSettings,
            ),
            SettingSearchEntry(
                Icons.Outlined.Download,
                stringResource(R.string.settings_item_downloads),
                stringResource(R.string.settings_item_downloads_subtitle),
                secContentPlayback,
                onNavigateToDownloads,
            ),
            SettingSearchEntry(
                Icons.AutoMirrored.Outlined.TrendingUp,
                stringResource(R.string.settings_item_region),
                REGION_NAMES[currentRegion] ?: currentRegion,
                secContentPlayback,
            ) { showRegionDialog = true },
            SettingSearchEntry(
                Icons.Outlined.NotificationsNone,
                stringResource(R.string.settings_item_notifications),
                stringResource(R.string.settings_item_notifications_subtitle),
                secNotifications,
                onNavigateToNotifications,
            ),
            SettingSearchEntry(
                Icons.Outlined.RecordVoiceOver,
                stringResource(R.string.settings_stt_provider),
                sttProviderLabel,
                secRecognition,
            ) { showSttProviderDialog = true },
            SettingSearchEntry(
                Icons.Outlined.Key,
                stringResource(R.string.settings_stt_api_key),
                sttApiKeyLabel,
                secRecognition,
            ) { showSttApiKeyDialog = true },
            SettingSearchEntry(
                Icons.Outlined.MusicNote,
                stringResource(R.string.settings_recognition_provider),
                recognitionProviderLabel,
                secRecognition,
            ) { showRecognitionProviderDialog = true },
            SettingSearchEntry(
                Icons.AutoMirrored.Outlined.CompareArrows,
                stringResource(R.string.settings_recognition_fallback),
                stringResource(R.string.settings_recognition_fallback_subtitle),
                secRecognition,
            ) { showRecognitionFallbackDialog = true },
            SettingSearchEntry(
                Icons.Outlined.NotificationsActive,
                stringResource(R.string.settings_recognition_notifications),
                stringResource(R.string.settings_recognition_notifications_subtitle),
                secRecognition,
            ) { onRecognitionNotificationsToggle(!recognitionNotificationsEnabled) },
            SettingSearchEntry(
                Icons.AutoMirrored.Outlined.OpenInNew,
                stringResource(R.string.settings_recognition_floating_button),
                stringResource(R.string.settings_recognition_floating_button_subtitle),
                secRecognition,
            ) { onRecognitionFloatingToggle(!floatingButtonShown) },
            SettingSearchEntry(
                Icons.Outlined.History,
                stringResource(R.string.settings_item_search_history),
                stringResource(R.string.settings_item_search_history_subtitle),
                secDataManagement,
                onNavigateToSearchHistory,
            ),
            SettingSearchEntry(
                Icons.Outlined.Schedule,
                stringResource(R.string.settings_item_time_management),
                stringResource(R.string.settings_item_time_management_subtitle),
                secDataManagement,
                onNavigateToTimeManagement,
            ),
            SettingSearchEntry(
                Icons.Outlined.Devices,
                stringResource(R.string.sync_devices_title),
                stringResource(R.string.sync_devices_subtitle),
                secDataManagement,
                onNavigateToSyncDevices,
            ),
            SettingSearchEntry(
                Icons.Outlined.Info,
                stringResource(R.string.settings_item_about_flow),
                stringResource(R.string.settings_item_about_flow_subtitle),
                secAbout,
                onNavigateToAbout,
            ),
            SettingSearchEntry(
                Icons.Outlined.BugReport,
                stringResource(R.string.settings_item_diagnostics),
                stringResource(R.string.settings_item_diagnostics_subtitle),
                secAbout,
                onNavigateToDiagnostics,
            ),
            SettingSearchEntry(
                Icons.Outlined.VolunteerActivism,
                stringResource(R.string.settings_item_support),
                stringResource(R.string.settings_item_support_subtitle),
                secAbout,
                onNavigateToDonations,
            ),
        ) +
            listOf(
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics", "Animation, position, providers", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics glow effect", "glow, shadow, karaoke", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics blur", "standard blur, inactive lines", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics animation style", "karaoke, apple music, fluid, metro", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics text size & spacing", "font size, line spacing", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics auto scroll", "autoscroll, synced lyrics", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Change lyrics on tap", "tap line to seek", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Swipe to change song (lyrics)", "next, previous track gesture", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.MusicNote, "Lyrics provider order", "lrclib, kugou, betterlyrics, simpmusic, youlyplus, source priority", secAppearance, onNavigateToLyrics),
                SettingSearchEntry(Icons.Outlined.GraphicEq, "Recognition card style", "corner radius, artwork size, compact, full", secAppearance, onNavigateToRecognitionAppearance),
                SettingSearchEntry(Icons.Outlined.GraphicEq, "Recognition floating button size", "overlay button shape", secAppearance, onNavigateToRecognitionAppearance),
                SettingSearchEntry(Icons.Outlined.Download, "Download threads", "parallel connections", secContentPlayback, onNavigateToDownloads),
                SettingSearchEntry(Icons.Outlined.Download, "Download quality & codec", "default quality, h264, vp9, av1", secContentPlayback, onNavigateToDownloads),
                SettingSearchEntry(Icons.Outlined.Download, "Download over Wi-Fi only", "network restriction, data saver", secContentPlayback, onNavigateToDownloads),
                SettingSearchEntry(Icons.Outlined.Download, "Download folder & filename template", "storage location", secContentPlayback, onNavigateToDownloads),
                SettingSearchEntry(Icons.Outlined.Download, "Download subtitles", "captions with download", secContentPlayback, onNavigateToDownloads),
                SettingSearchEntry(Icons.Outlined.Tune, "Library subscriptions shelf", "shelf, channel rail, library layout", secContentPlayback, onNavigateToContentSettings),
                SettingSearchEntry(Icons.Outlined.Tune, "Action Row", "Group, reorder, show/hide", secAppearance, onNavigateToActionRow),
                SettingSearchEntry(Icons.Outlined.GraphicEq, "Recognition Appearance", "Card & floating button style", secAppearance, onNavigateToRecognitionAppearance),
                SettingSearchEntry(Icons.Outlined.Tune, "Player Appearance", "Customize player controls & seekbar", secContentPlayback, onNavigateToPlayerAppearance),
                SettingSearchEntry(Icons.Outlined.HighQuality, "Video Quality", "Default quality Wi-Fi & cellular", secContentPlayback, onNavigateToVideoQuality),
                SettingSearchEntry(Icons.Outlined.Speed, "Buffer", "Buffer profile & sizes", secContentPlayback, onNavigateToBufferSettings),
                SettingSearchEntry(Icons.Outlined.Download, "Downloads", "Threads & download settings", secContentPlayback, onNavigateToDownloads),
            ) +
            if (BuildConfig.UPDATER_ENABLED) {
                listOf(
                    SettingSearchEntry(
                        Icons.Outlined.Update,
                        stringResource(R.string.check_for_updates),
                        stringResource(R.string.check_for_updates_subtitle),
                        secAbout,
                        onCheckForUpdatesClick,
                    ),
                )
            } else {
                emptyList()
            }
    val filteredEntries =
        if (searchQuery.isBlank()) emptyList()
        else {
            val scored = allSettingsEntries.mapNotNull { e ->
                com.omersusin.pitube.util.fuzzyScore(searchQuery,
                    com.omersusin.pitube.util.MatchField(e.title, 3),
                    com.omersusin.pitube.util.MatchField(e.subtitle.ifBlank { e.sectionLabel }, 2),
                    com.omersusin.pitube.util.MatchField(e.sectionLabel, 1)
                )?.let { e to it }
            }.sortedByDescending { it.second }.take(12).map { it.first }
            if (scored.isNotEmpty()) scored else allSettingsEntries.filter { e ->
                e.title.contains(searchQuery, ignoreCase = true) || e.subtitle.contains(searchQuery, ignoreCase = true) || e.sectionLabel.contains(searchQuery, ignoreCase = true)
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (isSearchActive) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_close_search))
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester),
                            placeholder = { Text(stringResource(R.string.ui_search_settings)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.ui_clear_search))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.ui_search_settings))
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        if (isSearchActive && searchQuery.isNotBlank()) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_search_no_results, searchQuery),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(filteredEntries.size) { index ->
                        SettingsSearchResultItem(
                            entry = filteredEntries[index],
                            onNavigate = {
                                isSearchActive = false
                                searchQuery = ""
                                filteredEntries[index].onClick()
                            },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // =================================================
                // GOOGLE ACCOUNT
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_account)) }
                item {
                    SettingsGroup {
                        if (isGoogleSignedIn) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val avatarUrl = youtubeAccountThumbnail?.takeIf { it.isNotBlank() }
                                if (avatarUrl == null) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = youtubeAccountName?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.settings_google_account_signed_in),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    val email = youtubeAccountEmail
                                    if (!email.isNullOrBlank()) {
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        val accountSwitcher = com.omersusin.pitube.data.local.AccountSwitcher(context)
                                        accountSwitcher.signOut(accountSwitcher.active().id)
                                        playerPreferences.clearYoutubeAccount()
                                        librarySyncResultText = null
                                        runCatching {
                                            com.omersusin.pitube.data.local.HomeFeedCacheRepository(context).clearAll()
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.settings_google_sign_out))
                                }
                            }
                            HorizontalDivider()
                            if (sessionExpired) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onNavigateToGoogleLogin)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.settings_session_expired),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                HorizontalDivider()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = librarySyncResultText
                                            ?: if (showPersistedSyncResult) {
                                                context.getString(
                                                    R.string.settings_google_sync_result,
                                                    lastLiked,
                                                    lastPlaylists,
                                                    lastChannels
                                                )
                                            } else {
                                                stringResource(R.string.settings_google_sync_subtitle)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSyncingLibrary) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    TextButton(onClick = {
                                        coroutineScope.launch { performLibrarySync() }
                                    }) {
                                        Text(stringResource(R.string.settings_google_sync_now))
                                    }
                                }
                            }
                        } else {
                            SettingsItem(
                                icon = Icons.Outlined.AccountCircle,
                                title = stringResource(R.string.settings_google_sign_in),
                                subtitle = stringResource(R.string.settings_google_sign_in_subtitle),
                                onClick = onNavigateToGoogleLogin,
                            )
                        }
                        SettingsItem(
                            icon = Icons.Outlined.SwapVert,
                            title = stringResource(R.string.account_switcher_switch_account),
                            subtitle = stringResource(R.string.account_switcher_switch_account_subtitle),
                            onClick = { showAccountSwitcher = true },
                        )
                    }
                }

                // =================================================
                // APPEARANCE
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_appearance)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_item_theme),
                            subtitle = stringResource(getThemeNameRes(currentTheme)),
                            onClick = onNavigateToAppearance,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_item_app_language),
                            subtitle = currentAppLanguageLabel,
                            onClick = { showAppLanguageDialog = true },
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.AppShortcut,
                            title = stringResource(R.string.settings_item_app_icon),
                            subtitle = stringResource(R.string.settings_item_app_icon_subtitle),
                            onClick = onNavigateToAppIconPicker,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Tune,
                            title = stringResource(R.string.settings_item_player_appearance),
                            subtitle = stringResource(R.string.settings_item_player_appearance_subtitle),
                            onClick = onNavigateToPlayerAppearance,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.SwapHoriz,
                            title = stringResource(R.string.settings_item_subscription_transfer),
                            subtitle = stringResource(R.string.settings_item_subscription_transfer_subtitle),
                            onClick = onNavigateToSubscriptionTransfer,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.GridView,
                            title = stringResource(R.string.settings_item_content_display),
                            subtitle = stringResource(R.string.settings_item_content_display_subtitle),
                            onClick = onNavigateToContentSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_item_datetime),
                            subtitle = stringResource(R.string.settings_item_datetime_subtitle),
                            onClick = onNavigateToDateTimeSettings,
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(icon = Icons.Outlined.MusicNote, title = "Lyrics", subtitle = "Animation, position, providers", onClick = onNavigateToLyrics)
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(icon = Icons.Outlined.Tune, title = "Action Row", subtitle = "Group, reorder, show/hide", onClick = onNavigateToActionRow)
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsItem(icon = Icons.Outlined.GraphicEq, title = "Recognition Appearance", subtitle = "Card & floating button style", onClick = onNavigateToRecognitionAppearance)
                    }
                }

                // =================================================
                // CONTENT & PLAYBACK
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_content_playback)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.PlayCircle,
                            title = stringResource(R.string.settings_item_player),
                            subtitle = stringResource(R.string.settings_item_player_subtitle),
                            onClick = onNavigateToPlayerSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Public,
                            title = stringResource(R.string.settings_item_proxy),
                            subtitle = stringResource(R.string.settings_item_proxy_subtitle),
                            onClick = onNavigateToProxySettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = painterResource(R.drawable.ic_block),
                            title = stringResource(R.string.sb_settings_title),
                            subtitle = stringResource(R.string.sb_settings_subtitle),
                            onClick = onNavigateToSponsorBlockSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.HighQuality,
                            title = stringResource(R.string.settings_item_quality),
                            subtitle = stringResource(R.string.settings_item_quality_subtitle),
                            onClick = onNavigateToVideoQuality,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Slideshow,
                            title = stringResource(R.string.shorts_quality_settings_title),
                            subtitle = stringResource(R.string.shorts_quality_settings_subtitle),
                            onClick = onNavigateToShortsQuality,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.settings_item_buffer),
                            subtitle = stringResource(R.string.settings_item_buffer_subtitle),
                            onClick = onNavigateToBufferSettings,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.settings_item_downloads),
                            subtitle = stringResource(R.string.settings_item_downloads_subtitle),
                            onClick = onNavigateToDownloads,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                            title = stringResource(R.string.settings_item_region),
                            subtitle = REGION_NAMES[currentRegion] ?: currentRegion,
                            onClick = { showRegionDialog = true },
                        )
                    }
                }

                // =================================================
                // TRANSLATION
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_translation)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.Translate,
                            title = stringResource(R.string.settings_item_translation),
                            subtitle = stringResource(R.string.settings_item_translation_subtitle),
                            onClick = onNavigateToTranslation,
                        )
                    }
                }

                // =================================================
                // NOTIFICATIONS
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_notifications)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.NotificationsNone,
                            title = stringResource(R.string.settings_item_notifications),
                            subtitle = stringResource(R.string.settings_item_notifications_subtitle),
                            onClick = onNavigateToNotifications,
                        )
                    }
                }

                // =================================================
                // SPEECH TO TEXT
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_speech_to_text)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.RecordVoiceOver,
                            title = stringResource(R.string.settings_stt_provider),
                            subtitle = sttProviderLabel,
                            onClick = { showSttProviderDialog = true },
                        )
                        if (sttProvider.isCloud) {
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                            SettingsItem(
                                icon = Icons.Outlined.Key,
                                title = stringResource(R.string.settings_stt_api_key),
                                subtitle = sttApiKeyLabel,
                                onClick = { showSttApiKeyDialog = true },
                            )
                        }
                    }
                }

                // =================================================
                // SONG RECOGNITION
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_recognition)) }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.MusicNote,
                            title = stringResource(R.string.settings_recognition_provider),
                            subtitle = recognitionProviderLabel,
                            onClick = { showRecognitionProviderDialog = true },
                        )
                        SettingsItem(
                            icon = Icons.AutoMirrored.Outlined.CompareArrows,
                            title = stringResource(R.string.settings_recognition_fallback),
                            subtitle = stringResource(R.string.settings_recognition_fallback_subtitle),
                            onClick = { showRecognitionFallbackDialog = true },
                        )
                        SettingsSwitchItem(
                            icon = Icons.Outlined.NotificationsActive,
                            title = stringResource(R.string.settings_recognition_notifications),
                            subtitle = stringResource(R.string.settings_recognition_notifications_subtitle),
                            checked = recognitionNotificationsEnabled,
                            onCheckedChange = ::onRecognitionNotificationsToggle,
                        )
                        SettingsSwitchItem(
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            title = stringResource(R.string.settings_recognition_floating_button),
                            subtitle = stringResource(R.string.settings_recognition_floating_button_subtitle),
                            checked = floatingButtonShown,
                            onCheckedChange = ::onRecognitionFloatingToggle,
                        )
                    }
                }

                // =================================================
                // DATA MANAGEMENT
                // =================================================
                item {
                    SectionHeader(
                        text = stringResource(R.string.settings_header_data_management),
                    )
                }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.settings_item_search_history),
                            subtitle = stringResource(R.string.settings_item_search_history_subtitle),
                            onClick = onNavigateToSearchHistory,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.settings_item_time_management),
                            subtitle = stringResource(R.string.settings_item_time_management_subtitle),
                            onClick = onNavigateToTimeManagement,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Devices,
                            title = stringResource(R.string.sync_devices_title),
                            subtitle = stringResource(R.string.sync_devices_subtitle),
                            onClick = onNavigateToSyncDevices,
                        )
                    }
                }

                // =================================================
                // ABOUT
                // =================================================
                item { SectionHeader(text = stringResource(R.string.settings_header_about)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_item_about_flow),
                            subtitle = stringResource(R.string.settings_item_about_flow_subtitle),
                            onClick = onNavigateToAbout,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.settings_item_diagnostics),
                            subtitle = stringResource(R.string.settings_item_diagnostics_subtitle),
                            onClick = onNavigateToDiagnostics,
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        if (BuildConfig.UPDATER_ENABLED) {
                            SettingsItem(
                                icon = if (isCheckingUpdate) Icons.Outlined.Sync else Icons.Outlined.Update,
                                title = stringResource(R.string.check_for_updates),
                                subtitle =
                                    if (isCheckingUpdate) {
                                        stringResource(R.string.checking_for_updates)
                                    } else {
                                        stringResource(R.string.check_for_updates_subtitle)
                                    },
                                onClick = onCheckForUpdatesClick,
                            )
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        SettingsItem(
                            icon = Icons.Outlined.VolunteerActivism,
                            title = stringResource(R.string.settings_item_support),
                            subtitle = stringResource(R.string.settings_item_support_subtitle),
                            onClick = onNavigateToDonations,
                        )
                    }
                }
            }
        }
    }

    // Update Available Dialog (github flavor only)
    if (BuildConfig.UPDATER_ENABLED) {
        val tag = updateAvailableTag
        if (tag != null) {
            AlertDialog(
                onDismissRequest = { updateAvailableTag = null },
                icon = { Icon(Icons.Outlined.Update, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.new_update_available), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        stringResource(R.string.update_available_template, tag),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        updateAvailableTag = null
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/omersusin/piTube/releases/latest"))
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateAvailableTag = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (showAppLanguageDialog) {
        var languageSearchQuery by remember { mutableStateOf("") }
        val normalizedCurrentLanguage =
            remember(currentAppLanguage) {
                AppLanguageManager.normalizeLanguageTag(currentAppLanguage)
            }
        val filteredLanguages =
            remember(languageSearchQuery, appLanguageOptions) {
                if (languageSearchQuery.isBlank()) {
                    appLanguageOptions
                } else {
                    appLanguageOptions.filter { option ->
                        option.nativeName.contains(languageSearchQuery, ignoreCase = true) ||
                            option.localizedName.contains(languageSearchQuery, ignoreCase = true) ||
                            option.tag.contains(languageSearchQuery, ignoreCase = true)
                    }
                }
            }
        AlertDialog(
            onDismissRequest = { showAppLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            playerPreferences.setAppLanguage(AppLanguageManager.SYSTEM_DEFAULT)
                                            AppLanguageManager.saveLanguageTag(context, AppLanguageManager.SYSTEM_DEFAULT)
                                            showAppLanguageDialog = false
                                            AppLanguageManager.activityContext(context)?.recreate()
                                        }
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = normalizedCurrentLanguage == AppLanguageManager.SYSTEM_DEFAULT,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(R.string.settings_language_system_default))
                                    Text(
                                        text = stringResource(R.string.settings_item_app_language_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(filteredLanguages.size) { index ->
                            val option = filteredLanguages[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            playerPreferences.setAppLanguage(option.tag)
                                            AppLanguageManager.saveLanguageTag(context, option.tag)
                                            showAppLanguageDialog = false
                                            AppLanguageManager.activityContext(context)?.recreate()
                                        }
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = normalizedCurrentLanguage == option.tag, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(option.nativeName)
                                    if (option.localizedName != option.nativeName) {
                                        Text(
                                            text = option.localizedName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Region Selection Dialog
    if (showRegionDialog) {
        var regionSearchQuery by remember { mutableStateOf("") }
        val filteredRegions =
            remember(regionSearchQuery) {
                if (regionSearchQuery.isBlank()) {
                    regionList
                } else {
                    regionList.filter { (code, name) ->
                        name.contains(regionSearchQuery, ignoreCase = true) ||
                            code.contains(regionSearchQuery, ignoreCase = true)
                    }
                }
            }
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text(stringResource(R.string.settings_region_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = regionSearchQuery,
                        onValueChange = { regionSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(filteredRegions.size) { index ->
                            val (code, name) = filteredRegions[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            playerPreferences.setTrendingRegion(code)
                                            showRegionDialog = false
                                        }
                                    }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = currentRegion == code, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRegionDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showRecognitionProviderDialog) {
        AlertDialog(
            onDismissRequest = { showRecognitionProviderDialog = false },
            title = { Text(stringResource(R.string.settings_recognition_provider)) },
            text = {
                Column {
                    RecognitionProvider.entries.forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        recognitionPreferences.setProvider(provider)
                                        showRecognitionProviderDialog = false
                                    }
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = recognitionProvider == provider, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (provider) {
                                    RecognitionProvider.SHAZAM -> stringResource(R.string.recognition_provider_shazam)
                                    RecognitionProvider.AUDD -> stringResource(R.string.recognition_provider_audd)
                                    RecognitionProvider.ACRCLOUD -> stringResource(R.string.recognition_provider_acrcloud)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRecognitionProviderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSttProviderDialog) {
        AlertDialog(
            onDismissRequest = { showSttProviderDialog = false },
            title = { Text(stringResource(R.string.settings_stt_provider)) },
            text = {
                Column {
                    SttProvider.entries.forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        recognitionPreferences.setSttProvider(provider)
                                        showSttProviderDialog = false
                                    }
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = sttProvider == provider, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    when (provider) {
                                        SttProvider.CIHAZ -> stringResource(R.string.stt_provider_cihaz)
                                        SttProvider.GROQ -> stringResource(R.string.stt_provider_groq)
                                        SttProvider.IBM_WATSON -> stringResource(R.string.stt_provider_ibm_watson)
                                        SttProvider.AZURE -> stringResource(R.string.stt_provider_azure)
                                        SttProvider.GOOGLE_CLOUD -> stringResource(R.string.stt_provider_google_cloud)
                                    },
                                )
                                Text(
                                    when (provider) {
                                        SttProvider.CIHAZ -> stringResource(R.string.stt_provider_cihaz_subtitle)
                                        SttProvider.GROQ -> stringResource(R.string.stt_provider_groq_subtitle)
                                        SttProvider.IBM_WATSON -> stringResource(R.string.stt_provider_ibm_watson_subtitle)
                                        SttProvider.AZURE -> stringResource(R.string.stt_provider_azure_subtitle)
                                        SttProvider.GOOGLE_CLOUD -> stringResource(R.string.stt_provider_google_cloud_subtitle)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSttProviderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSttApiKeyDialog) {
        var apiKeyText by rememberSaveable { mutableStateOf(sttApiKeys.getApiKey(sttProvider).orEmpty()) }
        var regionText by rememberSaveable {
            mutableStateOf(if (sttProvider == SttProvider.AZURE) sttApiKeys.getAzureRegion().orEmpty() else "")
        }
        var urlText by rememberSaveable {
            mutableStateOf(if (sttProvider == SttProvider.IBM_WATSON) sttApiKeys.getIbmInstanceUrl().orEmpty() else "")
        }
        AlertDialog(
            onDismissRequest = { showSttApiKeyDialog = false },
            title = { Text(stringResource(R.string.settings_stt_api_key)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_stt_api_key_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text(stringResource(R.string.settings_stt_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (sttProvider == SttProvider.AZURE) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = regionText,
                            onValueChange = { regionText = it },
                            label = { Text(stringResource(R.string.stt_azure_region)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (sttProvider == SttProvider.IBM_WATSON) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            label = { Text(stringResource(R.string.stt_ibm_instance_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sttApiKeys.setApiKey(sttProvider, apiKeyText)
                        if (sttProvider == SttProvider.AZURE) {
                            sttApiKeys.setAzureRegion(regionText)
                        }
                        if (sttProvider == SttProvider.IBM_WATSON) {
                            sttApiKeys.setIbmInstanceUrl(urlText)
                        }
                        showSttApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSttApiKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRecognitionFallbackDialog) {
        var fallbackDropdownFor by remember { mutableStateOf<RecognitionFailureType?>(null) }

        @Composable
        fun policyLabel(policy: FallbackPolicy): String =
            when (policy) {
                FallbackPolicy.IGNORE -> stringResource(R.string.recognition_fallback_ignore)
                FallbackPolicy.SAVE -> stringResource(R.string.recognition_fallback_save)
                FallbackPolicy.SAVE_AND_RETRY -> stringResource(R.string.recognition_fallback_save_retry)
            }

        @Composable
        fun failureLabel(type: RecognitionFailureType): String =
            when (type) {
                RecognitionFailureType.BAD_CONNECTION -> stringResource(R.string.recognition_failure_no_internet)
                RecognitionFailureType.NO_MATCH -> stringResource(R.string.recognition_failure_no_match)
                RecognitionFailureType.OTHER -> stringResource(R.string.recognition_failure_other)
            }

        AlertDialog(
            onDismissRequest = { showRecognitionFallbackDialog = false },
            title = { Text(stringResource(R.string.settings_recognition_fallback_dialog_title)) },
            text = {
                Column {
                    RecognitionFailureType.entries.forEach { type ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = failureLabel(type),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(onClick = { fallbackDropdownFor = type }) {
                                    Text(
                                        policyLabel(
                                            when (type) {
                                                RecognitionFailureType.BAD_CONNECTION -> recognitionFallbackState.badInternet
                                                RecognitionFailureType.NO_MATCH -> recognitionFallbackState.noMatch
                                                RecognitionFailureType.OTHER -> recognitionFallbackState.other
                                            },
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = fallbackDropdownFor == type,
                                    onDismissRequest = { fallbackDropdownFor = null },
                                ) {
                                    FallbackPolicy.entries.forEach { policy ->
                                        DropdownMenuItem(
                                            text = { Text(policyLabel(policy)) },
                                            onClick = {
                                                coroutineScope.launch {
                                                    when (type) {
                                                        RecognitionFailureType.BAD_CONNECTION -> recognitionPreferences.setFallbackBadInternet(policy)
                                                        RecognitionFailureType.NO_MATCH -> recognitionPreferences.setFallbackNoMatch(policy)
                                                        RecognitionFailureType.OTHER -> recognitionPreferences.setFallbackOther(policy)
                                                    }
                                                    fallbackDropdownFor = null
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_recognition_fallback_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRecognitionFallbackDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAccountSwitcher) {
        com.omersusin.pitube.ui.screens.account.AccountSwitcherSheet(
            onDismiss = { showAccountSwitcher = false },
            onAddYouTubeAccount = onAddYouTubeAccount,
        )
    }
}

private const val AUTO_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L // once a day

private val REGION_NAMES =
    mapOf(
        "DZ" to "Algeria",
        "AS" to "American Samoa",
        "AI" to "Anguilla",
        "AR" to "Argentina",
        "AW" to "Aruba",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BH" to "Bahrain",
        "BD" to "Bangladesh",
        "BY" to "Belarus",
        "BE" to "Belgium",
        "BM" to "Bermuda",
        "BO" to "Bolivia",
        "BA" to "Bosnia and Herzegovina",
        "BR" to "Brazil",
        "IO" to "British Indian Ocean Territory",
        "VG" to "British Virgin Islands",
        "BG" to "Bulgaria",
        "KH" to "Cambodia",
        "CA" to "Canada",
        "KY" to "Cayman Islands",
        "CL" to "Chile",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "HR" to "Croatia",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "SV" to "El Salvador",
        "EE" to "Estonia",
        "FK" to "Falkland Islands",
        "FO" to "Faroe Islands",
        "FI" to "Finland",
        "FR" to "France",
        "GF" to "French Guiana",
        "PF" to "French Polynesia",
        "GE" to "Georgia",
        "DE" to "Germany",
        "GH" to "Ghana",
        "GI" to "Gibraltar",
        "GR" to "Greece",
        "GL" to "Greenland",
        "GP" to "Guadeloupe",
        "GU" to "Guam",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "HK" to "Hong Kong",
        "HU" to "Hungary",
        "IS" to "Iceland",
        "IN" to "India",
        "ID" to "Indonesia",
        "IQ" to "Iraq",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JP" to "Japan",
        "JO" to "Jordan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KW" to "Kuwait",
        "LA" to "Laos",
        "LV" to "Latvia",
        "LB" to "Lebanon",
        "LY" to "Libya",
        "LI" to "Liechtenstein",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "MY" to "Malaysia",
        "MT" to "Malta",
        "MQ" to "Martinique",
        "YT" to "Mayotte",
        "MX" to "Mexico",
        "MD" to "Moldova",
        "ME" to "Montenegro",
        "MS" to "Montserrat",
        "MA" to "Morocco",
        "NP" to "Nepal",
        "NL" to "Netherlands",
        "NC" to "New Caledonia",
        "NZ" to "New Zealand",
        "NI" to "Nicaragua",
        "NG" to "Nigeria",
        "NF" to "Norfolk Island",
        "MP" to "Northern Mariana Islands",
        "NO" to "Norway",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PG" to "Papua New Guinea",
        "PY" to "Paraguay",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PL" to "Poland",
        "PT" to "Portugal",
        "PR" to "Puerto Rico",
        "QA" to "Qatar",
        "RE" to "Reunion",
        "RO" to "Romania",
        "RU" to "Russia",
        "SH" to "Saint Helena",
        "PM" to "Saint Pierre and Miquelon",
        "SA" to "Saudi Arabia",
        "SN" to "Senegal",
        "RS" to "Serbia",
        "SG" to "Singapore",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "ZA" to "South Africa",
        "KR" to "South Korea",
        "ES" to "Spain",
        "LK" to "Sri Lanka",
        "SJ" to "Svalbard and Jan Mayen",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "TH" to "Thailand",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "TC" to "Turks and Caicos Islands",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "VI" to "U.S. Virgin Islands",
        "UY" to "Uruguay",
        "VE" to "Venezuela",
        "VN" to "Vietnam",
    ).toList().sortedBy { it.second }.toMap()

private fun getThemeNameRes(theme: ThemeMode): Int =
    when (theme) {
        ThemeMode.LIGHT -> R.string.theme_name_pure_light
        ThemeMode.MINT_LIGHT -> R.string.theme_name_mint_fresh
        ThemeMode.ROSE_LIGHT -> R.string.theme_name_rose_petal
        ThemeMode.SKY_LIGHT -> R.string.theme_name_sky_blue
        ThemeMode.CREAM_LIGHT -> R.string.theme_name_cream_paper
        ThemeMode.DARK -> R.string.theme_name_classic_dark
        ThemeMode.OLED -> R.string.theme_name_true_black
        ThemeMode.MIDNIGHT_BLACK -> R.string.theme_name_midnight
        ThemeMode.OCEAN_BLUE -> R.string.theme_name_deep_ocean
        ThemeMode.FOREST_GREEN -> R.string.theme_name_forest
        ThemeMode.LAVENDER_MIST -> R.string.theme_name_lavender
        ThemeMode.SUNSET_ORANGE -> R.string.theme_name_sunset
        ThemeMode.PURPLE_NEBULA -> R.string.theme_name_nebula
        ThemeMode.ROSE_GOLD -> R.string.theme_name_rose_gold
        ThemeMode.ARCTIC_ICE -> R.string.theme_name_arctic
        ThemeMode.MINTY_FRESH -> R.string.theme_name_mint_night
        ThemeMode.CRIMSON_RED -> R.string.theme_name_crimson
        ThemeMode.COSMIC_VOID -> R.string.theme_name_cosmic_void
        ThemeMode.SOLAR_FLARE -> R.string.theme_name_solar_flare
        ThemeMode.CYBERPUNK -> R.string.theme_name_cyberpunk
        ThemeMode.ROYAL_GOLD -> R.string.theme_name_royal_gold
        ThemeMode.NORDIC_HORIZON -> R.string.theme_name_nordic
        ThemeMode.ESPRESSO -> R.string.theme_name_espresso
        ThemeMode.GUNMETAL -> R.string.theme_name_gunmetal
        ThemeMode.SYSTEM -> R.string.theme_name_system_default
        ThemeMode.MONOCHROME -> R.string.theme_name_monochrome
        ThemeMode.CUSTOM -> R.string.theme_name_custom
        ThemeMode.MATERIAL_YOU -> R.string.theme_name_material_you
    }

private data class SettingSearchEntry(
    val icon: Any,
    val title: String,
    val subtitle: String,
    val sectionLabel: String,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsSearchResultItem(
    entry: SettingSearchEntry,
    onNavigate: () -> Unit,
) {
    Column {
        Text(
            text = entry.sectionLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 72.dp, top = 8.dp, bottom = 2.dp),
        )
        when (entry.icon) {
            is ImageVector -> {
                SettingsItem(
                    icon = entry.icon,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    onClick = onNavigate,
                )
            }

            is Int -> {
                SettingsItem(
                    icon = painterResource(entry.icon),
                    title = entry.title,
                    subtitle = entry.subtitle,
                    onClick = onNavigate,
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
}

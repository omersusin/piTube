package com.omersusin.pitube

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.SubscriptionRepository
import com.omersusin.pitube.data.repository.NewPipeDownloader
import com.omersusin.pitube.data.repository.YouTubeRepository
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.models.YouTubeLocale
import com.omersusin.pitube.innertube.models.normalizeYouTubeHostLanguage
import com.omersusin.pitube.innertube.pages.NewPipeExtractor
import com.omersusin.pitube.network.AppProxyManager
import com.omersusin.pitube.notification.NotificationHelper
import com.omersusin.pitube.ui.screens.home.HomeFeedCache
import com.omersusin.pitube.utils.AppLanguageManager
import com.omersusin.pitube.utils.FlowCrashHandler
import com.omersusin.pitube.utils.PerformanceDispatcher
import com.omersusin.pitube.utils.potoken.NewPipePoTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.security.Security
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FlowApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    companion object {
        private const val TAG = "FlowApplication"
        private const val VISITOR_DATA_KEY = "visitor_data"
        private const val VISITOR_DATA_FETCHED_AT_KEY = "visitor_data_fetched_at"
        private const val VISITOR_DATA_MAX_AGE_MS = 6L * 60L * 60L * 1_000L
        private const val AUTO_LIBRARY_SYNC_INTERVAL_MS = 24L * 60L * 60L * 1_000L
        lateinit var appContext: Context
            private set
    }

    override fun attachBaseContext(base: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(base)
        super.attachBaseContext(AppLanguageManager.wrapContext(base, selectedLanguage))
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        val playerPreferences = PlayerPreferences(this)

        // Injects modern TLS/SSL certificates so OkHttp and Ktor don't crash
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Install crash handler for real-time monitoring
        FlowCrashHandler.install(this)

        try {
            val country = ContentCountry("US")
            val localization = Localization("en", "US")
            NewPipe.init(NewPipeDownloader.getInstance(this), localization, country)
            YoutubeStreamExtractor.setPoTokenProvider(NewPipePoTokenProvider)
            Log.d(TAG, "NewPipe initialized successfully with en-US settings")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }

        try {
            com.omersusin.pitube.utils.cipher.CipherDeobfuscator
                .initialize(this)
            Log.d(TAG, "CipherDeobfuscator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CipherDeobfuscator", e)
        }

        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")

        // Recognition feature: notification channel + offline-recording retry
        // monitor (replays saved samples once connectivity is restored).
        com.omersusin.pitube.recognition.RecognitionNotifier.ensureChannel(this)
        com.omersusin.pitube.recognition.RecognitionSamplesStore
            .startOfflineRetryMonitor(this)
        Log.d(TAG, "Recognition initialized")

        /*
        try {
            // Initialize YoutubeDL
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            Log.d(TAG, "YoutubeDL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        }
         */

        // Schedule periodic update checks (every 12 hours) — github flavor only
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            kotlinx.coroutines.delay(15_000L)
            if (BuildConfig.UPDATER_ENABLED) {
                com.omersusin.pitube.notification.UpdateCheckWorker
                    .schedulePeriodicCheck(this@FlowApplication)
            }
        }

        // Keep the home feed + account library rotating in the background so the
        // feed never serves the same pinned items (visitor rotation + cache
        // invalidation + library re-sync every 12 hours).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            com.omersusin.pitube.notification.FeedAndLibrarySyncWorker
                .schedulePeriodicSync(this@FlowApplication)
        }

        Log.d(TAG, "Workers scheduled successfully")

        // Fetch and cache visitor data for the lifetime of the install.
        // The X-Goog-Visitor-Id header prevents YouTube from returning empty
        // search results on tablets and fresh Android 16 installs (Issue #223).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            playerPreferences.proxyConfig.collectLatest { proxyConfig ->
                applyProxyConfig(proxyConfig)
            }
        }

        YouTube.onCookieRotated = { merged ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { com.omersusin.pitube.data.local.SessionManager(this@FlowApplication).saveCookies(merged) }
            }
        }
        YouTube.sessionStateListener = { loggedIn ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    com.omersusin.pitube.data.local.SessionManager(this@FlowApplication)
                        .setSessionExpired(!loggedIn)
                }
            }
        }
        YouTube.dataSyncIdListener = { healed ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val pm = com.omersusin.pitube.data.local.ProfileManager(this@FlowApplication)
                    val profile = pm.active()
                    if (profile.datasyncId != healed) {
                        pm.updateIdentity(profile.id, datasyncId = healed)
                    }
                }
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preferences = com.omersusin.pitube.data.local.PlayerPreferences(this@FlowApplication)
                val pm = com.omersusin.pitube.data.local.ProfileManager(this@FlowApplication)
                val legacyCookie = preferences.youtubeCookie.first()
                val legacyName = preferences.youtubeAccountName.first()
                val legacyAvatar = preferences.youtubeAccountThumbnail.first()
                if (pm.profiles.value.isEmpty()) {
                    pm.ensureMigrated(legacyCookie, legacyName, legacyAvatar)
                }
                val cookie = com.omersusin.pitube.data.local.SessionManager(this@FlowApplication).getCookies()
                YouTube.cookie = cookie
                YouTube.useLoginForBrowse = !cookie.isNullOrEmpty()
                val activeProfile = pm.active()
                YouTube.dataSyncId = activeProfile.datasyncId.takeIf { !activeProfile.isLocal }
                SessionManager.restored.complete(true)
                Log.d(TAG, "YouTube session restored, signedIn=${!cookie.isNullOrEmpty()}")
                if (!cookie.isNullOrBlank() && !HomeFeedCache.signedIn) {
                    HomeFeedCache.invalidate()
                }
                if (cookie.isNullOrBlank()) {
                    preferences.clearYoutubeAccount()
                } else {
                    preferences.refreshYoutubeCookie(cookie)
                }
                runCatching {
                    com.omersusin.pitube.data.local.SubscriptionRepository
                        .getInstance(this@FlowApplication).ensureScopeMigration()
                }
                runCatching { com.omersusin.pitube.data.local.LikedVideosRepository.getInstance(this@FlowApplication).ensureScopeMigration() }
                runCatching { com.omersusin.pitube.data.local.SearchHistoryRepository(this@FlowApplication).ensureScopeMigration() }
                launch {
                    kotlinx.coroutines.delay(10_000L)
                    runCatching { com.omersusin.pitube.data.local.ViewHistory.getInstance(this@FlowApplication).ensureScopeMigration() }
                    runCatching { com.omersusin.pitube.data.local.PlaylistRepository(this@FlowApplication).ensureScopeMigration() }
                }
                if (!cookie.isNullOrEmpty()) {
                    com.omersusin.pitube.sync.LibrarySyncLauncher.syncInBackground(applicationContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "session restore error: ${e.message}")
                runCatching { SessionManager.restored.complete(false) }
            }
            try {
                val now = System.currentTimeMillis()
                val ivorPrefs = getSharedPreferences("ivor_visitor_data", MODE_PRIVATE)
                var cached = ivorPrefs.getString("visitor_data", null)
                var cachedAt = ivorPrefs.getLong("visitor_data_at", 0L)
                var cacheIsFresh = cachedAt != 0L && now - cachedAt in 0 until VISITOR_DATA_MAX_AGE_MS
                var restored = false
                if (!cached.isNullOrEmpty() && cacheIsFresh) {
                    YouTube.visitorData = cached
                    Log.d(TAG, "visitorData restored from ivor prefs")
                    restored = true
                } else {
                    val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                    cached = prefs.getString(VISITOR_DATA_KEY, null)
                    cachedAt = prefs.getLong(VISITOR_DATA_FETCHED_AT_KEY, 0L)
                    cacheIsFresh = cachedAt != 0L && System.currentTimeMillis() - cachedAt in 0 until VISITOR_DATA_MAX_AGE_MS
                    if (!cached.isNullOrEmpty() && cacheIsFresh) {
                        YouTube.visitorData = cached
                        ivorPrefs.edit().putString("visitor_data", cached).putLong("visitor_data_at", cachedAt).apply()
                        Log.d(TAG, "visitorData restored from prefs")
                        restored = true
                    }
                }
                if (!restored) {
                    val data = YouTube.getVisitorData()
                    if (!data.isNullOrEmpty()) {
                        Log.d(TAG, "visitorData fetched and cached")
                    } else {
                        YouTube.visitorData().onSuccess { fallback ->
                            if (!fallback.isNullOrEmpty()) {
                                val t = System.currentTimeMillis()
                                ivorPrefs.edit().putString("visitor_data", fallback).putLong("visitor_data_at", t).apply()
                                getSharedPreferences("flow_prefs", MODE_PRIVATE).edit().putString(VISITOR_DATA_KEY, fallback).putLong(VISITOR_DATA_FETCHED_AT_KEY, t).apply()
                                YouTube.visitorData = fallback
                                Log.d(TAG, "visitorData fetched via fallback and cached")
                            }
                        }.onFailure { e -> Log.w(TAG, "visitorData fetch failed: ${e.message}") }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "visitorData init error: ${e.message}")
            }
            launch {
                // Deferred far past cold start: the prewarm spins up the
                // BotGuard WebView (chromium). Loading it while the user is
                // still settling on the home screen added to startup heating;
                // an on-demand mint serves playback that starts earlier, and
                // this then keeps the session warm for later.
                kotlinx.coroutines.delay(45_000L)
                try {
                    com.omersusin.pitube.utils.potoken.WebPoTokenSession
                        .prewarm()
                } catch (e: Exception) {
                    Log.w(TAG, "WebPoTokenSession prewarm failed: ${e.message}")
                }
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                com.omersusin.pitube.data.local.ProfileManager(this@FlowApplication).activeProfileId.drop(1).distinctUntilChanged().collect {
                    HomeFeedCache.clear()
                    Log.d(TAG, "HomeFeedCache cleared for profile switch")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Profile switch observer failed: ${e.message}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(
                playerPreferences.appLanguage,
                playerPreferences.trendingRegion,
            ) { lang, region ->
                val glCode = normalizeYouTubeCountry(region)
                val hlCode = normalizeYouTubeHostLanguage(lang)
                YouTubeLocale(gl = glCode, hl = hlCode)
            }.distinctUntilChanged().debounce(5_000).collectLatest { newLocale ->
                YouTube.locale = newLocale
                Log.d(TAG, "Dynamic YouTube Locale updated: gl=${newLocale.gl}, hl=${newLocale.hl}")
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var lastRegion: String? = null
            playerPreferences.trendingRegion.collectLatest { region ->
                if (lastRegion != null && lastRegion != region) {
                    Log.d(TAG, "Trending region changed from $lastRegion to $region. Invalidate visitor data.")
                    getSharedPreferences("ivor_visitor_data", MODE_PRIVATE).edit().clear().apply()
                    val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                    prefs.edit().remove(VISITOR_DATA_KEY).remove(VISITOR_DATA_FETCHED_AT_KEY).apply()
                    YouTube.visitorData = null
                    val data = YouTube.getVisitorData() ?: YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }
                    if (!data.isNullOrEmpty()) {
                        Log.d(TAG, "Fresh visitorData fetched for region: $region")
                    } else {
                        Log.w(TAG, "Failed to fetch fresh visitorData for region: $region")
                    }
                }
                lastRegion = region
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Thumbnail repair issues up to N sequential network calls;
                // never at T+0 — wait for the app to settle first.
                kotlinx.coroutines.delay(30_000L)
                val repository = SubscriptionRepository.getInstance(this@FlowApplication)
                val youtubeRepository = YouTubeRepository.getInstance(playerPreferences)
                val repaired =
                    repository.repairVideoThumbnailSubscriptions { channelId ->
                        withTimeoutOrNull(6_000L) {
                            youtubeRepository.fetchChannelAvatarById(channelId)
                        }.orEmpty()
                    }
                if (repaired > 0) {
                    Log.i(TAG, "Repaired $repaired subscription thumbnails")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Subscription thumbnail repair failed: ${e.message}")
            }
        }
    }

    private fun applyProxyConfig(config: com.omersusin.pitube.network.AppProxyConfig) {
        AppProxyManager.update(config)
        YouTube.proxy = AppProxyManager.currentProxy()
        YouTube.proxyAuth = AppProxyManager.currentHttpProxyAuthorizationHeader()
        NewPipeExtractor.invalidateClient()
    }

    private fun normalizeYouTubeCountry(region: String): String {
        val normalized = region.trim().uppercase(Locale.US)
        return if (normalized.matches(Regex("[A-Z]{2}"))) {
            normalized
        } else {
            Locale
                .getDefault()
                .country
                .trim()
                .uppercase(Locale.US)
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
                ?: "US"
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onLowMemory")
        releaseVolatileMemory()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onTrimMemory level=$level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            releaseVolatileMemory()
        }
    }

    private fun releaseVolatileMemory() {
        if (::imageLoader.isInitialized) {
            imageLoader.memoryCache?.clear()
        }
        if (::okHttpClient.isInitialized) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                okHttpClient.connectionPool.evictAll()
            }
        }
    }
}

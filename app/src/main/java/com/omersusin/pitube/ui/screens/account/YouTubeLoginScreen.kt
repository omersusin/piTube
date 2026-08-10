package com.omersusin.pitube.ui.screens.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.HomeFeedCacheRepository
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.YouTubeLibrarySync
import com.omersusin.pitube.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Plain ServiceLogin + continue param, matching the current working implementation in
// MetrolistGroup/Metrolist's LoginScreen.kt (verified against their live source). No
// user-agent spoofing: presenting as a real WebView with no extra params on the login
// URL is what currently gets past Google's embedded-browser check. Spoofing a desktop
// Chrome UA (an earlier version of this file did that) creates a mismatch with other
// signals Google's server can see, which is more likely to trigger the "this browser or
// app may not be secure" block than to avoid it.
private const val GOOGLE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/**
 * Signs the user into their real Google account via an embedded WebView pointed at Google's
 * own login page, then captures the resulting YouTube session cookie for use with InnerTube
 * and NewPipe (the same approach Metrolist/InnerTune/OuterTune use). No credentials ever pass
 * through app code; Google's page handles the login itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginComplete: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }

    var signedOut by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf<String?>(null) }
    var accountAvatar by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    var checkDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cookie = playerPreferences.youtubeCookie.first()
        loggedIn = !cookie.isNullOrEmpty()
        if (loggedIn) {
            accountName = playerPreferences.youtubeAccountName.first()
            accountAvatar = playerPreferences.youtubeAccountThumbnail.first()
        }
        checkDone = true
    }

    if (!checkDone) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (loggedIn && !signedOut) {
        AccountPanel(
            name = accountName,
            avatarUrl = accountAvatar,
            onSignOut = {
                coroutineScope.launch {
                    playerPreferences.clearYoutubeAccount()
                    YouTube.cookie = null
                    YouTube.useLoginForBrowse = false
                }
                signedOut = true
            },
            onNavigateBack = onNavigateBack,
        )
        return
    }

    var isLoading by remember { mutableStateOf(true) }
    var isFinishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun handleCookiesCaptured(cookies: String) {
        if (isFinishing) return
        isFinishing = true
        errorMessage = null
        YouTube.cookie = cookies
        YouTube.useLoginForBrowse = true
        coroutineScope.launch {
            YouTube.accountInfo()
                .onSuccess { account ->
                    playerPreferences.setYoutubeAccount(
                        cookie = cookies,
                        name = account.name,
                        email = account.email,
                        thumbnailUrl = account.thumbnailUrl
                    )
                    val appContext = context.applicationContext
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        runCatching { HomeFeedCacheRepository(appContext).clearAll() }
                        runCatching { YouTubeLibrarySync.sync(appContext) }
                    }
                    onLoginComplete()
                }
                .onFailure {
                    YouTube.cookie = null
                    YouTube.useLoginForBrowse = false
                    errorMessage = context.getString(R.string.settings_google_login_failed)
                    isFinishing = false
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                val currentUrl = url.orEmpty()
                                if (currentUrl.startsWith("https://music.youtube.com") ||
                                    currentUrl.startsWith("https://www.youtube.com")
                                ) {
                                    val cookies = cookieManager.getCookie(currentUrl)
                                    if (!cookies.isNullOrEmpty() && cookies.contains("SAPISID")) {
                                        handleCookiesCaptured(cookies)
                                    }
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl(GOOGLE_LOGIN_URL)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.webViewClient = WebViewClient()
                    view.destroy()
                }
            )

            if (isLoading || isFinishing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = {
                        errorMessage = null
                        webViewRef?.reload()
                    }) {
                        Text(stringResource(R.string.settings_google_login_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(
    name: String?,
    avatarUrl: String?,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.account_menu_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (name ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = name ?: stringResource(R.string.login_signed_in),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.logout_button))
            }
        }
    }
}

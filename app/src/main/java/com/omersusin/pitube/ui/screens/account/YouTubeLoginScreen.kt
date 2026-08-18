package com.omersusin.pitube.ui.screens.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.HomeFeedCacheRepository
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.YouTubeAuthUtils
import com.omersusin.pitube.data.local.YouTubeLibrarySync
import com.omersusin.pitube.data.local.YouTubeTokenParser
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

private enum class LoginMode { TOKEN, WEBVIEW }

/** Cookies surfaced as detection chips once something is pasted. */
private val REPORTED_COOKIES = listOf("SAPISID", "SID")

/**
 * Signs the user into their real Google account.
 *
 * Two paths, both producing the same stored session:
 *
 * 1. **Paste token / cookie** (default, always visible): a multi-line input
 *    that accepts the OuterTune/ViVi-style `***DATASYNC ID*** =…` token or a
 *    bare `Cookie` header, with a paste-from-clipboard action and live
 *    validation. This is the reliable path — Google blocks embedded-browser
 *    WebView logins, so it is the primary one.
 * 2. **WebView login** (secondary tab): the classic Google sign-in page; kept
 *    for accounts that still complete it, with the same capture pipeline.
 *
 * When [forceNewLogin] is set (the "Add account" flow), the current session is
 * cleared first so Google presents a fresh sign-in and the new account is added
 * to the roster without touching any already-stored account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    forceNewLogin: Boolean = false,
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

    if (loggedIn && !signedOut && !forceNewLogin) {
        AccountPanel(
            name = accountName,
            avatarUrl = accountAvatar,
            onSignOut = {
                coroutineScope.launch {
                    val appContext = context.applicationContext
                    val accountSwitcher = com.omersusin.pitube.data.local.AccountSwitcher(appContext)
                    accountSwitcher.signOut(accountSwitcher.active().id)
                    playerPreferences.clearYoutubeAccount()
                }
                signedOut = true
            },
            onNavigateBack = onNavigateBack,
        )
        return
    }

    var loginMode by remember { mutableIntStateOf(LoginMode.TOKEN.ordinal) }
    var isFinishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var tokenInput by remember { mutableStateOf("") }

    fun handleCookiesCaptured(cookies: String) {
        if (isFinishing) return
        isFinishing = true
        errorMessage = null
        YouTube.cookie = cookies
        YouTube.useLoginForBrowse = true
        coroutineScope.launch {
            // Fetch identity first when possible: it lets ProfileManager dedupe a
            // re-added account via datasyncId instead of leaving a duplicate row.
            // Best-effort only - accountInfo can transiently fail or rate-limit
            // and must never bounce the user back out of a successful sign-in.
            val identity = runCatching {
                com.omersusin.pitube.innertube.YouTube.accountInfo().getOrNull()
            }.getOrNull()
            val appContext = context.applicationContext
            val accountSwitcher = com.omersusin.pitube.data.local.AccountSwitcher(appContext)
            accountSwitcher.addYouTubeProfileAndSwitch(
                cookies = cookies,
                name = identity?.name,
                handle = identity?.channelHandle,
                email = identity?.email,
                avatarUrl = identity?.thumbnailUrl,
                datasyncId = identity?.datasyncId
            )
            playerPreferences.setYoutubeAccount(cookie = cookies, name = null, email = null, thumbnailUrl = null)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { HomeFeedCacheRepository(appContext).clearAll() }
                runCatching { YouTubeLibrarySync.sync(appContext) }
                // Retry the profile refetch in case the attempt above failed, and
                // stash the identity on the active profile.
                runCatching {
                    val account = YouTube.accountInfo().getOrNull()
                    if (account != null) {
                        playerPreferences.updateYoutubeAccountInfo(
                            account.name,
                            account.email,
                            account.thumbnailUrl
                        )
                        val sm = com.omersusin.pitube.data.local.SessionManager(appContext)
                        sm.saveUserName(account.name)
                        sm.saveUserEmail(account.email)
                        sm.saveUserAvatar(account.thumbnailUrl ?: "")
                        // Persist any identity details the first fetch missed, so
                        // the profile row shows the handle and the runtime session
                        // picks up the datasyncId (onBehalfOfUser on signed writes).
                        val pm = com.omersusin.pitube.data.local.ProfileManager(appContext)
                        val profile = pm.active()
                        var needDatasync = false
                        if (!account.channelHandle.isNullOrBlank() && profile.handle.isNullOrBlank()) {
                            pm.updateIdentity(profile.id, handle = account.channelHandle)
                        }
                        if (!account.datasyncId.isNullOrBlank() && profile.datasyncId.isNullOrBlank()) {
                            pm.updateIdentity(profile.id, datasyncId = account.datasyncId)
                            needDatasync = true
                        }
                        if (needDatasync) {
                            com.omersusin.pitube.innertube.YouTube.dataSyncId = account.datasyncId
                        }
                    }
                }
            }
            onLoginComplete()
        }
    }

    fun submitToken() {
        if (isFinishing) return
        val token = YouTubeTokenParser.parse(tokenInput)
        val normalized = YouTubeAuthUtils.normalizeCookieString(token.cookie)
        // Full-session bar: a captured jar missing the secure TS pair or any of
        // the auth cookies will silently answer signed-out once browsing, so it
        // is rejected here with the missing names shown to the user.
        val missing = com.omersusin.pitube.data.local.CookieRotation.missingRequiredCookies(normalized)
        if (missing.isNotEmpty()) {
            errorMessage = context.getString(R.string.cookie_paste_missing, missing.joinToString(", "))
            return
        }
        isFinishing = true
        errorMessage = null
        YouTube.cookie = normalized
        YouTube.useLoginForBrowse = true
        token.visitorData?.takeIf { it.isNotBlank() }?.let { YouTube.visitorData = it }
        token.dataSyncId?.takeIf { it.isNotBlank() }?.let { YouTube.dataSyncId = it }
        coroutineScope.launch {
            // Best-effort identity fetch for dedupe and avatar (never blocks the
            // sign-in); token markers are authoritative when present.
            val identity = runCatching {
                com.omersusin.pitube.innertube.YouTube.accountInfo().getOrNull()
            }.getOrNull()
            val appContext = context.applicationContext
            val accountSwitcher = com.omersusin.pitube.data.local.AccountSwitcher(appContext)
            accountSwitcher.addYouTubeProfileAndSwitch(
                cookies = normalized,
                name = token.accountName ?: identity?.name,
                handle = token.accountChannelHandle ?: identity?.channelHandle,
                email = token.accountEmail ?: identity?.email,
                avatarUrl = identity?.thumbnailUrl,
                datasyncId = token.dataSyncId ?: identity?.datasyncId,
                poToken = token.poToken
            )
            playerPreferences.setYoutubeAccount(cookie = normalized, name = null, email = null, thumbnailUrl = null)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { HomeFeedCacheRepository(appContext).clearAll() }
                runCatching { YouTubeLibrarySync.sync(appContext) }
                runCatching {
                    val account = YouTube.accountInfo().getOrNull()
                    if (account != null) {
                        playerPreferences.updateYoutubeAccountInfo(
                            account.name,
                            account.email,
                            account.thumbnailUrl
                        )
                        val sm = com.omersusin.pitube.data.local.SessionManager(appContext)
                        sm.saveUserName(account.name)
                        sm.saveUserEmail(account.email)
                        sm.saveUserAvatar(account.thumbnailUrl ?: "")
                        // Persist any identity details the first fetch missed, so
                        // the profile row shows the handle and the runtime session
                        // picks up the datasyncId (onBehalfOfUser on signed writes).
                        val pm = com.omersusin.pitube.data.local.ProfileManager(appContext)
                        val profile = pm.active()
                        var needDatasync = false
                        if (!account.channelHandle.isNullOrBlank() && profile.handle.isNullOrBlank()) {
                            pm.updateIdentity(profile.id, handle = account.channelHandle)
                        }
                        if (!account.datasyncId.isNullOrBlank() && profile.datasyncId.isNullOrBlank()) {
                            pm.updateIdentity(profile.id, datasyncId = account.datasyncId)
                            needDatasync = true
                        }
                        if (needDatasync) {
                            com.omersusin.pitube.innertube.YouTube.dataSyncId = account.datasyncId
                        }
                    }
                }
            }
            onLoginComplete()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            PrimaryTabRow(selectedTabIndex = loginMode) {
                Tab(
                    selected = loginMode == LoginMode.TOKEN.ordinal,
                    onClick = { loginMode = LoginMode.TOKEN.ordinal },
                    text = { Text(stringResource(R.string.login_tab_token)) }
                )
                Tab(
                    selected = loginMode == LoginMode.WEBVIEW.ordinal,
                    onClick = { loginMode = LoginMode.WEBVIEW.ordinal },
                    text = { Text(stringResource(R.string.login_tab_webview)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            when (loginMode) {
                LoginMode.TOKEN.ordinal -> TokenLoginPane(
                    input = tokenInput,
                    onInputChange = { tokenInput = it },
                    onPaste = {
                        val clipboard =
                            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                            ?.let { tokenInput = it }
                    },
                    onSubmit = { submitToken() },
                    enabled = !isFinishing,
                    isFinishing = isFinishing
                )
                else -> WebViewLoginPane(
                    isLoading = isFinishing,
                    errorMessage = errorMessage,
                    forceNewLogin = forceNewLogin,
                    onRetry = {
                        errorMessage = null
                        webViewRef?.reload()
                    },
                    onCookiesCaptured = ::handleCookiesCaptured,
                    onWebViewReady = { webViewRef = it }
                )
            }

            errorMessage?.let { message ->
                if (loginMode == LoginMode.TOKEN.ordinal) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Helper text lives inside the token tab only - the webview tab has
            // nothing to paste, so it shows no prompts, warnings or red alerts.
            if (loginMode == LoginMode.TOKEN.ordinal) {
                TokenHelpSection()
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TokenLoginPane(
    input: String,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    isFinishing: Boolean,
) {
    val token = remember(input) { YouTubeTokenParser.parse(input) }
    val normalized = YouTubeAuthUtils.normalizeCookieString(token.cookie)
    val missing = YouTubeAuthUtils.missingRequiredCookies(normalized)
    val hasInput = input.isNotBlank()
    val isValid = hasInput && missing.isEmpty()

    Column {
        Text(
            text = stringResource(R.string.login_token_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onPaste,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.cookie_paste_clipboard),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            label = { Text(stringResource(R.string.login_token_field_label)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.login_token_field_hint),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            isError = hasInput && !isValid,
            enabled = !isFinishing,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        AnimatedVisibility(
            visible = hasInput,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REPORTED_COOKIES.forEach { name ->
                        CookieChip(
                            name = name,
                            present = YouTubeAuthUtils.getCookieValue(normalized, name) != null
                        )
                    }
                }
                if (!isValid) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            R.string.cookie_paste_missing,
                            missing.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSubmit,
            enabled = isValid && !isFinishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isFinishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cookie_paste_sign_in),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun WebViewLoginPane(
    isLoading: Boolean,
    errorMessage: String?,
    forceNewLogin: Boolean,
    onRetry: () -> Unit,
    onCookiesCaptured: (String) -> Unit,
    onWebViewReady: (WebView?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                    // "Add account" from the switcher: drop the current session
                    // cookie so Google presents a fresh sign-in (it would otherwise
                    // detect the existing cookie and silently reuse it).
                    if (forceNewLogin && com.omersusin.pitube.innertube.YouTube.cookie?.isNotBlank() == true) {
                        cookieManager.removeAllCookies(null)
                        com.omersusin.pitube.innertube.YouTube.cookie = null
                        com.omersusin.pitube.innertube.YouTube.useLoginForBrowse = false
                        com.omersusin.pitube.innertube.YouTube.dataSyncId = null
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            val currentUrl = url.orEmpty()
                            if (currentUrl.startsWith("https://music.youtube.com") ||
                                currentUrl.startsWith("https://www.youtube.com")
                            ) {
                                val cookies = cookieManager.getCookie(currentUrl)
                                if (!cookies.isNullOrEmpty() && cookies.contains("SAPISID")) {
                                    onCookiesCaptured(cookies)
                                }
                            }
                        }
                    }
                    onWebViewReady(this)
                    loadUrl(GOOGLE_LOGIN_URL)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.webViewClient = WebViewClient()
                view.destroy()
                onWebViewReady(null)
            }
        )

        if (isLoading) {
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
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.settings_google_login_retry))
                }
            }
        }
    }
}

@Composable
private fun CookieChip(name: String, present: Boolean) {
    val background = if (present) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val foreground = if (present) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (present) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = foreground
            )
        }
    }
}

@Composable
private fun TokenHelpSection() {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chevron"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.cookie_paste_how),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    )
                ) {
                    val steps = listOf(
                        stringResource(R.string.cookie_paste_step_1),
                        stringResource(R.string.cookie_paste_step_2),
                        stringResource(R.string.cookie_paste_step_3),
                        stringResource(R.string.cookie_paste_step_4),
                        stringResource(R.string.cookie_paste_step_5),
                        stringResource(R.string.cookie_paste_step_6),
                    )
                    steps.forEachIndexed { index, step ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.cookie_paste_security_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cookie_paste_security_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.logout_button))
            }
        }
    }
}

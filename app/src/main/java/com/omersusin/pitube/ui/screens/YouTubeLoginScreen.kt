package com.omersusin.pitube.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.ProfileKind
import com.omersusin.pitube.data.ProfileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loginAttempted by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val profileManager = remember { ProfileManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { w ->
                w.stopLoading()
                w.destroy()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("YouTube Login") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        webView = this
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false

                                // Check for YouTube cookies on multiple possible domains
                                val cookieManager = CookieManager.getInstance()
                                val domains = listOf(
                                    "https://www.youtube.com",
                                    "https://youtube.com",
                                    "https://accounts.google.com",
                                    url
                                ).filterNotNull()

                                for (domain in domains) {
                                    val raw = cookieManager.getCookie(domain)
                                    if (raw != null && raw.contains("SID=") && raw.contains("__Secure-1PSID")) {
                                        AuthManager.saveRawCookies(ctx, raw)
                                        val cookieMap = mutableMapOf<String, String>()
                                        raw.split(";").forEach { cookie ->
                                            val parts = cookie.trim().split("=", limit = 2)
                                            if (parts.size == 2) cookieMap[parts[0].trim()] = parts[1].trim()
                                        }
                                        AuthManager.saveCookies(ctx, cookieMap)
                                        val activeProfile = profileManager.active()
                                        if (activeProfile.kind == ProfileKind.LOCAL) {
                                            profileManager.addYouTubeProfile(cookies = raw)
                                        } else {
                                            profileManager.saveCookiesFor(activeProfile.id, raw)
                                        }
                                        onBack()
                                        return
                                    }
                                }

                                // Also check if we're on YouTube and have basic auth cookies
                                if (url?.contains("youtube.com") == true && !loginAttempted) {
                                    loginAttempted = true
                                    val cookieString = cookieManager.getCookie("youtube.com") ?: ""
                                    if (cookieString.contains("SID=")) {
                                        AuthManager.saveRawCookies(ctx, cookieString)
                                        val cookieMap = mutableMapOf<String, String>()
                                        cookieString.split(";").forEach { cookie ->
                                            val parts = cookie.trim().split("=", limit = 2)
                                            if (parts.size == 2) cookieMap[parts[0].trim()] = parts[1].trim()
                                        }
                                        AuthManager.saveCookies(ctx, cookieMap)
                                        val activeProfile = profileManager.active()
                                        if (activeProfile.kind == ProfileKind.LOCAL) {
                                            profileManager.addYouTubeProfile(cookies = cookieString)
                                        } else {
                                            profileManager.saveCookiesFor(activeProfile.id, cookieString)
                                        }
                                        onBack()
                                    }
                                }
                            }
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3D%252F&hl=en")
                    }
                }
            )
        }
    }
}

package com.omersusin.pitube.ui.screens

import android.util.Log
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
import com.omersusin.pitube.data.KodaAuth
import com.omersusin.pitube.data.ProfileKind
import com.omersusin.pitube.data.ProfileManager

private const val TAG = "YouTubeLoginScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
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
                        webView = this
                        webViewClient = object : WebViewClient() {
                            private var completed = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (completed) return

                                Log.d(TAG, "onPageFinished url=$url")

                                // Check cookies from music.youtube.com (Koda's approach)
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://music.youtube.com")
                                    ?: cookieManager.getCookie("https://www.youtube.com")

                                if (cookies != null) {
                                    val missing = KodaAuth.missingRequired(cookies)
                                    Log.d(TAG, "Cookies found, missing=$missing")
                                    if (missing.isEmpty()) {
                                        completed = true
                                        val normalized = KodaAuth.normalize(cookies)
                                        Log.d(TAG, "Login success, saving cookies (${normalized.length} chars)")

                                        // Save to AuthManager
                                        AuthManager.saveRawCookies(ctx, normalized)
                                        val cookieMap = mutableMapOf<String, String>()
                                        normalized.split(";").forEach { cookie ->
                                            val parts = cookie.trim().split("=", limit = 2)
                                            if (parts.size == 2) cookieMap[parts[0].trim()] = parts[1].trim()
                                        }
                                        AuthManager.saveCookies(ctx, cookieMap)

                                        // Save to ProfileManager
                                        val activeProfile = profileManager.active()
                                        if (activeProfile.kind == ProfileKind.LOCAL) {
                                            profileManager.addYouTubeProfile(cookies = normalized)
                                        } else {
                                            profileManager.saveCookiesFor(activeProfile.id, normalized)
                                        }

                                        // Flush WebView cookies
                                        CookieManager.getInstance().flush()

                                        onBack()
                                    }
                                }
                            }
                        }
                        // Use service=youtube like Koda does
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com")
                    }
                }
            )
        }
    }
}

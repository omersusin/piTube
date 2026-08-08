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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("YouTube Login") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (url?.contains("youtube.com") == true) {
                                    val cookieManager = CookieManager.getInstance()
                                    val raw = cookieManager.getCookie("https://www.youtube.com") ?: cookieManager.getCookie(url)
                                    if (raw != null && raw.contains("SID=")) {
                                        AuthManager.saveRawCookies(ctx, raw)
                                        val cookieMap = mutableMapOf<String, String>()
                                        raw.split(";").forEach { cookie ->
                                            val parts = cookie.trim().split("=")
                                            if (parts.size >= 2) cookieMap[parts[0]] = parts.drop(1).joinToString("=")
                                        }
                                        AuthManager.saveCookies(ctx, cookieMap)
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

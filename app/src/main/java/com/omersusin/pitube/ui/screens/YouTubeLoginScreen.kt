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
    var showCookiePaste by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
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
                                
                                // Check if we are on YouTube and logged in
                                if (url?.contains("youtube.com") == true) {
                                    val cookieManager = CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie(url)
                                    if (cookies != null && cookies.contains("SID=")) {
                                        // Extract specific cookies
                                        val cookieMap = mutableMapOf<String, String>()
                                        cookies.split(";").forEach { cookie ->
                                            val parts = cookie.trim().split("=")
                                            if (parts.size == 2) {
                                                val key = parts[0]
                                                val value = parts[1]
                                                if (key in listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "LOGIN_INFO", "PREF")) {
                                                    cookieMap[key] = value
                                                }
                                            }
                                        }
                                        if (cookieMap.isNotEmpty()) {
                                            AuthManager.saveCookies(ctx, cookieMap)
                                            // Navigate back to settings
                                            onBack() 
                                        }
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

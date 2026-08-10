package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.screens.account.YouTubeLoginScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val playerPreferences = PlayerPreferences(LocalContext.current)

    var showLogin by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    var checkDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loggedIn = !playerPreferences.youtubeCookie.first().isNullOrEmpty()
        checkDone = true
    }

    fun finish() {
        scope.launch {
            playerPreferences.setOnboardingComplete()
            onComplete()
        }
    }

    if (!checkDone) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (showLogin) {
        YouTubeLoginScreen(
            onLoginComplete = { finish() },
            onNavigateBack = { showLogin = false }
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.welcome_to_flow),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            if (loggedIn) {
                Button(
                    onClick = { finish() },
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(stringResource(R.string.onboarding_btn_continue))
                }
            } else {
                Button(
                    onClick = { showLogin = true },
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(stringResource(R.string.settings_google_login_title))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.onboarding_btn_skip))
            }
        }
    }
}

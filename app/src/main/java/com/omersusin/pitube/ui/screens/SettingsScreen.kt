package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.PrefsManager
import com.omersusin.pitube.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit
) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(AuthManager.isLoggedIn(context)) }
    var sponsorBlock by remember { mutableStateOf(PrefsManager.isSponsorBlockEnabled(context)) }
    var zenMode by remember { mutableStateOf(PrefsManager.isZenMode(context)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoggedIn) {
            Text("Logged in to YouTube", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                AuthManager.logout(context)
                isLoggedIn = false
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Logout")
            }
        } else {
            Text("Not logged in", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenLogin) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Playback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SponsorBlock (skip sponsors)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = sponsorBlock, onCheckedChange = {
                sponsorBlock = it
                PrefsManager.setSponsorBlockEnabled(context, it)
            })
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Wellbeing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Zen Mode", style = MaterialTheme.typography.bodyLarge)
                Text("Home shows only subscriptions, hides related videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = zenMode, onCheckedChange = {
                zenMode = it
                PrefsManager.setZenMode(context, it)
            })
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = currentTheme == ThemeMode.SYSTEM, onClick = { onThemeChange(ThemeMode.SYSTEM) })
            Text("System", modifier = Modifier.clickable { onThemeChange(ThemeMode.SYSTEM) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = currentTheme == ThemeMode.LIGHT, onClick = { onThemeChange(ThemeMode.LIGHT) })
            Text("Light", modifier = Modifier.clickable { onThemeChange(ThemeMode.LIGHT) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = currentTheme == ThemeMode.DARK, onClick = { onThemeChange(ThemeMode.DARK) })
            Text("Dark", modifier = Modifier.clickable { onThemeChange(ThemeMode.DARK) })
        }
    }
}

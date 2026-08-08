package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.KodaAuth
import com.omersusin.pitube.data.NotInterested
import com.omersusin.pitube.data.PrefsManager
import com.omersusin.pitube.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSubsMgmt: () -> Unit,
    onOpenNotInterested: () -> Unit,
    account: AccountFetcher.AccountInfo?
) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(AuthManager.isLoggedIn(context)) }
    var sponsorBlock by remember { mutableStateOf(PrefsManager.isSponsorBlockEnabled(context)) }
    var zenMode by remember { mutableStateOf(PrefsManager.isZenMode(context)) }
    var hideShorts by remember { mutableStateOf(PrefsManager.isHideShorts(context)) }
    var hideCounters by remember { mutableStateOf(PrefsManager.isHideCounters(context)) }
    var hideComments by remember { mutableStateOf(PrefsManager.isHideComments(context)) }
    var autoExpand by remember { mutableStateOf(PrefsManager.isAutoExpandDesc(context)) }
    var hideLikes by remember { mutableStateOf(PrefsManager.isHideLikeButtons(context)) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieText by remember { mutableStateOf("") }
    var cookieError by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }; Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoggedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    val url = account?.avatarUrl
                    if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Text((account?.name?.firstOrNull() ?: 'U').toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column { Text(account?.name ?: "Logged in to YouTube", style = MaterialTheme.typography.bodyLarge); Text("Google account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { AuthManager.logout(context); AccountFetcher.clearCache(context); isLoggedIn = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Logout") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { showCookieDialog = true }) { Text("Paste cookies") }
            }
        } else {
            Text("Not logged in", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onOpenLogin) { Icon(Icons.Default.AccountCircle, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Sign in with Google") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { showCookieDialog = true }) { Text("Paste cookies") }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenDownloads() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Download, contentDescription = null); Spacer(modifier = Modifier.width(12.dp)); Text("Downloads", style = MaterialTheme.typography.bodyLarge) }
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenHistory() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, contentDescription = null); Spacer(modifier = Modifier.width(12.dp)); Text("Watch History", style = MaterialTheme.typography.bodyLarge) }
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenStats() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Analytics, contentDescription = null); Spacer(modifier = Modifier.width(12.dp)); Text("Statistics", style = MaterialTheme.typography.bodyLarge) }
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenSubsMgmt() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.People, contentDescription = null); Spacer(modifier = Modifier.width(12.dp)); Text("Manage Subscriptions", style = MaterialTheme.typography.bodyLarge) }
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpenNotInterested() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.VisibilityOff, contentDescription = null); Spacer(modifier = Modifier.width(12.dp)); Text("Not Interested", style = MaterialTheme.typography.bodyLarge) }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Playback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("SponsorBlock", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = sponsorBlock, onCheckedChange = { sponsorBlock = it; PrefsManager.setSponsorBlockEnabled(context, it) }) }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Wellbeing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("Zen Mode", style = MaterialTheme.typography.bodyLarge); Text("Home shows only subscriptions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = zenMode, onCheckedChange = { zenMode = it; PrefsManager.setZenMode(context, it) }) }
        Spacer(modifier = Modifier.height(24.dp))

        Text("UI Customization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Hide Shorts", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = hideShorts, onCheckedChange = { hideShorts = it; PrefsManager.setHideShorts(context, it) }) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Hide view/like counters", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = hideCounters, onCheckedChange = { hideCounters = it; PrefsManager.setHideCounters(context, it) }) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Hide like/dislike buttons", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = hideLikes, onCheckedChange = { hideLikes = it; PrefsManager.setHideLikeButtons(context, it) }) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Hide comments section", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = hideComments, onCheckedChange = { hideComments = it; PrefsManager.setHideComments(context, it) }) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Auto-expand description", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(checked = autoExpand, onCheckedChange = { autoExpand = it; PrefsManager.setAutoExpandDesc(context, it) }) }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentTheme == ThemeMode.SYSTEM, onClick = { onThemeChange(ThemeMode.SYSTEM) }); Text("System", modifier = Modifier.clickable { onThemeChange(ThemeMode.SYSTEM) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentTheme == ThemeMode.LIGHT, onClick = { onThemeChange(ThemeMode.LIGHT) }); Text("Light", modifier = Modifier.clickable { onThemeChange(ThemeMode.LIGHT) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentTheme == ThemeMode.DARK, onClick = { onThemeChange(ThemeMode.DARK) }); Text("Dark", modifier = Modifier.clickable { onThemeChange(ThemeMode.DARK) }) }
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("Paste YouTube cookies") },
            text = { Column { Text("On your PC: open youtube.com → F12 → Network → copy the Cookie header value.", style = MaterialTheme.typography.bodySmall); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = cookieText, onValueChange = { cookieText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3); if (cookieError.isNotBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(cookieError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } },
            confirmButton = { Button(onClick = {
                val normalized = KodaAuth.normalize(cookieText)
                val missing = KodaAuth.missingRequired(normalized)
                if (missing.isNotEmpty()) { cookieError = "Missing required cookies: ${missing.joinToString(", ")}" } else {
                    AuthManager.saveRawCookies(context, normalized)
                    isLoggedIn = true
                    AccountFetcher.clearCache(context)
                    showCookieDialog = false
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showCookieDialog = false }) { Text("Cancel") } }
        )
    }
}

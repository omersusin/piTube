package com.omersusin.pitube.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.AccountFetcher
import com.omersusin.pitube.data.AuthManager
import com.omersusin.pitube.data.KodaAuth
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
    BackHandler { onBack() }
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
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Section
            item {
                SectionHeader("Account")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isLoggedIn) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    val url = account?.avatarUrl
                                    if (url != null) AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    else Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account?.name ?: "Logged in",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Google Account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        AuthManager.logout(context)
                                        AccountFetcher.clearCache(context)
                                        isLoggedIn = false
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Logout") }
                                OutlinedButton(onClick = { showCookieDialog = true }) {
                                    Text("Paste Cookies")
                                }
                            }
                        } else {
                            Column {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Not signed in", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onOpenLogin) {
                                        Icon(Icons.Default.Login, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign in")
                                    }
                                    OutlinedButton(onClick = { showCookieDialog = true }) {
                                        Text("Paste Cookies")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Library Section
            item {
                SectionHeader("Library")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsNavItem(
                            icon = Icons.Default.Download,
                            label = "Downloads",
                            onClick = onOpenDownloads
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavItem(
                            icon = Icons.Default.History,
                            label = "Watch History",
                            onClick = onOpenHistory
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavItem(
                            icon = Icons.Default.BarChart,
                            label = "Statistics",
                            onClick = onOpenStats
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavItem(
                            icon = Icons.Default.Subscriptions,
                            label = "Manage Subscriptions",
                            onClick = onOpenSubsMgmt
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavItem(
                            icon = Icons.Default.VisibilityOff,
                            label = "Not Interested",
                            onClick = onOpenNotInterested
                        )
                    }
                }
            }

            // Playback Section
            item {
                SectionHeader("Playback")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SettingsToggleItem(
                        icon = Icons.Default.Block,
                        label = "SponsorBlock",
                        description = "Skip sponsor segments",
                        checked = sponsorBlock,
                        onCheckedChange = {
                            sponsorBlock = it
                            PrefsManager.setSponsorBlockEnabled(context, it)
                        }
                    )
                }
            }

            // Wellbeing Section
            item {
                SectionHeader("Wellbeing")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SettingsToggleItem(
                        icon = Icons.Default.SelfImprovement,
                        label = "Zen Mode",
                        description = "Home shows only subscriptions",
                        checked = zenMode,
                        onCheckedChange = {
                            zenMode = it
                            PrefsManager.setZenMode(context, it)
                        }
                    )
                }
            }

            // UI Customization Section
            item {
                SectionHeader("UI Customization")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsToggleItem(
                            icon = Icons.Default.ShortText,
                            label = "Hide Shorts",
                            checked = hideShorts,
                            onCheckedChange = {
                                hideShorts = it
                                PrefsManager.setHideShorts(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleItem(
                            icon = Icons.Default.VisibilityOff,
                            label = "Hide Counters",
                            description = "Hide view/like counts",
                            checked = hideCounters,
                            onCheckedChange = {
                                hideCounters = it
                                PrefsManager.setHideCounters(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleItem(
                            icon = Icons.Default.ThumbUp,
                            label = "Hide Like Buttons",
                            checked = hideLikes,
                            onCheckedChange = {
                                hideLikes = it
                                PrefsManager.setHideLikeButtons(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleItem(
                            icon = Icons.Default.Comment,
                            label = "Hide Comments",
                            checked = hideComments,
                            onCheckedChange = {
                                hideComments = it
                                PrefsManager.setHideComments(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleItem(
                            icon = Icons.Default.UnfoldMore,
                            label = "Auto-expand Description",
                            checked = autoExpand,
                            onCheckedChange = {
                                autoExpand = it
                                PrefsManager.setAutoExpandDesc(context, it)
                            }
                        )
                    }
                }
            }

            // Appearance Section
            item {
                SectionHeader("Appearance")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showThemeDialog = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Theme", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                currentTheme.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = ThemeMode.entries.chunked(3)
                    items(rows.size) { rowIndex ->
                        val row = rows[rowIndex]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { mode ->
                                val isSelected = currentTheme == mode
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.2f)
                                        .clickable {
                                            onThemeChange(mode)
                                            showThemeDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.name.replace("_", "\n"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            // Fill empty spaces
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Cookie Dialog
    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("Paste YouTube Cookies") },
            text = {
                Column {
                    Text(
                        "On your PC:\n1. Open youtube.com\n2. Press F12 → Network tab\n3. Copy the Cookie header value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Cookie value...") }
                    )
                    if (cookieError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            cookieError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val normalized = KodaAuth.normalize(cookieText)
                    val missing = KodaAuth.missingRequired(normalized)
                    if (missing.isNotEmpty()) {
                        cookieError = "Missing: ${missing.joinToString(", ")}"
                    } else {
                        AuthManager.saveRawCookies(context, normalized)
                        isLoggedIn = true
                        AccountFetcher.clearCache(context)
                        showCookieDialog = false
                        cookieText = ""
                        cookieError = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCookieDialog = false
                    cookieText = ""
                    cookieError = ""
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingsNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

package com.omersusin.pitube.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.BuildConfig
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.notification.BackgroundWorkPolicy
import com.omersusin.pitube.notification.UpdateCheckWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }

    val notificationsEnabled by prefs.notificationsEnabled.collectAsState(initial = true)
    val notifDownloads by prefs.notifDownloadsEnabled.collectAsState(initial = true)
    val notifReminders by prefs.notifRemindersEnabled.collectAsState(initial = true)
    val notifUpdates by prefs.notifUpdatesEnabled.collectAsState(initial = true)
    val notifGeneral by prefs.notifGeneralEnabled.collectAsState(initial = true)
    val notifActionLike by prefs.notificationActionLike.collectAsState(initial = false)
    val notifActionDislike by prefs.notificationActionDislike.collectAsState(initial = false)
    val notifActionRadio by prefs.notificationActionRadio.collectAsState(initial = false)

    var backgroundWorkAllowed by remember {
        mutableStateOf(BackgroundWorkPolicy.isBackgroundWorkUnrestricted(context))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.notif_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.NotificationsOff,
                        title = stringResource(R.string.notif_master_toggle),
                        subtitle = stringResource(R.string.notif_master_toggle_subtitle),
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                prefs.setNotificationsEnabled(enabled)
                                if (enabled) {
                                    if (BuildConfig.UPDATER_ENABLED) {
                                        UpdateCheckWorker.schedulePeriodicCheck(context, reschedule = true)
                                    }
                                    if (!backgroundWorkAllowed) {
                                        BackgroundWorkPolicy.requestUnrestrictedBackgroundWork(context)
                                    }
                                } else {
                                    UpdateCheckWorker.cancelScheduledChecks(context)
                                }
                            }
                        },
                    )
                    if (notificationsEnabled && !backgroundWorkAllowed) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsItem(
                            icon = Icons.Outlined.BatteryAlert,
                            title = stringResource(R.string.notif_background_restricted_title),
                            subtitle = stringResource(R.string.notif_background_restricted_subtitle),
                            onClick = { BackgroundWorkPolicy.requestUnrestrictedBackgroundWork(context) },
                        )
                    }
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.notif_settings_playback_buttons_header))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ThumbUp,
                        title = stringResource(R.string.notif_button_like),
                        subtitle = stringResource(R.string.notif_button_like_subtitle),
                        checked = notifActionLike,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { prefs.setNotificationActionLike(enabled) }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ThumbDown,
                        title = stringResource(R.string.notif_button_dislike),
                        subtitle = stringResource(R.string.notif_button_dislike_subtitle),
                        checked = notifActionDislike,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { prefs.setNotificationActionDislike(enabled) }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Radio,
                        title = stringResource(R.string.notif_button_radio),
                        subtitle = stringResource(R.string.notif_button_radio_subtitle),
                        checked = notifActionRadio,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { prefs.setNotificationActionRadio(enabled) }
                        },
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.notif_type_downloads),
                        subtitle = stringResource(R.string.notif_type_downloads_subtitle),
                        checked = notifDownloads,
                        enabled = notificationsEnabled,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifDownloadsEnabled(it) } },
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Bedtime,
                        title = stringResource(R.string.notif_type_reminders),
                        subtitle = stringResource(R.string.notif_type_reminders_subtitle),
                        checked = notifReminders,
                        enabled = notificationsEnabled,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifRemindersEnabled(it) } },
                    )
                    if (BuildConfig.UPDATER_ENABLED) {
                        HorizontalDivider(
                            Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Update,
                            title = stringResource(R.string.notif_type_updates),
                            subtitle = stringResource(R.string.notif_type_updates_subtitle),
                            checked = notifUpdates,
                            enabled = notificationsEnabled,
                            onCheckedChange = { coroutineScope.launch { prefs.setNotifUpdatesEnabled(it) } },
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notif_type_general),
                        subtitle = stringResource(R.string.notif_type_general_subtitle),
                        checked = notifGeneral,
                        enabled = notificationsEnabled,
                        onCheckedChange = { coroutineScope.launch { prefs.setNotifGeneralEnabled(it) } },
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = stringResource(R.string.notif_system_settings),
                        subtitle = stringResource(R.string.notif_system_settings_subtitle),
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

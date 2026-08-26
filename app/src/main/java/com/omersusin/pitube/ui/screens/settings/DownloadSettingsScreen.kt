package com.omersusin.pitube.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.VideoQuality
import com.omersusin.pitube.data.local.VideoCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private enum class DownloadLocationTarget {
    VIDEO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    val maxDialogHeight = (configuration.screenHeightDp.dp - 48.dp).coerceAtMost(560.dp)
    val maxDialogListHeight = (configuration.screenHeightDp.dp * 0.55f).coerceAtMost(360.dp)
    
    val parallelEnabled by preferences.parallelDownloadEnabled.collectAsState(initial = true)
    val threadCount by preferences.downloadThreads.collectAsState(initial = 3)
    val wifiOnly by preferences.downloadOverWifiOnly.collectAsState(initial = false)
    val defaultQuality by preferences.defaultDownloadQuality.collectAsState(initial = VideoQuality.Q_720p)
    val defaultCodec by preferences.defaultDownloadCodec.collectAsState(initial = VideoCodec.AUTO)
    val downloadLocation by preferences.downloadLocation.collectAsState(initial = null)
    var videoTemplate by remember { mutableStateOf("") }
    var audioTemplate by remember { mutableStateOf("") }
    var subtitleLanguage by remember { mutableStateOf("") }
    val videoFolder by preferences.downloadVideoFolder.collectAsState(initial = "")
    val audioFolder by preferences.downloadAudioFolder.collectAsState(initial = "")
    val writeSubtitles by preferences.downloadWriteSubtitles.collectAsState(initial = false)
    val autoSubtitles by preferences.downloadAutoSubtitles.collectAsState(initial = true)
    val metadataFiles by preferences.downloadMetadataFiles.collectAsState(initial = false)
    val notificationActions by preferences.downloadNotificationActions.collectAsState(initial = true)
    val externalDownloaderEnabled by preferences.externalDownloaderEnabled.collectAsState(initial = false)
    val externalDownloaderPackage by preferences.externalDownloaderPackage.collectAsState(initial = "")
    var showSubtitleLanguageDialog by remember { mutableStateOf(false) }
    var showDownloaderPackageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        videoTemplate = preferences.downloadFilenameTemplateVideo.first()
        audioTemplate = preferences.downloadFilenameTemplateAudio.first()
        subtitleLanguage = preferences.downloadSubtitleLanguage.first()
    }

    val videoFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            coroutineScope.launch { preferences.setDownloadVideoFolder(it.toString()) }
        }
    }
    val audioFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            coroutineScope.launch { preferences.setDownloadAudioFolder(it.toString()) }
        }
    }

    // Dialog states
    var showThreadDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCodecDialog by remember { mutableStateOf(false) }
    var locationDialogTarget by remember { mutableStateOf<DownloadLocationTarget?>(null) }

    // Permission states (Android 11+ only)
    var hasAllFilesAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
        )
    }
    var mediaVideoGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var mediaAudioGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    // Re-check permission states when returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasAllFilesAccess = Environment.isExternalStorageManager()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaVideoGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_VIDEO
                    ) == PackageManager.PERMISSION_GRANTED
                    mediaAudioGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE (opens system settings page)
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesAccess = Environment.isExternalStorageManager()
        }
    }

    // Launcher for READ_MEDIA_VIDEO
    val mediaVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaVideoGranted = granted
    }

    // Launcher for READ_MEDIA_AUDIO
    val mediaAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaAudioGranted = granted
    }

    // Runtime permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        Log.d("DownloadSettings", "Storage permissions granted=$granted")
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            val path: String? = runCatching {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size == 2) {
                    val volume = parts[0]
                    val relativePath = parts[1]
                    if (volume.equals("primary", ignoreCase = true)) {
                        "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                    } else {
                        "/storage/$volume/$relativePath"
                    }
                } else null
            }.getOrNull() ?: uri.path
            coroutineScope.launch {
                if (!path.isNullOrBlank()) {
                    try { File(path).mkdirs() } catch (_: Exception) {}
                    preferences.setDownloadLocation(path)
                }
                locationDialogTarget = null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val writeGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!writeGranted) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
    
    val defaultVideoPath = remember {
        try {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "piTube"
            ).absolutePath
        } catch (e: Exception) {
            "Internal App Storage"
        }
    }
    val displayPath = downloadLocation ?: defaultVideoPath
    
    // Storage Info
    var freeSpace by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }
    var totalSpace by remember { mutableStateOf("") }
    var usedSpacePercentage by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(downloadLocation) {
        try {
            val statsPath = downloadLocation 
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path
            val file = File(statsPath)
            if (!file.exists()) file.mkdirs()
            
            val stat = android.os.StatFs(file.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            val total = stat.blockCountLong * stat.blockSizeLong
            
            val availableGB = available / (1024f * 1024f * 1024f)
            val totalGB = total / (1024f * 1024f * 1024f)
            
            freeSpace = String.format("%.1f GB", availableGB)
            totalSpace = String.format("%.1f GB", totalGB)
            
            if (total > 0) {
                usedSpacePercentage = (total - available).toFloat() / total.toFloat()
            }
        } catch (e: Exception) {
            freeSpace = context.getString(R.string.unknown)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.download_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            ),
            windowInsets = WindowInsets(0.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==================== PERMISSIONS (Android 11+) ====================
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    Text(
                        stringResource(R.string.permissions_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Outlined.FolderOpen,
                            title = stringResource(R.string.files_access_title),
                            subtitle = if (hasAllFilesAccess)
                                stringResource(R.string.files_access_granted_subtitle)
                            else
                                stringResource(R.string.files_access_denied_subtitle),
                            onClick = {
                                if (!hasAllFilesAccess) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        allFilesLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        try {
                                            allFilesLauncher.launch(
                                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            SettingsItem(
                                icon = Icons.Outlined.VideoLibrary,
                                title = stringResource(R.string.media_access_title),
                                subtitle = if (mediaVideoGranted)
                                    stringResource(R.string.media_access_granted_subtitle)
                                else
                                    stringResource(R.string.media_access_denied_subtitle),
                                onClick = {
                                    if (!mediaVideoGranted) {
                                        mediaVideoLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ==================== STORAGE ====================
            item {
                Text(
                    stringResource(R.string.storage_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.internal_storage_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.free_space_template, freeSpace),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { usedSpacePercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.total_space_template, totalSpace),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Outlined.VideoLibrary,
                        title = stringResource(R.string.video_download_location_label),
                        subtitle = displayPath,
                        onClick = { locationDialogTarget = DownloadLocationTarget.VIDEO }
                    )
                }
            }

            // ==================== PREFERENCES ====================
            item {
                Text(
                    stringResource(R.string.preferences_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.HighQuality,
                        title = stringResource(R.string.default_video_quality_label),
                        subtitle = defaultQuality.label,
                        onClick = { showQualityDialog = true }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.VideoSettings,
                        title = stringResource(R.string.default_download_codec_label),
                        subtitle = defaultCodec.label,
                        onClick = { showCodecDialog = true }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Wifi,
                        title = stringResource(R.string.download_over_wifi_only),
                        subtitle = stringResource(R.string.reduce_data_usage_subtitle),
                        checked = wifiOnly,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadOverWifiOnly(it) } }
                    )
                }
            }

            // ==================== PERFORMANCE ====================
            item {
                Text(
                    stringResource(R.string.performance_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.RocketLaunch,
                        title = stringResource(R.string.parallel_downloading_title),
                        subtitle = stringResource(R.string.parallel_downloading_subtitle),
                        checked = parallelEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setParallelDownloadEnabled(it) } }
                    )
                    if (parallelEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.concurrent_threads_title),
                            subtitle = stringResource(R.string.threads_per_download_template, threadCount),
                            onClick = { showThreadDialog = true }
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.performance_optimization_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Customization: names, folders, subtitles, metadata, notification ──
            item {
                Text(
                    stringResource(R.string.download_customization_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.download_settings_filename_templates),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.download_settings_template_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = videoTemplate,
                            onValueChange = {
                                videoTemplate = it
                                coroutineScope.launch { preferences.setDownloadFilenameTemplateVideo(it) }
                            },
                            label = { Text(stringResource(R.string.download_settings_template_video)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = audioTemplate,
                            onValueChange = {
                                audioTemplate = it
                                coroutineScope.launch { preferences.setDownloadFilenameTemplateAudio(it) }
                            },
                            label = { Text(stringResource(R.string.download_settings_template_audio)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        FolderOverrideRow(
                            title = stringResource(R.string.download_settings_folder_video),
                            value = videoFolder,
                            onPick = { videoFolderPicker.launch(null) },
                            onReset = {
                                coroutineScope.launch { preferences.setDownloadVideoFolder("") }
                            },
                        )
                        FolderOverrideRow(
                            title = stringResource(R.string.download_settings_folder_audio),
                            value = audioFolder,
                            onPick = { audioFolderPicker.launch(null) },
                            onReset = {
                                coroutineScope.launch { preferences.setDownloadAudioFolder("") }
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.ClosedCaption,
                        title = stringResource(R.string.download_settings_write_subtitles),
                        subtitle = stringResource(R.string.download_settings_write_subtitles_subtitle),
                        checked = writeSubtitles,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadWriteSubtitles(it) } }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.download_settings_auto_subtitles),
                        subtitle = stringResource(R.string.download_settings_auto_subtitles_subtitle),
                        checked = autoSubtitles,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadAutoSubtitles(it) } }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.download_settings_subtitle_language),
                        subtitle = subtitleLanguage.ifBlank {
                            stringResource(R.string.download_settings_subtitle_language_subtitle)
                        },
                        onClick = { showSubtitleLanguageDialog = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.download_settings_metadata_title),
                        subtitle = stringResource(R.string.download_settings_metadata_subtitle),
                        checked = metadataFiles,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadMetadataFiles(it) } }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.download_settings_notification_actions),
                        subtitle = stringResource(R.string.download_settings_notification_actions_subtitle),
                        checked = notificationActions,
                        onCheckedChange = { coroutineScope.launch { preferences.setDownloadNotificationActions(it) } }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.FileDownload,
                        title = stringResource(R.string.settings_external_downloader),
                        subtitle = stringResource(R.string.settings_external_downloader_desc),
                        checked = externalDownloaderEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setExternalDownloaderEnabled(it) } }
                    )
                    if (externalDownloaderEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Apps,
                            title = stringResource(R.string.settings_external_downloader_package),
                            subtitle = externalDownloaderPackage.ifBlank { "—" },
                            onClick = { showDownloaderPackageDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showSubtitleLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleLanguageDialog = false },
            title = { Text(stringResource(R.string.download_settings_subtitle_language)) },
            text = {
                OutlinedTextField(
                    value = subtitleLanguage,
                    onValueChange = { subtitleLanguage = it },
                    placeholder = { Text("en / tr / …") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch { preferences.setDownloadSubtitleLanguage(subtitleLanguage.trim()) }
                        showSubtitleLanguageDialog = false
                    },
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDownloaderPackageDialog) {
        var packageValue by remember { mutableStateOf(externalDownloaderPackage) }
        val pm = context.packageManager
        // Installed apps that advertise a text/share handler — one-tap picks.
        val shareTargets =
            remember {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
                runCatching {
                    pm.queryIntentActivities(intent, 0)
                        .mapNotNull { info ->
                            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                            val pkg = info.activityInfo?.packageName?.trim().orEmpty()
                            if (label.isBlank() || pkg.isBlank() || pkg == context.packageName) {
                                null
                            } else {
                                pkg to label
                            }
                        }
                        .distinctBy { it.first }
                        .sortedBy { it.second.lowercase() }
                }.getOrDefault(emptyList())
            }
        // Downloader apps whose SEND filter may not match text/plain (some
        // builds declare different mimes) — surfaced whenever installed.
        val knownDownloaders =
            remember(shareTargets) {
                KNOWN_DOWNLOADER_PACKAGES.mapNotNull { pkg ->
                    if (shareTargets.any { it.first == pkg }) return@mapNotNull null
                    val installed =
                        runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().trim()
                        }.getOrNull()
                    if (installed.isNullOrBlank()) null else pkg to installed
                }
            }
        val manualPackageInstalled =
            packageValue.isBlank() ||
                runCatching {
                    pm.getPackageInfo(packageValue.trim(), 0)
                    true
                }.getOrDefault(false)
        AlertDialog(
            onDismissRequest = { showDownloaderPackageDialog = false },
            title = { Text(stringResource(R.string.settings_external_downloader_package)) },
            text = {
                Column {
                    if (shareTargets.isNotEmpty()) {
                        Text(
                            stringResource(R.string.downloader_installed_apps_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .padding(vertical = 4.dp),
                        ) {
                            val allTargets = shareTargets + knownDownloaders
                            items(allTargets.size) { index ->
                                val (pkg, label) = allTargets[index]
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { packageValue = pkg }
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = packageValue == pkg,
                                        onClick = { packageValue = pkg },
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                    OutlinedTextField(
                        value = packageValue,
                        onValueChange = { packageValue = it },
                        placeholder = { Text("com.junkfood.seal / com.ytdlnis.downloader") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (!manualPackageInstalled) {
                        Text(
                            stringResource(R.string.downloader_package_not_installed_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch { preferences.setExternalDownloaderPackage(packageValue.trim()) }
                        showDownloaderPackageDialog = false
                    },
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloaderPackageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ==================== DIALOGS ====================

    if (showThreadDialog) {
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            icon = { Icon(Icons.Outlined.Speed, null) },
            title = { Text(stringResource(R.string.concurrent_threads_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.select_threads_count_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Slider(
                        value = threadCount.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setDownloadThreads(it.toInt()) } },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.threads_count_label, threadCount), 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { 
                TextButton(onClick = { showThreadDialog = false }) { 
                    Text(stringResource(R.string.close)) 
                } 
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (showQualityDialog) {
        val qualities = listOf(
            VideoQuality.Q_144p, VideoQuality.Q_240p, VideoQuality.Q_360p,
            VideoQuality.Q_480p, VideoQuality.Q_720p,
            VideoQuality.Q_1080p, VideoQuality.Q_1440p, VideoQuality.Q_2160p
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = { Icon(Icons.Outlined.HighQuality, null) },
            title = { Text(stringResource(R.string.quality)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogListHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        preferences.setDefaultDownloadQuality(quality)
                                        showQualityDialog = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultQuality == quality,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCodecDialog) {
        AlertDialog(
            onDismissRequest = { showCodecDialog = false },
            icon = { Icon(Icons.Outlined.VideoSettings, null) },
            title = { Text(stringResource(R.string.default_download_codec_label)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogListHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    VideoCodec.values().forEach { codec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        preferences.setDefaultDownloadCodec(codec)
                                        showCodecDialog = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultCodec == codec,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = codec.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (codec == VideoCodec.AUTO) {
                                    Text(
                                        text = stringResource(R.string.default_download_codec_auto_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCodecDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    locationDialogTarget?.let { dialogTarget ->
        val selectedLocation = downloadLocation
        val dialogTitle = stringResource(R.string.video_download_location_label)
        val downloadsPath = remember {
            try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "piTube").absolutePath }
            catch (_: Exception) { null }
        }
        val secondaryPath = remember {
            try {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "piTube").absolutePath
            } catch (_: Exception) { null }
        }
        val internalPath = remember { File(context.filesDir, "downloads").absolutePath }

        val presetPaths = listOfNotNull(downloadsPath, secondaryPath, internalPath)
        val isSafCustomSelected = selectedLocation != null && selectedLocation !in presetPaths

        var showManualDialog by remember { mutableStateOf(false) }
        var manualPathInput by remember(dialogTarget, selectedLocation) {
            mutableStateOf(if (isSafCustomSelected) selectedLocation else "")
        }

        BasicAlertDialog(onDismissRequest = { locationDialogTarget = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    
                    // HEADER
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            dialogTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.location_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // HELPER COMPOSABLE FOR ROWS
                    @Composable
                    fun PresetRow(label: String, path: String?, isRecommended: Boolean = false) {
                        val isSelected = path != null && path == selectedLocation || (path == null && selectedLocation == null)
                        
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    path?.let { p -> try { File(p).mkdirs() } catch (_: Exception) {} }
                                    preferences.setDownloadLocation(path)
                                    locationDialogTarget = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected, 
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = label, 
                                            style = MaterialTheme.typography.bodyLarge, 
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false) 
                                        )
                                        
                                        if (isRecommended) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.location_badge_recommended),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (path != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(24.dp))

                        Text(
                            stringResource(R.string.location_preset_header).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                        )

                        downloadsPath?.let { PresetRow(stringResource(R.string.location_downloads_label), it, isRecommended = dialogTarget == DownloadLocationTarget.VIDEO) }
                        secondaryPath?.let {
                            PresetRow(stringResource(R.string.location_movies_label), it)
                        }
                        PresetRow(stringResource(R.string.location_internal_app_label), internalPath)

                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.location_custom_header).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                        )

                        // SAF PICKER ROW
                        val safBg = if (isSafCustomSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent
                        val safBorder = if (isSafCustomSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                        Surface(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = safBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, safBorder)
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = if (isSafCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.location_custom_saf_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isSafCustomSelected) selectedLocation
                                               else stringResource(R.string.location_custom_saf_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // MANUAL PICKER ROW
                        Surface(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.location_custom_manual_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.location_custom_manual_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { locationDialogTarget = null }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }

        if (showManualDialog) {
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                icon = { Icon(Icons.Outlined.Edit, null) },
                title = { Text(stringResource(R.string.location_manual_dialog_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.location_custom_manual_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = manualPathInput,
                            onValueChange = { manualPathInput = it },
                            label = { Text(stringResource(R.string.location_manual_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = manualPathInput.trim()
                            if (trimmed.isNotBlank()) {
                                coroutineScope.launch {
                                    try { File(trimmed).mkdirs() } catch (_: Exception) {}
                                    preferences.setDownloadLocation(trimmed)
                                    showManualDialog = false
                                    locationDialogTarget = null
                                }
                            }
                        },
                        enabled = manualPathInput.trim().isNotBlank()
                    ) {
                        Text(stringResource(R.string.location_manual_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun FolderOverrideRow(
    title: String,
    value: String,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.ifBlank { stringResource(R.string.download_settings_folder_default) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) { Text(stringResource(R.string.btn_reset)) }
            TextButton(onClick = onPick) { Text(stringResource(R.string.btn_change)) }
        }
    }
}


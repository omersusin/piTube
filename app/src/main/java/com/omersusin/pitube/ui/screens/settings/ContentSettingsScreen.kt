package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.local.PlayerRelatedCardStyle
import com.omersusin.pitube.data.local.WatchedThreshold
import com.omersusin.pitube.ui.NavigationVisibility
import com.omersusin.pitube.ui.resolveDefaultNavTabIndex
import com.omersusin.pitube.ui.visibleNavTabIndices
import com.omersusin.pitube.ui.theme.GridItemSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    
    val gridSizeString by preferences.gridItemSize.collectAsState(initial = "BIG")
    val currentGridSize = try {
        GridItemSize.valueOf(gridSizeString)
    } catch (e: Exception) {
        GridItemSize.BIG
    }
    
    val isShortsShelfEnabled by preferences.shortsShelfEnabled.collectAsState(initial = true)
    val isHomeShortsShelfEnabled by preferences.homeShortsShelfEnabled.collectAsState(initial = true)
    val isHomeNavigationEnabled by preferences.homeNavigationEnabled.collectAsState(initial = true)
    val isShortsNavigationEnabled by preferences.shortsNavigationEnabled.collectAsState(initial = true)
    val isContinueWatchingEnabled by preferences.continueWatchingEnabled.collectAsState(initial = true)
    val showRelatedVideos by preferences.showRelatedVideos.collectAsState(initial = true)
    
    val homeViewModeString by preferences.homeViewMode.collectAsState(initial = com.omersusin.pitube.data.local.HomeViewMode.GRID)
    val currentHomeViewMode = homeViewModeString

    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsState(initial = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsState(initial = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsState(initial = true)
    val currentRelatedCardStyle by preferences.playerRelatedCardStyle.collectAsState(initial = PlayerRelatedCardStyle.COMPACT)
    val hideWatchedVideosFromHome by preferences.hideWatchedVideosFromHome.collectAsState(initial = false)
    val hideUnplayableVideosFromSubscriptions by preferences.hideUnplayableVideosFromSubscriptions.collectAsState(initial = false)
    val libraryShelfEnabled by preferences.libraryShelfEnabled.collectAsState(initial = true)
    val watchedThreshold by preferences.watchedThreshold.collectAsState(initial = com.omersusin.pitube.data.local.WatchedThreshold.ALMOST_FINISHED)
    var showWatchedThresholdDialog by remember { mutableStateOf(false) }
    val blockedChannelIds by preferences.blockedChannelIds.collectAsState(initial = emptySet())
    var showBlockedChannelsDialog by remember { mutableStateOf(false) }
    val hiddenVideoIds by preferences.hiddenVideoIds.collectAsState(initial = emptySet())
    var showHiddenVideosDialog by remember { mutableStateOf(false) }
    val historyDefaultRange by preferences.historyDefaultRange.collectAsState(initial = "all_time")
    var showHistoryRangeDialog by remember { mutableStateOf(false) }
    val bottomNavHideOnScroll by preferences.bottomNavHideOnScroll.collectAsState(initial = true)
    val shareWithoutText by preferences.shareWithoutText.collectAsState(initial = false)
    val disableShortsPlayer by preferences.disableShortsPlayer.collectAsState(initial = false)
    val showShortsPlayerPrompt by preferences.showShortsPlayerPrompt.collectAsState(initial = true)
    val showRegionPickerInExplore by preferences.showRegionPickerInExplore.collectAsState(initial = true)
    val videoTitleMaxLines by preferences.videoTitleMaxLines.collectAsState(initial = 1)
    val videoCardMarkWatchedEnabled by preferences.videoCardMarkWatchedEnabled.collectAsState(initial = false)
    val commentsEnabled by preferences.commentsEnabled.collectAsState(initial = true)
    val commentsPreviewEnabled by preferences.commentsPreviewEnabled.collectAsState(initial = true)
    val navTabOrder by preferences.navTabOrder.collectAsState(initial = com.omersusin.pitube.data.local.DEFAULT_NAV_TAB_ORDER)
    val defaultNavTabIndex by preferences.defaultNavTabIndex.collectAsState(initial = 0)
    val navigationVisibility = NavigationVisibility(
        home = isHomeNavigationEnabled,
        shorts = isShortsNavigationEnabled,
    )
    val visibleNavIndices = visibleNavTabIndices(navTabOrder, navigationVisibility)
    val resolvedDefaultNavTabIndex = resolveDefaultNavTabIndex(
        preferredIndex = defaultNavTabIndex,
        order = navTabOrder,
        visibility = navigationVisibility
    )
    
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.content_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Layout Settings Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_display))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_grid_size_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_grid_size_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_grid_big_title),
                                description = stringResource(R.string.content_settings_grid_big_desc),
                                isSelected = currentGridSize == GridItemSize.BIG,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setGridItemSize("BIG")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_grid_small_title),
                                description = stringResource(R.string.content_settings_grid_small_desc),
                                isSelected = currentGridSize == GridItemSize.SMALL,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setGridItemSize("SMALL")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Home Layout Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_home_layout))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (currentHomeViewMode == com.omersusin.pitube.data.local.HomeViewMode.GRID) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_home_layout_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_home_layout_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LayoutOption(
                                title = stringResource(R.string.content_settings_layout_grid),
                                icon = Icons.Outlined.GridView,
                                isSelected = currentHomeViewMode == com.omersusin.pitube.data.local.HomeViewMode.GRID,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setHomeViewMode(com.omersusin.pitube.data.local.HomeViewMode.GRID)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            LayoutOption(
                                title = stringResource(R.string.content_settings_layout_list),
                                icon = Icons.AutoMirrored.Outlined.List,
                                isSelected = currentHomeViewMode == com.omersusin.pitube.data.local.HomeViewMode.LIST,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setHomeViewMode(com.omersusin.pitube.data.local.HomeViewMode.LIST)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Home Feed Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_home_feed))
                SettingsGroup {
                    NavTabOrderSettings(
                        order = navTabOrder,
                        enabledIndices = visibleNavIndices.toSet(),
                        defaultTabIndex = resolvedDefaultNavTabIndex,
                        onMove = { index, direction ->
                            val currentIndex = navTabOrder.indexOf(index)
                            val targetIndex = (currentIndex + direction).coerceIn(0, navTabOrder.lastIndex)
                            if (currentIndex >= 0 && currentIndex != targetIndex) {
                                val updated = navTabOrder.toMutableList()
                                val moved = updated.removeAt(currentIndex)
                                updated.add(targetIndex, moved)
                                coroutineScope.launch {
                                    preferences.setNavTabOrder(updated)
                                }
                            }
                        },
                        onDefaultSelected = { index ->
                            coroutineScope.launch {
                                preferences.setDefaultNavTabIndex(index)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Block,
                        title = stringResource(R.string.content_settings_hide_unplayable_subscriptions_title),
                        subtitle = stringResource(R.string.content_settings_hide_unplayable_subscriptions_subtitle),
                        checked = hideUnplayableVideosFromSubscriptions,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferences.setHideUnplayableVideosFromSubscriptions(enabled)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    // Library layout
                    SectionHeader(text = stringResource(R.string.settings_library_section))
                    SettingsGroup {
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Subscriptions,
                            title = stringResource(R.string.library_shelf_enabled),
                            subtitle = stringResource(R.string.library_shelf_enabled_subtitle),
                            checked = libraryShelfEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    preferences.setLibraryShelfEnabled(enabled)
                                }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    DismissedContentRow(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.content_settings_history_range_title),
                        subtitle =
                            when (historyDefaultRange) {
                                "today" -> stringResource(R.string.history_range_today)
                                "this_week" -> stringResource(R.string.history_range_this_week)
                                else -> stringResource(R.string.history_range_all_time)
                            },
                        onClick = { showHistoryRangeDialog = true },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(modifier = Modifier.padding(16.dp)) {
                        DismissedContentRow(
                            icon = Icons.Outlined.Block,
                            title = stringResource(R.string.dismissed_content_manage_blocked, blockedChannelIds.size),
                            onClick = { showBlockedChannelsDialog = true },
                        )
                        DismissedContentRow(
                            icon = Icons.Outlined.VisibilityOff,
                            title = stringResource(R.string.dismissed_content_manage_hidden, hiddenVideoIds.size),
                            onClick = { showHiddenVideosDialog = true },
                        )
                    }
                }
            }

            // Video Player Section
            item {
                SectionHeader(text = stringResource(R.string.content_settings_header_player))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.SmartDisplay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_related_card_style_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_related_card_style_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_related_card_compact),
                                description = stringResource(R.string.content_settings_related_card_compact_desc),
                                isSelected = currentRelatedCardStyle == PlayerRelatedCardStyle.COMPACT,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setPlayerRelatedCardStyle(PlayerRelatedCardStyle.COMPACT)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            GridSizeOption(
                                title = stringResource(R.string.content_settings_related_card_full_width),
                                description = stringResource(R.string.content_settings_related_card_full_width_desc),
                                isSelected = currentRelatedCardStyle == PlayerRelatedCardStyle.FULL_WIDTH,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setPlayerRelatedCardStyle(PlayerRelatedCardStyle.FULL_WIDTH)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Video Title Lines Section
            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Title,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.content_settings_video_title_lines_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.content_settings_video_title_lines_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                1 to stringResource(R.string.content_settings_title_lines_1),
                                2 to stringResource(R.string.content_settings_title_lines_2),
                                3 to stringResource(R.string.content_settings_title_lines_3),
                                0 to stringResource(R.string.content_settings_title_lines_unlimited)
                            ).forEach { (lines, label) ->
                                val isSelected = videoTitleMaxLines == lines
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                preferences.setVideoTitleMaxLines(lines)
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                         else androidx.compose.ui.text.font.FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showWatchedThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showWatchedThresholdDialog = false },
            title = {
                Text(
                    stringResource(R.string.content_settings_watched_threshold_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.content_settings_watched_threshold_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    listOf(
                        WatchedThreshold.ALMOST_FINISHED,
                        WatchedThreshold.PERCENT_99,
                        WatchedThreshold.PERCENT_95,
                        WatchedThreshold.PERCENT_90
                    ).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferences.setWatchedThreshold(option) }
                                    showWatchedThresholdDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = watchedThreshold == option, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = watchedThresholdLabel(option),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWatchedThresholdDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (showBlockedChannelsDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedChannelsDialog = false },
            title = {
                Text(
                    stringResource(R.string.blocked_channels_header),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (blockedChannelIds.isEmpty()) {
                    Text(
                        stringResource(R.string.blocked_channels_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        blockedChannelIds.forEach { channelId ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = channelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            preferences.unblockChannel(channelId)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.blocked_item_unblock_channel))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedChannelsDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            dismissButton = {
                if (blockedChannelIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                blockedChannelIds.forEach { preferences.unblockChannel(it) }
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.blocked_clear_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    if (showHistoryRangeDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryRangeDialog = false },
            title = { Text(stringResource(R.string.content_settings_history_range_title)) },
            text = {
                Column {
                    listOf(
                        "all_time" to stringResource(R.string.history_range_all_time),
                        "today" to stringResource(R.string.history_range_today),
                        "this_week" to stringResource(R.string.history_range_this_week),
                    ).forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferences.setHistoryDefaultRange(key) }
                                        showHistoryRangeDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = historyDefaultRange == key,
                                onClick = {
                                    coroutineScope.launch { preferences.setHistoryDefaultRange(key) }
                                    showHistoryRangeDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryRangeDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    if (showHiddenVideosDialog) {        AlertDialog(
            onDismissRequest = { showHiddenVideosDialog = false },
            title = {
                Text(
                    stringResource(R.string.hidden_videos_header),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (hiddenVideoIds.isEmpty()) {
                    Text(
                        stringResource(R.string.hidden_videos_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        hiddenVideoIds.forEach { videoId ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = videoId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            preferences.removeHiddenVideo(videoId)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.hidden_video_unhide))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenVideosDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            dismissButton = {
                if (hiddenVideoIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                hiddenVideoIds.forEach { preferences.removeHiddenVideo(it) }
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.hidden_videos_clear_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}


@Composable
private fun NavTabOrderSettings(
    order: List<Int>,
    enabledIndices: Set<Int>,
    defaultTabIndex: Int,
    onMove: (index: Int, direction: Int) -> Unit,
    onDefaultSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.content_settings_nav_order_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.content_settings_nav_order_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        order.forEachIndexed { position, index ->
            val enabled = index in enabledIndices
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = defaultTabIndex == index,
                    enabled = enabled,
                    onClick = { onDefaultSelected(index) }
                )
                Icon(
                    navTabIcon(index),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = navTabLabel(index),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onMove(index, -1) },
                    enabled = position > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                }
                IconButton(
                    onClick = { onMove(index, 1) },
                    enabled = position < order.lastIndex,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                }
            }
        }
    }
}

@Composable
private fun navTabLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.nav_home)
    1 -> stringResource(R.string.nav_shorts)
    4 -> stringResource(R.string.nav_library)
    else -> stringResource(R.string.nav_home)
}

@Composable
private fun navTabIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Outlined.Home
    1 -> ImageVector.vectorResource(id = R.drawable.ic_shorts)
    3 -> Icons.Outlined.Subscriptions
    4 -> Icons.Outlined.VideoLibrary
    else -> Icons.Outlined.Home
}

@Composable
private fun watchedThresholdLabel(threshold: WatchedThreshold): String = when (threshold) {
    WatchedThreshold.PERCENT_90 -> stringResource(R.string.content_settings_watched_threshold_90)
    WatchedThreshold.PERCENT_95 -> stringResource(R.string.content_settings_watched_threshold_95)
    WatchedThreshold.PERCENT_99 -> stringResource(R.string.content_settings_watched_threshold_99)
    WatchedThreshold.ALMOST_FINISHED -> stringResource(R.string.content_settings_watched_threshold_almost)
}

@Composable
private fun LayoutOption(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GridSizeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.ui_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun DismissedContentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

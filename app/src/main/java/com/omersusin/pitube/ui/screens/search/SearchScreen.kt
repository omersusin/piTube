@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.omersusin.pitube.ui.screens.search

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.*
import com.omersusin.pitube.data.local.ContentType
import com.omersusin.pitube.data.local.SearchFilter
import com.omersusin.pitube.data.local.SearchHistoryItem
import com.omersusin.pitube.data.model.*
import com.omersusin.pitube.data.paging.SearchResultItem
import com.omersusin.pitube.data.search.SearchSuggestionsService
import com.omersusin.pitube.ui.components.*
import com.omersusin.pitube.utils.formatDuration
import com.omersusin.pitube.utils.formatSubscriberCount
import com.omersusin.pitube.utils.formatViewCount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    onVoiceSearch: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val searchHistoryRepo = remember { SearchHistoryRepository(context) }
    val uiState by viewModel.uiState.collectAsState()
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = viewModel.uiState.value.query,
                selection = TextRange(viewModel.uiState.value.query.length),
            ),
        )
    }
    var isSearchFocused by remember { mutableStateOf(false) }

    var hasPerformedSearch by rememberSaveable { mutableStateOf(false) }
    var isNavigatingAway by remember { mutableStateOf(false) }

    val searchHistory by searchHistoryRepo
        .getSearchHistoryFlow()
        .collectAsState(initial = emptyList())
    val suggestionsEnabled by searchHistoryRepo
        .isSearchSuggestionsEnabledFlow()
        .collectAsState(initial = true)
    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    val matchingHistorySuggestions =
        remember(searchHistory, searchQuery.text) {
            val queryText = searchQuery.text.trim()
            if (queryText.isBlank()) {
                emptyList()
            } else {
                val normalizedQuery = queryText.lowercase()
                val matchingQueries =
                    searchHistory
                        .asSequence()
                        .map { it.query.trim() }
                        .filter { it.isNotBlank() && it.contains(queryText, ignoreCase = true) }
                        .distinctBy { it.lowercase() }
                        .toList()
                val prefixMatches = matchingQueries.filter { it.lowercase().startsWith(normalizedQuery) }
                val containsMatches = matchingQueries.filterNot { it.lowercase().startsWith(normalizedQuery) }
                (prefixMatches + containsMatches).take(5)
            }
        }
    val orderedSuggestions =
        remember(matchingHistorySuggestions, liveSuggestions) {
            (matchingHistorySuggestions + liveSuggestions)
                .distinctBy { it.trim().lowercase() }
                .take(10)
        }

    val dismissKeyboard: () -> Unit =
        remember(focusManager, keyboardController) {
            {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                isSearchFocused = false
            }
        }

    val setSearchQueryToEnd: (String) -> Unit =
        remember {
            { value ->
                searchQuery = TextFieldValue(value, selection = TextRange(value.length))
            }
        }

    val navigateToVideo: (Video) -> Unit =
        remember(dismissKeyboard, onVideoClick) {
            { video ->
                isNavigatingAway = true
                hasPerformedSearch = true
                dismissKeyboard()
                onVideoClick(video)
            }
        }

    val navigateToChannel: (Channel) -> Unit =
        remember(dismissKeyboard, onChannelClick) {
            { channel ->
                isNavigatingAway = true
                hasPerformedSearch = true
                dismissKeyboard()
                onChannelClick(channel)
            }
        }

    val navigateToPlaylist: (Playlist) -> Unit =
        remember(dismissKeyboard, onPlaylistClick) {
            { playlist ->
                isNavigatingAway = true
                hasPerformedSearch = true
                dismissKeyboard()
                onPlaylistClick(playlist)
            }
        }

    val togglePlaylistSaved: (Playlist) -> Unit =
        remember(context) {
            { playlist ->
                scope.launch {
                    val nowSaved = viewModel.togglePlaylistSave(playlist)
                    Toast.makeText(
                        context,
                        context.getString(
                            if (nowSaved) R.string.ui_playlist_saved_to_library else R.string.ui_playlist_removed_from_library,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    LaunchedEffect(Unit) {
        if (!hasPerformedSearch) {
            delay(200)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }
        }
    }

    // Voice/song recognition queries arrive through the bridge (submitted by
    // the recognition modal before this screen exists): run them immediately.
    LaunchedEffect(Unit) {
        com.omersusin.pitube.ui.recognition.RecognitionSearchBridge.pendingQuery.collect { query ->
            if (!query.isNullOrBlank()) {
                hasPerformedSearch = true
                isSearchFocused = false
                setSearchQueryToEnd(query)
                dismissKeyboard()
                viewModel.search(query)
            }
            com.omersusin.pitube.ui.recognition.RecognitionSearchBridge.consume()
        }
    }

    LaunchedEffect(isNavigatingAway) {
        if (isNavigatingAway) {
            repeat(5) {
                delay(80)
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            }
            isNavigatingAway = false
        }
    }

    LaunchedEffect(searchQuery, isSearchFocused) {
        val queryText = searchQuery.text
        if (queryText.length >= 2 && isSearchFocused && suggestionsEnabled) {
            isLoadingSuggestions = true
            delay(280)
            try {
                liveSuggestions = SearchSuggestionsService.getSuggestions(queryText)
            } catch (_: Exception) {
                liveSuggestions = emptyList()
            }
            isLoadingSuggestions = false
        } else {
            liveSuggestions = emptyList()
            isLoadingSuggestions = false
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) {
            searchHistoryRepo.saveSearchQuery(uiState.query)
            gridState.scrollToItem(0)
        }
        if (!isSearchFocused && searchQuery.text != uiState.query) {
            setSearchQueryToEnd(uiState.query)
        }
    }

    LaunchedEffect(isSearchFocused) {
        if (isSearchFocused) {
            keyboardController?.show()
        }
    }

    val selectedContentType = uiState.filters?.contentType ?: ContentType.VIDEOS
    val activeOrPendingFilters = uiState.filters ?: SearchFilter(contentType = selectedContentType)
    val sharedVideoTitle = stringResource(R.string.shared_video)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        SearchBarRow(
            query = searchQuery,
            onQueryChange = {
                if (!isNavigatingAway) {
                    searchQuery = it
                }
            },
            onSearch = {
                val queryText = searchQuery.text
                if (queryText.isNotBlank()) {
                    dismissKeyboard()
                    liveSuggestions = emptyList()

                    val videoId = extractVideoId(queryText)
                    if (videoId != null) {
                        navigateToVideo(
                            Video(
                                id = videoId,
                                title = sharedVideoTitle,
                                channelName = sharedVideoTitle,
                                channelId = "",
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                duration = 0,
                                viewCount = 0L,
                                uploadDate = "",
                                channelThumbnailUrl = "",
                            ),
                        )
                        return@SearchBarRow
                    }

                    viewModel.search(queryText, activeOrPendingFilters)
                }
            },
            onClear = {
                setSearchQueryToEnd("")
                liveSuggestions = emptyList()
                viewModel.clearSearch()
            },
            onVoiceSearch = onVoiceSearch,
            isSearchFocused = isSearchFocused,
            onFocusChange = { focused ->
                if (isNavigatingAway) return@SearchBarRow
                isSearchFocused = focused
            },
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        AnimatedVisibility(
            visible =
                isSearchFocused && searchQuery.text.isNotEmpty() &&
                    (orderedSuggestions.isNotEmpty() || isLoadingSuggestions),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SuggestionsCard(
                query = searchQuery.text,
                suggestions = orderedSuggestions,
                isLoading = isLoadingSuggestions,
                onSuggestionClick = { s ->
                    dismissKeyboard()
                    setSearchQueryToEnd(s)
                    liveSuggestions = emptyList()

                    val videoId = extractVideoId(s)
                    if (videoId != null) {
                        navigateToVideo(
                            Video(
                                id = videoId,
                                title = sharedVideoTitle,
                                channelName = sharedVideoTitle,
                                channelId = "",
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                duration = 0,
                                viewCount = 0L,
                                uploadDate = "",
                                channelThumbnailUrl = "",
                            ),
                        )
                    } else {
                        viewModel.search(s, activeOrPendingFilters)
                    }
                },
                onFillClick = { setSearchQueryToEnd(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val hasQuery = uiState.query.isNotBlank()

        SearchContentTabs(
            selectedContentType = selectedContentType,
            onContentTypeSelected = { type ->
                val base = uiState.filters ?: SearchFilter()
                viewModel.updateFilters(base.copy(contentType = type))
            },
        )

        if (selectedContentType == ContentType.VIDEOS) {
            VideoFilterChipRows(
                selectedUploadDate = uiState.filters?.uploadDate ?: UploadDate.ANY,
                onUploadDateSelected = { date ->
                    val base = uiState.filters ?: SearchFilter()
                    viewModel.updateFilters(base.copy(uploadDate = date))
                },
                selectedDuration = uiState.filters?.duration ?: Duration.ANY,
                onDurationSelected = { dur ->
                    val base = uiState.filters ?: SearchFilter()
                    viewModel.updateFilters(base.copy(duration = dur))
                },
                selectedSortType = uiState.filters?.sortType ?: SortType.RELEVANCE,
                onSortTypeSelected = {
                    val base = uiState.filters ?: SearchFilter()
                    viewModel.updateFilters(base.copy(sortType = it))
                },
            )
        }

        if (!hasQuery) {
            DiscoverScreen(
                searchHistory = searchHistory,
                onHistoryClick = { q ->
                    dismissKeyboard()
                    setSearchQueryToEnd(q)
                    viewModel.search(q, activeOrPendingFilters)
                },
                onHistoryDelete = { item ->
                    scope.launch { searchHistoryRepo.deleteSearchItem(item.id) }
                },
                onClearHistory = {
                    scope.launch { searchHistoryRepo.clearSearchHistory() }
                },
            )
        } else {
            val isInitialLoading =
                pagingItems.loadState.refresh is LoadState.Loading
            val isInitialError =
                pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0
            val isEmptyResults =
                pagingItems.loadState.refresh is LoadState.NotLoading &&
                    pagingItems.itemCount == 0 &&
                    !isInitialLoading
            val resultCount =
                remember(pagingItems.loadState.refresh, pagingItems.itemCount) {
                    (0 until pagingItems.itemCount).count {
                        pagingItems.peek(it) !is SearchResultItem.ShortsShelfResult
                    }
                }

            if (!isInitialLoading && !isInitialError && !isEmptyResults) {
                ResultsSectionHeader(
                    contentType = selectedContentType,
                    resultCount = resultCount,
                )
            }

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                when {
                    isInitialLoading -> {
                        ShimmerResultsScreen(false, 1)
                    }

                    isInitialError -> {
                        val err =
                            (pagingItems.loadState.refresh as LoadState.Error).error
                        SearchErrorState(
                            message = err.localizedMessage ?: stringResource(R.string.search_failed),
                            onRetry = pagingItems::retry,
                        )
                    }

                    isEmptyResults -> {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.empty_results_title),
                            body = stringResource(R.string.empty_results_body),
                            actionLabel = stringResource(R.string.retry),
                            onAction = pagingItems::retry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        SearchResultList(
                            pagingItems,
                            gridState,
                            savedPlaylistIds,
                            togglePlaylistSaved,
                            navigateToVideo,
                            navigateToChannel,
                            navigateToPlaylist,
                            dismissKeyboard,
                        )
                    }
                }
            }
        }
    }
}

private fun extractVideoId(url: String): String? {
    if (!isSupportedVideoUrl(url)) return null
    val patterns =
        listOf(
            Regex("v=([^&]+)"),
            Regex("shorts/([^/?]+)"),
            Regex("youtu.be/([^/?]+)"),
            Regex("embed/([^/?]+)"),
            Regex("v/([^/?]+)"),
        )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) return match.groupValues[1]
    }
    return url.substringAfterLast("/").substringBefore("?").ifEmpty { null }
}

private fun isSupportedVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("youtube.com") ||
        lower.contains("youtu.be") ||
        lower.contains("youtube-nocookie.com") ||
        lower.contains("piped") ||
        lower.contains("invidious") ||
        lower.contains("yewtu.be")
}

@Composable
private fun SearchBarRow(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onVoiceSearch: () -> Unit,
    isSearchFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val focusAnim by animateFloatAsState(
        targetValue = if (isSearchFocused) 1f else 0f,
        animationSpec = tween(300),
        label = "focus",
    )
    val primary = MaterialTheme.colorScheme.primary
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(23.dp))
                .drawBehind {
                    if (focusAnim > 0f) {
                        drawRoundRect(
                            brush =
                                Brush.sweepGradient(
                                    listOf(
                                        primary.copy(alpha = focusAnim * 0.9f),
                                        primary.copy(alpha = focusAnim * 0.3f),
                                        primary.copy(alpha = focusAnim * 0.9f),
                                    ),
                                ),
                            cornerRadius =
                                androidx.compose.ui.geometry.CornerRadius(
                                    23.dp.toPx(),
                                ),
                            style = Stroke(width = (2.5f * focusAnim).dp.toPx()),
                        )
                    }
                }.background(
                    color =
                        if (isSearchFocused) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                    shape = RoundedCornerShape(23.dp),
                ).clickable(
                    indication = null,
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint =
                    if (isSearchFocused) {
                        primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            onFocusChange(state.isFocused)
                        },
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { if (query.text.isNotBlank()) onSearch() },
                    ),
                cursorBrush = SolidColor(primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.text.isEmpty()) {
                            Text(
                                stringResource(R.string.search_videos_channels_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.55f,
                                    ),
                                fontSize = 16.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (query.text.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .padding(end = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onVoiceSearch),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = stringResource(R.string.voice_search_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContentTabs(
    selectedContentType: ContentType,
    onContentTypeSelected: (ContentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabIndex =
        when (selectedContentType) {
            ContentType.PLAYLISTS -> 1
            ContentType.CHANNELS -> 2
            else -> 0
        }
    PrimaryTabRow(
        selectedTabIndex = tabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Tab(
            selected = tabIndex == 0,
            onClick = { onContentTypeSelected(ContentType.VIDEOS) },
            text = {
                Text(
                    stringResource(R.string.videos_header),
                    fontWeight = if (tabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                )
            },
        )
        Tab(
            selected = tabIndex == 1,
            onClick = { onContentTypeSelected(ContentType.PLAYLISTS) },
            text = {
                Text(
                    stringResource(R.string.tab_playlists),
                    fontWeight = if (tabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                )
            },
        )
        Tab(
            selected = tabIndex == 2,
            onClick = { onContentTypeSelected(ContentType.CHANNELS) },
            text = {
                Text(
                    stringResource(R.string.channels_header),
                    fontWeight = if (tabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                )
            },
        )
    }
}

@Composable
private fun VideoFilterChipRows(
    selectedUploadDate: UploadDate,
    onUploadDateSelected: (UploadDate) -> Unit,
    selectedDuration: Duration,
    onDurationSelected: (Duration) -> Unit,
    selectedSortType: SortType,
    onSortTypeSelected: (SortType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uploadDateLabels =
        listOf(
            UploadDate.ANY to R.string.date_any,
            UploadDate.TODAY to R.string.date_today,
            UploadDate.THIS_WEEK to R.string.date_this_week,
            UploadDate.THIS_MONTH to R.string.date_this_month,
            UploadDate.THIS_YEAR to R.string.date_this_year,
        )
    val durationLabels =
        listOf(
            Duration.ANY to R.string.duration_any,
            Duration.UNDER_4_MINUTES to R.string.duration_under_4,
            Duration.FROM_4_TO_20_MINUTES to R.string.duration_4_20,
            Duration.OVER_20_MINUTES to R.string.duration_over_20,
        )
    val sortTypeLabels =
        listOf(
            SortType.RELEVANCE to R.string.sort_relevance,
            SortType.NEWEST to R.string.sort_newest,
            SortType.VIEWS to R.string.sort_most_viewed,
            SortType.RATING to R.string.sort_rating,
        )

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uploadDateLabels, key = { it.first.name }) { (value, labelRes) ->
                ContentFilterChip(
                    title = stringResource(labelRes),
                    isSelected = value == selectedUploadDate,
                    onClick = { onUploadDateSelected(value) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(durationLabels, key = { "d_${it.first.name}" }) { (value, labelRes) ->
                ContentFilterChip(
                    title = stringResource(labelRes),
                    isSelected = value == selectedDuration,
                    onClick = { onDurationSelected(value) },
                )
            }
            item(key = "divider") {
                VerticalDivider(
                    modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
            items(sortTypeLabels, key = { "s_${it.first.name}" }) { (value, labelRes) ->
                ContentFilterChip(
                    title = stringResource(labelRes),
                    isSelected = value == selectedSortType,
                    onClick = { onSortTypeSelected(value) },
                )
            }
        }
    }
}

@Composable
private fun ResultsSectionHeader(
    contentType: ContentType,
    resultCount: Int,
    modifier: Modifier = Modifier,
) {
    val (icon, labelRes) =
        when (contentType) {
            ContentType.PLAYLISTS -> Icons.Outlined.VideoLibrary to R.string.tab_playlists
            ContentType.CHANNELS -> Icons.Outlined.People to R.string.channels_header
            else -> Icons.Outlined.SmartDisplay to R.string.videos_header
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.search_results_count, resultCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun searchItemKey(
    item: SearchResultItem?,
    index: Int,
): Any =
    when (item) {
        is SearchResultItem.VideoResult -> "v_${item.video.id}"
        is SearchResultItem.ChannelResult -> "c_${item.channel.id}"
        is SearchResultItem.PlaylistResult -> "p_${item.playlist.id}"
        is SearchResultItem.ShortsShelfResult -> SHORTS_SHELF_KEY
        null -> "placeholder_$index"
    }

/** Lets the grid reuse an item's composition when a slot is filled by another item of the same kind. */
private fun searchItemContentType(item: SearchResultItem?): Any =
    when (item) {
        is SearchResultItem.VideoResult -> "video"
        is SearchResultItem.ChannelResult -> "channel"
        is SearchResultItem.PlaylistResult -> "playlist"
        is SearchResultItem.ShortsShelfResult -> SHORTS_SHELF_KEY
        null -> "placeholder"
    }

private const val SHORTS_SHELF_KEY = "shortsShelf"

@Composable
private fun SearchResultList(
    pagingItems: androidx.paging.compose.LazyPagingItems<SearchResultItem>,
    listState: LazyGridState,
    savedPlaylistIds: Set<String>,
    onToggleSavePlaylist: (Playlist) -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    dismissKeyboard: () -> Unit,
) {
    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(1),
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event =
                                awaitPointerEvent(
                                    pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                                )
                            if (event.changes.any { it.pressed }) {
                                dismissKeyboard()
                            }
                        }
                    }
                },
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp),
    ) {
        items(
            count = pagingItems.itemCount,
            key = { i -> searchItemKey(pagingItems.peek(i), i) },
            contentType = { i -> searchItemContentType(pagingItems.peek(i)) },
        ) { i ->
            when (val item = pagingItems[i]) {
                is SearchResultItem.VideoResult -> {
                    VideoCardFullWidth(
                        video = item.video,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { onVideoClick(item.video) },
                        onChannelClick = { channelId ->
                            onChannelClick(
                                Channel(
                                    id = channelId,
                                    name = item.video.channelName,
                                    thumbnailUrl = item.video.channelThumbnailUrl,
                                    subscriberCount = 0,
                                    url = "https://www.youtube.com/channel/$channelId",
                                ),
                            )
                        },
                    )
                }

                is SearchResultItem.ChannelResult -> {
                    SearchChannelCard(
                        item.channel,
                        onClick = {
                            onChannelClick(item.channel)
                        },
                    )
                }

                is SearchResultItem.PlaylistResult -> {
                    PlaylistCard(
                        item.playlist,
                        onClick = {
                            onPlaylistClick(item.playlist)
                        },
                        showSaveAction = true,
                        isSaved = item.playlist.id in savedPlaylistIds,
                        onSaveClick = { onToggleSavePlaylist(item.playlist) },
                    )
                }

                is SearchResultItem.ShortsShelfResult -> {
                    ShortsShelf(shorts = item.shorts, onShortClick = onVideoClick)
                }

                null -> {}
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingFooter(
                pagingItems.loadState.append,
                pagingItems::retry,
                pagingItems.itemCount,
            )
        }
    }
}

@Composable
private fun PagingFooter(
    appendState: LoadState,
    onRetry: () -> Unit,
    itemCount: Int,
) {
    when {
        appendState is LoadState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.loading_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        appendState is LoadState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    appendState.error.localizedMessage ?: stringResource(R.string.load_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        appendState.endOfPaginationReached && itemCount > 0 -> {
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.end_of_results),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.5f,
                            ),
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShimmerResultsScreen(
    isGrid: Boolean,
    columns: Int,
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(8, key = { "shimmer_$it" }, contentType = { "shimmer" }) { ShimmerGridVideoCard() }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = if (columns == 1) 0.dp else 16.dp,
                    end = if (columns == 1) 0.dp else 16.dp,
                    top = 8.dp,
                    bottom = 80.dp,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    if (columns == 1) 0.dp else 12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    if (columns == 1) 0.dp else 12.dp,
                ),
        ) {
            items(8, key = { "shimmer_$it" }, contentType = { "shimmer" }) {
                if (columns == 1) {
                    ShimmerVideoCardFullWidth()
                } else {
                    ShimmerGridVideoCard()
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    searchHistory: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.recent_searches),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(
                            stringResource(R.string.clear_search_history),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            items(searchHistory.take(8), key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    onClick = { onHistoryClick(item.query) },
                    onDelete = { onHistoryDelete(item) },
                )
            }
            item {
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (searchHistory.isEmpty()) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint =
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.2f,
                                ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.search_empty_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: SearchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.type == SearchType.VOICE) {
                Icons.Filled.Mic
            } else {
                Icons.Filled.History
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            item.query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SuggestionsCard(
    query: String,
    suggestions: List<String>,
    isLoading: Boolean,
    onSuggestionClick: (String) -> Unit,
    onFillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            if (isLoading && suggestions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            items(suggestions, key = { it }) { s ->
                SuggestionRow(
                    s,
                    query,
                    { onSuggestionClick(s) },
                    { onFillClick(s) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    query: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            buildAnnotatedString {
                val lo = suggestion.lowercase()
                val qlo = query.lowercase()
                val idx = lo.indexOf(qlo)
                if (idx >= 0) {
                    append(suggestion.substring(0, idx))
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        append(
                            suggestion.substring(idx, idx + query.length),
                        )
                    }
                    append(suggestion.substring(idx + query.length))
                } else {
                    append(suggestion)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onFill, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.NorthWest,
                "Fill",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
@Composable
private fun SearchErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "Search Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

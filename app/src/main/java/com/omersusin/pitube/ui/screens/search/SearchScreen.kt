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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
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
    val musicEnabled by viewModel.musicCategoriesEnabled.collectAsState()
    val musicResults by viewModel.musicResults.collectAsState()
    val searchChipOrder by viewModel.searchChipOrder.collectAsState()
    val searchGridMode by viewModel.searchGridMode.collectAsState()
    val searchChipHidden by viewModel.searchChipHidden.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val discoverViewModel: DiscoverViewModel = hiltViewModel()
    val discoverState by discoverViewModel.state.collectAsState()
    val discoverChipOrder by discoverViewModel.chipOrder.collectAsState()
    val discoverChipHidden by discoverViewModel.chipHidden.collectAsState()

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

    val selectedContentType = uiState.filters?.contentType ?: ContentType.ALL
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchContentChips(
                selectedContentType = selectedContentType,
                onContentTypeSelected = { type ->
                    val base = uiState.filters ?: SearchFilter()
                    viewModel.updateFilters(base.copy(contentType = type))
                },
                musicCategory = uiState.musicCategory,
                onMusicCategorySelected = viewModel::selectMusicCategory,
                musicEnabled = musicEnabled,
                chipOrder = searchChipOrder,
                hiddenChips = searchChipHidden,
                modifier = Modifier.weight(1f),
            )
            if (selectedContentType == ContentType.VIDEOS && uiState.musicCategory == null) {
                FilterFunnelButton(
                    hasActiveFilters = (uiState.filters ?: SearchFilter()).hasActiveVideoFilters(),
                    onClick = { showFilterSheet = true },
                )
            }
        }

        if (showFilterSheet) {
            VideoFilterBottomSheet(
                current = uiState.filters ?: SearchFilter(),
                onApply = { filters ->
                    viewModel.updateFilters(filters)
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false },
            )
        }

        if (!hasQuery) {
            DiscoverScreen(
                searchHistory = searchHistory,
                discoverState = discoverState,
                chipOrder = discoverChipOrder,
                hiddenChips = discoverChipHidden,
                isGrid = searchGridMode,
                onToggleGrid = viewModel::toggleSearchGridMode,
                onLoadMoreDiscover = discoverViewModel::loadMore,
                onTopicClick = { topic ->
                    dismissKeyboard()
                    setSearchQueryToEnd(topic)
                    viewModel.search(topic, activeOrPendingFilters)
                },
                onVideoClick = navigateToVideo,
                onChannelClick = navigateToChannel,
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
        } else if (uiState.musicCategory != null) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                MusicResultsList(
                    category = uiState.musicCategory!!,
                    results = musicResults,
                    onVideoClick = navigateToVideo,
                    onChannelClick = navigateToChannel,
                    onRetry = { viewModel.selectMusicCategory(uiState.musicCategory) },
                    onLoadMore = viewModel::loadMoreMusicResults,
                )
            }
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

            val grouped = selectedContentType == ContentType.ALL
            if (!grouped && !isInitialLoading && !isInitialError && !isEmptyResults) {
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
                            grouped = grouped,
                            onSelectTab = { type ->
                                viewModel.updateFilters(
                                    (uiState.filters ?: SearchFilter()).copy(contentType = type),
                                )
                            },
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

@Composable
private fun SearchContentChips(
    selectedContentType: ContentType,
    onContentTypeSelected: (ContentType) -> Unit,
    musicCategory: MusicCategory?,
    onMusicCategorySelected: (MusicCategory?) -> Unit,
    musicEnabled: Boolean,
    chipOrder: List<String>,
    hiddenChips: Set<String>,
    modifier: Modifier = Modifier,
) {
    data class ChipRef(
        val key: String,
        val label: String,
        val selected: Boolean,
        val onClick: () -> Unit,
    )

    val chips =
        buildList {
            add(
                ChipRef(
                    "all",
                    stringResource(R.string.tab_all),
                    selected = selectedContentType == ContentType.ALL && musicCategory == null,
                ) { onContentTypeSelected(ContentType.ALL) },
            )
            add(
                ChipRef(
                    "videos",
                    stringResource(R.string.videos_header),
                    selected = selectedContentType == ContentType.VIDEOS && musicCategory == null,
                ) { onContentTypeSelected(ContentType.VIDEOS) },
            )
            if (musicEnabled) {
                add(
                    ChipRef(
                        "songs",
                        stringResource(R.string.tab_songs),
                        selected = musicCategory == MusicCategory.SONGS,
                    ) { onMusicCategorySelected(MusicCategory.SONGS) },
                )
                add(
                    ChipRef(
                        "artists",
                        stringResource(R.string.tab_artists),
                        selected = musicCategory == MusicCategory.ARTISTS,
                    ) { onMusicCategorySelected(MusicCategory.ARTISTS) },
                )
            }
            add(
                ChipRef(
                    "playlists",
                    stringResource(R.string.tab_playlists),
                    selected = selectedContentType == ContentType.PLAYLISTS && musicCategory == null,
                ) { onContentTypeSelected(ContentType.PLAYLISTS) },
            )
            add(
                ChipRef(
                    "channels",
                    stringResource(R.string.channels_header),
                    selected = selectedContentType == ContentType.CHANNELS && musicCategory == null,
                ) { onContentTypeSelected(ContentType.CHANNELS) },
            )
        }

    // User customization wins: hidden chips dropped, then ordered (missing
    // keys keep their default position after the explicitly ordered ones).
    val visibleChips = chips.filter { it.key !in hiddenChips }
    val orderedChips =
        if (chipOrder.isEmpty()) {
            visibleChips
        } else {
            visibleChips.sortedBy { chip ->
                chipOrder.indexOf(chip.key).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
        }
    // Everything hidden → the strip disappears entirely (user request).
    if (orderedChips.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(orderedChips, key = { it.key }) { chip ->
            ContentFilterChip(
                title = chip.label,
                isSelected = chip.selected,
                onClick = chip.onClick,
            )
        }
    }
}

/**
 * Funnel entry for the video-filter bottom sheet; the dot badge signals any
 * non-default filter without opening it.
 */
@Composable
private fun FilterFunnelButton(
    hasActiveFilters: Boolean,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = stringResource(R.string.filter_sheet_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasActiveFilters) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** Radio row shared by the sheet's single-choice sections. */
@Composable
private fun <T> FilterRadioGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        options.forEach { (value, label) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Video search filters in a bottom sheet: upload date + duration radios and a
 * multi-select feature group. Apply commits to the ViewModel (server-side
 * InnerTube filtering); Reset clears the draft in place.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VideoFilterBottomSheet(
    current: SearchFilter,
    onApply: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftDate by remember { mutableStateOf(current.uploadDate) }
    var draftDuration by remember { mutableStateOf(current.duration) }
    var draftFeatures by remember { mutableStateOf(current.features) }

    val dateOptions =
        listOf(
            UploadDate.ANY to stringResource(R.string.date_any),
            UploadDate.TODAY to stringResource(R.string.date_today),
            UploadDate.THIS_WEEK to stringResource(R.string.date_this_week),
            UploadDate.THIS_MONTH to stringResource(R.string.date_this_month),
            UploadDate.THIS_YEAR to stringResource(R.string.date_this_year),
        )
    val durationOptions =
        listOf(
            Duration.ANY to stringResource(R.string.duration_any),
            Duration.UNDER_4_MINUTES to stringResource(R.string.duration_under_4),
            Duration.FROM_4_TO_20_MINUTES to stringResource(R.string.duration_4_20),
            Duration.OVER_20_MINUTES to stringResource(R.string.duration_over_20),
        )
    val featureLabels =
        mapOf(
            SearchFeature.LIVE to R.string.feature_live,
            SearchFeature.HD to R.string.feature_hd,
            SearchFeature.FOUR_K to R.string.feature_4k,
            SearchFeature.HDR to R.string.feature_hdr,
            SearchFeature.SUBTITLES to R.string.feature_subtitles,
            SearchFeature.CREATIVE_COMMONS to R.string.feature_cc,
            SearchFeature.SPHERICAL_360 to R.string.feature_360,
        )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.filter_section_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FilterRadioGroup(dateOptions, draftDate) { draftDate = it }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.filter_section_duration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FilterRadioGroup(durationOptions, draftDuration) { draftDuration = it }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.filter_section_features),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                featureLabels.forEach { (feature, labelRes) ->
                    val selected = feature in draftFeatures
                    ContentFilterChip(
                        title = stringResource(labelRes),
                        isSelected = selected,
                        onClick = {
                            draftFeatures =
                                if (selected) draftFeatures - feature else draftFeatures + feature
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    draftDate = UploadDate.ANY
                    draftDuration = Duration.ANY
                    draftFeatures = emptySet()
                }) {
                    Text(stringResource(R.string.btn_reset))
                }
                Button(
                    onClick = {
                        onApply(
                            current.copy(
                                uploadDate = draftDate,
                                duration = draftDuration,
                                features = draftFeatures,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.btn_save))
                }
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

/** One rendered row of the results grid: either a raw paging index or a synthetic section row. */
private data class GroupedRow(val kind: Int, val index: Int = -1) {
    companion object {
        const val HEADER_CHANNELS = 0
        const val HEADER_PLAYLISTS = 1
        const val HEADER_VIDEOS = 2
        const val SEE_ALL_CHANNELS = 3
        const val SEE_ALL_PLAYLISTS = 4
        const val RAW = 5
    }
}

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
    grouped: Boolean,
    onSelectTab: (ContentType) -> Unit,
) {
    // Hybrid grouped layout ("Tümü" tab): hoist the channel and playlist hits
    // into digest sections above the paged video stream, Koda-style.
    val rows: List<Any> =
        remember(pagingItems.itemCount, grouped) {
            if (!grouped) {
                (0 until pagingItems.itemCount).toList()
            } else {
                val channelIndices =
                    (0 until pagingItems.itemCount).filter { pagingItems.peek(it) is SearchResultItem.ChannelResult }
                val playlistIndices =
                    (0 until pagingItems.itemCount).filter { pagingItems.peek(it) is SearchResultItem.PlaylistResult }
                buildList {
                    if (channelIndices.isNotEmpty()) {
                        add(GroupedRow(GroupedRow.HEADER_CHANNELS))
                        channelIndices.forEach { add(it) }
                        add(GroupedRow(GroupedRow.SEE_ALL_CHANNELS))
                    }
                    if (playlistIndices.isNotEmpty()) {
                        add(GroupedRow(GroupedRow.HEADER_PLAYLISTS))
                        playlistIndices.forEach { add(it) }
                        add(GroupedRow(GroupedRow.SEE_ALL_PLAYLISTS))
                    }
                    add(GroupedRow(GroupedRow.HEADER_VIDEOS))
                    val hoisted = channelIndices.toSet() + playlistIndices.toSet()
                    (0 until pagingItems.itemCount).filterNot { it in hoisted }.forEach { add(it) }
                }
            }
        }

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
            count = rows.size,
            key = { r ->
                when (val row = rows[r]) {
                    is Int -> searchItemKey(pagingItems.peek(row), row)
                    is GroupedRow ->
                        when (row.kind) {
                            GroupedRow.HEADER_CHANNELS -> "hdr_channels"
                            GroupedRow.HEADER_PLAYLISTS -> "hdr_playlists"
                            GroupedRow.HEADER_VIDEOS -> "hdr_videos"
                            GroupedRow.SEE_ALL_CHANNELS -> "see_all_channels"
                            else -> "see_all_playlists"
                        }
                    else -> "row_$r"
                }
            },
            contentType = { r ->
                when (val row = rows[r]) {
                    is Int -> searchItemContentType(pagingItems.peek(row))
                    is GroupedRow ->
                        if (row.kind == GroupedRow.RAW) "" else "section"
                    else -> "placeholder"
                }
            },
        ) { r ->
            when (val row = rows[r]) {
                is GroupedRow ->
                    when (row.kind) {
                        GroupedRow.HEADER_CHANNELS ->
                            GroupSectionHeader(
                                icon = Icons.Outlined.People,
                                label = stringResource(R.string.channels_header),
                                showSeeAll = true,
                                onSeeAll = { onSelectTab(ContentType.CHANNELS) },
                            )
                        GroupedRow.HEADER_PLAYLISTS ->
                            GroupSectionHeader(
                                icon = Icons.Outlined.VideoLibrary,
                                label = stringResource(R.string.tab_playlists),
                                showSeeAll = true,
                                onSeeAll = { onSelectTab(ContentType.PLAYLISTS) },
                            )
                        GroupedRow.HEADER_VIDEOS ->
                            GroupSectionHeader(
                                icon = Icons.Outlined.SmartDisplay,
                                label = stringResource(R.string.videos_header),
                                showSeeAll = false,
                                onSeeAll = {},
                            )
                        GroupedRow.SEE_ALL_CHANNELS ->
                            SeeAllRow(stringResource(R.string.channels_header)) { onSelectTab(ContentType.CHANNELS) }
                        else ->
                            SeeAllRow(stringResource(R.string.tab_playlists)) { onSelectTab(ContentType.PLAYLISTS) }
                    }

                is Int -> {
                    when (val item = pagingItems[row]) {
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
private fun GroupSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (showSeeAll) {
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.see_all), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SeeAllRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
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

private val EXPLORE_TOPICS =
    listOf(
        "gaming" to R.string.topic_gaming,
        "music" to R.string.topic_music,
        "news" to R.string.topic_news,
        "live" to R.string.topic_live,
        "podcasts" to R.string.topic_podcasts,
        "movies" to R.string.topic_movies,
        "tech" to R.string.topic_tech,
        "sports" to R.string.topic_sports,
        "learning" to R.string.topic_learning,
    )

@Composable
private fun DiscoverScreen(
    searchHistory: List<SearchHistoryItem>,
    discoverState: DiscoverViewModel.DiscoverState,
    chipOrder: List<String>,
    hiddenChips: Set<String>,
    isGrid: Boolean,
    onToggleGrid: () -> Unit,
    onLoadMoreDiscover: () -> Unit,
    onTopicClick: (String) -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
    ) {
        // Explore topics — tap a chip to search that topic (Koda's video-mode explore).
        // Everything hidden → the whole section (title + row) disappears.
        val visibleTopics =
            EXPLORE_TOPICS
                .filter { (key, _) -> key !in hiddenChips }
                .sortedBy { (key, _) ->
                    chipOrder.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
                }
        if (visibleTopics.isNotEmpty() || searchHistory.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.discover_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (visibleTopics.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleTopics, key = { it.first }) { (_, res) ->
                        val topicLabel = stringResource(res)
                        ContentFilterChip(
                            title = topicLabel,
                            isSelected = false,
                            onClick = { onTopicClick(topicLabel) },
                        )
                    }
                }
            }
        }

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
            items(searchHistory.take(8), key = { "h_${it.id}" }) { item ->
                HistoryRow(
                    item = item,
                    onClick = { onHistoryClick(item.query) },
                    onDelete = { onHistoryDelete(item) },
                )
            }
        }

        // Discover feed: personal → taste → trending, with load-more.
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(discoverSourceLabel(discoverState.source)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                // Same grid/list switch the channel page uses (shared pref).
                IconButton(onClick = onToggleGrid, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription =
                            if (isGrid) {
                                stringResource(R.string.ui_list_view)
                            } else {
                                stringResource(R.string.ui_grid_view)
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (discoverState.isLoading && discoverState.videos.isEmpty()) {
            items(4, key = { "discover_shimmer_$it" }, contentType = { "shimmer" }) {
                ShimmerVideoCardFullWidth()
            }
        } else if (discoverState.videos.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.discover_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(
                discoverState.videos,
                key = { "d_${it.id}" },
                contentType = { "discover_video" },
            ) { video ->
                if (isGrid) {
                    VideoCardFullWidth(
                        video = video,
                        useInternalPadding = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { onVideoClick(video) },
                        onChannelClick = { channelId ->
                            channelId.takeIf { it.isNotBlank() }?.let {
                                onChannelClick(
                                    Channel(
                                        id = it,
                                        name = video.channelName,
                                        thumbnailUrl = video.channelThumbnailUrl,
                                        subscriberCount = 0,
                                        url = "https://www.youtube.com/channel/$it",
                                    ),
                                )
                            }
                        },
                    )
                } else {
                    CompactVideoCard(
                        video = video,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { onVideoClick(video) },
                    )
                }
            }
            if (!discoverState.endReached) {
                // Infinite scroll: composing the sentinel fires the next page;
                // keyed on size so a visible sentinel re-fires after each page.
                item(key = "discover_more") {
                    LaunchedEffect(discoverState.videos.size) { onLoadMoreDiscover() }
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun discoverSourceLabel(source: DiscoverViewModel.Source): Int =
    when (source) {
        DiscoverViewModel.Source.PERSONALIZED -> R.string.discover_for_you
        DiscoverViewModel.Source.TASTE -> R.string.discover_taste
        else -> R.string.discover_trending
    }

@Composable
private fun MusicResultsList(
    category: MusicCategory,
    results: MusicResults,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        results.isLoading && results.songs.isEmpty() && results.artists.isEmpty() -> {
            ShimmerResultsScreen(false, 1)
        }

        results.error && results.songs.isEmpty() && results.artists.isEmpty() -> {
            SearchErrorState(
                message = stringResource(R.string.music_search_failed),
                onRetry = onRetry,
            )
        }

        category == MusicCategory.SONGS && results.songs.isEmpty() -> {
            EmptyState(
                icon = Icons.Outlined.MusicNote,
                title = stringResource(R.string.empty_results_title),
                body = stringResource(R.string.empty_results_body),
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        }

        category == MusicCategory.ARTISTS && results.artists.isEmpty() -> {
            EmptyState(
                icon = Icons.Outlined.People,
                title = stringResource(R.string.empty_results_title),
                body = stringResource(R.string.empty_results_body),
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        }

        else -> {
            val heroArtist = results.mainArtist
            val relatedArtists =
                heroArtist?.let { main -> results.artists.filter { it.id != main.id } }
                    ?: results.artists
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp),
            ) {
                if (category == MusicCategory.SONGS) {
                    items(results.songs, key = { "ms_${it.id}" }, contentType = { "song" }) { song ->
                        VideoCardFullWidth(
                            video = song,
                            modifier = Modifier.padding(vertical = 4.dp),
                            onClick = { onVideoClick(song) },
                            onChannelClick = { channelId ->
                                channelId.takeIf { it.isNotBlank() }?.let {
                                    onChannelClick(
                                        Channel(
                                            id = it,
                                            name = song.channelName,
                                            thumbnailUrl = song.channelThumbnailUrl,
                                            subscriberCount = 0,
                                            url = "https://www.youtube.com/channel/$it",
                                        ),
                                    )
                                }
                            },
                        )
                    }
                } else {
                    if (heroArtist != null) {
                        item(key = "ma_main_${heroArtist.id}") {
                            Column {
                                Text(
                                    stringResource(R.string.music_main_artist),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                )
                                SearchChannelCard(heroArtist, onClick = { onChannelClick(heroArtist) })
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                    item(key = "ma_related_header") {
                        Text(
                            stringResource(R.string.music_related_artists),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    items(relatedArtists, key = { "ma_${it.id}" }, contentType = { "artist" }) { artist ->
                        SearchChannelCard(artist, onClick = { onChannelClick(artist) })
                    }
                }
                if (!results.endReached && category == MusicCategory.SONGS) {
                    item(key = "music_more") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                            OutlinedButton(onClick = onLoadMore, enabled = !results.isLoading) {
                                if (results.isLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.load_more))
                            }
                        }
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

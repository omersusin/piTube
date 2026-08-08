package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.*
import com.omersusin.pitube.ui.components.VideoListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var dateFilter by remember { mutableStateOf(VideoSearchDateFilter.ANY) }
    var sortBy by remember { mutableStateOf(VideoSearchSort.RELEVANCE) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryRepository.getHistory(context)) }
    val scope = rememberCoroutineScope()

    fun performSearch(q: String) {
        val parsed = YouTubeLinkParser.parse(q)
        if (parsed?.videoId != null) {
            // If user pasted a URL, treat as video navigation
            scope.launch {
                try {
                    val info = PipedApiService.create().getStreams(parsed.videoId)
                    val video = VideoItem(
                        id = parsed.videoId,
                        title = info.title,
                        description = info.description,
                        thumbnailUrl = info.uploaderUrl,
                        uploader = info.uploader,
                        viewCount = 0L,
                        duration = 0
                    )
                    onVideoClick(video)
                } catch (e: Exception) { /* fall through */ }
            }
            return
        }
        if (q.isBlank()) return
        scope.launch {
            isLoading = true
            SearchHistoryRepository.addQuery(context, q)
            history = SearchHistoryRepository.getHistory(context)
            val effectiveQuery = dateFilter.applyTo(q)
            try {
                results = PipedApiService.create().search(effectiveQuery).items
            } catch (e: Exception) {
                results = emptyList()
            }
            isLoading = false
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
                    placeholder = { Text("Search videos or paste URL...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showFilterMenu = !showFilterMenu }) {
                                Text(if (dateFilter == VideoSearchDateFilter.ANY) "⚙" else "🎯")
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { performSearch(query) }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch(query) })
                )
                if (showFilterMenu) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Filter: ", style = MaterialTheme.typography.bodySmall)
                        VideoSearchDateFilter.values().forEach { f ->
                            FilterChip(
                                selected = dateFilter == f,
                                onClick = { dateFilter = f },
                                label = { Text(f.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Sort: ", style = MaterialTheme.typography.bodySmall)
                        VideoSearchSort.values().forEach { s ->
                            FilterChip(
                                selected = sortBy == s,
                                onClick = { sortBy = s },
                                label = { Text(s.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            item { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (results.isEmpty() && query.isEmpty()) {
            item { Text("Recent Searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(history) { h ->
                ListItem(
                    headlineContent = { Text(h) },
                    modifier = Modifier.clickable {
                        query = h
                        performSearch(h)
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            SearchHistoryRepository.removeQuery(context, h)
                            history = SearchHistoryRepository.getHistory(context)
                        }) { Icon(Icons.Default.Close, "Remove") }
                    }
                )
            }
        } else {
            items(results) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onChannelClick = { onChannelClick(video.uploaderName) }
                )
            }
        }
    }
}

package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.InnerTubeClient
import com.omersusin.pitube.data.SearchHistoryRepository
import com.omersusin.pitube.data.StreamResolver
import com.omersusin.pitube.data.VideoItem
import com.omersusin.pitube.data.YouTubeLinkParser
import com.omersusin.pitube.ui.components.VideoListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var isGrid by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf("Any") }
    var selectedUploadDate by remember { mutableStateOf("Any") }
    var selectedSort by remember { mutableStateOf("Relevance") }
    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryRepository.getHistory(context)) }
    val scope = rememberCoroutineScope()

    val durationOptions = listOf("Any", "Short (< 4 min)", "Medium (4-20 min)", "Long (> 20 min)")
    val uploadDateOptions = listOf("Any", "Hour", "Today", "This week", "This month", "This year")
    val sortOptions = listOf("Relevance", "Upload date", "View count", "Rating")

    fun performSearch(q: String) {
        if (q.isBlank()) return
        scope.launch {
            isLoading = true; hasSearched = true
            SearchHistoryRepository.addQuery(context, q)
            history = SearchHistoryRepository.getHistory(context)
            val parsed = YouTubeLinkParser.parse(q)
            if (parsed?.videoId != null) {
                try {
                    val r = StreamResolver.resolve(parsed.videoId, context)
                    results = listOf(VideoItem(
                        url = "https://www.youtube.com/watch?v=${parsed.videoId}",
                        title = r?.title ?: "Video", thumbnailUrl = null, uploaderName = r?.uploader ?: "YouTube",
                        uploaderAvatar = null, duration = 0, views = 0L, uploadedDate = null, isShort = false
                    ))
                } catch (e: Exception) { results = emptyList() }
            } else {
                // Build search query with filters
                val searchQuery = buildString {
                    append(q)
                    if (selectedUploadDate != "Any") {
                        when (selectedUploadDate) {
                            "Hour" -> append(" E")      // last hour
                            "Today" -> append(" D")     // today
                            "This week" -> append(" W") // this week
                            "This month" -> append(" M") // this month
                            "This year" -> append(" Y")  // this year
                        }
                    }
                    if (selectedDuration != "Any") {
                        when (selectedDuration) {
                            "Short (< 4 min)" -> append(" short")
                            "Medium (4-20 min)" -> append(" medium")
                            "Long (> 20 min)" -> append(" long")
                        }
                    }
                }
                try { results = InnerTubeClient.searchVideos(context, searchQuery) } catch (e: Exception) { results = emptyList() }
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search videos or paste a YouTube link...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Default.FilterList, "Filters") }
                    IconButton(onClick = { isGrid = !isGrid }) {
                        Icon(if (isGrid) Icons.Default.List else Icons.Default.GridView, "Toggle view")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch(query) }),
            shape = RoundedCornerShape(24.dp)
        )

        if (showFilters) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Upload Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uploadDateOptions) { option ->
                        FilterChip(
                            selected = selectedUploadDate == option,
                            onClick = { selectedUploadDate = option },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(durationOptions) { option ->
                        FilterChip(
                            selected = selectedDuration == option,
                            onClick = { selectedDuration = option },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sort By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortOptions) { option ->
                        FilterChip(
                            selected = selectedSort == option,
                            onClick = { selectedSort = option },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!hasSearched) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (history.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent Searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { SearchHistoryRepository.clearHistory(context); history = emptyList() }) { Text("Clear all") }
                        }
                    }
                    items(history) { h ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(h, modifier = Modifier.weight(1f).clickable { query = h; performSearch(h) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { SearchHistoryRepository.removeQuery(context, h); history = SearchHistoryRepository.getHistory(context) }) { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp)) }
                        }
                    }
                } else {
                    item { Text("Search for videos...", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results) { video ->
                    VideoListItem(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.channelId ?: video.uploaderUrl?.substringAfter("/channel/")?.substringBefore("/") ?: "") })
                }
            }
        }
    }
}

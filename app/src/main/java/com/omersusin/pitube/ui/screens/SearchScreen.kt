package com.omersusin.pitube.ui.screens

import com.omersusin.pitube.data.SearchHistoryRepository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.SearchHistoryManager
import com.omersusin.pitube.data.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Composable
fun SearchScreen(onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(SearchHistoryRepository.getHistory(LocalContext.current)) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search videos...") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                SearchHistoryRepository.addQuery(context, query)
                                history = SearchHistoryRepository.getHistory(context)
                                results = PipedApiService.create().search(query).items
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                }
            )
        }
        
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (results.isEmpty() && query.isEmpty()) {
            item {
                Text("Recent Searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            items(history) { h ->
                ListItem(
                    headlineContent = { Text(h) },
                    modifier = Modifier.clickable {
                        query = h
                        scope.launch {
                            isLoading = true
                            results = PipedApiService.create().search(h).items
                            isLoading = false
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            SearchHistoryRepository.removeQuery(context, h)
                            history = SearchHistoryRepository.getHistory(context)
                        }) {
                            Icon(Icons.Default.Close, "Remove")
                        }
                    }
                )
            }
        } else {
            items(results) { video ->
                VideoListItem(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.uploaderName) })
            }
        }
    }
}

fun SearchScreen(onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchHistory = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { searchHistory.clear(); searchHistory.addAll(SearchHistoryManager.getHistory(context)) }

    val performSearch: (String) -> Unit = { query ->
        if (query.isNotBlank()) {
            searchQuery = query
            isSearching = true
            hasSearched = true
            SearchHistoryManager.addToHistory(context, query)
            searchHistory.clear()
            searchHistory.addAll(SearchHistoryManager.getHistory(context))
            scope.launch {
                try { searchResults = PipedApiService.create().search(query).items }
                catch (e: Exception) { searchResults = emptyList() }
                isSearching = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search piTube...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") } }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }), shape = RoundedCornerShape(24.dp))
        if (isSearching) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (!hasSearched) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (searchHistory.isNotEmpty()) {
                    item { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Recent Searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { SearchHistoryManager.clearHistory(context); searchHistory.clear() }) { Text("Clear all") } }; Spacer(modifier = Modifier.height(8.dp)) }
                    items(searchHistory) { history ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = history, modifier = Modifier.weight(1f).clickable { performSearch(history) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { SearchHistoryManager.removeFromHistory(context, history); searchHistory.remove(history) }) { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        }
                    }
                } else { item { Text("Search for videos...", style = MaterialTheme.typography.bodyLarge) } }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) { items(searchResults) { video -> SearchVideoCard(video = video, onClick = { onVideoClick(video) }) } }
        }
    }
}

@Composable
fun SearchVideoCard(video: VideoItem, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = video.safeThumb, contentDescription = video.title, modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${video.uploaderName}${video.uploadedDate?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

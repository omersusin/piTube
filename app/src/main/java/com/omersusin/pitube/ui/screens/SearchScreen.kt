package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ime.ImeAction
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
    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryRepository.getHistory(context)) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search videos...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
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
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch {
                        isLoading = true
                        SearchHistoryRepository.addQuery(context, query)
                        history = SearchHistoryRepository.getHistory(context)
                        results = PipedApiService.create().search(query).items
                        isLoading = false
                    }
                })
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
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onChannelClick = { onChannelClick(video.uploaderName) }
                )
            }
        }
    }
}

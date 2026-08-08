package com.omersusin.pitube.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.PipedApiService
import com.omersusin.pitube.data.SearchHistoryRepository
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
    val context = LocalContext.current
    var history by remember { mutableStateOf(SearchHistoryRepository.getHistory(context)) }
    val scope = rememberCoroutineScope()

    fun performSearch(q: String) {
        if (q.isBlank()) return
        scope.launch {
            isLoading = true; hasSearched = true
            SearchHistoryRepository.addQuery(context, q)
            history = SearchHistoryRepository.getHistory(context)
            val parsed = YouTubeLinkParser.parse(q)
            if (parsed?.videoId != null) {
                try {
                    val info = PipedApiService.create().getStreams(parsed.videoId)
                    results = listOf(VideoItem(
                        url = "https://www.youtube.com/watch?v=${parsed.videoId}",
                        title = info.title, thumbnailUrl = null, uploaderName = info.uploader,
                        uploaderAvatar = null, duration = 0, views = 0L, uploadedDate = null, isShort = false
                    ))
                } catch (e: Exception) { results = emptyList() }
            } else {
                try { results = PipedApiService.create().search(q).items } catch (e: Exception) { results = emptyList() }
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
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch(query) }),
            shape = RoundedCornerShape(24.dp)
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!hasSearched) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (history.isNotEmpty()) {
                    item { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent Searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { SearchHistoryRepository.clearHistory(context); history = emptyList() }) { Text("Clear all") }
                    } }
                    items(history) { h ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(h, modifier = Modifier.weight(1f).clickable { query = h; performSearch(h) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { SearchHistoryRepository.removeQuery(context, h); history = SearchHistoryRepository.getHistory(context) }) { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp)) }
                        }
                    }
                } else { item { Text("Search for videos...", style = MaterialTheme.typography.bodyLarge) } }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results) { video ->
                    VideoListItem(video = video, onClick = { onVideoClick(video) }, onChannelClick = { onChannelClick(video.uploaderName) })
                }
            }
        }
    }
}

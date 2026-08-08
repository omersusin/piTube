package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.HistoryManager
import com.omersusin.pitube.data.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(HistoryManager.getHistory(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Watch History") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = { IconButton(onClick = { HistoryManager.clear(context); history = mutableListOf() }) { Icon(Icons.Default.Delete, contentDescription = "Clear") } }
        )
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No watch history") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { video ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = video.safeThumb, contentDescription = null, modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(video.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

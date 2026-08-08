package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.HistoryManager
import com.omersusin.pitube.data.WatchHistoryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val history = remember { HistoryManager.getHistory(context) }
    val watchHistory = remember { WatchHistoryRepository.getHistory(context) }
    
    val totalPlays = history.size
    val uniqueChannels = history.map { it.uploaderName }.distinct().size
    val avgWatchTimeMs = if (watchHistory.isNotEmpty()) {
        watchHistory.map { it.watchDurationMs }.average().toLong()
    } else 0L
    
    val topChannels = history.groupBy { it.uploaderName }
        .map { (channel, videos) -> Pair(channel, videos.size) }
        .sortedByDescending { it.second }
        .take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Plays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(totalPlays.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Unique Channels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(uniqueChannels.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Avg Watch Time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("${avgWatchTimeMs / 1000}s", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            if (topChannels.isNotEmpty()) {
                item {
                    Text("Top Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
                
                items(topChannels) { pair ->
                    val channel = pair.first
                    val count = pair.second
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${topChannels.indexOf(pair) + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(channel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("$count plays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

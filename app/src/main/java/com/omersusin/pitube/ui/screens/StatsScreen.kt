package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.StatsRepository
import com.omersusin.pitube.data.WatchHistoryRepository

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val stats by remember { mutableStateOf(StatsRepository.stats(context)) }
    val history by remember { mutableStateOf(WatchHistoryRepository.getHistory(context)) }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Statistics", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total Plays", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.totalPlays}", style = MaterialTheme.typography.headlineMedium)
                        }
                        Column {
                            Text("Day Streak", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.streak} 🔥", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Completed", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.completedVideos}", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text("Avg Watch", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.avgWatchTime / 1000}s", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        
        item {
            Text("Top Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        
        items(stats.topChannels) { (channel, count) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(channel, style = MaterialTheme.typography.bodyLarge)
                    Text("${count} plays", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        item {
            Text("Recent Watches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        
        items(history.take(10)) { entry ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(entry.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${entry.completionPercentage().toInt()}% • ${entry.watchDurationMs / 1000}s", 
                         style = MaterialTheme.typography.labelSmall, 
                         color = if (entry.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.LocalSubs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var subs by remember { mutableStateOf(LocalSubs.getAll(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Manage Subscriptions") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        if (subs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No local subscriptions yet.\nSubscribe from a channel page.") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subs) { sub ->
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = sub.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(sub.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { LocalSubs.unsubscribe(context, sub.channelId); subs = LocalSubs.getAll(context) }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                }
            }
        }
    }
}

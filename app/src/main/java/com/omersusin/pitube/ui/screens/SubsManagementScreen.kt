package com.omersusin.pitube.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.LocalSubs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var subs by remember { mutableStateOf(LocalSubs.all(context)) }
    var message by remember { mutableStateOf("") }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                val added = LocalSubs.import(context, text)
                message = "Imported $added channels"
                subs = LocalSubs.all(context)
            } catch (e: Exception) { message = "Import failed: ${e.message}" }
        }
    }
    
    Scaffold(topBar = {
        TopAppBar(title = { Text("Manage Subscriptions") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                IconButton(onClick = { importLauncher.launch("*/*") }) { Icon(Icons.Default.FileUpload, contentDescription = "Import") }
                IconButton(onClick = {
                    val json = LocalSubs.exportNewPipe(context)
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, json); putExtra(Intent.EXTRA_SUBJECT, "Subscriptions") }
                    context.startActivity(Intent.createChooser(intent, "Share"))
                }) { Icon(Icons.Default.Share, contentDescription = "Export") }
            })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotBlank()) { Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
            Text("${subs.size} subscriptions", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subs) { sub ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.bodyLarge)
                            Text(sub.channelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { LocalSubs.remove(context, sub.channelId); subs = LocalSubs.all(context) }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }
}

package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

private val DEFAULT_ORDER = listOf("like","save","download","background","share_group","lyrics")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionRowSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val orderCsv by prefs.actionRowOrder.collectAsState(initial = "")
    val grouped by prefs.actionRowGrouped.collectAsState(initial = true)
    val visibilityCsv by prefs.actionRowVisibility.collectAsState(initial = "")
    val knownIds = DEFAULT_ORDER + listOf("share", "copy_link", "copy_at_time")
    val order = remember(orderCsv) {
        val parsed = orderCsv.split(",").filter { it.isNotBlank() && it in knownIds }
        if (parsed.isEmpty()) DEFAULT_ORDER else parsed + DEFAULT_ORDER.filter { it !in parsed }
    }
    val visibility = remember(visibilityCsv) { if (visibilityCsv.isBlank()) order.associateWith { true } else visibilityCsv.split(",").associate { val p = it.split(":"); p[0] to (p.getOrNull(1) != "0") } }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = "Action Row", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SectionHeader(text = "Grouping") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(icon = Icons.Filled.Share, title = "Group share actions", subtitle = "Collapse Share / Copy link / Copy at time into one chip", checked = grouped, onCheckedChange = { scope.launch { prefs.setActionRowGrouped(it) } })
                }
                Text("When off, three separate chips show.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            item { SectionHeader(text = "Order & visibility (drag handle to reorder)") }
            item {
                com.omersusin.pitube.ui.screens.settings.components.DragReorderColumn(
                    items = order,
                    itemKey = { it },
                    onMove = { from, to ->
                        val n = order.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) }
                        scope.launch { prefs.setActionRowOrder(n.joinToString(",")) }
                    },
                ) { id ->
                    val visible = visibility[id] ?: true
                    val label = when (id) { "like" -> "Like / Dislike"; "save" -> "Save"; "download" -> "Download"; "background" -> "Background"; "share_group" -> "Share group"; "lyrics" -> "Lyrics"; else -> id }
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(if (visible) "Visible" else "Hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(checked = visible, onCheckedChange = { v ->
                            val nv = order.associateWith { visibility[it] ?: true }.toMutableMap().also { it[id] = v }
                            scope.launch { prefs.setActionRowVisibility(nv.entries.joinToString(",") { (k, vv) -> "$k:${if (vv) 1 else 0}" }) }
                        })
                    }
                }
            }
            item {
                OutlinedButton(onClick = { scope.launch { prefs.setActionRowOrder(DEFAULT_ORDER.joinToString(",")); prefs.setActionRowVisibility(""); prefs.setActionRowGrouped(true) } }, modifier = Modifier.fillMaxWidth()) { Text("Reset to default") }
            }
        }
    }
}

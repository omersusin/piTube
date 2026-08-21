package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

private val DEFAULT_ORDER = listOf("like","save","download","background","share_group","lyrics")
private val SHARE_MEMBERS = setOf("share","copy_link","copy_at_time")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionRowSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val orderCsv by prefs.actionRowOrder.collectAsState(initial = "")
    val grouped by prefs.actionRowGrouped.collectAsState(initial = true)
    val visibilityCsv by prefs.actionRowVisibility.collectAsState(initial = "")
    val order = remember(orderCsv) { if (orderCsv.isBlank()) DEFAULT_ORDER else orderCsv.split(",").filter { it.isNotBlank() } }
    val visibility = remember(visibilityCsv) { if (visibilityCsv.isBlank()) order.associateWith { true } else visibilityCsv.split(",").associate { val p = it.split(":"); p[0] to (p.getOrNull(1) != "0") } }

    Scaffold(topBar = { TopAppBar(title = { Text("Action Row") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Group share / copy link / copy at time")
                    Switch(checked = grouped, onCheckedChange = { scope.launch { prefs.setActionRowGrouped(it) } })
                }
                Text("Ungrouped shows 3 chips; grouped shows one Share chip opening a sheet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Reorder & visibility", style = MaterialTheme.typography.titleSmall)
            }
            itemsIndexed(order) { idx, id ->
                val visible = visibility[id] ?: true
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(id, modifier = Modifier.weight(1f))
                        Switch(checked = visible, onCheckedChange = { v ->
                            val nv = order.associateWith { visibility[it] ?: true }.toMutableMap().also { it[id] = v }
                            scope.launch { prefs.setActionRowVisibility(nv.entries.joinToString(",") { (k, vv) -> "$k:${if (vv) 1 else 0}" }) }
                        })
                        IconButton(onClick = {
                            if (idx > 0) { val n = order.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) }; scope.launch { prefs.setActionRowOrder(n.joinToString(",")) } }
                        }, enabled = idx > 0) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = {
                            if (idx < order.lastIndex) { val n = order.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) }; scope.launch { prefs.setActionRowOrder(n.joinToString(",")) } }
                        }, enabled = idx < order.lastIndex) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            item {
                TextButton(onClick = { scope.launch { prefs.setActionRowOrder(DEFAULT_ORDER.joinToString(",")); prefs.setActionRowVisibility(""); prefs.setActionRowGrouped(true) } }) { Text("Reset to default") }
            }
        }
    }
}

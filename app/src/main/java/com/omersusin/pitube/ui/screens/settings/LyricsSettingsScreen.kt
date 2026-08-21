package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.local.LyricsAnimationStyle
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val anim by prefs.lyricsAnimation.collectAsState(initial = LyricsAnimationStyle.VIVIMUSIC_FLUID.name)
    val pos by prefs.lyricsTextPosition.collectAsState(initial = "CENTER")
    val glow by prefs.lyricsGlowEnabled.collectAsState(initial = true)
    val blur by prefs.lyricsStandardBlur.collectAsState(initial = 0f)
    val size by prefs.lyricsTextSize.collectAsState(initial = 20f)
    val spacing by prefs.lyricsLineSpacing.collectAsState(initial = 1.4f)
    val autoScroll by prefs.lyricsAutoScroll.collectAsState(initial = true)
    val changeOnClick by prefs.lyricsChangeOnClick.collectAsState(initial = false)
    val swipe by prefs.lyricsSwipeToChangeSong.collectAsState(initial = false)
    val showPP by prefs.lyricsShowPlayPauseOnThumbnail.collectAsState(initial = true)
    val order by prefs.lyricsProviderOrder.collectAsState(initial = "lrclib,kugou,transcript")
    Scaffold(topBar = { TopAppBar(title = { Text("Lyrics") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Animation", style = MaterialTheme.typography.titleMedium)
                LyricsAnimationStyle.values().forEach { s ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = s.name == anim, onClick = { scope.launch { prefs.setLyricsAnimation(s) } })
                        TextButton(onClick = { scope.launch { prefs.setLyricsAnimation(s) } }) { Text(s.name) }
                    }
                }
            }
            item { HorizontalDivider() }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Text position: $pos"); TextButton(onClick = { scope.launch { prefs.setLyricsTextPosition(if (pos == "CENTER") "TOP" else if (pos == "TOP") "BOTTOM" else "CENTER") } }) { Text("Change") } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Glow"); Switch(checked = glow, onCheckedChange = { scope.launch { prefs.setLyricsGlowEnabled(it) } }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Text size: ${size.toInt()}"); Slider(value = size, onValueChange = { scope.launch { prefs.setLyricsTextSize(it) } }, valueRange = 12f..28f, modifier = Modifier.width(160.dp)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Line spacing: $spacing"); Slider(value = spacing, onValueChange = { scope.launch { prefs.setLyricsLineSpacing(it) } }, valueRange = 0.8f..2.2f, modifier = Modifier.width(160.dp)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Blur: $blur"); Slider(value = blur, onValueChange = { scope.launch { prefs.setLyricsStandardBlur(it) } }, valueRange = 0f..1f, modifier = Modifier.width(160.dp)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Auto scroll"); Switch(checked = autoScroll, onCheckedChange = { scope.launch { prefs.setLyricsAutoScroll(it) } }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Change on click"); Switch(checked = changeOnClick, onCheckedChange = { scope.launch { prefs.setLyricsChangeOnClick(it) } }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Swipe to change song"); Switch(checked = swipe, onCheckedChange = { scope.launch { prefs.setLyricsSwipeToChangeSong(it) } }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Show play/pause on thumbnail"); Switch(checked = showPP, onCheckedChange = { scope.launch { prefs.setLyricsShowPlayPauseOnThumbnail(it) } }) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Provider order: $order"); TextButton(onClick = { scope.launch { prefs.setLyricsProviderOrder(if (order.startsWith("lrclib")) "transcript,lrclib,kugou" else "lrclib,kugou,transcript") } }) { Text("Toggle") } } }
        }
    }
}

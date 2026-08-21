package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionAppearanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val cardStyle by prefs.recognitionCardStyle.collectAsState(initial = "default")
    val radius by prefs.recognitionCardCornerRadius.collectAsState(initial = 20f)
    val artSize by prefs.recognitionArtSize.collectAsState(initial = 72)
    Scaffold(topBar = { TopAppBar(title = { Text("Recognition Appearance") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Card style: $cardStyle"); Row { listOf("default","compact","full").forEach { s -> FilterChip(selected = s == cardStyle, onClick = { scope.launch { prefs.setRecognitionCardStyle(s) } }, label = { Text(s) }, modifier = Modifier.padding(end = 8.dp)) } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Corner radius: ${radius.toInt()}"); Slider(value = radius, onValueChange = { scope.launch { prefs.setRecognitionCardCornerRadius(it) } }, valueRange = 8f..28f, modifier = Modifier.width(160.dp)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Art size: $artSize"); Slider(value = artSize.toFloat(), onValueChange = { scope.launch { prefs.setRecognitionArtSize(it.toInt()) } }, valueRange = 48f..96f, steps = 5, modifier = Modifier.width(160.dp)) } }
        }
    }
}

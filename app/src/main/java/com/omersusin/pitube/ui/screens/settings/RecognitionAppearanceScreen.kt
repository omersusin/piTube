package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionAppearanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val cardStyle by prefs.recognitionCardStyle.collectAsState(initial = "default")
    val cardTint by prefs.recognitionCardTint.collectAsState(initial = "auto")
    val floatingTint by prefs.recognitionFloatingTint.collectAsState(initial = "auto")
    val blobTint by prefs.recognitionBlobTint.collectAsState(initial = "auto")
    val voiceTint by prefs.recognitionVoiceTint.collectAsState(initial = "auto")
    val radius by prefs.recognitionCardCornerRadius.collectAsState(initial = 20f)
    val artSize by prefs.recognitionArtSize.collectAsState(initial = 72)
    val floatingSize by prefs.recognitionFloatingSize.collectAsState(initial = 64)
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = "Recognition", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SectionHeader(text = "Card") }
            item {
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Style", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("default","compact","full").forEach { s ->
                                FilterChip(selected = s == cardStyle, onClick = { scope.launch { prefs.setRecognitionCardStyle(s) } }, label = { Text(s) })
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Corner radius: ${radius.toInt()} dp", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = radius, onValueChange = { scope.launch { prefs.setRecognitionCardCornerRadius(it) } }, valueRange = 8f..28f)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Artwork size: $artSize dp", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = artSize.toFloat(), onValueChange = { scope.launch { prefs.setRecognitionArtSize(it.toInt()) } }, valueRange = 48f..96f, steps = 5)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Card accent", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto", "primary", "secondary", "tertiary").forEach { t ->
                                FilterChip(selected = t == cardTint, onClick = { scope.launch { prefs.setRecognitionCardTint(t) } }, label = { Text(t) })
                            }
                        }
                        Text(
                            "\"auto\" follows the app theme; the others tint the card with that theme accent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { SectionHeader(text = "Floating button") }
            item {
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Button size: ${floatingSize} dp", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = floatingSize.toFloat(), onValueChange = { scope.launch { prefs.setRecognitionFloatingSize(it.toInt()) } }, valueRange = 48f..96f)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Button accent", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto", "primary", "secondary", "tertiary").forEach { t ->
                                FilterChip(selected = t == floatingTint, onClick = { scope.launch { prefs.setRecognitionFloatingTint(t) } }, label = { Text(t) })
                            }
                        }
                    }
                }
            }
            item { SectionHeader(text = "Blob") }
            item {
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Blob accent", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto", "primary", "secondary", "tertiary").forEach { t ->
                                FilterChip(selected = t == blobTint, onClick = { scope.launch { prefs.setRecognitionBlobTint(t) } }, label = { Text(t) })
                            }
                        }
                        Text(
                            "\"auto\" follows the app theme; the others tint the recognition blob with that theme accent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { SectionHeader(text = stringResource(R.string.settings_recognition_section_voice)) }
            item {
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Voice accent", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto", "primary", "secondary", "tertiary").forEach { t ->
                                FilterChip(selected = t == voiceTint, onClick = { scope.launch { prefs.setRecognitionVoiceTint(t) } }, label = { Text(t) })
                            }
                        }
                        Text(
                            "\"auto\" follows the app theme; the others tint the voice-mode face with that theme accent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

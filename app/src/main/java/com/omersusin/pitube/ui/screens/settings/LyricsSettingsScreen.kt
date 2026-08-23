package com.omersusin.pitube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.LyricsAnimationStyle
import com.omersusin.pitube.data.local.LyricsTextPosition
import com.omersusin.pitube.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { PlayerPreferences(ctx) }
    val scope = rememberCoroutineScope()
    val anim by prefs.lyricsAnimation.collectAsState(initial = LyricsAnimationStyle.VIVIMUSIC_FLUID.name)
    val posName by prefs.lyricsTextPosition.collectAsState(initial = LyricsTextPosition.CENTER.name)
    val pos = remember(posName) { LyricsTextPosition.fromString(posName) }
    val glow by prefs.lyricsGlowEnabled.collectAsState(initial = true)
    val blur by prefs.lyricsStandardBlur.collectAsState(initial = 0f)
    val size by prefs.lyricsTextSize.collectAsState(initial = 20f)
    val spacing by prefs.lyricsLineSpacing.collectAsState(initial = 1.4f)
    val autoScroll by prefs.lyricsAutoScroll.collectAsState(initial = true)
    val changeOnClick by prefs.lyricsChangeOnClick.collectAsState(initial = false)
    val swipe by prefs.lyricsSwipeToChangeSong.collectAsState(initial = false)
    val showPP by prefs.lyricsShowPlayPauseOnThumbnail.collectAsState(initial = true)
    val order by prefs.lyricsProviderOrder.collectAsState(initial = com.omersusin.pitube.data.lyrics.LyricsProviders.DEFAULT_ORDER)
    val translationEnabled by prefs.lyricsTranslationEnabled.collectAsState(initial = true)
    val translationTargetLang by prefs.lyricsTranslationTargetLang.collectAsState(initial = "")
    val syncOffset by prefs.lyricsSyncOffsetMs.collectAsState(initial = 0)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(text = "Lyrics", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SectionHeader(text = "Animation") }
            item {
                SettingsGroup {
                    LyricsAnimationStyle.values().forEachIndexed { idx, s ->
                        val selected = s.name == anim
                        Row(modifier = Modifier.fillMaxWidth().clickable { scope.launch { prefs.setLyricsAnimation(s) } }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { Text(s.displayName, style = MaterialTheme.typography.bodyLarge); if (selected) Text("Selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        if (idx < LyricsAnimationStyle.values().lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            item { SectionHeader(text = "Position") }
            item {
                SettingsGroup {
                    LyricsTextPosition.values().forEachIndexed { idx, p ->
                        val selected = p == pos
                        Row(modifier = Modifier.fillMaxWidth().clickable { scope.launch { prefs.setLyricsTextPosition(p) } }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(p.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                        if (idx < LyricsTextPosition.values().lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            item { SectionHeader(text = "Effects") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(icon = Icons.Outlined.AutoAwesome, title = "Glowing lyrics", subtitle = "Highlight active line with glow", checked = glow, onCheckedChange = { scope.launch { prefs.setLyricsGlowEnabled(it) } })
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Standard blur: ${(blur * 10).toInt() / 10f}", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = blur, onValueChange = { scope.launch { prefs.setLyricsStandardBlur(it) } }, valueRange = 0f..1f)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Text size: ${size.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = size, onValueChange = { scope.launch { prefs.setLyricsTextSize(it) } }, valueRange = 12f..28f)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Line spacing: ${(spacing * 10).toInt() / 10f}x", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = spacing, onValueChange = { scope.launch { prefs.setLyricsLineSpacing(it) } }, valueRange = 0.8f..2.2f)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        // ±50 ms steps across a ±5 s window; positive values make
                        // lyrics highlight EARLIER (shifts the effective clock forward).
                        val stepped = (syncOffset / 50) * 50
                        Text(
                            "Sync offset: ${if (stepped >= 0) "+" else ""}${stepped}ms",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = stepped.toFloat(),
                            onValueChange = { scope.launch { prefs.setLyricsSyncOffsetMs(((it / 50).toInt() * 50)) } },
                            valueRange = -5000f..5000f,
                            steps = ((5000 * 2) / 50) - 1,
                        )
                        Text(
                            "Nudge if lyrics are consistently early or late",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { SectionHeader(text = "Behavior") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(icon = Icons.Outlined.VerticalAlignCenter, title = "Auto scroll", subtitle = "Follow playback position", checked = autoScroll, onCheckedChange = { scope.launch { prefs.setLyricsAutoScroll(it) } })
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(icon = Icons.Outlined.TouchApp, title = "Change on tap", subtitle = "Tap line to seek", checked = changeOnClick, onCheckedChange = { scope.launch { prefs.setLyricsChangeOnClick(it) } })
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(icon = Icons.Outlined.Swipe, title = "Swipe to change song", subtitle = "Horizontal swipe skips track", checked = swipe, onCheckedChange = { scope.launch { prefs.setLyricsSwipeToChangeSong(it) } })
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(icon = Icons.Outlined.PlayArrow, title = "Play/pause on thumbnail", subtitle = "Show control on artwork", checked = showPP, onCheckedChange = { scope.launch { prefs.setLyricsShowPlayPauseOnThumbnail(it) } })
                }
            }
            item { SectionHeader(text = "Translation") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Translate,
                        title = "Translate lyrics",
                        subtitle = "Show translated line under the active line (Musixmatch, follows app language)",
                        checked = translationEnabled,
                        onCheckedChange = { scope.launch { prefs.setLyricsTranslationEnabled(it) } },
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    // Target language: blank follows the app/system locale.
                    val langOptions = listOf(
                        "" to "Follow app language",
                        "tr" to "Turkish",
                        "en" to "English",
                        "de" to "German",
                        "fr" to "French",
                        "es" to "Spanish",
                        "it" to "Italian",
                        "ru" to "Russian",
                        "ar" to "Arabic",
                        "az" to "Azerbaijani",
                    )
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Translation language", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            langOptions.forEach { (code, label) ->
                                val selected = translationTargetLang == code
                                androidx.compose.material3.FilterChip(
                                    selected = selected,
                                    onClick = { scope.launch { prefs.setLyricsTranslationTargetLang(code) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader(text = "Providers") }
            item {
                val orderList = remember(order) { order.split(",").map { it.trim() }.filter { it.isNotBlank() } }
                val allProviders = listOf(
                    "lrclib" to "LRCLIB",
                    "betterlyrics" to "BetterLyrics",
                    "musixmatch" to "Musixmatch",
                    "simpmusic" to "SimpMusic",
                    "paxsenix" to "Paxsenix",
                    "kugou" to "KuGou",
                    "youlyplus" to "YouLyPlus",
                    "transcript" to "YouTube Transcript",
                )
                // THE FIX for the dead-feeling drag: the displayed list must BE
                // the persisted order. Passing the static catalog meant rows
                // never moved on screen — only the number badges reshuffled.
                val displayItems = remember(order) {
                    val known = allProviders.associateBy { it.first }
                    orderList.mapNotNull { known[it] } +
                        allProviders.filter { it.first !in orderList.toSet() }
                }
                // Drag rows to reorder — persisted on every swap.
                SettingsGroup {
                    com.omersusin.pitube.ui.screens.settings.components.DragReorderColumn(
                        items = displayItems,
                        itemKey = { it.first },
                        onMove = { from, to ->
                            val ids = displayItems.map { it.first }.toMutableList()
                            val moved = ids.getOrNull(from) ?: return@DragReorderColumn
                            ids.removeAt(from)
                            ids.add(to.coerceIn(0, ids.size), moved)
                            scope.launch { prefs.setLyricsProviderOrder(ids.joinToString(",")) }
                        },
                    ) { (id, label) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge); Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Icon(
                                Icons.Outlined.DragIndicator,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

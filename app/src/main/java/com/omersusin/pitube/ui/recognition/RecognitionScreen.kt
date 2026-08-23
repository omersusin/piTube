package com.omersusin.pitube.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.recognition.VoiceRecognitionSource
import kotlinx.coroutines.delay

/**
 * Full-screen Voice/Song recognition modal (Google voice-search style): close
 * top-left, Voice/Song segmented pill + settings gear top-right, an
 * amplitude-reactive talking face (Voice) / morphing blob (Song) while
 * listening, big mic button. Opened from the Search screen's mic, the
 * recognition notification and the floating overlay button. Final results
 * auto-submit as a piTube search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: RecognitionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = remember { PlayerPreferences(context) }
    val blobTint by prefs.recognitionBlobTint.collectAsState(initial = "auto")

    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            micPermissionGranted = granted
        }

    val onMicClick: () -> Unit = {
        when {
            uiState.phase == RecognitionPhase.LISTENING ||
                uiState.phase == RecognitionPhase.PROCESSING ->
                viewModel.stopListening()

            !micPermissionGranted -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> viewModel.startListening()
        }
    }

    // Theme-consistent redesign: both modes render on the app's own themed
    // surface — no forced-dark scheme, no hardcoded gradient. All color
    // personality lives in the blob/face widgets, which read theme tokens.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.displayCutout),
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar: close + title + mode toggle ───────────────────────────
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                RecognitionModeToggle(
                    mode = uiState.mode,
                    enabled = uiState.phase != RecognitionPhase.LISTENING &&
                        uiState.phase != RecognitionPhase.PROCESSING,
                    onModeChange = viewModel::setMode,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // ── Center content ─────────────────────────────────────────────────
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState.phase) {
                    RecognitionPhase.LISTENING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (uiState.mode) {
                                RecognitionMode.VOICE -> TalkingFace(
                                    amplitude = uiState.levels.lastOrNull() ?: 0f,
                                    levels = uiState.levels,
                                    modifier = Modifier.size(224.dp),
                                )
                                RecognitionMode.SONG -> MorphingBlob(
                                    amplitude = uiState.levels.lastOrNull() ?: 0f,
                                    levels = uiState.levels,
                                    modifier = Modifier.size(224.dp),
                                    bands = uiState.bands,
                                    blobTint = blobTint,
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(
                                    if (uiState.mode == RecognitionMode.VOICE) {
                                        R.string.recognition_listening_voice
                                    } else {
                                        R.string.recognition_listening_song
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.recognition_tap_to_stop),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    RecognitionPhase.PROCESSING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                strokeWidth = 4.dp,
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.recognition_processing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    RecognitionPhase.SUCCESS -> {
                        val transcript = uiState.transcript
                        val track = uiState.track
                        if (transcript != null) {
                            VoiceResultCard(
                                transcript = transcript,
                                source = uiState.voiceSource,
                                onSearch = { onSearch(transcript) },
                            )
                        } else if (track != null) {
                            TrackResultCard(
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                coverArtUrl = track.coverArtUrl,
                                onSearch = { onSearch(track.searchQuery) },
                            )
                        }
                    }

                    RecognitionPhase.ERROR -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        ) {
                            Text(
                                text = uiState.message ?: stringResource(R.string.recognition_error_generic),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            if (uiState.recordingSaved) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        if (uiState.retryScheduled) {
                                            R.string.recognition_saved_and_will_retry
                                        } else {
                                            R.string.recognition_recording_saved
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            OutlinedButton(onClick = viewModel::startListening) {
                                Text(stringResource(R.string.recognition_retry))
                            }
                        }
                    }

                    RecognitionPhase.IDLE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (uiState.mode) {
                                RecognitionMode.VOICE -> TalkingFace(
                                    amplitude = 0f,
                                    levels = emptyList(),
                                    modifier = Modifier.size(224.dp),
                                )
                                RecognitionMode.SONG -> MorphingBlob(
                                    amplitude = 0f,
                                    levels = emptyList(),
                                    modifier = Modifier.size(224.dp),
                                    bands = null,
                                    blobTint = blobTint,
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            if (!micPermissionGranted) {
                                Card(
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                     ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MicOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.recognition_mic_permission_needed),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            },
                                        ) {
                                            Text(stringResource(R.string.recognition_grant_permission))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Mic button ──────────────────────────────────────────────────────
            if (uiState.phase != RecognitionPhase.SUCCESS) {
                RecognitionMicButton(
                    isListening = uiState.phase == RecognitionPhase.LISTENING,
                    isSongMode = uiState.mode == RecognitionMode.SONG,
                    onClick = onMicClick,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(
                        when (uiState.mode) {
                            RecognitionMode.VOICE -> R.string.recognition_voice_hint
                            RecognitionMode.SONG -> R.string.recognition_song_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(48.dp))
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        }
    }
}

/**
 * Accent-tint resolution shared by the recognition cards and the floating
 * button. "auto" keeps the themed surfaceVariant; the other options swap in
 * the matching theme accent's container tone.
 */
@Composable
private fun recognitionAccentContainer(tint: String): Color = when (tint) {
    "primary" -> MaterialTheme.colorScheme.primaryContainer
    "secondary" -> MaterialTheme.colorScheme.secondaryContainer
    "tertiary" -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun RecognitionModeToggle(
    mode: RecognitionMode,
    enabled: Boolean,
    onModeChange: (RecognitionMode) -> Unit,
) {
    val voiceSelected = mode == RecognitionMode.VOICE
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(surfaceColor)
                .padding(4.dp),
    ) {
        ModeSegment(
            text = stringResource(R.string.recognition_mode_voice),
            selected = voiceSelected,
            enabled = enabled,
            onClick = { onModeChange(RecognitionMode.VOICE) },
        )
        ModeSegment(
            text = stringResource(R.string.recognition_mode_song),
            selected = !voiceSelected,
            enabled = enabled,
            onClick = { onModeChange(RecognitionMode.SONG) },
        )
    }
}

@Composable
private fun ModeSegment(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "segmentBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segmentContent",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = contentColor,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(background)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun RecognitionMicButton(
    isListening: Boolean,
    isSongMode: Boolean,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { com.omersusin.pitube.data.local.PlayerPreferences(ctx) }
    val floatingTint by prefs.recognitionFloatingTint.collectAsState(initial = "auto")
    val idleColor = recognitionAccentContainer(floatingTint)
    val background by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.primary else idleColor,
        label = "micBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "micFg",
    )
    Box(
        modifier =
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSongMode) Icons.Rounded.GraphicEq else Icons.Filled.Mic,
            contentDescription = stringResource(R.string.recognition_mic_content_description),
            tint = contentColor,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun VoiceResultCard(
    transcript: String,
    source: VoiceRecognitionSource?,
    onSearch: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { com.omersusin.pitube.data.local.PlayerPreferences(ctx) }
    val cardTint by prefs.recognitionCardTint.collectAsState(initial = "auto")
    Card(
        modifier = Modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = recognitionAccentContainer(cardTint)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = transcript,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (source != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        when (source) {
                            VoiceRecognitionSource.ON_DEVICE -> R.string.recognition_source_on_device
                            VoiceRecognitionSource.GROQ -> R.string.recognition_source_groq
                            VoiceRecognitionSource.IBM_WATSON -> R.string.recognition_source_ibm_watson
                            VoiceRecognitionSource.AZURE -> R.string.recognition_source_azure
                            VoiceRecognitionSource.GOOGLE_CLOUD -> R.string.recognition_source_google_cloud
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recognition_search_now))
            }
        }
    }
}

@Composable
private fun TrackResultCard(
    title: String,
    artist: String,
    album: String?,
    coverArtUrl: String?,
    onSearch: () -> Unit,
) {
    // Recognition appearance settings (default = auto theme; optional customization).
    val context = LocalContext.current
    val prefs = remember(context) { com.omersusin.pitube.data.local.PlayerPreferences(context) }
    val cardStyle by prefs.recognitionCardStyle.collectAsState(initial = "default")
    val cardTint by prefs.recognitionCardTint.collectAsState(initial = "auto")
    val cornerRadius by prefs.recognitionCardCornerRadius.collectAsState(initial = 20f)
    val artSize by prefs.recognitionArtSize.collectAsState(initial = 72)
    val cardPadding = when (cardStyle) {
        "compact" -> 12.dp
        "full" -> 24.dp
        else -> 20.dp
    }
    Card(
        modifier = Modifier.padding(horizontal = if (cardStyle == "full") 16.dp else 24.dp),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(containerColor = recognitionAccentContainer(cardTint)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (cardStyle == "full") 4.dp else 0.dp),
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!coverArtUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverArtUrl,
                        contentDescription = album ?: title,
                        modifier =
                            Modifier
                                .size(artSize.dp)
                                .clip(RoundedCornerShape((cornerRadius * 0.6f).dp)),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(artSize.dp)
                                .clip(RoundedCornerShape((cornerRadius * 0.6f).dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recognition_search_now))
            }
        }
    }
}
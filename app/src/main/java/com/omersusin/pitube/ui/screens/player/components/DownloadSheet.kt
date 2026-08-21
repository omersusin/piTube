package com.omersusin.pitube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.local.PlayerPreferences
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.data.video.download.DownloadLauncher
import com.omersusin.pitube.data.video.download.DownloadMode
import com.omersusin.pitube.data.video.download.DownloadPlan
import com.omersusin.pitube.data.video.download.DownloadPlanner
import com.omersusin.pitube.player.stream.InnerTubeStreamBridge
import com.omersusin.pitube.player.stream.InnerTubeVideoStreamExtractor
import com.omersusin.pitube.player.stream.VideoCodecUtils
import com.omersusin.pitube.ui.components.rememberFlowSheetState
import com.omersusin.pitube.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * The single download surface (mandatory fix #1/#3/#6).
 *
 * Every "İndir" tap in the app opens this modal bottom sheet — the player
 * dialog, the home-feed quick actions, Shorts, playlists. It shows two
 * independently expandable accordion sections ("Video quality" / "Audio
 * quality") built from the REAL stream data, never mixes the two lists, and
 * only starts a download after the user confirms.
 *
 * Streams are passed in when the caller already has them (player screen);
 * otherwise the sheet fetches them itself (home feed, Shorts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    video: Video,
    streamInfo: StreamInfo? = null,
    innerTubeVideoFormats: List<com.omersusin.pitube.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    innerTubeAudioFormats: List<com.omersusin.pitube.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
    streamSizes: Map<String, Long> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val lastDownloadType by prefs.lastDownloadType.collectAsState(initial = null)
    val lastDownloadHeight by prefs.lastDownloadHeight.collectAsState(initial = null)
    val lastDownloadCodec by prefs.lastDownloadCodec.collectAsState(initial = null)
    val lastDownloadAudioLabel by prefs.lastDownloadAudioLabel.collectAsState(initial = null)
    val downloadThreads by prefs.downloadThreads.collectAsState(initial = 3)
    val targetQuality by prefs.defaultDownloadQuality.collectAsState(initial = com.omersusin.pitube.data.local.VideoQuality.Q_720p)
    val preferredAudioLanguage by prefs.preferredAudioLanguage.collectAsState(initial = "")

    var audioOnly by remember { mutableStateOf(false) }
    var videoAccordionExpanded by remember { mutableStateOf(true) }
    var audioAccordionExpanded by remember { mutableStateOf(false) }
    var selectedVideoKey by remember { mutableStateOf<String?>(null) }
    var selectedAudioUrl by remember { mutableStateOf<String?>(null) }
    var preferredCodec by remember { mutableStateOf(lastDownloadCodec) }
    var expandedVideoHeight by remember { mutableStateOf<Int?>(null) }
    var expandedAudioGroup by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var fetchedVideoFormats by remember { mutableStateOf<List<com.omersusin.pitube.innertube.models.response.PlayerResponse.StreamingData.Format>>(emptyList()) }
    var fetchedAudioFormats by remember { mutableStateOf<List<com.omersusin.pitube.innertube.models.response.PlayerResponse.StreamingData.Format>>(emptyList()) }

    val effectiveVideoFormats = innerTubeVideoFormats + fetchedVideoFormats
    val effectiveAudioFormats = innerTubeAudioFormats + fetchedAudioFormats

    val videoStreams = remember(effectiveVideoFormats, streamInfo) {
        val converted = InnerTubeStreamBridge.convertVideoFormats(effectiveVideoFormats)
        val extracted = streamInfo?.videoStreams?.filterIsInstance<VideoStream>().orEmpty() +
            streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>().orEmpty()
        (converted + extracted).distinctBy { it.getContent() }
    }
    val audioStreams = remember(effectiveAudioFormats, streamInfo) {
        val converted = InnerTubeStreamBridge.convertAudioFormats(effectiveAudioFormats)
        val extracted = streamInfo?.audioStreams.orEmpty()
        DownloadStreamHelpers.mergeAudioDownloadStreams(converted, extracted)
            .distinctBy { it.getContent() }
    }

    val hasStreams = videoStreams.isNotEmpty() || audioStreams.isNotEmpty()

    LaunchedEffect(video.id, hasStreams) {
        if (hasStreams) return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            val result = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    InnerTubeVideoStreamExtractor.extract(video.id)
                }
            }
            if (result != null && (result.videoFormats.isNotEmpty() || result.audioFormats.isNotEmpty())) {
                fetchedVideoFormats = result.videoFormats
                fetchedAudioFormats = result.audioFormats
            } else {
                loadFailed = true
            }
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val plannerInput = remember(videoStreams, audioStreams, preferredAudioLanguage, targetQuality) {
        DownloadPlanner.PlannerInput(
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            preferredAudioLanguage = preferredAudioLanguage.ifBlank { null },
            targetHeight = targetQuality.height,
        )
    }

    val distinctVideoStreams = remember(videoStreams) {
        videoStreams
            .distinctBy {
                "${VideoPlayerUtils.qualityHeightFromStream(it)}_${VideoCodecUtils.codecKeyFromStream(it)}"
            }
            .sortedWith(
                compareByDescending<VideoStream> { VideoPlayerUtils.qualityHeightFromStream(it) }
                    .thenBy { DownloadPlanner.CODEC_PRIORITY[VideoCodecUtils.codecKeyFromStream(it)] ?: 99 },
            )
    }

    // Default selection: last-used choice when it still exists, else planner default.
    val defaultCandidate = remember(plannerInput, lastDownloadHeight, lastDownloadCodec) {
        val lastMatch = plannerInput.allCandidates.firstOrNull {
            it.height == lastDownloadHeight && it.codecKey == lastDownloadCodec
        }
        lastMatch ?: DownloadPlanner.defaultVideoPick(plannerInput)
    }
    val defaultAudio = remember(plannerInput, lastDownloadAudioLabel) {
        val lastMatch = audioStreams.firstOrNull {
            DownloadStreamHelpers.audioBitrateKbps(it).toString() == lastDownloadAudioLabel
        }
        lastMatch ?: DownloadPlanner.pickAudio(
            plannerInput.audioStreams,
            defaultCandidate?.codecKey ?: DownloadPlanner.CODEC_H264,
            plannerInput.preferredAudioLanguage,
        )
    }

    LaunchedEffect(defaultCandidate, defaultAudio, lastDownloadType) {
        audioOnly = lastDownloadType == "AUDIO"
        selectedVideoKey = defaultCandidate?.let {
            "${it.height}_${it.codecKey}"
        }
        selectedAudioUrl = defaultAudio?.getContent()?.takeIf { it.isNotBlank() }
    }

    val selectedCandidate = distinctVideoStreams.firstOrNull {
        "${VideoPlayerUtils.qualityHeightFromStream(it)}_${VideoCodecUtils.codecKeyFromStream(it)}" == selectedVideoKey
    }
    val selectedAudio = audioStreams.firstOrNull { it.getContent() == selectedAudioUrl }
    val selectedIsMuxed = selectedCandidate?.isVideoOnly() == false

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        coil3.compose.AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            if (video.duration > 0) Text(String.format("%d:%02d", video.duration / 60, video.duration % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            when {
                loading -> item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.loading_ellipsis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                !hasStreams && loadFailed -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.no_download_streams),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                coroutineScope.launch {
                                    trySabrDownloadFromDialog(context, video)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.ui_try_sabr_download))
                        }
                    }
                }

                else -> {
                    item {
                        AccordionHeader(
                            title = stringResource(R.string.download_section_video_quality),
                            subtitle = selectedCandidate?.let {
                                "${VideoCodecUtils.codecLabelFromKey(VideoCodecUtils.codecKeyFromStream(it))} " +
                                    "${VideoPlayerUtils.qualityHeightFromStream(it)}p"
                            },
                            expanded = videoAccordionExpanded && !audioOnly,
                            onToggle = { videoAccordionExpanded = !videoAccordionExpanded },
                        )
                    }
                    item {
                        AnimatedVisibility(
                            visible = videoAccordionExpanded && !audioOnly,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                if (distinctVideoStreams.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.no_download_streams),
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val heightsDescending = remember(distinctVideoStreams) {
                                        distinctVideoStreams
                                            .map { VideoPlayerUtils.qualityHeightFromStream(it) }
                                            .distinct()
                                    }
                                    val codecChips = listOf(
                                        null to stringResource(R.string.download_codec_auto),
                                        DownloadPlanner.CODEC_H264 to VideoCodecUtils.codecLabelFromKey(DownloadPlanner.CODEC_H264),
                                        DownloadPlanner.CODEC_VP9 to VideoCodecUtils.codecLabelFromKey(DownloadPlanner.CODEC_VP9),
                                        DownloadPlanner.CODEC_AV1 to VideoCodecUtils.codecLabelFromKey(DownloadPlanner.CODEC_AV1),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        codecChips.forEach { (codecKey, label) ->
                                            FilterChip(
                                                selected = preferredCodec == codecKey,
                                                onClick = {
                                                    preferredCodec = codecKey
                                                    coroutineScope.launch {
                                                        prefs.setPreferredDownloadCodec(codecKey)
                                                    }
                                                },
                                                label = { Text(label) },
                                            )
                                        }
                                    }
                                    heightsDescending.forEach { height ->
                                        val streamsAtHeight = distinctVideoStreams.filter {
                                            VideoPlayerUtils.qualityHeightFromStream(it) == height
                                        }
                                        val effectiveCodec =
                                            preferredCodec?.takeIf { codec ->
                                                streamsAtHeight.any { VideoCodecUtils.codecKeyFromStream(it) == codec }
                                            }
                                                ?: VideoCodecUtils.codecKeyFromStream(streamsAtHeight.first())
                                        val isExpanded = expandedVideoHeight == height
                                        VideoHeightRow(
                                            height = height,
                                            sizeInBytes = streamSizes[
                                                VideoPlayerUtils.streamSizeKey(height, effectiveCodec)
                                            ],
                                            expanded = isExpanded,
                                            onClick = {
                                                if (isExpanded) {
                                                    expandedVideoHeight = null
                                                } else {
                                                    expandedVideoHeight = height
                                                    val prefill =
                                                        preferredCodec?.takeIf { codec ->
                                                            streamsAtHeight.any {
                                                                VideoCodecUtils.codecKeyFromStream(it) == codec
                                                            }
                                                        }
                                                    if (prefill != null) {
                                                        selectedVideoKey = "${height}_$prefill"
                                                        val prefillStream = streamsAtHeight.first {
                                                            VideoCodecUtils.codecKeyFromStream(it) == prefill
                                                        }
                                                        if (!prefillStream.isVideoOnly()) selectedAudioUrl = null
                                                    }
                                                }
                                            },
                                        )
                                        AnimatedVisibility(visible = isExpanded) {
                                            Column {
                                                streamsAtHeight.forEach { stream ->
                                                    val codecKey = VideoCodecUtils.codecKeyFromStream(stream)
                                                    VideoQualityRow(
                                                        stream = stream,
                                                        codecKey = codecKey,
                                                        height = height,
                                                        sizeInBytes = streamSizes[
                                                            VideoPlayerUtils.streamSizeKey(height, codecKey)
                                                        ],
                                                        isSelected = "${height}_$codecKey" == selectedVideoKey,
                                                        enabled = !audioOnly,
                                                        onClick = {
                                                            selectedVideoKey = "${height}_$codecKey"
                                                            if (!stream.isVideoOnly()) selectedAudioUrl = null
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AccordionHeader(
                            title = stringResource(R.string.download_section_audio_quality),
                            subtitle = if (!audioOnly && selectedIsMuxed) {
                                stringResource(R.string.download_audio_builtin)
                            } else {
                                selectedAudio?.let {
                                    "${DownloadStreamHelpers.audioFormatLabel(it)} " +
                                        "${DownloadStreamHelpers.audioBitrateKbps(it)}kbps"
                                }
                            },
                            expanded = audioAccordionExpanded,
                            onToggle = { audioAccordionExpanded = !audioAccordionExpanded },
                        )
                    }
                    item {
                        AnimatedVisibility(
                            visible = audioAccordionExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.download_audio_only),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(R.string.download_audio_only_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = audioOnly,
                                        onCheckedChange = { checked ->
                                            audioOnly = checked
                                            videoAccordionExpanded = !checked
                                        },
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                if (!audioOnly && selectedIsMuxed) {
                                    Text(
                                        text = stringResource(R.string.download_audio_builtin_hint),
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (audioStreams.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.no_download_streams),
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val audioGroups = remember(audioStreams) {
                                        audioStreams
                                            .groupBy { DownloadStreamHelpers.audioGroupLabel(it) }
                                            .map { (group, streams) ->
                                                group to streams.sortedByDescending {
                                                    DownloadStreamHelpers.audioBitrateKbps(it)
                                                }
                                            }
                                            .sortedBy { (group, _) ->
                                                when (group) {
                                                    "OPUS" -> 0
                                                    "M4A" -> 1
                                                    "MP3" -> 2
                                                    else -> 3
                                                }
                                            }
                                    }
                                    audioGroups.forEach { (group, streams) ->
                                        val selectedInGroup = streams.firstOrNull {
                                            it.getContent() == selectedAudioUrl
                                        }
                                        AudioGroupRow(
                                            group = group,
                                            subtitle = selectedInGroup?.let {
                                                "${DownloadStreamHelpers.audioFormatLabel(it)} " +
                                                    "${DownloadStreamHelpers.audioBitrateKbps(it)}kbps"
                                            },
                                            expanded = expandedAudioGroup == group,
                                            onClick = {
                                                expandedAudioGroup =
                                                    if (expandedAudioGroup == group) null else group
                                            },
                                        )
                                        AnimatedVisibility(visible = expandedAudioGroup == group) {
                                            Column {
                                                streams.forEach { audio ->
                                                    AudioQualityRow(
                                                        audio = audio,
                                                        isSelected = audio.getContent() == selectedAudioUrl,
                                                        enabled = audioOnly || !selectedIsMuxed,
                                                        onClick = {
                                                            selectedAudioUrl =
                                                                audio.getContent().takeIf { it.isNotBlank() }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val plan = buildPlan(
                                    video = video,
                                    audioOnly = audioOnly,
                                    plannerInput = plannerInput,
                                    selectedCandidate = selectedCandidate,
                                    selectedAudio = selectedAudio,
                                    threads = downloadThreads,
                                )
                                if (plan != null) {
                                    DownloadLauncher.start(context, plan)
                                    onDismiss()
                                }
                            },
                            enabled = canStart(audioOnly, selectedCandidate, selectedAudio),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.download))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionHeader(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun VideoHeightRow(
    height: Int,
    sizeInBytes: Long?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val sizeText =
        if (sizeInBytes != null && sizeInBytes > 0) {
            String.format("~%.2f MB", sizeInBytes / (1024.0 * 1024.0))
        } else {
            null
        }
    val resBadge =
        when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "HD"
            else -> null
        }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${height}p" + (resBadge?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sizeText != null) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (resBadge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color =
                        when (resBadge) {
                            "4K" -> MaterialTheme.colorScheme.tertiary
                            "2K" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = resBadge,
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioGroupRow(
    group: String,
    subtitle: String?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoQualityRow(
    stream: VideoStream,
    codecKey: String,
    height: Int,
    sizeInBytes: Long?,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val codecLabel = VideoCodecUtils.codecLabelFromKey(codecKey)
    val qualityLabel = "$codecLabel ${height}p"
    val sizeText =
        if (sizeInBytes != null && sizeInBytes > 0) {
            String.format("~%.2f MB", sizeInBytes / (1024.0 * 1024.0))
        } else {
            null
        }
    val resBadge =
        when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "HD"
            else -> null
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        sizeText,
                        if (stream.isVideoOnly()) stringResource(R.string.download_stream_video_only) else stringResource(R.string.download_stream_muxed),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (resBadge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color =
                        when (resBadge) {
                            "4K" -> MaterialTheme.colorScheme.tertiary
                            "2K" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = resBadge,
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AudioQualityRow(
    audio: AudioStream,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bitrate = DownloadStreamHelpers.audioBitrateKbps(audio)
    val audioFormat = DownloadStreamHelpers.audioFormatLabel(audio)
    val languageLabel = DownloadStreamHelpers.audioLanguageLabel(audio)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$audioFormat ${bitrate}kbps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (languageLabel != null) {
                    Text(
                        text = languageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun canStart(
    audioOnly: Boolean,
    selectedCandidate: VideoStream?,
    selectedAudio: AudioStream?,
): Boolean = if (audioOnly) selectedAudio != null else selectedCandidate != null

private fun buildPlan(
    video: Video,
    audioOnly: Boolean,
    plannerInput: DownloadPlanner.PlannerInput,
    selectedCandidate: VideoStream?,
    selectedAudio: AudioStream?,
    threads: Int,
): DownloadPlan? {
    if (audioOnly) {
        val audio = selectedAudio ?: return null
        val url = audio.getContent().takeIf { it.isNotBlank() } ?: return null
        return DownloadPlan(
            video = video,
            mode = DownloadMode.AUDIO,
            qualityLabel = "${DownloadStreamHelpers.audioBitrateKbps(audio)}kbps",
            videoUrl = url,
            audioExtension = DownloadStreamHelpers.audioFileExtension(audio),
            audioMimeType = audio.format?.mimeType,
            threads = threads,
        )
    }
    val candidate = plannerInput.allCandidates.firstOrNull {
        it.stream.getContent() == selectedCandidate?.getContent()
    } ?: return null
    val plan = DownloadPlanner.videoPlan(video, plannerInput, candidate, threads)
    // User-picked audio overrides the planner default (video-only only).
    if (!candidate.isMuxed && selectedAudio != null) {
        return plan.copy(audioUrl = selectedAudio.getContent().takeIf { it.isNotBlank() })
    }
    return plan
}
package com.omersusin.pitube.ui.screens

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.omersusin.pitube.PipState
import com.omersusin.pitube.data.*
import com.omersusin.pitube.data.addWatchHistory
import com.omersusin.pitube.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatCount(n: Long): String = when { n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0); n >= 1_000 -> String.format("%.1fK", n / 1_000.0); else -> n.toString() }
fun formatTimestamp(ms: Long): String = String.format("%d:%02d", ms / 60000, (ms / 1000) % 60)

class CommentsPagingSource(private val api: PipedApiService, private val videoId: String) : PagingSource<String, Comment>() {
    override fun getRefreshKey(state: PagingState<String, Comment>): String? = null
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Comment> {
        return try {
            val token = params.key
            val response = if (token == null) api.getComments(videoId) else api.getNextComments(videoId, token)
            LoadResult.Page(data = response.comments, prevKey = null, nextKey = response.nextpage)
        } catch (e: Exception) { LoadResult.Error(e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(video: VideoItem, onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(AudioManager::class.java)
    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

    var streamInfo by remember { mutableStateOf<StreamInfo?>(null) }
    var resolved by remember { mutableStateOf<StreamResolver.Resolved?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var segments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var votes by remember { mutableStateOf<VoteInfo?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }
    var deArrowTitle by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var sleepChoice by remember { mutableIntStateOf(0) }
    var sleepDeadline by remember { mutableLongStateOf(0L) }
    var noteText by remember { mutableStateOf("") }
    var notes by remember(video.videoId) { mutableStateOf(NotesManager.getNotes(context, video.videoId)) }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var showChat by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var captionTracks by remember { mutableStateOf<List<Captions.Track>>(emptyList()) }
    var activeCaption by remember { mutableStateOf<Captions.Track?>(null) }
    var captions by remember { mutableStateOf<List<Captions.Cue>>(emptyList()) }
    var currentCaption by remember { mutableStateOf("") }
    var subscribed by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf<List<VideoChapter>>(emptyList()) }

    // Gesture state
    var showControls by remember { mutableStateOf(true) }
    var isSeekForwardActive by remember { mutableStateOf(false) }
    var isSeekBackActive by remember { mutableStateOf(false) }
    var seekAccumulatedSeconds by remember { mutableIntStateOf(0) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember { mutableFloatStateOf(1.0f) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    val zenMode = remember { PrefsManager.isZenMode(context) }
    val hideCounters = remember { PrefsManager.isHideCounters(context) }
    val hideComments = remember { PrefsManager.isHideComments(context) }
    val hideLikes = remember { PrefsManager.isHideLikeButtons(context) }
    val autoExpand = remember { PrefsManager.isAutoExpandDesc(context) }
    var descriptionExpanded by remember { mutableStateOf(autoExpand) }
    var currentSpeed by remember { mutableStateOf(PrefsManager.getPlaybackSpeed(context)) }

    val scope = rememberCoroutineScope()
    val exoPlayer = remember { PlayerHolder.getPlayer(context) }

    val comments = remember(video.videoId) { Pager(PagingConfig(pageSize = 20)) { CommentsPagingSource(PipedApiService.create(), video.videoId) }.flow }.collectAsLazyPagingItems()

    LaunchedEffect(video.videoId) {
        HistoryManager.addToHistory(context, video)
        scope.launch { StatsRepository(context).addPlayEvent(video, 0L) }
        addWatchHistory(context, video.videoId, video.title, video.uploaderName, video.thumbnailUrl)
        isLoading = true; error = null; downloadStarted = false; deArrowTitle = null; showOriginal = false; chatMessages = emptyList()
        notes = NotesManager.getNotes(context, video.videoId)
        scope.launch {
            try {
                val r = StreamResolver.resolve(video.videoId, context)
                resolved = r
                var piped: StreamInfo? = null
                try { piped = PipedApiService.create().getStreams(video.videoId) } catch (e: Exception) { }
                streamInfo = piped ?: r?.let { StreamInfo(title = it.title, description = it.description, uploader = it.uploader, uploaderUrl = it.uploaderUrl, hls = it.playUrl, dash = null) }
                val mediaSource = r?.let { StreamResolver.buildMediaSource(context, it) }
                if (mediaSource != null) {
                    exoPlayer.setMediaSource(mediaSource)
                    val resumePos = ResumeManager.getResumePosition(context, video.videoId)
                    if (resumePos > 0L) exoPlayer.seekTo(resumePos)
                    exoPlayer.prepare()
                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(currentSpeed)
                    exoPlayer.playWhenReady = true
                } else { error = "No playable stream found" }
                try { if (PrefsManager.isSponsorBlockEnabled(context)) segments = SponsorBlockService.create().getSegments(video.videoId, """["sponsor","selfpromo","intro","outro","preview"]""") } catch (e: Exception) { segments = emptyList() }
                try { votes = RydService.create().getVotes(video.videoId) } catch (e: Exception) { }
                try { deArrowTitle = DeArrowService.create().getBranding(video.videoId).titles.maxByOrNull { it.votes }?.title } catch (e: Exception) { }
                try { captionTracks = Captions.tracks(context, video.videoId) } catch (e: Exception) { }
            } catch (e: Exception) { error = e.message } finally { isLoading = false }
        }
        scope.launch {
            try {
                var token = LiveChatManager.getInitialContinuation(context, video.videoId)
                if (token != null) showChat = true
                while (token != null) {
                    val res = LiveChatManager.poll(context, token)
                    if (res.first.isNotEmpty()) chatMessages = (chatMessages + res.first).takeLast(200)
                    token = res.second
                    delay(4000)
                }
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(activeCaption) { captions = activeCaption?.let { Captions.load(it) } ?: emptyList() }
    LaunchedEffect(exoPlayer.currentPosition) { val pos = exoPlayer.currentPosition; currentCaption = captions.firstOrNull { pos >= it.startMs && pos < it.endMs }?.text ?: "" }

    DisposableEffect(video.videoId) { onDispose { ResumeManager.saveResumePosition(context, video.videoId, exoPlayer.currentPosition); SessionResume.save(context, video, exoPlayer.currentPosition) } }

    LaunchedEffect(segments, sleepDeadline) {
        while (true) {
            delay(500)
            if (sleepDeadline > 0L && System.currentTimeMillis() >= sleepDeadline) { exoPlayer.pause(); sleepDeadline = 0L; sleepChoice = 0 }
            if (segments.isNotEmpty()) {
                val pos = exoPlayer.currentPosition / 1000.0
                segments.firstOrNull { pos >= it.segment[0] && pos < it.segment[1] - 0.3 }?.let { exoPlayer.seekTo((it.segment[1] * 1000).toLong()) }
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    val inPip = PipState.inPip.value
    Column(modifier = Modifier.fillMaxSize()) {
        if (!inPip) TopAppBar(title = { }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, actions = { if (chatMessages.isNotEmpty()) { IconButton(onClick = { showChat = !showChat }) { Icon(Icons.Default.Chat, contentDescription = "Chat", tint = if (showChat) MaterialTheme.colorScheme.primary else Color.Unspecified) } } ; IconButton(onClick = { showQuickActions = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        if (inPip) { Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize()) } }
        else if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (error != null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error", color = MaterialTheme.colorScheme.error) } }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                            .videoPlayerControls(
                                showControls = showControls,
                                onShowControlsChange = { showControls = it },
                                onSeekForward = { seconds ->
                                    isSeekForwardActive = true
                                    seekAccumulatedSeconds = seconds
                                    val target = (exoPlayer.currentPosition + seconds * 1000L).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(target)
                                    scope.launch { delay(500); isSeekForwardActive = false }
                                },
                                onSeekBack = { seconds ->
                                    isSeekBackActive = true
                                    seekAccumulatedSeconds = seconds
                                    val target = (exoPlayer.currentPosition + seconds * 1000L).coerceAtLeast(0)
                                    exoPlayer.seekTo(target)
                                    scope.launch { delay(500); isSeekBackActive = false }
                                },
                                currentPosition = { exoPlayer.currentPosition },
                                duration = exoPlayer.duration,
                                isFullscreen = false,
                                onBrightnessChange = { brightnessLevel = it },
                                onShowBrightnessChange = { showBrightnessOverlay = it },
                                onVolumeChange = { volumeLevel = it },
                                onShowVolumeChange = { showVolumeOverlay = it },
                                onBack = onBack,
                                brightnessLevel = brightnessLevel,
                                volumeLevel = volumeLevel,
                                maxVolume = maxVolume,
                                audioManager = audioManager,
                                activity = activity,
                                onPlayPause = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                doubleTapSeekMs = 10_000L
                            )
                    ) {
                        AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
                        
                        // Overlay controls
                        if (showControls) {
                            // Top gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .align(Alignment.TopCenter)
                                    .background(Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                    ))
                            )
                            
                            // Bottom controls
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    ))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTimestamp(exoPlayer.currentPosition),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = formatTimestamp(exoPlayer.duration),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { 
                                        if (exoPlayer.duration > 0) exoPlayer.currentPosition.toFloat() / exoPlayer.duration 
                                        else 0f 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                        
                        // Seek overlay
                        if (isSeekForwardActive || isSeekBackActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (isSeekForwardActive) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "${seekAccumulatedSeconds}s",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                            }
                        }
                        
                        // Brightness overlay
                        if (showBrightnessOverlay) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Brightness6, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${(brightnessLevel * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        // Volume overlay
                        if (showVolumeOverlay) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${(volumeLevel * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        // Caption
                        if (currentCaption.isNotBlank()) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    currentCaption,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                streamInfo?.let { info ->
                    item { Column(modifier = Modifier.padding(16.dp)) { Text(text = if (!showOriginal && deArrowTitle != null) deArrowTitle!! else info.title.ifBlank { video.title }, style = MaterialTheme.typography.titleLarge); if (deArrowTitle != null && deArrowTitle != info.title) { TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "Show honest title" else "Show original title", style = MaterialTheme.typography.bodySmall) } }; Spacer(modifier = Modifier.height(4.dp)); Row(modifier = Modifier.clickable { onChannelClick(info.uploaderUrl.substringAfter("/channel/")) }, verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = video.uploaderAvatar, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(8.dp)); Text(info.uploader, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); Button(onClick = { scope.launch { if (VideoEngagement.subscribe(context, info.uploaderUrl.substringAfter("/channel/"), !subscribed)) subscribed = !subscribed } }, colors = ButtonDefaults.buttonColors(containerColor = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error)) { Text(if (subscribed) "Subscribed" else "Subscribe") } } } }
                    if (!hideLikes && !hideCounters) {
                        item { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.likes?.toLong() ?: 0L)); Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.dislikes?.toLong() ?: 0L)) } }; TextButton(onClick = { sleepChoice = when (sleepChoice) { 0 -> 5; 5 -> 15; 15 -> 30; else -> 0 }; sleepDeadline = if (sleepChoice == 0) 0L else System.currentTimeMillis() + sleepChoice * 60_000L }) { Text(if (sleepChoice == 0) "Sleep" else "${sleepChoice}m") }; Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = { showSpeedDialog = true }) { Icon(Icons.Default.Speed, contentDescription = "Speed", tint = if (currentSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.Unspecified) }; if (captionTracks.isNotEmpty()) { IconButton(onClick = { activeCaption = if (activeCaption == null) captionTracks.firstOrNull() else null }) { Icon(Icons.Default.Subtitles, contentDescription = "CC", tint = if (activeCaption != null) MaterialTheme.colorScheme.primary else Color.Unspecified) } }; Button(onClick = { if (!downloadStarted) { downloadStarted = true; val r = resolved; val title = info.title.ifBlank { video.title }; val item = DownloadTracker.start(video.videoId + "_" + System.currentTimeMillis(), title); DownloadManager.downloadVideo(context, title, r?.downloadUrl, r?.audioUrl, r?.playUrl, item) } }) { if (downloadStarted) Icon(Icons.Default.Done, contentDescription = "Started") else Icon(Icons.Default.Download, contentDescription = "Download") } } }
                    }
                    // Chapters button
                    if (chapters.isNotEmpty()) {
                        item {
                            OutlinedButton(
                                onClick = { showChapters = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.List, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chapters (${chapters.size})")
                            }
                        }
                    }
                    
                    if (showChat && chatMessages.isNotEmpty()) { item { Text("Live Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), color = MaterialTheme.colorScheme.primary) }; items(chatMessages.reversed().take(50)) { msg -> Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Text("${msg.author}: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(msg.text, style = MaterialTheme.typography.bodyMedium) } } }
                    
                    // Description with show more button
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { showDescription = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    info.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Show more...",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    item { Column(modifier = Modifier.padding(horizontal = 16.dp)) { Text("Timestamp Notes", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Note...") }, singleLine = true); Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { if (noteText.isNotBlank()) { NotesManager.addNote(context, video.videoId, VideoNote(exoPlayer.currentPosition, noteText.trim())); notes = NotesManager.getNotes(context, video.videoId); noteText = "" } }) { Icon(Icons.Default.Add, contentDescription = "Add") } }; notes.forEachIndexed { index, note -> Row(modifier = Modifier.fillMaxWidth().clickable { exoPlayer.seekTo(note.timeMs) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(formatTimestamp(note.timeMs), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall) }; Spacer(modifier = Modifier.width(8.dp)); Text(note.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); IconButton(onClick = { NotesManager.deleteNote(context, video.videoId, index); notes = NotesManager.getNotes(context, video.videoId) }) { Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp)) } } } } }
                }
                if (!zenMode && streamInfo?.relatedStreams?.isNotEmpty() == true) { item { Text("Related Videos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }; streamInfo?.relatedStreams?.let { related -> items(related.filter { !it.isShort || !PrefsManager.isHideShorts(context) }) { rv -> Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(rv) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = rv.safeThumb, contentDescription = null, modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(12.dp)); Column { Text(rv.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(modifier = Modifier.height(4.dp)); Text(rv.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
                if (!hideComments) {
                    item { Text("Comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }
                    val commentCount = comments.itemCount
                    if (comments.loadState.refresh is androidx.paging.LoadState.Loading) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } } else { items(commentCount) { index -> val c = comments[index]; if (c != null) { Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { AsyncImage(model = c.authorThumbnail, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(12.dp)); Column { Text("${c.author} - ${c.commentedTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(2.dp)); Text(c.commentText, style = MaterialTheme.typography.bodyMedium); Spacer(modifier = Modifier.height(2.dp)); if (!hideCounters) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(formatCount(c.likes), style = MaterialTheme.typography.bodySmall) } } } } } }; if (comments.loadState.append is androidx.paging.LoadState.Loading) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } } }
                }
            }
        }
    }

    if (showSpeedDialog) { AlertDialog(onDismissRequest = { showSpeedDialog = false }, title = { Text("Playback Speed") }, text = { Column { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed -> Row(modifier = Modifier.fillMaxWidth().clickable { currentSpeed = speed; exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed); PrefsManager.setPlaybackSpeed(context, speed); showSpeedDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentSpeed == speed, onClick = { currentSpeed = speed; exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed); PrefsManager.setPlaybackSpeed(context, speed); showSpeedDialog = false }); Spacer(modifier = Modifier.width(8.dp)); Text("${speed}x") } } } }, confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("Close") } }) }
    
    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onDismiss = { showQuickActions = false },
            onChannelClick = onChannelClick,
            onBlockChannel = { channelName ->
                val notInterestedRepo = NotInterestedRepository(context)
                notInterestedRepo.blockChannel(channelId = null, name = channelName)
            },
            onNotInterested = { onBack() }
        )
    }
    
    if (showHideDialog) {
        val notInterestedRepo = remember { NotInterestedRepository(context) }
        AlertDialog(
            onDismissRequest = { showHideDialog = false },
            title = { Text("Not Interested") },
            text = { Text("Hide this video or channel from recommendations?") },
            confirmButton = {
                Button(onClick = {
                    notInterestedRepo.hideVideo(video)
                    showHideDialog = false
                    onBack()
                }) { Text("Hide Video") }
            },
            dismissButton = {
                TextButton(onClick = {
                    notInterestedRepo.blockChannel(channelId = null, name = video.uploaderName)
                    showHideDialog = false
                    onBack()
                }) { Text("Hide Channel") }
            }
        )
    }

    // Chapters Bottom Sheet
    if (showChapters && chapters.isNotEmpty()) {
        ChaptersBottomSheet(
            chapters = chapters,
            currentPositionMs = exoPlayer.currentPosition,
            onChapterClick = { timestampMs ->
                exoPlayer.seekTo(timestampMs)
                showChapters = false
            },
            onDismiss = { showChapters = false }
        )
    }

    // Description Bottom Sheet
    if (showDescription && streamInfo != null) {
        DescriptionBottomSheet(
            streamInfo = streamInfo!!,
            currentPositionMs = exoPlayer.currentPosition,
            onTimestampClick = { timestampMs ->
                exoPlayer.seekTo(timestampMs)
                showDescription = false
            },
            onDismiss = { showDescription = false }
        )
    }
}

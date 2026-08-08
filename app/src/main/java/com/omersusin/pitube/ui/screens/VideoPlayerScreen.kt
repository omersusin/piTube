package com.omersusin.pitube.ui.screens

import com.omersusin.pitube.data.WatchHistoryRepository
import com.omersusin.pitube.data.PlaybackSessionRepository
import com.omersusin.pitube.data.StatsRepository
import com.omersusin.pitube.data.SearchHistoryRepository

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
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
import kotlinx.coroutines.Dispatchers
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
        HistoryManager.addToHistory(context, video); WatchHistoryRepository.addVideo(context, video)
        StatsRepo.record(context, video.videoId, video.uploaderName)
        isLoading = true; error = null; downloadStarted = false; deArrowTitle = null; showOriginal = false; chatMessages = emptyList()
        notes = NotesManager.getNotes(context, video.videoId)
        scope.launch {
            try {
                val r = StreamResolver.resolve(video.videoId)
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
                } else { error = (r?.let { "No playable stream: hls=${it.hlsUrl != null} prog=${it.playUrl != null} merge=${it.videoOnlyUrl != null && it.audioUrl != null}" } ?: "All sources failed (NewPipe+InnerTube+Piped)") }
                try { if (PrefsManager.isSponsorBlockEnabled(context)) segments = SponsorBlockService.create().getSegments(video.videoId, """["sponsor","selfpromo","intro","outro","preview"]""") } catch (e: Exception) { segments = emptyList() }
                try { votes = RydService.create().getVotes(video.videoId) } catch (e: Exception) { }
                try { deArrowTitle = DeArrowService.create().getBranding(video.videoId).titles.maxByOrNull { it.votes }?.title } catch (e: Exception) { }
                try { captionTracks = Captions.tracks(video.videoId) } catch (e: Exception) { }
            } catch (e: Exception) { error = e.message } finally { isLoading = false }
        }
        scope.launch {
            try {
                var token = LiveChatManager.getInitialContinuation(video.videoId)
                if (token != null) showChat = true
                while (token != null) {
                    val res = LiveChatManager.poll(token)
                    if (res.first.isNotEmpty()) chatMessages = (chatMessages + res.first).takeLast(200)
                    token = res.second
                    delay(4000)
                }
            } catch (e: Exception) { }
        }
    }
    
    LaunchedEffect(activeCaption) {
        captions = activeCaption?.let { Captions.load(it) } ?: emptyList()
    }
    
    LaunchedEffect(exoPlayer.currentPosition) {
        val pos = exoPlayer.currentPosition
        currentCaption = captions.firstOrNull { pos >= it.startMs && pos < it.endMs }?.text ?: ""
    }

    DisposableEffect(video.videoId) {
        onDispose {
            ResumeManager.saveResumePosition(context, video.videoId, exoPlayer.currentPosition)
            SessionResume.save(context, video, exoPlayer.currentPosition)
        }
    }

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

    val inPip = PipState.inPip.value
    Column(modifier = Modifier.fillMaxSize()) {
        if (!inPip) TopAppBar(title = { }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, actions = {
            if (chatMessages.isNotEmpty()) { IconButton(onClick = { showChat = !showChat }) { Icon(Icons.Default.Chat, contentDescription = "Chat", tint = if (showChat) MaterialTheme.colorScheme.primary else Color.Unspecified) } }
            IconButton(onClick = { showHideDialog = true }) { Icon(Icons.Default.VisibilityOff, contentDescription = "Hide") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        if (inPip) { Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize()) } }
        else if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (error != null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error", color = MaterialTheme.colorScheme.error) } }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box {
                        AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = true } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
                        if (currentCaption.isNotBlank()) {
                            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                                Text(currentCaption, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                streamInfo?.let { info ->
                    item { Column(modifier = Modifier.padding(16.dp)) { Text(text = if (!showOriginal && deArrowTitle != null) deArrowTitle!! else info.title.ifBlank { video.title }, style = MaterialTheme.typography.titleLarge); if (deArrowTitle != null && deArrowTitle != info.title) { TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "🎯 Show honest title" else "Show original title", style = MaterialTheme.typography.bodySmall) } }; Spacer(modifier = Modifier.height(4.dp)); Row(modifier = Modifier.clickable { onChannelClick(info.uploaderUrl.substringAfter("/channel/")) }, verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = video.uploaderAvatar, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(info.uploader, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Button(onClick = { scope.launch { if (VideoEngagement.subscribe(context, info.uploaderUrl.substringAfter("/channel/"), !subscribed)) subscribed = !subscribed } }, colors = ButtonDefaults.buttonColors(containerColor = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error)) { Text(if (subscribed) "Subscribed" else "Subscribe") }
                    } } }
                    if (!hideLikes && !hideCounters) {
                        item { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.likes?.toLong() ?: 0L)); Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.dislikes?.toLong() ?: 0L)) } }; TextButton(onClick = { sleepChoice = when (sleepChoice) { 0 -> 5; 5 -> 15; 15 -> 30; else -> 0 }; sleepDeadline = if (sleepChoice == 0) 0L else System.currentTimeMillis() + sleepChoice * 60_000L }) { Text(if (sleepChoice == 0) "😴 Sleep" else "😴 ${sleepChoice}m") }; Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = { showSpeedDialog = true }) { Icon(Icons.Default.Speed, contentDescription = "Speed", tint = if (currentSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.Unspecified) }; if (captionTracks.isNotEmpty()) { IconButton(onClick = { activeCaption = if (activeCaption == null) captionTracks.firstOrNull() else null }) { Icon(Icons.Default.Subtitles, contentDescription = "CC", tint = if (activeCaption != null) MaterialTheme.colorScheme.primary else Color.Unspecified) } }; Button(onClick = { if (!downloadStarted) { downloadStarted = true; val r = resolved; val title = info.title.ifBlank { video.title }; val item = DownloadTracker.start(video.videoId + "_" + System.currentTimeMillis(), title); DownloadManager.downloadVideo(context, title, r?.downloadUrl, r?.audioUrl, r?.playUrl, item) } }) { if (downloadStarted) Icon(Icons.Default.Done, contentDescription = "Started") else Icon(Icons.Default.Download, contentDescription = "Download") } } }
                    } else {
                        item { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { sleepChoice = when (sleepChoice) { 0 -> 5; 5 -> 15; 15 -> 30; else -> 0 }; sleepDeadline = if (sleepChoice == 0) 0L else System.currentTimeMillis() + sleepChoice * 60_000L }) { Text(if (sleepChoice == 0) "😴 Sleep" else "😴 ${sleepChoice}m") }; Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = { showSpeedDialog = true }) { Icon(Icons.Default.Speed, contentDescription = "Speed", tint = if (currentSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.Unspecified) }; if (captionTracks.isNotEmpty()) { IconButton(onClick = { activeCaption = if (activeCaption == null) captionTracks.firstOrNull() else null }) { Icon(Icons.Default.Subtitles, contentDescription = "CC", tint = if (activeCaption != null) MaterialTheme.colorScheme.primary else Color.Unspecified) } }; Button(onClick = { if (!downloadStarted) { downloadStarted = true; val r = resolved; val title = info.title.ifBlank { video.title }; val item = DownloadTracker.start(video.videoId + "_" + System.currentTimeMillis(), title); DownloadManager.downloadVideo(context, title, r?.downloadUrl, r?.audioUrl, r?.playUrl, item) } }) { if (downloadStarted) Icon(Icons.Default.Done, contentDescription = "Started") else Icon(Icons.Default.Download, contentDescription = "Download") } } }
                    }
                    if (showChat && chatMessages.isNotEmpty()) { item { Text("Live Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), color = MaterialTheme.colorScheme.primary) }; items(chatMessages.reversed().take(50)) { msg -> Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Text("${msg.author}: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(msg.text, style = MaterialTheme.typography.bodyMedium) } } }
                    item { var expanded by remember { mutableStateOf(descriptionExpanded) }; Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(modifier = Modifier.padding(12.dp)) { Text(sanitizeYouTubeText(info.description), style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis); Text(text = if (expanded) "Show less" else "Show more", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } } }
                    item { Column(modifier = Modifier.padding(horizontal = 16.dp)) { Text("📝 Timestamp Notes", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Note...") }, singleLine = true); Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { if (noteText.isNotBlank()) { NotesManager.addNote(context, video.videoId, VideoNote(exoPlayer.currentPosition, noteText.trim())); notes = NotesManager.getNotes(context, video.videoId); noteText = "" } }) { Icon(Icons.Default.Add, contentDescription = "Add") } }; notes.forEachIndexed { index, note -> Row(modifier = Modifier.fillMaxWidth().clickable { exoPlayer.seekTo(note.timeMs) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(formatTimestamp(note.timeMs), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall) }; Spacer(modifier = Modifier.width(8.dp)); Text(note.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); IconButton(onClick = { NotesManager.deleteNote(context, video.videoId, index); notes = NotesManager.getNotes(context, video.videoId) }) { Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp)) } } } } }
                }
                if (!zenMode && streamInfo?.relatedStreams?.isNotEmpty() == true) { item { Text("Related Videos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }; streamInfo?.relatedStreams?.let { related -> items(related.filter { !it.isShort || !PrefsManager.isHideShorts(context) }) { rv -> Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(rv) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = rv.safeThumb, contentDescription = null, modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(12.dp)); Column { Text(rv.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(modifier = Modifier.height(4.dp)); Text(rv.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
                if (!hideComments) {
                    item { Text("Comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }
                    val commentCount = comments.itemCount
                    if (comments.loadState.refresh is androidx.paging.LoadState.Loading) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } } else { items(commentCount) { index -> val c = comments[index]; if (c != null) { Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { AsyncImage(model = c.authorThumbnail, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(12.dp)); Column { Text("${c.author} • ${c.commentedTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(2.dp)); Text(c.commentText, style = MaterialTheme.typography.bodyMedium); Spacer(modifier = Modifier.height(2.dp)); if (!hideCounters) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(formatCount(c.likes), style = MaterialTheme.typography.bodySmall) } } } } } }; if (comments.loadState.append is androidx.paging.LoadState.Loading) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } } }
                }
            }
        }
    }

    if (showSpeedDialog) {
        AlertDialog(onDismissRequest = { showSpeedDialog = false }, title = { Text("Playback Speed") }, text = {
            Column { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed -> Row(modifier = Modifier.fillMaxWidth().clickable { currentSpeed = speed; exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed); PrefsManager.setPlaybackSpeed(context, speed); showSpeedDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentSpeed == speed, onClick = { currentSpeed = speed; exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed); PrefsManager.setPlaybackSpeed(context, speed); showSpeedDialog = false }); Spacer(modifier = Modifier.width(8.dp)); Text("${speed}x") } } }
        }, confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("Close") } })
    }
    
    if (showHideDialog) {
        AlertDialog(onDismissRequest = { showHideDialog = false }, title = { Text("Not Interested") }, text = { Text("Hide this video or channel from recommendations?") },
            confirmButton = { Button(onClick = { NotInterested.hideVideo(context, video); showHideDialog = false; onBack() }) { Text("Hide Video") } },
            dismissButton = { TextButton(onClick = { NotInterested.hideChannel(context, video.uploaderName); showHideDialog = false; onBack() }) { Text("Hide Channel") } })
    }
}

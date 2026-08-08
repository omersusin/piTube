package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
    var downloadState by remember { mutableIntStateOf(-1) }
    var deArrowTitle by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var sleepChoice by remember { mutableIntStateOf(0) }
    var sleepDeadline by remember { mutableLongStateOf(0L) }
    var noteText by remember { mutableStateOf("") }
    var notes by remember(video.videoId) { mutableStateOf(NotesManager.getNotes(context, video.videoId)) }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var showChat by remember { mutableStateOf(false) }
    val zenMode = remember { PrefsManager.isZenMode(context) }
    val scope = rememberCoroutineScope()
    val exoPlayer = remember { PlayerHolder.getPlayer(context) }

    val comments = remember(video.videoId) {
        Pager(PagingConfig(pageSize = 20)) { CommentsPagingSource(PipedApiService.create(), video.videoId) }.flow
    }.collectAsLazyPagingItems()

    LaunchedEffect(video.videoId) {
        HistoryManager.addToHistory(context, video)
        isLoading = true; error = null; downloadState = -1; deArrowTitle = null; showOriginal = false
        notes = NotesManager.getNotes(context, video.videoId)
        scope.launch {
            try {
                // 1. Real streams via NewPipe Extractor
                val r = StreamResolver.resolve(video.videoId)
                resolved = r

                // 2. Metadata (related, description) via Piped
                var piped: StreamInfo? = null
                try { piped = PipedApiService.create().getStreams(video.videoId) } catch (e: Exception) { }
                streamInfo = piped ?: r?.let {
                    StreamInfo(title = it.title, description = it.description, uploader = it.uploader,
                        uploaderUrl = it.uploaderUrl, hls = it.playUrl, dash = null)
                }

                val playUrl = r?.playUrl
                if (playUrl != null) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(playUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else { error = "Could not find stream URL" }

                try { if (PrefsManager.isSponsorBlockEnabled(context)) segments = SponsorBlockService.create().getSegments(video.videoId, """["sponsor","selfpromo","intro","outro","preview"]""") } catch (e: Exception) { segments = emptyList() }
                try { votes = RydService.create().getVotes(video.videoId) } catch (e: Exception) { }
                try { deArrowTitle = DeArrowService.create().getBranding(video.videoId).titles.maxByOrNull { it.votes }?.title } catch (e: Exception) { }
            } catch (e: Exception) { error = e.message } finally { isLoading = false }
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
        if (!inPip) TopAppBar(
            title = { },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (chatMessages.isNotEmpty()) {
                    IconButton(onClick = { showChat = !showChat }) { Icon(Icons.Default.Chat, contentDescription = "Chat", tint = if (showChat) MaterialTheme.colorScheme.primary else Color.Unspecified) }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        if (inPip) { Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize()) } }
        else if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (error != null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error", color = MaterialTheme.colorScheme.error) } }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = true } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) }
                streamInfo?.let { info ->
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = if (!showOriginal && deArrowTitle != null) deArrowTitle!! else info.title.ifBlank { video.title }, style = MaterialTheme.typography.titleLarge)
                            if (deArrowTitle != null && deArrowTitle != info.title) { TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "🎯 Show honest title" else "Show original title", style = MaterialTheme.typography.bodySmall) } }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.clickable { onChannelClick(info.uploaderUrl.substringAfter("/channel/")) }) { Text(info.uploader, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.likes?.toLong() ?: 0L))
                                    Spacer(modifier = Modifier.width(12.dp)); Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(formatCount(votes?.dislikes?.toLong() ?: 0L))
                                }
                            }
                            TextButton(onClick = { sleepChoice = when (sleepChoice) { 0 -> 5; 5 -> 15; 15 -> 30; else -> 0 }; sleepDeadline = if (sleepChoice == 0) 0L else System.currentTimeMillis() + sleepChoice * 60_000L }) { Text(if (sleepChoice == 0) "😴 Sleep" else "😴 ${sleepChoice}m") }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = {
                                if (downloadState == -1 || downloadState >= 101) {
                                    downloadState = 0
                                    scope.launch(Dispatchers.IO) {
                                        val r = resolved
                                        val videoUrl = r?.downloadUrl ?: info.videoStreams.filter { it.mimeType.contains("mp4", true) }.maxByOrNull { it.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }?.url ?: info.hls
                                        val audioUrl = r?.audioUrl ?: info.audioStreams.maxByOrNull { it.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }?.url
                                        if (videoUrl == null) { downloadState = 102; return@launch }
                                        DownloadManager.downloadVideo(context, info.title.ifBlank { video.title }, videoUrl, audioUrl, onProgress = { p -> downloadState = p }, onDone = { downloadState = 101 }, onError = { downloadState = 102 })
                                    }
                                }
                            }) {
                                when { downloadState in 0..100 -> Text("$downloadState%"); downloadState == 101 -> Icon(Icons.Default.Done, contentDescription = "Done"); downloadState == 102 -> Text("Retry"); else -> Icon(Icons.Default.Download, contentDescription = "Download") }
                            }
                        }
                    }
                    if (showChat && chatMessages.isNotEmpty()) {
                        item { Text("Live Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), color = MaterialTheme.colorScheme.primary) }
                        items(chatMessages.reversed().take(50)) { msg ->
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("${msg.author}: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(info.description, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                                Text(text = if (expanded) "Show less" else "Show more", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("📝 Timestamp Notes", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Note...") }, singleLine = true)
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { if (noteText.isNotBlank()) { NotesManager.addNote(context, video.videoId, VideoNote(exoPlayer.currentPosition, noteText.trim())); notes = NotesManager.getNotes(context, video.videoId); noteText = "" } }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                            }
                            notes.forEachIndexed { index, note ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { exoPlayer.seekTo(note.timeMs) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(formatTimestamp(note.timeMs), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall) }
                                    Spacer(modifier = Modifier.width(8.dp)); Text(note.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { NotesManager.deleteNote(context, video.videoId, index); notes = NotesManager.getNotes(context, video.videoId) }) { Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }

                if (!zenMode && streamInfo?.relatedStreams?.isNotEmpty() == true) {
                    item { Text("Related Videos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }
                    streamInfo?.relatedStreams?.let { related ->
                        items(related.filter { !it.isShort }) { rv ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(rv) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = rv.safeThumb, contentDescription = null, modifier = Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column { Text(rv.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(modifier = Modifier.height(4.dp)); Text(rv.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }

                item { Text("Comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) }
                val commentCount = comments.itemCount
                if (comments.loadState.refresh is androidx.paging.LoadState.Loading) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    items(commentCount) { index ->
                        val c = comments[index]
                        if (c != null) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                AsyncImage(model = c.authorThumbnail, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("${c.author} • ${c.commentedTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(c.commentText, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(formatCount(c.likes), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    if (comments.loadState.append is androidx.paging.LoadState.Loading) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }
}

package com.omersusin.pitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.LikedVideosRepository
import com.omersusin.pitube.data.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedVideosScreen(onBack: () -> Unit, onVideoClick: (VideoItem) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val liked = remember { LikedVideosRepository.getAll(context) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Liked Videos") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        if (liked.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("No liked videos yet.\nTap the heart in a video.") }
        else LazyColumn { items(liked) { v ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onVideoClick(v) }.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                AsyncImage(model = v.safeThumb, null, modifier = Modifier.width(140.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.width(12.dp))
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            }
        } }
    }
}

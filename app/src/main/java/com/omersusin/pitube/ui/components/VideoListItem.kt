package com.omersusin.pitube.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omersusin.pitube.data.VideoItem

@Composable
fun VideoListItem(video: VideoItem, onClick: () -> Unit, onChannelClick: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val channelId = video.channelId ?: video.uploaderUrl?.substringAfter("/channel/")?.substringBefore("/") ?: ""
    val metadata = listOfNotNull(
        video.uploaderName,
        VideoItem.formatViewCount(video.views).takeIf { it.isNotBlank() },
        video.uploadedDate
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Thumbnail with duration badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .thumbnailGradientOverlay()
        ) {
            AsyncImage(
                model = video.highResThumbnailUrl ?: video.safeThumb,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.duration > 0 || video.isLive) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color = if (video.isLive) Color(0xFFCC0000).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (video.isLive) "LIVE" else video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info row: avatar + title + metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = video.uploaderAvatar ?: video.channelIconUrl,
                contentDescription = video.uploaderName,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onChannelClick(channelId) },
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onChannelClick(channelId) }
                )
            }
        }
    }
}

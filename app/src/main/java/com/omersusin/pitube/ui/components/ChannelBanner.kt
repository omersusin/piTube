package com.omersusin.pitube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

const val CHANNEL_BANNER_ASPECT_RATIO = 1060f / 175f

@Composable
fun ChannelBanner(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val bannerModifier = modifier
        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
        .fillMaxWidth()
        .aspectRatio(CHANNEL_BANNER_ASPECT_RATIO)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (imageUrl.isNullOrBlank()) {
        androidx.compose.foundation.layout.Box(modifier = bannerModifier)
        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = "Channel banner",
        modifier = bannerModifier,
        contentScale = ContentScale.Crop
    )
}

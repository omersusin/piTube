package com.omersusin.pitube.ui.screens.player.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omersusin.pitube.R
import com.omersusin.pitube.data.model.Video
import com.omersusin.pitube.innertube.YouTube
import com.omersusin.pitube.innertube.models.YouTubeClient
import com.omersusin.pitube.player.sabr.integration.SabrUrlResolver
import com.omersusin.pitube.ui.components.SubtitleCustomizer
import com.omersusin.pitube.ui.components.SubtitleStyle
import com.omersusin.pitube.ui.components.rememberFlowSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleCustomizerDialog(
    subtitleStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
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
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    Text(
                        text = stringResource(R.string.filter_subtitles),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                SubtitleCustomizer(
                    currentStyle = subtitleStyle,
                    onStyleChange = onStyleChange,
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

internal suspend fun trySabrDownloadFromDialog(
    context: Context,
    video: Video,
) {
    try {
        Toast.makeText(context, context.getString(R.string.toast_trying_sabr_download), Toast.LENGTH_SHORT).show()
        val sabrInfo =
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(8000L) {
                    val playerResponse =
                        YouTube
                            .player(video.id, client = YouTubeClient.ANDROID)
                            .getOrNull() ?: return@withTimeoutOrNull null
                    SabrUrlResolver.resolve(playerResponse)
                }
            }
        if (sabrInfo != null) {
            val codecHint = if (sabrInfo.videoItag in listOf(313, 271, 308, 248, 303, 247, 302, 244, 243, 242)) "vp9" else null
            com.omersusin.pitube.data.video.downloader.FlowDownloadService.startSabrDownload(
                context = context,
                video = video,
                quality = "best",
                sabrStreamingUrl = sabrInfo.streamingUrl,
                audioItag = sabrInfo.audioItag,
                audioLmt = sabrInfo.audioLmt,
                videoItag = sabrInfo.videoItag,
                videoLmt = sabrInfo.videoLmt,
                poToken = sabrInfo.poToken,
                visitorId = sabrInfo.visitorId,
                ustreamerConfig = sabrInfo.ustreamerConfig,
                durationMs = sabrInfo.durationMs,
                videoCodec = codecHint,
            )
            Toast.makeText(context, context.getString(R.string.toast_sabr_download_started), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_no_download_source), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_sabr_download_failed, e.message), Toast.LENGTH_SHORT).show()
    }
}

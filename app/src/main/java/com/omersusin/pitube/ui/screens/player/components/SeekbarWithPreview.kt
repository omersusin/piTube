package com.omersusin.pitube.ui.screens.player.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.omersusin.pitube.data.model.SponsorBlockSegment
import com.omersusin.pitube.innertube.models.StoryboardFrameset
import com.omersusin.pitube.utils.formatDuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import android.util.Log
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs
import kotlin.math.roundToInt

// Custom seekbar drawing buffer, SponsorBlock segments and chapter gaps over the progress track.
@Composable
fun rememberSponsorSegmentColors(
    playerPreferences: com.omersusin.pitube.data.local.PlayerPreferences,
): Map<String, Color> {
    val sponsor by playerPreferences.sbColorForCategory("sponsor").collectAsState(initial = null)
    val intro by playerPreferences.sbColorForCategory("intro").collectAsState(initial = null)
    val outro by playerPreferences.sbColorForCategory("outro").collectAsState(initial = null)
    val selfpromo by playerPreferences.sbColorForCategory("selfpromo").collectAsState(initial = null)
    val interaction by playerPreferences.sbColorForCategory("interaction").collectAsState(initial = null)
    val musicOfftopic by playerPreferences.sbColorForCategory("music_offtopic").collectAsState(initial = null)
    val filler by playerPreferences.sbColorForCategory("filler").collectAsState(initial = null)
    val preview by playerPreferences.sbColorForCategory("preview").collectAsState(initial = null)
    val exclusiveAccess by playerPreferences.sbColorForCategory("exclusive_access").collectAsState(initial = null)
    val poiHighlight by playerPreferences.sbColorForCategory("poi_highlight").collectAsState(initial = null)

    return buildMap {
        sponsor?.let { put("sponsor", Color(it)) }
        intro?.let { put("intro", Color(it)) }
        outro?.let { put("outro", Color(it)) }
        selfpromo?.let { put("selfpromo", Color(it)) }
        interaction?.let { put("interaction", Color(it)) }
        musicOfftopic?.let { put("music_offtopic", Color(it)) }
        filler?.let { put("filler", Color(it)) }
        preview?.let { put("preview", Color(it)) }
        exclusiveAccess?.let { put("exclusive_access", Color(it)) }
        poiHighlight?.let { put("poi_highlight", Color(it)) }
    }
}

@Composable
fun SeekbarWithPreview(
    /**
     * Progress provider rather than a value: the playhead is written several times a second, and
     * reading it at the call site subscribed the whole player-controls overlay to that tick.
     * Invoking it here confines the recomposition to this component.
     */
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<StreamSegment> = emptyList(),
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    sponsorSegmentColors: Map<String, Color> = emptyMap(),
    duration: Long = 0L,
    bufferedValue: Float = 0f,
    edgeAligned: Boolean = false,
    storyboardFrameset: StoryboardFrameset? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    val thumbFillColor = MaterialTheme.colorScheme.surface
    val thumbStateLayerColor = primaryColor.copy(alpha = 0.18f)

    var edgePointerActive by remember { mutableStateOf(false) }

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged || edgePointerActive

    val progress = value()

    // Internal value to keep the thumb following the finger smoothly
    var internalValue by remember { mutableFloatStateOf(progress) }

    // Sync internal value with external value when not interacting
    LaunchedEffect(progress) {
        if (!isInteracting) {
            internalValue = progress
        }
    }

    // Storyboard preview bubble geometry
    var seekbarWidth by remember { mutableFloatStateOf(0f) }
    var lastStoryboardLogAt by remember { mutableLongStateOf(0L) }
    val storyboardPreview: StoryboardPreviewData? = if (
        isInteracting &&
        storyboardFrameset != null &&
        duration > 0 &&
        seekbarWidth > 0f
    ) {
        val positionMs = (internalValue.coerceIn(0f, 1f) * duration).toLong()
        val frame = storyboardFrameset.frameBoundsAt(positionMs)
        val sheetUrl = frame?.let { storyboardFrameset.urls.getOrNull(it.urlIndex) }
        // Diagnostics: a scrub recomposes constantly, so throttle to one line
        // per state class — enough to tell "bubble never composes" apart from
        // "bubble composes but the image renders blank".
        val now = System.currentTimeMillis()
        if (now - lastStoryboardLogAt > 1500) {
            lastStoryboardLogAt = now
            if (frame == null) {
                Log.w(TAG, "preview SUPPRESSED frame=null pos=$positionMs urls=${storyboardFrameset.urls.size}")
            } else if (sheetUrl == null) {
                Log.w(TAG, "preview SUPPRESSED urlIndex=${frame.urlIndex} out-of-range urls=${storyboardFrameset.urls.size}")
            } else {
                Log.d(
                    TAG,
                    "preview ACTIVE urls=${storyboardFrameset.urls.size} cell=${storyboardFrameset.frameWidth}x" +
                        "${storyboardFrameset.frameHeight} idx=${frame.urlIndex} bounds=(${frame.left},${frame.top}," +
                        "${frame.right},${frame.bottom})",
                )
            }
        }
        if (frame != null && sheetUrl != null) {
            StoryboardPreviewData(storyboardFrameset, frame, sheetUrl, positionMs)
        } else {
            null
        }
    } else {
        null
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 10.dp else 5.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "trackHeight"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(clip = false)
            .onSizeChanged { seekbarWidth = it.width.toFloat() },
        contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.TopStart
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 14.dp else 32.dp),
            contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.Center
        ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 14.dp else trackHeight)
        ) {
            val trackHeightPx = trackHeight.toPx()
            val width = size.width
            val trackTop = if (edgeAligned) size.height - trackHeightPx else 0f
            val trackBottom = trackTop + trackHeightPx
            val trackCenterY = trackTop + trackHeightPx / 2f
            val capRadius = CornerRadius(trackHeightPx / 2f)

            val gapWidth = if (isInteracting) 3.dp.toPx() else 2.dp.toPx()
            val boundaries = if (chapters.isNotEmpty() && duration > 0) {
                chapters.asSequence()
                    .map { it.startTimeSeconds }
                    .filter { it > 0 }
                    .map { (it * 1000f) / duration.toFloat() }
                    .filter { it in 0f..1f }
                    .map { it * width }
                    .sorted()
                    .toList()
            } else {
                emptyList()
            }

            val segments = buildList {
                var segStart = 0f
                for (boundary in boundaries) {
                    val segEnd = boundary - gapWidth / 2f
                    if (segEnd > segStart) {
                        add(segStart to segEnd)
                    }
                    segStart = (boundary + gapWidth / 2f).coerceAtMost(width)
                }
                if (width > segStart) {
                    add(segStart to width)
                }
            }

            val trackPath = Path().apply {
                segments.forEachIndexed { index, (segStart, segEnd) ->
                    val startRadius = if (index == 0) capRadius else CornerRadius.Zero
                    val endRadius = if (index == segments.lastIndex) capRadius else CornerRadius.Zero
                    addRoundRect(
                        RoundRect(
                            rect = Rect(segStart, trackTop, segEnd, trackBottom),
                            topLeft = startRadius,
                            topRight = endRadius,
                            bottomRight = endRadius,
                            bottomLeft = startRadius
                        )
                    )
                }
            }

            clipPath(trackPath) {
                drawRect(
                    color = trackColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(width, trackHeightPx)
                )

                if (bufferedValue > 0f) {
                    drawRect(
                        color = bufferedTrackColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(width * bufferedValue.coerceIn(0f, 1f), trackHeightPx)
                    )
                }

                // Active track (progress)
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(width * internalValue, trackHeightPx)
                )

                // SponsorBlock segments above progress so they remain visible after playback passes them.
                if (duration > 0) {
                    sponsorSegments.forEach { segment ->
                        val startRatio = (segment.startTime * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                        val endRatio = (segment.endTime * 1000f / duration.toFloat()).coerceIn(0f, 1f)

                        if (endRatio > startRatio) {
                            val startX = startRatio * width
                            val segWidth = (endRatio * width) - startX

                            var segmentColor = when (segment.category) {
                                "sponsor" -> Color(0xFF00D100) // Green
                                "selfpromo" -> Color(0xFFFFFF00) // Yellow
                                "interaction", "poi_highlight" -> Color(0xFFFF00FF) // Magenta
                                "intro" -> Color(0xFF00FFFF) // Cyan
                                "outro" -> Color(0xFF00FFFF) // Cyan
                                "music_offtopic" -> Color(0xFFFF8000) // Orange
                                "filler" -> Color(0xFF9E9E9E) // Gray
                                "preview" -> Color(0xFFFF9800) // Deep orange
                                "exclusive_access" -> Color(0xFFAA00FF) // Purple
                                else -> Color(0xFF00D100)
                            }
                            sponsorSegmentColors[segment.category]?.let { segmentColor = it }
                            drawRect(
                                color = segmentColor.copy(alpha = 0.78f),
                                topLeft = Offset(startX, trackTop),
                                size = Size(segWidth, trackHeightPx)
                            )
                        }
                    }

                    val currentTimeSeconds = internalValue.coerceIn(0f, 1f) * duration / 1000f
                    val isInsideSponsorSegment = sponsorSegments.any { segment ->
                        currentTimeSeconds >= segment.startTime && currentTimeSeconds < segment.endTime
                    }
                    if (isInsideSponsorSegment) {
                        val playheadX = width * internalValue.coerceIn(0f, 1f)
                        val outerWidth = minOf(4.dp.toPx(), width)
                        val innerWidth = minOf(2.dp.toPx(), width)
                        drawRect(
                            color = thumbFillColor,
                            topLeft = Offset(
                                x = (playheadX - outerWidth / 2f).coerceIn(0f, width - outerWidth),
                                y = trackTop
                            ),
                            size = Size(outerWidth, trackHeightPx)
                        )
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset(
                                x = (playheadX - innerWidth / 2f).coerceIn(0f, width - innerWidth),
                                y = trackTop
                            ),
                            size = Size(innerWidth, trackHeightPx)
                        )
                    }
                }
            }

            if (edgeAligned && thumbScale > 0f) {
                val thumbRadius = 7.dp.toPx() * thumbScale
                val thumbX = if (width > thumbRadius * 2f) {
                    (width * internalValue).coerceIn(thumbRadius, width - thumbRadius)
                } else {
                    width * internalValue
                }
                drawCircle(
                    color = primaryColor.copy(alpha = 0.24f),
                    radius = thumbRadius + 8.dp.toPx(),
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }

        // The actual slider
        @OptIn(ExperimentalMaterial3Api::class)
        Slider(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                onValueChange(newValue)
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
            },
            modifier = if (edgeAligned) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = thumbFillColor,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent
            ),
            thumb = {
                if (edgeAligned) {
                    Spacer(modifier = Modifier.size(0.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(thumbScale)
                            .drawBehind {
                                if (isInteracting) {
                                    drawCircle(
                                        color = thumbStateLayerColor,
                                        radius = 20.dp.toPx()
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(thumbFillColor, CircleShape)
                                .border(3.dp, primaryColor, CircleShape)
                        )
                    }
                }
            }
        )

        if (edgeAligned) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(enabled, valueRange, steps) {
                        if (!enabled) return@pointerInput

                        fun valueForX(x: Float): Float {
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = (x / width).coerceIn(0f, 1f)
                            val steppedFraction = if (steps > 0) {
                                val intervals = steps + 1
                                (fraction * intervals).roundToInt()
                                    .coerceIn(0, intervals)
                                    .toFloat() / intervals.toFloat()
                            } else {
                                fraction
                            }
                            return valueRange.start +
                                (valueRange.endInclusive - valueRange.start) * steppedFraction
                        }

                        fun updateValueFromX(x: Float) {
                            val newValue = valueForX(x)
                            if (abs(newValue - internalValue) > 0.0001f) {
                                internalValue = newValue
                                onValueChange(newValue)
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            edgePointerActive = true
                            down.consume()
                            updateValueFromX(down.position.x)

                            try {
                                var activePointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == activePointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                    activePointerId = change.id
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    if (change.positionChange() != Offset.Zero) {
                                        updateValueFromX(change.position.x)
                                    }
                                    change.consume()
                                }
                            } finally {
                                edgePointerActive = false
                                onValueChangeFinished?.invoke()
                            }
                        }
                    }
            )
        }

        storyboardPreview?.let { preview ->
            // Render through a Popup: the bubble draws in the window overlay
            // layer, so no ancestor container (video surface wrapper, controls
            // fade group, edge insets) can clip it — the inline offset version
            // was getting cut off at the top of the controls stack.
            val density = LocalDensity.current
            val bubbleWidthPx = with(density) { StoryboardPreviewWidth.toPx() }
            val bubbleHeightPx = bubbleWidthPx *
                (preview.frameset.frameHeight.toFloat() / preview.frameset.frameWidth.toFloat())
            val thumbX = seekbarWidth * internalValue.coerceIn(0f, 1f)
            val bubbleX = (thumbX - bubbleWidthPx / 2f)
                .coerceIn(0f, (seekbarWidth - bubbleWidthPx).coerceAtLeast(0f))
            val verticalGapPx = with(density) { 12.dp.toPx() }
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = (anchorBounds.left + bubbleX.roundToInt())
                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val y = (anchorBounds.top - popupContentSize.height - verticalGapPx.roundToInt())
                            .coerceAtLeast(0)
                        return IntOffset(x, y)
                    }
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                StoryboardPreviewBubble(
                    sheetUrl = preview.sheetUrl,
                    frameset = preview.frameset,
                    frame = preview.frame,
                    positionMs = preview.positionMs,
                )
            }
        }
        }
    }
}

private data class StoryboardPreviewData(
    val frameset: StoryboardFrameset,
    val frame: StoryboardFrameset.FrameBounds,
    val sheetUrl: String,
    val positionMs: Long,
)

@Composable
private fun StoryboardPreviewBubble(
    sheetUrl: String,
    frameset: StoryboardFrameset,
    frame: StoryboardFrameset.FrameBounds,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val bubbleWidth = StoryboardPreviewWidth
    val cellScale = bubbleWidth / frameset.frameWidth
    val bubbleHeight = cellScale * frameset.frameHeight
    val sheetWidth = cellScale * frameset.framesPerPageX * frameset.frameWidth
    val sheetHeight = cellScale * frameset.framesPerPageY * frameset.frameHeight

    // Decode at the SOURCE sprite-sheet's true pixel grid. The old
    // density-scaled dp size asked Coil for e.g. ~4200px regardless of what
    // the actual sheet is, and failures surfaced as a permanently empty box.
    // Memory cache stays off: long videos carry many multi-megabyte pages;
    // compressed pages stay on disk.
    val context = LocalContext.current
    val sheetSourceW = frameset.framesPerPageX * frameset.frameWidth
    val sheetSourceH = frameset.framesPerPageY * frameset.frameHeight
    val imageRequest = remember(sheetUrl, sheetSourceW, sheetSourceH) {
        ImageRequest.Builder(context)
            .data(sheetUrl)
            .size(sheetSourceW, sheetSourceH)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true)
            .crossfade(false)
            .listener(
                onStart = { Log.d(TAG, "sheet START $sheetUrl") },
                onSuccess = { _, result ->
                    Log.d(TAG, "sheet OK via ${result.dataSource} $sheetUrl")
                },
                onError = { _, result ->
                    Log.w(TAG, "storyboard sheet failed $sheetUrl: ${result.throwable}")
                },
            )
            .build()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(bubbleWidth, bubbleHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = -(cellScale * frame.left).roundToPx(),
                            y = -(cellScale * frame.top).roundToPx()
                        )
                    }
                    .size(sheetWidth, sheetHeight)
            )
        }
        Text(
            text = formatDuration((positionMs / 1000L).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 3.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

private val StoryboardPreviewWidth = 168.dp

private const val TAG = "SeekbarPreview"

package com.omersusin.pitube.ui.screens.settings.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Lightweight drag-to-reorder column with a 6-dot handle per row.
 *
 * Long-press the handle and drag: rows swap as the dragged item crosses their
 * midpoint; [onMove] fires once per swap so callers persist the new order.
 *
 * Implementation notes (why it works): the drag detector lives on the handle
 * keyed by [Unit] so it is NEVER restarted mid-gesture when [items] reorder;
 * all changing values (items, callback, this row's key) are read through
 * [rememberUpdatedState].
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    itemKey: (T) -> String,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = remember { mutableIntStateOf(0) }

    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { _, item ->
            val key = itemKey(item)
            val isDragging = draggingKey == key
            val latestRowKey by rememberUpdatedState(key)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .onGloballyPositioned { coords ->
                        if (itemHeightPx.intValue == 0 && coords.size.height > 0) {
                            itemHeightPx.intValue = coords.size.height
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { itemContent(item) }
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = latestRowKey
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val h = itemHeightPx.intValue.takeIf { it > 0 }
                                        ?: return@detectDragGesturesAfterLongPress
                                    var from = currentItems.indexOfFirst { itemKey(it) == draggingKey }
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    while (dragOffsetY > h / 2f && from < currentItems.lastIndex) {
                                        currentOnMove(from, from + 1)
                                        from++
                                        dragOffsetY -= h
                                    }
                                    while (dragOffsetY < -h / 2f && from > 0) {
                                        currentOnMove(from, from - 1)
                                        from--
                                        dragOffsetY += h
                                    }
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    dragOffsetY = 0f
                                },
                            )
                        },
                )
            }
        }
    }
}

package com.abdellatif.fleur.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

@Composable
fun Modifier.dragReorder(
    id: String,
    list: LazyListState,
    onMove: (String, String) -> Unit,
): Modifier {
    val move by rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = LocalPreferences.current.haptics
    val background = MaterialTheme.colorScheme.background
    var dragging by remember(id) { mutableStateOf(false) }
    var cursor by remember(id) { mutableFloatStateOf(0f) }
    var touchOffset by remember(id) { mutableFloatStateOf(0f) }
    return zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            val current = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0
            translationY = if (dragging) cursor - current - touchOffset else 0f
            shadowElevation = if (dragging) 3.dp.toPx() else 0f
        }
        .drawBehind { if (dragging) drawRect(background) }
        .pointerInput(id, list) {
            var lastTarget: String? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    dragging = true
                    touchOffset = position.y
                    cursor =
                        (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset
                            ?: 0) + position.y
                    lastTarget = null
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onDrag = { change, amount ->
                    change.consume()
                    cursor += amount.y
                    val layout = list.layoutInfo
                    val target =
                        layout.visibleItemsInfo
                            .firstOrNull {
                                it.key is String &&
                                    cursor >= it.offset &&
                                    cursor < it.offset + it.size
                            }
                            ?.key as? String
                    if (target == id) lastTarget = null
                    else if (target != null && target != lastTarget) {
                        move(id, target)
                        lastTarget = target
                    }
                    val edge = 64.dp.toPx()
                    val scroll =
                        when {
                            cursor < layout.viewportStartOffset + edge -> -18f
                            cursor > layout.viewportEndOffset - edge -> 18f
                            else -> 0f
                        }
                    if (scroll != 0f) scope.launch { list.scrollBy(scroll) }
                },
            )
        }
}

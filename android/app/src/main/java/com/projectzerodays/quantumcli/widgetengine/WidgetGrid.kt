package com.projectzerodays.quantumcli.widgetengine

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.projectzerodays.quantumcli.education.ContentRegistry
import com.projectzerodays.quantumcli.education.WalkthroughEngine

/**
 * Drag / lock / arrange widget grid.
 *
 * Long-press a card to lift it (haptic tick), drag it over another card to
 * reorder live, release to persist. The lock chip freezes the layout and
 * fires the `widget.lock.toggled` walkthrough event. Arrangement persists
 * per-route via [WidgetLayoutStore]. Cards reflow across two columns on
 * wide screens (FlowRow), and the grid lives happily inside a scrolling
 * parent — cards stack vertically on phones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetGrid(
    route: String,
    initialOrder: List<String>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var locked by remember { mutableStateOf(WidgetLayoutStore.isLocked(route)) }
    val order = remember(initialOrder) { WidgetLayoutStore.load(route, initialOrder).toMutableStateList() }
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            order.forEach { id ->
                val isDragging = draggingId == id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                shadowElevation = 24f
                            }
                        }
                        .onGloballyPositioned { itemBounds[id] = it.boundsInRoot() }
                        .then(
                            if (locked) Modifier else Modifier.pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingId = id
                                        dragStartCenter = itemBounds[id]?.center ?: Offset.Zero
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { _, amount ->
                                        val dragged = draggingId ?: return@detectDragGesturesAfterLongPress
                                        dragOffset += amount
                                        val pointer = dragStartCenter + dragOffset
                                        val target = itemBounds.entries.firstOrNull {
                                            it.key != dragged && it.value.contains(pointer)
                                        }?.key ?: return@detectDragGesturesAfterLongPress
                                        val from = order.indexOf(dragged)
                                        val to = order.indexOf(target)
                                        if (from >= 0 && to >= 0) {
                                            order.add(to, order.removeAt(from))
                                            // Anchor the drag math to the item's new home.
                                            dragStartCenter = itemBounds[dragged]?.center
                                                ?: (dragStartCenter + dragOffset)
                                            dragOffset = Offset.Zero
                                        }
                                    },
                                    onDragEnd = {
                                        val dragged = draggingId
                                        if (dragged != null) {
                                            WidgetLayoutStore.save(WidgetLayout(route, order.toList()))
                                        }
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = Offset.Zero
                                    },
                                )
                            }
                        ),
                ) { itemContent(id) }
            }
        }

        if (!locked) {
            androidx.compose.material3.Text(
                text = ContentRegistry.get("widget.unlock_hint")?.body
                    ?: "Long-press a card and drag to arrange — lock when done",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LockChip(
            locked = locked,
            onToggle = {
                locked = !locked
                WidgetLayoutStore.setLocked(route, locked)
                if (!locked) WalkthroughEngine.triggered("widget.lock.toggled")
            },
        )
    }
}

@Composable
private fun LockChip(locked: Boolean, onToggle: () -> Unit) {
    val tooltip = ContentRegistry.get("widget.lock")
    androidx.compose.material3.AssistChip(
        onClick = onToggle,
        label = {
            androidx.compose.material3.Text(
                if (locked) tooltip?.title ?: "Arrange" else "Lock layout"
            )
        },
        modifier = Modifier.padding(top = 6.dp),
    )
}

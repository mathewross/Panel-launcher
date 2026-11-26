package com.panel.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.AppEntry

private const val TAP = 1
private const val CANCEL = 2

/**
 * Home-screen grid. Tap opens an app; long-press and drag moves it (the others slide out of
 * the way); long-press without moving shows the app's menu.
 */
@Composable
fun ReorderableAppGrid(
    vm: LauncherViewModel,
    apps: List<AppEntry>,
    columns: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val savedOrder = apps.map { it.key }
    var order by remember { mutableStateOf(savedOrder) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    val byKey by rememberUpdatedState(apps.associateBy { it.key })

    // Follow external changes (added/removed apps), but never in the middle of a drag.
    LaunchedEffect(savedOrder) { if (draggingKey == null) order = savedOrder }

    fun itemAt(pos: Offset): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { i ->
        pos.x >= i.offset.x && pos.x < i.offset.x + i.size.width &&
            pos.y >= i.offset.y && pos.y < i.offset.y + i.size.height
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val key = itemAt(down.position)?.key as? String ?: return@awaitEachGesture

                // Phase 1: tap, scroll, or long-press?
                val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result = 0
                    while (result == 0) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        result = when {
                            c == null -> CANCEL
                            c.changedToUpIgnoreConsumed() -> if (c.isConsumed) CANCEL else TAP
                            c.isConsumed -> CANCEL // the grid started scrolling
                            (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> CANCEL
                            else -> 0
                        }
                    }
                    result
                }
                when (outcome) {
                    TAP -> { byKey[key]?.let { vm.launch(it) }; return@awaitEachGesture }
                    CANCEL -> return@awaitEachGesture
                }

                // Phase 2: long-pressed. Drag to move; no movement means "show the menu".
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                draggingKey = key
                dragOffset = Offset.Zero
                var moved = 0f
                while (true) {
                    // Initial pass + consume, so the grid doesn't scroll and the panel doesn't open.
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) { c.consume(); break }
                    val d = c.positionChange()
                    c.consume()
                    if (d == Offset.Zero) continue
                    moved += d.getDistance()
                    dragOffset += d

                    val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
                    if (info.index != order.indexOf(key)) continue // wait for the last move to be laid out
                    val center = Offset(
                        info.offset.x + dragOffset.x + info.size.width / 2f,
                        info.offset.y + dragOffset.y + info.size.height / 2f,
                    )
                    val target = itemAt(center) ?: continue
                    if (target.key == key) continue
                    val from = order.indexOf(key)
                    val to = order.indexOf(target.key as? String ?: continue)
                    if (from < 0 || to < 0) continue
                    order = order.toMutableList().apply { add(to, removeAt(from)) }
                    // The item's slot moves to the target's position; keep it under the finger.
                    dragOffset += Offset(
                        (info.offset.x - target.offset.x).toFloat(),
                        (info.offset.y - target.offset.y).toFloat(),
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (moved < viewConfiguration.touchSlop) {
                    menuKey = key
                } else {
                    // Keep any saved keys we aren't showing (e.g. an app that's temporarily unavailable).
                    vm.prefs.favorites = order + vm.prefs.favorites.filter { it !in order }
                }
                draggingKey = null
                dragOffset = Offset.Zero
            }
        },
    ) {
        items(order, key = { it }) { key ->
            val app = byKey[key] ?: return@items
            val dragging = key == draggingKey
            Box(
                if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        scaleX = 1.12f
                        scaleY = 1.12f
                    }
                } else Modifier.animateItem(),
                contentAlignment = Alignment.Center,
            ) {
                AppVisual(app, iconSize, vm.prefs.iconRoundness, vm.prefs.showLabels)
                DropdownMenu(expanded = menuKey == key, onDismissRequest = { menuKey = null }) {
                    homeMenu(vm, app, inDock = false).forEach { a ->
                        DropdownMenuItem(text = { Text(a.label) }, onClick = { menuKey = null; a.run() })
                    }
                }
            }
        }
    }
}

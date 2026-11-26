package com.panel.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.panel.launcher.LauncherViewModel
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAP = 1
private const val CANCEL = 2

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())

/**
 * Home-screen grid with fixed cells, like the Pixel launcher: an app can sit in any cell.
 * Tap opens; long-press and drag moves it to any cell (dropping on another app swaps the
 * two); long-press without moving shows the app's menu.
 */
@Composable
fun FreeAppGrid(
    vm: LauncherViewModel,
    columns: Int,
    iconSize: Dp,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val byKey by rememberUpdatedState(vm.appsByKey)
    val saved = vm.prefs.favorites

    var slots by remember { mutableStateOf(saved) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverIndex by remember { mutableIntStateOf(-1) }
    var settleKey by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) { if (draggingKey == null) slots = saved }

    val showLabels = vm.prefs.showLabels
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellW = maxWidth / columns
        val cellH = iconSize + 12.dp + (if (showLabels) 24.dp else 0.dp) + 8.dp
        val rowsFit = max(1, (maxHeight / cellH).toInt())
        val lastUsed = slots.indexOfLast { vm.occupied(it) }
        val rows = max(rowsFit, lastUsed / columns + 1)
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }

        fun indexAt(p: Offset): Int {
            if (p.x < 0 || p.y < 0) return -1
            val c = (p.x / cellWpx).toInt()
            val r = (p.y / cellHpx).toInt()
            return if (c < columns && r < rows) r * columns + c else -1
        }
        fun slotOffset(i: Int) = IntOffset(((i % columns) * cellWpx).roundToInt(), ((i / columns) * cellHpx).roundToInt())
        fun slotCenter(i: Int) = Offset((i % columns + 0.5f) * cellWpx, (i / columns + 0.5f) * cellHpx)

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = rows > rowsFit && draggingKey == null)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cellH * rows)
                    .pointerInput(columns, rows, cellWpx, cellHpx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val from = indexAt(down.position)
                            val key = slots.getOrNull(from)?.takeIf { vm.occupied(it) } ?: return@awaitEachGesture

                            // Phase 1: tap, scroll, or long-press?
                            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var result = 0
                                while (result == 0) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    result = when {
                                        c == null -> CANCEL
                                        c.changedToUpIgnoreConsumed() -> if (c.isConsumed) CANCEL else TAP
                                        c.isConsumed -> CANCEL
                                        (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> CANCEL
                                        else -> 0
                                    }
                                }
                                result
                            }
                            when (outcome) {
                                TAP -> {
                                    val folder = vm.folderOf(key)
                                    if (folder != null) onOpenFolder(folder.id) else byKey[key]?.let { vm.launch(it) }
                                    return@awaitEachGesture
                                }
                                CANCEL -> return@awaitEachGesture
                            }

                            // Phase 2: long-pressed — drag it around.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingKey = key
                            dragOffset = Offset.Zero
                            hoverIndex = from
                            var moved = 0f
                            while (true) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) { c.consume(); break }
                                val d = c.positionChange()
                                c.consume()
                                if (d == Offset.Zero) continue
                                moved += d.getDistance()
                                dragOffset += d
                                val over = indexAt(slotCenter(from) + dragOffset)
                                if (over != hoverIndex) {
                                    hoverIndex = over
                                    if (over >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                            if (moved < viewConfiguration.touchSlop) {
                                menuKey = key
                                draggingKey = null
                                hoverIndex = -1
                                return@awaitEachGesture
                            }

                            val target = hoverIndex
                            var finalIndex = from
                            if (target >= 0 && target != from) {
                                // Empty cell: move. Onto an app: new folder. Onto a folder: add to it.
                                slots = vm.dropSlot(slots, from, target)
                                finalIndex = target
                            }
                            // Glide from where it was dropped into its cell.
                            val remaining = (slotOffset(from) - slotOffset(finalIndex)).let {
                                Offset(it.x.toFloat(), it.y.toFloat())
                            } + dragOffset
                            dragOffset = remaining
                            hoverIndex = -1
                            scope.launch {
                                settle.snapTo(remaining)
                                settleKey = key
                                draggingKey = null
                                dragOffset = Offset.Zero
                                settle.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = 600f))
                                settleKey = null
                            }
                        }
                    },
            ) {
                // Where the app will land.
                if (draggingKey != null && hoverIndex >= 0) {
                    Box(
                        Modifier
                            .offset { slotOffset(hoverIndex) }
                            .size(cellW, cellH)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(WallpaperText.color.copy(alpha = 0.14f)),
                    )
                }
                slots.forEachIndexed { index, k ->
                    val app = byKey[k]
                    val folder = vm.folderOf(k)?.takeIf { vm.occupied(k) }
                    if (app != null || folder != null) key(k) {
                        val animated by animateIntOffsetAsState(slotOffset(index), label = "slot")
                        val dragging = k == draggingKey
                        val settling = k == settleKey
                        Box(
                            Modifier
                                .offset {
                                    when {
                                        dragging -> slotOffset(index) + dragOffset.round()
                                        settling -> slotOffset(index) + settle.value.round()
                                        else -> animated
                                    }
                                }
                                .zIndex(if (dragging || settling) 1f else 0f)
                                .size(cellW, cellH)
                                .graphicsLayer {
                                    val s = if (dragging) 1.12f else 1f
                                    scaleX = s
                                    scaleY = s
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (app != null) AppVisual(app, iconSize, vm.prefs.iconRoundness, showLabels)
                            else if (folder != null) FolderVisual(vm, folder, iconSize, showLabels)
                            DropdownMenu(expanded = menuKey == k, onDismissRequest = { menuKey = null }) {
                                val items = if (app != null) homeMenu(vm, app, inDock = false) else listOf(
                                    MenuAction("Open") { folder?.let { onOpenFolder(it.id) } },
                                    MenuAction("Ungroup") { folder?.let { vm.dissolveFolder(it.id) } },
                                )
                                items.forEach { a ->
                                    DropdownMenuItem(text = { Text(a.label) }, onClick = { menuKey = null; a.run() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

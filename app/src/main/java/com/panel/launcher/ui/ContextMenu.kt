package com.panel.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One row of a long-press menu. */
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val run: () -> Unit,
)

/** A menu to show: where it was pressed, what to offer, and (optionally) the item to lift. */
class MenuRequest(
    val anchor: Rect,
    val items: List<MenuAction>,
    val preview: (@Composable () -> Unit)? = null,
)

@Stable
class ContextMenuController {
    var request by mutableStateOf<MenuRequest?>(null)
        private set

    fun show(anchor: Rect, items: List<MenuAction>, preview: (@Composable () -> Unit)? = null) {
        if (items.isNotEmpty()) request = MenuRequest(anchor, items, preview)
    }

    fun dismiss() { request = null }
}

val LocalContextMenu = staticCompositionLocalOf { ContextMenuController() }

private val MenuWidth = 236.dp
private val RowHeight = 48.dp

/**
 * iOS-style long-press menu: everything else dims and blurs (the home layer blurs itself while
 * [ContextMenuController.request] is set), the pressed item is lifted, and a rounded menu card
 * appears next to it.
 */
@Composable
fun BoxScope.ContextMenuOverlay(controller: ContextMenuController) {
    val req = controller.request
    val t by animateFloatAsState(if (req != null) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "menu")
    val last = remember { arrayOfNulls<MenuRequest>(1) }
    if (req != null) last[0] = req
    val r = req ?: last[0]
    BackHandler(enabled = req != null) { controller.dismiss() }
    if (r == null || t <= 0.01f) return

    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = t }
            .background(Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { controller.dismiss() } },
    )

    // The pressed item, lifted above the blur.
    r.preview?.let { preview ->
        val w = with(density) { r.anchor.width.toDp() }
        val h = with(density) { r.anchor.height.toDp() }
        Box(
            Modifier
                .offset { IntOffset(r.anchor.left.roundToInt(), r.anchor.top.roundToInt()) }
                .size(w, h)
                .graphicsLayer {
                    val s = 1f + 0.08f * t
                    scaleX = s; scaleY = s
                },
            contentAlignment = Alignment.TopCenter,
        ) { preview() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val cardW = with(density) { MenuWidth.toPx() }
        val cardH = with(density) { (RowHeight * r.items.size).toPx() }
        val gap = with(density) { 10.dp.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val bottomLimit = maxH - with(density) { 72.dp.toPx() }
        val below = r.anchor.bottom + gap + cardH < bottomLimit
        val y = if (below) r.anchor.bottom + gap else (r.anchor.top - gap - cardH).coerceAtLeast(margin)
        val x = (r.anchor.center.x - cardW / 2f).coerceIn(margin, (maxW - cardW - margin).coerceAtLeast(margin))
        val originX = ((r.anchor.center.x - x) / cardW).coerceIn(0f, 1f)

        Column(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(MenuWidth)
                .graphicsLayer {
                    alpha = t
                    val s = 0.82f + 0.18f * t
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(originX, if (below) 0f else 1f)
                }
                .shadow(24.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xEB2A2A2D)),
        ) {
            r.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = Color(0x26FFFFFF), thickness = 0.5.dp)
                val color = if (item.destructive) Color(0xFFFF453A) else Color.White
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .clickable { controller.dismiss(); item.run() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    item.icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

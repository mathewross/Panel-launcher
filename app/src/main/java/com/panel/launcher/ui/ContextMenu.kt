package com.panel.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
    /** Extra content above the actions (shortcuts, notifications); makes the card wider. */
    val extra: (@Composable () -> Unit)? = null,
)

@Stable
class ContextMenuController {
    var request by mutableStateOf<MenuRequest?>(null)
        private set

    fun show(
        anchor: Rect,
        items: List<MenuAction>,
        extra: (@Composable () -> Unit)? = null,
        preview: (@Composable () -> Unit)? = null,
    ) {
        if (items.isNotEmpty() || extra != null) request = MenuRequest(anchor, items, preview, extra)
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

    // The card: measured first, then placed below the item if it fits, otherwise above,
    // otherwise as high as needed to stay on screen. Moves up for the keyboard when replying.
    val wide = r.extra != null
    val cardWidth = if (wide) 336.dp else MenuWidth
    val origin = remember { floatArrayOf(0.5f, 0f) }
    Layout(
        modifier = Modifier.fillMaxSize().imePadding(),
        content = {
            Column(
                Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        alpha = t
                        val s = 0.82f + 0.18f * t
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(origin[0], origin[1])
                    }
                    .shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xF5242427))
                    .border(0.75.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp)),
            ) {
                r.extra?.let { extra ->
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp)) { extra() }
                    if (r.items.isNotEmpty()) HorizontalDivider(color = Color(0x33FFFFFF), thickness = 6.dp)
                }
                r.items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 0.5.dp)
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
        },
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val margin = 16.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val p = measurables.first().measure(
            Constraints(maxWidth = (maxW - 2 * margin).coerceAtLeast(0), maxHeight = (maxH - 2 * margin).coerceAtLeast(0)),
        )
        val below = r.anchor.bottom + gap + p.height <= maxH - margin
        val above = r.anchor.top - gap - p.height >= margin
        val y = when {
            below -> r.anchor.bottom.roundToInt() + gap
            above -> r.anchor.top.roundToInt() - gap - p.height
            else -> (maxH - margin - p.height).coerceAtLeast(margin)
        }
        val x = (r.anchor.center.x - p.width / 2f).roundToInt().coerceIn(margin, (maxW - p.width - margin).coerceAtLeast(margin))
        origin[0] = ((r.anchor.center.x - x) / p.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        origin[1] = if (below) 0f else 1f
        layout(maxW, maxH) { p.place(x, y) }
    }
}

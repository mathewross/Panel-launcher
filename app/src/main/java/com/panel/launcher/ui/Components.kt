package com.panel.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.data.AppEntry

val TileShape = RoundedCornerShape(26.dp)

/** A rounded "widget" box, iOS style. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    shape: Shape = TileShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalTiles.current
    val haptics = LocalHapticFeedback.current
    var m = modifier
        .clip(shape)
        .background(p.bg)
        .border(0.75.dp, p.border, shape)
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
        )
    }
    // On see-through glass, give text a soft shadow so it reads on bright wallpapers too.
    val style = if (p.glass) LocalTextStyle.current.merge(TextStyle(shadow = WallpaperText.shadow)) else LocalTextStyle.current
    CompositionLocalProvider(LocalTextStyle provides style) {
        Box(m.padding(padding), content = content)
    }
}

/** Receives each icon's on-screen bounds, so launches can grow out of the icon. */
val LocalIconBounds = staticCompositionLocalOf<(String, android.graphics.Rect) -> Unit> { { _, _ -> } }

@Composable
fun AppIcon(app: AppEntry, size: Dp, roundness: Int, modifier: Modifier = Modifier) {
    val record = LocalIconBounds.current
    Image(
        bitmap = app.icon,
        contentDescription = app.label,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .onGloballyPositioned { c ->
                val r = c.boundsInWindow()
                record(app.key, android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
            }
            .clip(RoundedCornerShape(roundness.coerceIn(0, 50))),
    )
}

data class MenuAction(val label: String, val run: () -> Unit)

/** Icon + label that opens on tap and shows a menu on long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppEntry,
    iconSize: Dp,
    roundness: Int,
    showLabel: Boolean,
    onOpen: () -> Unit,
    menu: () -> List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        AppVisual(
            app, iconSize, roundness, showLabel,
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); open = true },
                ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu().forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, onClick = { open = false; a.run() })
            }
        }
    }
}

/** Icon with its name underneath, no touch handling. */
@Composable
fun AppVisual(app: AppEntry, iconSize: Dp, roundness: Int, showLabel: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        AppIcon(app, iconSize, roundness)
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label,
                style = WallpaperText,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(iconSize + 16.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

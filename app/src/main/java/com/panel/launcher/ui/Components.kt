package com.panel.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
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
    var m = if (p.glass) modifier.glass(shape, p).clip(shape)
    else modifier.clip(shape).background(p.bg).border(0.75.dp, p.border, shape)
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
    val look = LocalIconLook.current
    Image(
        bitmap = app.icon,
        contentDescription = app.label,
        contentScale = ContentScale.Crop,
        colorFilter = look.filter,
        modifier = modifier
            .size(size)
            .onGloballyPositioned { c ->
                val r = c.boundsInWindow()
                record(app.key, android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
            }
            .clip(look.shape),
    )
}


/** Icon + label that opens on tap and shows the iOS-style menu on long-press. */
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
    val haptics = LocalHapticFeedback.current
    val menus = LocalContextMenu.current
    val sheet = LocalAppSheet.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "press")
    AppVisual(
        app, iconSize, roundness, showLabel,
        modifier
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer { scaleX = press; scaleY = press }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val c = coords[0] ?: return@combinedClickable
                    menus.show(c.boundsInWindow(), menu(), extra = sheet(app)) { AppVisual(app, iconSize, roundness, showLabel) }
                },
            ),
    )
}

/**
 * Icon with its name underneath, no touch handling. When the app has notifications it glows
 * softly in its own colour, and the name "whispers" who it's from (e.g. "Sarah · 2").
 */
@Composable
fun AppVisual(app: AppEntry, iconSize: Dp, roundness: Int, showLabel: Boolean, modifier: Modifier = Modifier) {
    val badge = LocalBadges.current(app)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box(
            if (badge != null) Modifier.notificationGlow(badge.color) else Modifier,
        ) {
            AppIcon(app, iconSize, roundness)
            if (badge != null && !showLabel) {
                CountPill(badge.count, badge.color, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            if (badge != null) {
                Row(
                    Modifier
                        .widthIn(max = iconSize + 22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(badge.color.copy(alpha = 0.88f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        badge.who, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge.count > 1) {
                        Text(" · ${badge.count}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
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

package com.panel.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.PanelWidgetView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One hosted widget, filling its box. [onLongPress] gets the widget's on-screen bounds. */
@Composable
fun HostedWidget(
    vm: LauncherViewModel,
    id: Int,
    modifier: Modifier = Modifier,
    onLongPress: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val info = remember(id) { vm.widgetInfo(id) }
    if (info == null) {
        Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0x22000000)), contentAlignment = Alignment.Center) {
            Text("Widget unavailable", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        return
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val density = LocalDensity.current
    AndroidView(
        factory = { context ->
            vm.widgetHost.createView(context, id, info)
        },
        update = { view ->
            (view as? PanelWidgetView)?.onLongPress = {
                coords[0]?.takeIf { it.isAttached }?.let { c -> onLongPress?.invoke(c.boundsInWindow()) }
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .onGloballyPositioned { c ->
                coords[0] = c
                // Tell the widget how much room it has, so it picks the right layout.
                val w = with(density) { c.size.width.toDp().value.toInt() }
                val h = with(density) { c.size.height.toDp().value.toInt() }
                lastSizes[id]?.let { if (it == w to h) return@onGloballyPositioned }
                lastSizes[id] = w to h
                runCatching {
                    vm.widgetManager.updateAppWidgetOptions(id, Bundle().apply {
                        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                    })
                }
            },
    )
}

private val lastSizes = HashMap<Int, Pair<Int, Int>>()

/** The user's widgets, stacked under the boxes. Long-press one for its menu. */
@Composable
fun WidgetStack(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val list = vm.widgets
    if (list.isEmpty()) return
    val menus = LocalContextMenu.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { w ->
            HostedWidget(
                vm, w.id, Modifier.fillMaxWidth().height(w.heightDp.dp),
                onLongPress = { bounds ->
                    menus.show(
                        bounds,
                        listOf(
                            MenuAction("Taller", Icons.Outlined.UnfoldMore) { vm.resizeWidget(w.id, 40) },
                            MenuAction("Shorter", Icons.Outlined.UnfoldLess) { vm.resizeWidget(w.id, -40) },
                            MenuAction("Move up", Icons.Outlined.ArrowUpward) { vm.moveWidget(w.id, -1) },
                            MenuAction("Move down", Icons.Outlined.ArrowDownward) { vm.moveWidget(w.id, 1) },
                            MenuAction("Remove widget", Icons.Outlined.Delete, destructive = true) { vm.removeWidget(w.id) },
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Widget picker: every widget your apps offer, with previews, in a ¾-height sheet over the
 * blurred home screen. Tap one to add it.
 */
@Composable
fun BoxScope.WidgetPicker(vm: LauncherViewModel, open: Boolean, onDismiss: () -> Unit) {
    val t by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 380f), label = "widgets")
    BackHandler(enabled = open) { onDismiss() }
    if (t <= 0.01f) return
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val groups by produceState(emptyList<Pair<String, List<AppWidgetProviderInfo>>>(), open) {
        if (!open) return@produceState
        value = withContext(Dispatchers.IO) {
            vm.widgetProviders()
                .groupBy { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString() }.getOrDefault(it.provider.packageName) }
                .toList().sortedBy { it.first.lowercase() }
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = t.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    )
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .graphicsLayer { translationY = (1f - t) * size.height }
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xF01C1C1E))
            .border(0.75.dp, Color(0x1FFFFFFF), shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(Color(0x4DFFFFFF)))
        }
        Text(
            "Widgets", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = PixelDisplay,
            modifier = Modifier.padding(start = 22.dp, bottom = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            groups.forEach { (appName, infos) ->
                item(key = "h_$appName") {
                    Text(
                        appName, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(infos, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                    WidgetChoice(info) {
                        onDismiss()
                        (ctx as? Activity)?.let { vm.beginAddWidget(it, info) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d = info.loadPreviewImage(ctx, density.densityDpi) ?: info.loadIcon(ctx, density.densityDpi)
                val w = d.intrinsicWidth.coerceIn(1, 900)
                val h = d.intrinsicHeight.coerceIn(1, 600)
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, w, h); d.draw(android.graphics.Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    val label = remember(info) { info.loadLabel(ctx.packageManager) ?: "" }
    val cells = if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) {
        "${info.targetCellWidth} × ${info.targetCellHeight}"
    } else {
        val dp = density.density
        "${((info.minWidth / dp + 30) / 70).toInt().coerceAtLeast(1)} × ${((info.minHeight / dp + 30) / 70).toInt().coerceAtLeast(1)}"
    }
    Column(
        Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0x14FFFFFF)).clickable(onClick = onClick).padding(14.dp),
    ) {
        preview?.let {
            Box(Modifier.fillMaxWidth().heightIn(max = 160.dp), contentAlignment = Alignment.Center) {
                Image(it, null, modifier = Modifier.heightIn(max = 160.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(cells, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
    }
}

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)

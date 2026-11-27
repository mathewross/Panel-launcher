package com.panel.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.AppEntry
import com.panel.launcher.data.AppShortcut
import com.panel.launcher.data.NotifItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Badge for an app, or null (provided by the home screen). */
val LocalBadges = staticCompositionLocalOf<(AppEntry) -> LauncherViewModel.Badge?> { { null } }

/** Extra sheet content for an app's long-press menu (provided by the home screen). */
val LocalAppSheet = staticCompositionLocalOf<(AppEntry) -> (@Composable () -> Unit)?> { { null } }

private val Subtle = Color(0xFF9A9AA0)

/**
 * Niagara-style app sheet: the app's notifications (open, reply inline, clear) and its own
 * shortcuts, shown above the usual actions in the long-press menu.
 */
@Composable
fun AppSheetExtras(vm: LauncherViewModel, app: AppEntry) {
    val menus = LocalContextMenu.current
    val ctx = LocalContext.current
    val shortcuts by produceState(emptyList<AppShortcut>(), app.key) {
        value = withContext(Dispatchers.IO) { vm.repo.shortcuts(app) }
    }
    val notifs = vm.notificationsFor(app.packageName)
    val color = vm.colorFor(app)
    Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PixelDisplay)
            if (notifs.isNotEmpty()) CountPill(notifs.sumOf { it.count }, color)
        }
        Spacer(Modifier.height(8.dp))
        if (notifs.isNotEmpty()) {
            notifs.take(4).forEach { item ->
                NotifCard(vm, item, color) { menus.dismiss(); vm.openNotification(item) }
            }
        } else if (!vm.hasNotificationAccess && vm.prefs.showBadges) {
            Row(
                Modifier.fillMaxWidth().clickable { menus.dismiss(); vm.openNotificationAccess(ctx) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = Subtle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Show this app's notifications here (needs notification access)", color = Subtle, fontSize = 13.sp)
            }
        }
        if (shortcuts.isNotEmpty()) {
            shortcuts.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { menus.dismiss(); vm.repo.startShortcut(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(0x1FFFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) { s.icon?.let { Image(it, null, modifier = Modifier.size(22.dp).clip(CircleShape)) } }
                    Spacer(Modifier.width(12.dp))
                    Text(s.label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun NotifCard(vm: LauncherViewModel, item: NotifItem, color: Color, onOpen: () -> Unit) {
    var replying by remember(item.key) { mutableStateOf(false) }
    var text by remember(item.key) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Column(
        Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ago(vm.now, item.time), color = Subtle, fontSize = 12.sp)
            Box(
                Modifier.size(30.dp).clip(CircleShape).clickable { vm.dismissNotification(item) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Clear", tint = Subtle, modifier = Modifier.size(16.dp)) }
        }
        if (item.text.isNotBlank()) {
            Text(item.text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 8.dp))
        }
        if (item.replyAction != null) {
            if (!replying) {
                Text(
                    "Reply", color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { replying = true }.padding(4.dp),
                )
            } else {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(
                    Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x26FFFFFF)).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = LocalTextStyle.current.fontFamily),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)),
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp).focusRequester(focus),
                    )
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = text.isNotBlank()) {
                            if (vm.replyTo(item, text.trim())) {
                                vm.toast("Sent")
                                text = ""
                                replying = false
                            } else vm.toast("Couldn't send")
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send",
                            tint = if (text.isNotBlank()) Color(0xFF0A84FF) else Subtle, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun ago(now: Long, t: Long): String {
    val m = ((now - t) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 24 * 60 -> "${m / 60}h"
        else -> "${m / (24 * 60)}d"
    }
}

/** Small glass count pill in the app's colour. */
@Composable
fun CountPill(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** A soft, slowly breathing halo behind an icon that has something new. */
@Composable
fun Modifier.notificationGlow(color: Color): Modifier {
    val pulse = rememberInfiniteTransition(label = "glow")
    val a by pulse.animateFloat(
        0.35f, 0.85f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glowAlpha",
    )
    return this.drawBehind {
        val r = size.minDimension * 0.78f
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = a),
                0.55f to color.copy(alpha = a * 0.45f),
                1f to Color.Transparent,
                center = center, radius = r,
            ),
            radius = r,
        )
    }
}

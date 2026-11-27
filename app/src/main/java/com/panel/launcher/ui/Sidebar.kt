package com.panel.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.AppEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private sealed interface SideRow {
    data class Header(val letter: String) : SideRow
    data class App(val app: AppEntry) : SideRow
    data object Recents : SideRow
}

private fun letterOf(label: String): String {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c.toString() else "#"
}

/**
 * Slide-in side panel with every app, A–Z, plus search. [progress] (0 closed … 1 open) is read
 * only while drawing, so it can follow the finger every frame without recomposing.
 */
@Composable
fun BoxScope.Sidebar(
    progress: () -> Float,
    vm: LauncherViewModel,
    focusSearch: Boolean,
    /** 0…1: blurs the panel while a long-press menu / app sheet is open on top of it. */
    blur: () -> Float,
    onAddToFolder: (AppEntry) -> Unit,
    drag: Modifier,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    val left = vm.prefs.sidebarOnLeft
    val shown by remember { derivedStateOf { progress() > 0.001f } }
    if (shown) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .background(Color.Black.copy(alpha = 0.22f))
                .then(drag)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    // Always composed (just off-screen when closed) so it's instantly ready to slide in.
    Box(
        Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .graphicsLayer {
                translationX = (1f - progress()) * (size.width + 40f) * (if (left) -1f else 1f)
                val b = blur()
                renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && b > 0.01f) {
                    androidx.compose.ui.graphics.BlurEffect(36f * b, 36f * b, androidx.compose.ui.graphics.TileMode.Decal)
                } else null
            }
            .then(drag),
    ) {
        SidebarContent(vm, left, shown, focusSearch, onAddToFolder, onClose, onSettings)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SidebarContent(
    vm: LauncherViewModel,
    left: Boolean,
    visible: Boolean,
    focusSearch: Boolean,
    onAddToFolder: (AppEntry) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible, focusSearch) {
        if (visible && focusSearch) {
            delay(200) // let the slide-in start first
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (!visible) { query = ""; focus.clearFocus() }
    }

    val rows: List<SideRow> = remember(vm.apps, query, vm.prefs.showRecents, vm.prefs.recentApps) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            vm.apps.filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
                .map { SideRow.App(it) }
        } else buildList {
            if (vm.prefs.showRecents && vm.recentApps(5).isNotEmpty()) add(SideRow.Recents)
            var last = ""
            vm.apps.forEach { app ->
                val l = letterOf(app.label)
                if (l != last) { add(SideRow.Header(l)); last = l }
                add(SideRow.App(app))
            }
        }
    }
    val letterIndex = remember(rows) {
        rows.withIndex().filter { it.value is SideRow.Header }
            .associate { (it.value as SideRow.Header).letter to it.index }
    }

    fun open(app: AppEntry) {
        vm.launch(app)
        onClose()
    }

    val shape = if (left) RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    else RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
    var activeLetter by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.86f)
            .widthIn(max = 420.dp)
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xE0151517))
            .border(0.75.dp, Color(0x1AFFFFFF), shape)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 6.dp, top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
            Text("Apps", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(end = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF9A9AA0), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search", color = Color(0xFF8E8E93), fontSize = 16.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = LocalTextStyle.current.fontFamily),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        (rows.firstOrNull { it is SideRow.App } as? SideRow.App)?.let { open(it.app) }
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(rows, key = { _, r ->
                        when (r) {
                            is SideRow.Header -> "h_" + r.letter
                            is SideRow.App -> r.app.key
                            SideRow.Recents -> "recents"
                        }
                    }) { _, r ->
                        when (r) {
                            is SideRow.Header -> Text(
                                r.letter, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
                            )
                            is SideRow.App -> SidebarApp(vm, r.app, onAddToFolder) { open(r.app) }
                            SideRow.Recents -> RecentRow(vm) { open(it) }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                if (query.isBlank() && letterIndex.size > 1) {
                    AlphabetScrubber(letterIndex.keys.toList(), onActive = { activeLetter = it }) { letter ->
                        letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                    }
                }
            }
            // Big letter while scrubbing the alphabet, iOS-style.
            activeLetter?.let { l ->
                Box(
                    Modifier.align(Alignment.Center).size(84.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color(0xCC2C2C2E)),
                    contentAlignment = Alignment.Center,
                ) { Text(l, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** One app in the panel list (also used as the lifted preview in its long-press menu). */
@Composable
private fun SidebarRowVisual(vm: LauncherViewModel, app: AppEntry, modifier: Modifier = Modifier) {
    val badge = vm.badgeFor(app)
    val latest = if (badge != null) vm.notificationsFor(app.packageName).firstOrNull() else null
    Row(modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(if (badge != null) Modifier.notificationGlow(badge.color) else Modifier) {
            AppIcon(app, 44.dp, vm.prefs.iconRoundness)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Color.White, fontSize = 16.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Niagara-style one-line preview of the latest notification.
            if (latest != null) {
                Text(
                    listOf(latest.title, latest.text).filter { it.isNotBlank() }.joinToString(": "),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            CountPill(badge.count, badge.color)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarApp(vm: LauncherViewModel, app: AppEntry, onAddToFolder: (AppEntry) -> Unit, onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val menus = LocalContextMenu.current
    val sheet = LocalAppSheet.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val left = vm.prefs.sidebarOnLeft
    var swipe by remember { mutableFloatStateOf(0f) }
    fun showSheet() {
        val c = coords[0] ?: return
                    val items = listOf(
                        if (vm.isFavorite(app)) MenuAction("Remove from home", Icons.Outlined.RemoveCircleOutline) { vm.toggleFavorite(app) }
                        else MenuAction("Add to home", Icons.Outlined.Home) { vm.toggleFavorite(app) },
                        if (vm.isDocked(app)) MenuAction("Remove from dock", Icons.Outlined.RemoveCircleOutline) { vm.toggleDock(app) }
                        else MenuAction("Add to dock", Icons.Outlined.AddCircleOutline) { vm.toggleDock(app) },
                        MenuAction("Add to folder…", Icons.Outlined.CreateNewFolder) { onAddToFolder(app) },
                        MenuAction("App info", Icons.Outlined.Info) { vm.repo.openAppInfo(app) },
                        MenuAction("Uninstall", Icons.Outlined.Delete, destructive = true) { vm.repo.uninstall(app) },
                    )
                    val w = c.size.width
                    menus.show(c.boundsInWindow(), items, extra = sheet(app)) {
                        SidebarRowVisual(
                            vm, app,
                            Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { w.toDp() })
                                .clip(RoundedCornerShape(14.dp)).background(Color(0xF2242426)),
                        )
                    }
    }
    SidebarRowVisual(
        vm, app,
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer { translationX = swipe * 0.35f }
            // Niagara-style: swipe the app toward the middle of the screen to open its sheet.
            .pointerInput(left) {
                val inward = if (left) 1f else -1f
                val threshold = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = 0f
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        if (over * inward > 0) { change.consume(); total = over }
                    } ?: return@awaitEachGesture
                    if (total == 0f) return@awaitEachGesture
                    var fired = false
                    horizontalDrag(start.id) { c ->
                        total += c.positionChange().x
                        c.consume()
                        swipe = (total * inward).coerceAtLeast(0f) * inward
                        if (!fired && total * inward > threshold) {
                            fired = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSheet()
                        }
                    }
                    swipe = 0f
                }
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSheet()
                },
            ),
    )
}

/** The last few apps opened through Panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(vm: LauncherViewModel, onOpen: (AppEntry) -> Unit) {
    Column(
        Modifier.padding(top = 8.dp, bottom = 4.dp, end = 10.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0x14FFFFFF)).padding(vertical = 8.dp),
    ) {
        Text(
            "Recent", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )
        // As many as fit the panel's width (3–6), each in an equal cell so nothing gets cut off.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            val n = (maxWidth / 66.dp).toInt().coerceIn(3, 6)
            val iconSize = minOf(46.dp, maxWidth / n - 18.dp)
            Row(Modifier.fillMaxWidth()) {
                vm.recentApps(n).forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalWallpaperText provides TextOnDarkWallpaper) {
                            AppVisual(
                                app, iconSize, vm.prefs.iconRoundness, showLabel = true,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(onClick = { onOpen(app) }),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetScrubber(letters: List<String>, onActive: (String?) -> Unit, onLetter: (String) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != last) {
            last = l
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onActive(l)
            onLetter(l)
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures { last = ""; pick(it.y); onActive(null) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { last = ""; pick(it.y) },
                    onDragEnd = { onActive(null) },
                    onDragCancel = { onActive(null) },
                ) { change, _ -> pick(change.position.y) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it, color = Color(0xFF0A84FF), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}

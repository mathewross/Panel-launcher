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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
}

private fun letterOf(label: String): String {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c.toString() else "#"
}

/** Slide-in side panel with every app, A–Z, plus search. */
@Composable
fun BoxScope.Sidebar(
    visible: Boolean,
    vm: LauncherViewModel,
    focusSearch: Boolean,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    val left = vm.prefs.sidebarOnLeft
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(tween(260)) { if (left) -it else it },
        exit = slideOutHorizontally(tween(220)) { if (left) -it else it },
    ) {
        SidebarContent(vm, left, visible, focusSearch, onClose, onSettings)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarContent(
    vm: LauncherViewModel,
    left: Boolean,
    visible: Boolean,
    focusSearch: Boolean,
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

    val rows: List<SideRow> = remember(vm.apps, query) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            vm.apps.filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
                .map { SideRow.App(it) }
        } else buildList {
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

    val shape = if (left) RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
    else RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.86f)
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(Color(0xF0141416))
            .pointerInput(left) {
                val threshold = with(density) { 70.dp.toPx() }
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if ((left && total < -threshold) || (!left && total > threshold)) onClose()
                }) { _, amount -> total += amount }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 6.dp, top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF9A9AA0))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = Color(0xFF8E8E93), fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            (rows.firstOrNull { it is SideRow.App } as? SideRow.App)?.let { open(it.app) }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(rows, key = { _, r ->
                    when (r) { is SideRow.Header -> "h_" + r.letter; is SideRow.App -> r.app.key }
                }) { _, r ->
                    when (r) {
                        is SideRow.Header -> Text(
                            r.letter, color = Color(0xFF8E8E93), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                        )
                        is SideRow.App -> SidebarApp(vm, r.app) { open(r.app) }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            if (query.isBlank() && letterIndex.size > 1) {
                AlphabetScrubber(letterIndex.keys.toList()) { letter ->
                    letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarApp(vm: LauncherViewModel, app: AppEntry, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, 42.dp, vm.prefs.iconRoundness)
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val items = buildList<Pair<String, () -> Unit>> {
                add((if (vm.isFavorite(app)) "Remove from home" else "Add to home") to { vm.toggleFavorite(app) })
                add((if (vm.isDocked(app)) "Remove from dock" else "Add to dock") to { vm.toggleDock(app) })
                add("App info" to { vm.repo.openAppInfo(app) })
                add("Uninstall" to { vm.repo.uninstall(app) })
            }
            items.forEach { (label, run) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; run() })
            }
        }
    }
}

@Composable
private fun AlphabetScrubber(letters: List<String>, onLetter: (String) -> Unit) {
    var height by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != last) {
            last = l
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(l)
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures { last = ""; pick(it.y) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragStart = { last = ""; pick(it.y) }) { change, _ -> pick(change.position.y) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it, color = Color(0xFF0A84FF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
    }
}

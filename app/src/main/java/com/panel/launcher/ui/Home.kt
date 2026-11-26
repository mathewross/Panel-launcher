package com.panel.launcher.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.PickTarget
import com.panel.launcher.data.AppEntry
import com.panel.launcher.data.WeatherRepository

@Composable
fun HomeScreen(vm: LauncherViewModel, homePresses: Int) {
    val ctx = LocalContext.current
    var sidebar by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickTarget?>(null) }

    // Pressing Home while already home closes whatever is open.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { sidebar = false; settings = false; agenda = false }
    }
    BackHandler(enabled = true) { /* the home screen itself has nowhere to go back to */ }
    BackHandler(enabled = sidebar || settings) {
        if (settings) settings = false else sidebar = false
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshCalendar()
        if (!granted) { vm.toast("Calendar access is off — you can allow it in Settings › Permissions"); settings = true }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshWeather(force = true)
        else { vm.toast("No location — type your city in Settings › Weather instead"); settings = true }
    }

    val actions = HeaderActions(
        clock = { if (!vm.openClock(ctx)) picker = PickTarget.CLOCK },
        calendar = { if (!vm.openCalendar(ctx)) picker = PickTarget.CALENDAR },
        weather = {
            val configured = vm.prefs.cityLat.isNotBlank() || WeatherRepository.hasLocationPermission(ctx)
            when {
                !configured -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                !vm.openWeather() -> picker = PickTarget.WEATHER
            }
        },
        weatherLongPress = { vm.refreshWeather(force = true); vm.toast("Refreshing weather…") },
        agenda = { agenda = true },
        event = { vm.openEvent(ctx, it) },
        connectCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
    )

    val left = vm.prefs.sidebarOnLeft
    val density = LocalDensity.current
    CompositionLocalProvider(LocalTiles provides paletteFor(vm.prefs.tileStyle)) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(left) {
                    val threshold = with(density) { 70.dp.toPx() }
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if ((left && total > threshold) || (!left && total < -threshold)) sidebar = true
                        },
                    ) { _, amount -> total += amount }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                Header(vm, actions)
                Spacer(Modifier.height(18.dp))
                FavoritesGrid(vm, onOpenSidebar = { sidebar = true }, modifier = Modifier.weight(1f))
                Dock(vm)
                Spacer(Modifier.height(10.dp))
            }

            EdgeHandle(left) { sidebar = true }

            Sidebar(
                visible = sidebar, vm = vm,
                onClose = { sidebar = false },
                onSettings = { settings = true },
            )

            SettingsScreen(
                visible = settings, vm = vm,
                onClose = { settings = false },
                onPick = { picker = it },
                requestLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                requestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
            )
        }

        if (agenda) AgendaSheet(vm) { agenda = false }

        picker?.let { target ->
            val title = when (target) {
                PickTarget.WEATHER -> "Weather tile opens…"
                PickTarget.CLOCK -> "Clock tile opens…"
                PickTarget.CALENDAR -> "Calendar opens…"
            }
            AppPickerDialog(
                title = title, apps = vm.apps, allowDefault = target != PickTarget.WEATHER,
                onDismiss = { picker = null },
                onPick = { app ->
                    val key = app?.key.orEmpty()
                    when (target) {
                        PickTarget.WEATHER -> vm.prefs.weatherApp = key
                        PickTarget.CLOCK -> vm.prefs.clockApp = key
                        PickTarget.CALENDAR -> vm.prefs.calendarApp = key
                    }
                    picker = null
                    if (!settings) app?.let(vm::launch)
                },
            )
        }
    }
}

private fun iconSizeFor(columns: Int): Dp = when {
    columns <= 3 -> 70.dp
    columns == 4 -> 60.dp
    columns == 5 -> 52.dp
    else -> 46.dp
}

@Composable
private fun FavoritesGrid(vm: LauncherViewModel, onOpenSidebar: () -> Unit, modifier: Modifier) {
    val favs = vm.prefs.favorites.mapNotNull { vm.appsByKey[it] }
    if (favs.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (vm.apps.isNotEmpty()) {
                Text(
                    "Swipe ${if (vm.prefs.sidebarOnLeft) "right" else "left"} or tap the handle to open your apps.\n" +
                        "Long-press an app there to add it to this screen.",
                    style = WallpaperText.copy(color = Color.White.copy(alpha = 0.85f)),
                    fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenSidebar).padding(16.dp),
                )
            }
        }
        return
    }
    val cols = vm.prefs.columns
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(favs, key = { it.key }) { app ->
            Box(contentAlignment = Alignment.Center) {
                AppCell(
                    app = app, iconSize = iconSizeFor(cols), roundness = vm.prefs.iconRoundness,
                    showLabel = vm.prefs.showLabels, onOpen = { vm.launch(app) },
                    menu = { homeMenu(vm, app, inDock = false) },
                )
            }
        }
    }
}

@Composable
private fun Dock(vm: LauncherViewModel) {
    val dock = vm.prefs.dock.mapNotNull { vm.appsByKey[it] }
    if (dock.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(LocalTiles.current.bg)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dock.forEach { app ->
            AppCell(
                app = app, iconSize = 56.dp, roundness = vm.prefs.iconRoundness, showLabel = false,
                onOpen = { vm.launch(app) }, menu = { homeMenu(vm, app, inDock = true) },
            )
        }
    }
}

fun homeMenu(vm: LauncherViewModel, app: AppEntry, inDock: Boolean): List<MenuAction> = buildList {
    if (inDock) {
        add(MenuAction("Move left") { vm.moveDock(app, -1) })
        add(MenuAction("Move right") { vm.moveDock(app, 1) })
        add(MenuAction("Remove from dock") { vm.toggleDock(app) })
    } else {
        add(MenuAction("Move earlier") { vm.moveFavorite(app, -1) })
        add(MenuAction("Move later") { vm.moveFavorite(app, 1) })
        add(MenuAction("Remove from home") { vm.toggleFavorite(app) })
        if (!vm.isDocked(app)) add(MenuAction("Add to dock") { vm.toggleDock(app) })
    }
    add(MenuAction("App info") { vm.repo.openAppInfo(app) })
    add(MenuAction("Uninstall") { vm.repo.uninstall(app) })
}

@Composable
private fun BoxScope.EdgeHandle(left: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .offset(y = 40.dp)
            .width(20.dp)
            .height(110.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(width = 4.dp, height = 54.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}

package com.panel.launcher.ui

import android.Manifest
import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.panel.launcher.data.HeaderStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.panel.launcher.data.DockPosition
import com.panel.launcher.data.WeatherRepository

@Composable
fun HomeScreen(vm: LauncherViewModel, homePresses: Int) {
    val ctx = LocalContext.current
    var settings by rememberSaveable { mutableStateOf(false) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickTarget?>(null) }
    var focusSearch by remember { mutableStateOf(false) }

    // ---- App panel: 0 = closed, 1 = open. Follows your finger while you swipe. ----
    val scope = rememberCoroutineScope()
    val panel = remember { Animatable(0f) }
    val left = vm.prefs.sidebarOnLeft
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val panelWidthPx = with(density) { (screenWidthDp * 0.86f).coerceAtMost(420f).dp.toPx() }
    val panelSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)
    val openPanel: (Boolean) -> Unit = { search ->
        focusSearch = search
        scope.launch { panel.animateTo(1f, panelSpring) }
    }
    val closePanel: () -> Unit = {
        focusSearch = false
        scope.launch { panel.animateTo(0f, panelSpring) }
    }
    val panelDrag = rememberDraggableState { delta ->
        val change = (if (left) delta else -delta) / panelWidthPx
        scope.launch { panel.snapTo((panel.value + change).coerceIn(0f, 1f)) }
    }
    val settlePanel: suspend (Float) -> Unit = { velocity ->
        val v = (if (left) velocity else -velocity) / panelWidthPx // panel-widths per second, + = opening
        val target = when {
            v > 1.2f -> 1f
            v < -1.2f -> 0f
            panel.value > 0.4f -> 1f
            else -> 0f
        }
        if (target == 0f) focusSearch = false
        panel.animateTo(target, panelSpring, initialVelocity = v)
    }
    val panelDragModifier = Modifier.draggable(
        panelDrag, Orientation.Horizontal,
        enabled = !settings,
        onDragStarted = { focusSearch = false },
        onDragStopped = { settlePanel(it) },
    )
    val panelOpen by remember { derivedStateOf { panel.targetValue > 0f } }

    // Pressing Home while already home closes whatever is open.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { panel.snapTo(0f); focusSearch = false; settings = false; agenda = false }
    }
    BackHandler(enabled = true) { /* the home screen itself has nowhere to go back to */ }
    BackHandler(enabled = panelOpen || settings) {
        if (settings) settings = false else closePanel()
    }

    // Dark status-bar icons over a light wallpaper (but not over the dark panel or settings).
    val light = vm.lightWallpaper
    val panelMostlyOpen by remember { derivedStateOf { panel.value > 0.5f } }
    val view = LocalView.current
    val darkBarIcons = light && !settings && !panelMostlyOpen
    SideEffect {
        (ctx as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = darkBarIcons
                isAppearanceLightNavigationBars = darkBarIcons
            }
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshCalendar()
        if (!granted) { vm.toast("Calendar access is off — you can allow it in Settings › Permissions"); settings = true }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshWeather(force = true)
        else { vm.toast("No location — type your city in Settings › Weather instead"); settings = true }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importWallpaper)
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

    CompositionLocalProvider(
        LocalTiles provides paletteFor(vm.prefs.tileStyle, light),
        LocalWallpaperText provides if (light) TextOnLightWallpaper else TextOnDarkWallpaper,
    ) {
        Box(Modifier.fillMaxSize().then(panelDragModifier)) {
            val dockPos = vm.prefs.dockPosition
            val bigClock = vm.prefs.headerStyle == HeaderStyle.CLOCK
            var clockHeight by remember { mutableIntStateOf(0) }

            // Layer 1: Panel's own wallpaper photo, if one is chosen (otherwise the system wallpaper shows through).
            vm.wallpaper?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // Layer 2: the big clock.
            if (bigClock) {
                BigClock(
                    vm, actions,
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 14.dp)
                        .onSizeChanged { clockHeight = it.height },
                )
            }
            // Layer 3: the photo's subject, cut out and drawn over the clock (depth effect).
            val subject = vm.wallpaperSubject
            if (bigClock && vm.prefs.depthEffect && subject != null && vm.wallpaper != null) {
                Image(subject, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // Layer 4: everything you touch.
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                if (bigClock) {
                    Spacer(Modifier.height(with(density) { clockHeight.toDp() } + 8.dp))
                }
                Row(Modifier.weight(1f)) {
                    if (dockPos == DockPosition.LEFT) {
                        SideRail(vm, actions)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Header(vm, actions)
                        Spacer(Modifier.height(20.dp))
                        FavoritesGrid(vm, onOpenSidebar = { openPanel(false) }, modifier = Modifier.weight(1f))
                    }
                    if (dockPos == DockPosition.RIGHT) {
                        Spacer(Modifier.width(12.dp))
                        SideRail(vm, actions)
                    }
                }
                if (dockPos == DockPosition.BOTTOM) Dock(vm)
                SearchBar(
                    onSearch = { openPanel(true) },
                    onApps = { openPanel(false) },
                    onSettings = { settings = true },
                )
            }

            Sidebar(
                progress = { panel.value }, vm = vm, focusSearch = focusSearch,
                drag = panelDragModifier,
                onClose = closePanel,
                onSettings = { settings = true },
            )

            SettingsScreen(
                visible = settings, vm = vm,
                onClose = { settings = false },
                onPick = { picker = it },
                requestLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                pickWallpaper = {
                    wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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

private fun iconSizeFor(columns: Int, withRail: Boolean): Dp = when {
    columns <= 3 -> if (withRail) 64.dp else 70.dp
    columns == 4 -> if (withRail) 56.dp else 60.dp
    columns == 5 -> if (withRail) 46.dp else 52.dp
    else -> 42.dp
}

@Composable
private fun FavoritesGrid(vm: LauncherViewModel, onOpenSidebar: () -> Unit, modifier: Modifier) {
    val favs = vm.prefs.favorites.mapNotNull { vm.appsByKey[it] }
    if (favs.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (vm.apps.isNotEmpty()) {
                Text(
                    "Tap Search or ☰ below to see all your apps.\n" +
                        "Long-press an app there to add it here or to the dock.",
                    style = WallpaperText.copy(color = WallpaperText.color.copy(alpha = 0.85f)),
                    fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenSidebar).padding(16.dp),
                )
            }
        }
        return
    }
    val cols = vm.prefs.columns
    ReorderableAppGrid(
        vm = vm, apps = favs, columns = cols,
        iconSize = iconSizeFor(cols, vm.prefs.dockPosition != DockPosition.BOTTOM) * (vm.prefs.iconScale / 100f),
        modifier = modifier,
    )
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
        val vertical = vm.prefs.dockPosition != DockPosition.BOTTOM
        add(MenuAction(if (vertical) "Move up" else "Move left") { vm.moveDock(app, -1) })
        add(MenuAction(if (vertical) "Move down" else "Move right") { vm.moveDock(app, 1) })
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

/** Folio-style vertical rail: a weather pill on top, the dock as a tall pill below. */
@Composable
private fun SideRail(vm: LauncherViewModel, actions: HeaderActions) {
    val p = LocalTiles.current
    val pill = RoundedCornerShape(36.dp)
    // Dock height (Settings): 0 = straight under the weather pill … 100 = bottom of the screen.
    val f = vm.prefs.dockHeight.coerceIn(0, 100) / 100f
    Column(
        Modifier.width(68.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tile(
            Modifier.fillMaxWidth(), onClick = actions.weather, onLongClick = actions.weatherLongPress,
            padding = 0.dp, shape = pill,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val w = vm.weather
                if (w == null) {
                    Text("🌤️", fontSize = 24.sp)
                    Text(if (vm.weatherLoading) "…" else "Set up", color = p.subtle, fontSize = 11.sp)
                } else {
                    Text(WeatherRepository.emoji(w.code, w.isDay), fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(Fmt.temp(w.tempC, vm.prefs.fahrenheit), color = p.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${Fmt.temp(w.maxC, vm.prefs.fahrenheit)} ${Fmt.temp(w.minC, vm.prefs.fahrenheit)}",
                        color = p.subtle, fontSize = 11.sp, maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (f > 0f) Spacer(Modifier.weight(f))
        val dock = vm.prefs.dock.mapNotNull { vm.appsByKey[it] }
        if (dock.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(pill)
                    .background(p.bg)
                    .border(0.75.dp, p.border, pill)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                dock.forEach { app ->
                    AppCell(
                        app = app, iconSize = 50.dp, roundness = vm.prefs.iconRoundness, showLabel = false,
                        onOpen = { vm.launch(app) }, menu = { homeMenu(vm, app, inDock = true) },
                    )
                }
            }
        }
        if (f < 1f) Spacer(Modifier.weight(1f - f))
    }
}

/** Bottom bar: settings, a search pill that opens the app panel with the keyboard, and an all-apps button. */
@Composable
private fun SearchBar(onSearch: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit) {
    val p = LocalTiles.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = WallpaperText.color.copy(alpha = 0.85f))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(p.bg)
                .border(0.75.dp, p.border, RoundedCornerShape(50))
                .clickable(onClick = onSearch)
                .padding(horizontal = 26.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = p.fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search", color = p.fg, fontSize = 15.sp)
        }
        IconButton(onClick = onApps) {
            Icon(Icons.Default.Menu, "All apps", tint = WallpaperText.color.copy(alpha = 0.85f))
        }
    }
}

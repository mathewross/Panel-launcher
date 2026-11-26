package com.panel.launcher.ui

import android.Manifest
import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel, homePresses: Int) {
    val ctx = LocalContext.current
    var settings by rememberSaveable { mutableStateOf(false) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickTarget?>(null) }
    var focusSearch by remember { mutableStateOf(false) }
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var folderPickFor by remember { mutableStateOf<AppEntry?>(null) }

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

    // ---- Vertical swipes on the home screen: down = notification shade, up = app panel. ----
    var verticalTotal by remember { mutableStateOf(0f) }
    val verticalDrag = rememberDraggableState { verticalTotal += it }
    val swipeThreshold = with(density) { 48.dp.toPx() }
    val verticalDragModifier = Modifier.draggable(
        verticalDrag, Orientation.Vertical,
        enabled = !settings && !panelOpen && openFolder == null,
        onDragStarted = { verticalTotal = 0f },
        onDragStopped = { velocity ->
            when {
                verticalTotal > swipeThreshold || velocity > 1200f ->
                    if (vm.prefs.swipeDownShade) vm.expandNotifications()
                verticalTotal < -swipeThreshold || velocity < -1200f ->
                    if (vm.prefs.swipeUpPanel) openPanel(false)
            }
        },
    )

    // ---- Home zooms away as an app opens, and settles back in when you return. ----
    val homeScale = remember { Animatable(1f) }
    val homeAlpha = remember { Animatable(1f) }
    // Cascade on return: header, then grid, then dock/search arrive one after another.
    val entrance = remember { List(3) { Animatable(1f) } }
    val entranceShift = with(density) { 70.dp.toPx() }
    LaunchedEffect(vm.launchTick) {
        if (vm.launchTick == 0 || !vm.prefs.openAnimation) return@LaunchedEffect
        // The home screen pushes away as the app grows out of its icon.
        launch { homeScale.animateTo(1.18f, tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        homeAlpha.animateTo(0f, tween(200))
        // If the app didn't take over the screen (e.g. it failed to open), come back.
        kotlinx.coroutines.delay(900)
        if (vm.resumed) {
            launch { homeAlpha.animateTo(1f, tween(200)) }
            homeScale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
        }
    }
    LaunchedEffect(vm.resumeTick) {
        if (homeAlpha.value < 1f) {
            homeScale.snapTo(0.82f)
            entrance.forEach { it.snapTo(0f) }
            launch { homeAlpha.animateTo(1f, tween(260)) }
            entrance.forEachIndexed { i, e ->
                launch {
                    kotlinx.coroutines.delay(40L + i * 60L)
                    e.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 260f))
                }
            }
            homeScale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 210f))
        }
    }
    val entranceLayer: androidx.compose.ui.graphics.GraphicsLayerScope.(Int) -> Unit = { i ->
        val e = entrance[i].value
        translationY = (1f - e) * entranceShift
        alpha = e.coerceIn(0f, 1f)
    }

    // ---- Depth: the home screen blurs and sinks back while the panel, a folder or a menu is open. ----
    val menuController = remember { ContextMenuController() }
    val folderT by animateFloatAsState(if (openFolder != null) 1f else 0f, tween(280), label = "folderDepth")
    val menuT by animateFloatAsState(if (menuController.request != null) 1f else 0f, tween(220), label = "menuDepth")
    val agendaT by animateFloatAsState(if (agenda) 1f else 0f, tween(300), label = "agendaDepth")
    val depth: () -> Float = { maxOf(panel.value, folderT, menuT, agendaT) }
    val blurPx = with(density) { 24.dp.toPx() }
    val depthLayer: androidx.compose.ui.graphics.GraphicsLayerScope.(Float) -> Unit = { extraScale ->
        val d = depth()
        val s = extraScale * (1f - 0.05f * d)
        scaleX = s; scaleY = s
        renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && d > 0.01f) {
            androidx.compose.ui.graphics.BlurEffect(blurPx * d, blurPx * d, androidx.compose.ui.graphics.TileMode.Decal)
        } else null
    }
    // Blur the phone's own wallpaper too (Android 12+, where the phone supports window blur).
    LaunchedEffect(Unit) {
        val w = (ctx as? Activity)?.window ?: return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT < 31) return@LaunchedEffect
        var lastRadius = -1
        androidx.compose.runtime.snapshotFlow { depth() }.collect { d ->
            val radius = (d * 60).toInt()
            if (radius == lastRadius) return@collect
            lastRadius = radius
            runCatching {
                if (radius > 0) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = radius }
            }
        }
    }

    // With a Panel wallpaper photo, stop Android drawing the system wallpaper behind it too
    // (otherwise both show for a moment when returning home).
    val ownWallpaper = vm.wallpaper != null
    SideEffect {
        (ctx as? Activity)?.window?.let { w ->
            if (ownWallpaper) {
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            } else {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
    }

    // Keep the home screen upright even with auto-rotate on (Settings › Always portrait).
    val lockPortrait = vm.prefs.lockPortrait
    SideEffect {
        (ctx as? Activity)?.requestedOrientation =
            if (lockPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Pressing Home while already home closes whatever is open.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { panel.snapTo(0f); focusSearch = false; settings = false; agenda = false; openFolder = null }
    }
    BackHandler(enabled = true) { /* the home screen itself has nowhere to go back to */ }
    BackHandler(enabled = panelOpen || settings || openFolder != null) {
        when {
            settings -> settings = false
            openFolder != null -> openFolder = null
            else -> closePanel()
        }
    }

    // Dark status-bar icons over a light wallpaper (but not over the dark panel or settings).
    val light = vm.lightWallpaper
    val panelMostlyOpen by remember { derivedStateOf { panel.value > 0.5f } }
    val view = LocalView.current
    val darkBarIcons = light && !settings && !panelMostlyOpen
    // Optional: hide the status bar on the home screen (the time box already shows the time).
    // Android only allows hiding the whole bar; swipe down from the top edge to peek at it.
    val hideBar = vm.prefs.hideStatusBar && !settings
    SideEffect {
        (ctx as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = darkBarIcons
                isAppearanceLightNavigationBars = darkBarIcons
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hideBar) hide(WindowInsetsCompat.Type.statusBars())
                else show(WindowInsetsCompat.Type.statusBars())
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
        LocalIconBounds provides { key, rect -> vm.iconBounds[key] = rect },
        LocalGlass provides vm.glass,
        LocalIconLook provides IconLook(
            iconShapeFor(vm.prefs.iconShape, vm.prefs.iconRoundness),
            pastelFilter(vm.prefs.iconPastel / 100f),
        ),
        LocalContextMenu provides menuController,
    ) {
        Box(Modifier.fillMaxSize().then(panelDragModifier).then(verticalDragModifier)) {
            val dockPos = vm.prefs.dockPosition
            val bigClock = vm.prefs.headerStyle == HeaderStyle.CLOCK
            var clockHeight by remember { mutableIntStateOf(0) }

            // Layer 1: Panel's own wallpaper photo, if one is chosen (otherwise the system wallpaper shows through).
            vm.wallpaper?.let {
                Image(
                    it, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        // The wallpaper moves a little with the home zoom (never smaller than the
                        // screen, so nothing shows round its edges) and blurs behind overlays.
                        val d = depth()
                        val s = maxOf(1f, 1f + (homeScale.value - 1f) * 0.35f) * (1f + 0.04f * d)
                        scaleX = s; scaleY = s
                        renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && d > 0.01f) {
                            androidx.compose.ui.graphics.BlurEffect(blurPx * d, blurPx * d, androidx.compose.ui.graphics.TileMode.Clamp)
                        } else null
                    },
                )
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    depthLayer(homeScale.value)
                    alpha = homeAlpha.value
                },
            ) {
            // Layer 2: the big clock.
            if (bigClock) {
                BigClock(
                    vm, actions,
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
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
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                if (bigClock) {
                    Spacer(Modifier.height(with(density) { clockHeight.toDp() } + 8.dp))
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Size of the square time/date boxes, so the weather pill can grow to match.
                    val boxSize = if (vm.prefs.headerStyle == HeaderStyle.BOXES) {
                        (maxWidth - 68.dp - 12.dp - 12.dp) / 2
                    } else null
                    Row(Modifier.fillMaxSize()) {
                        if (dockPos == DockPosition.LEFT) {
                            Box(Modifier.graphicsLayer { entranceLayer(2) }) { SideRail(vm, actions, boxSize) }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Header(vm, actions, Modifier.graphicsLayer { entranceLayer(0) })
                            Spacer(Modifier.height(20.dp))
                            FavoritesGrid(
                                vm, onOpenSidebar = { openPanel(false) }, onOpenFolder = { openFolder = it },
                                onOpenSettings = { settings = true },
                                onPickPanelWallpaper = {
                                    wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).graphicsLayer { entranceLayer(1) },
                            )
                        }
                        if (dockPos == DockPosition.RIGHT) {
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.graphicsLayer { entranceLayer(2) }) { SideRail(vm, actions, boxSize) }
                        }
                    }
                }
                if (dockPos == DockPosition.BOTTOM) Box(Modifier.graphicsLayer { entranceLayer(2) }) { Dock(vm) }
                SearchBar(
                    onSearch = { openPanel(true) },
                    onApps = { openPanel(false) },
                    onSettings = { settings = true },
                )
            }
            } // end of zooming home layer

            FolderOverlay(vm, openFolder) { openFolder = null }

            Sidebar(
                progress = { panel.value }, vm = vm, focusSearch = focusSearch,
                onAddToFolder = { folderPickFor = it },
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

            AgendaPanel(vm, agenda) { agenda = false }

            ContextMenuOverlay(menuController)
        }

        folderPickFor?.let { app -> FolderPickerDialog(vm, app) { folderPickFor = null } }

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
private fun FavoritesGrid(
    vm: LauncherViewModel,
    onOpenSidebar: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPickPanelWallpaper: () -> Unit,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val favs = vm.prefs.favorites.filter { vm.occupied(it) }
    Box(modifier.fillMaxWidth()) {
        val cols = vm.prefs.columns
        FreeAppGrid(
            vm = vm, columns = cols, onOpenFolder = onOpenFolder,
            iconSize = iconSizeFor(cols, vm.prefs.dockPosition != DockPosition.BOTTOM) * (vm.prefs.iconScale / 100f),
            emptySpaceMenu = {
                listOf(
                    MenuAction("Wallpaper & style", Icons.Outlined.Wallpaper) {
                        // On a Pixel this opens Google's Wallpaper & style app.
                        if (!vm.start(ctx, Intent(Intent.ACTION_SET_WALLPAPER))) {
                            vm.start(ctx, Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
                        }
                    },
                    MenuAction("Panel wallpaper photo", Icons.Outlined.Image) { onPickPanelWallpaper() },
                    MenuAction("Home settings", Icons.Outlined.Settings) { onOpenSettings() },
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (favs.isEmpty() && vm.apps.isNotEmpty()) {
            Text(
                "Tap Search or ☰ below to see all your apps.\n" +
                    "Long-press an app there to add it here or to the dock.",
                style = WallpaperText.copy(color = WallpaperText.color.copy(alpha = 0.85f)),
                fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSidebar).padding(16.dp),
            )
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
            .surface(RoundedCornerShape(32.dp), LocalTiles.current)
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
        add(MenuAction(if (vertical) "Move up" else "Move left", if (vertical) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowBack) { vm.moveDock(app, -1) })
        add(MenuAction(if (vertical) "Move down" else "Move right", if (vertical) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowForward) { vm.moveDock(app, 1) })
        add(MenuAction("Remove from dock", Icons.Outlined.RemoveCircleOutline) { vm.toggleDock(app) })
    } else {
        add(MenuAction("Remove from home", Icons.Outlined.RemoveCircleOutline) { vm.toggleFavorite(app) })
        if (!vm.isDocked(app)) add(MenuAction("Add to dock", Icons.Outlined.AddCircleOutline) { vm.toggleDock(app) })
    }
    add(MenuAction("App info", Icons.Outlined.Info) { vm.repo.openAppInfo(app) })
    add(MenuAction("Uninstall", Icons.Outlined.Delete, destructive = true) { vm.repo.uninstall(app) })
}

/**
 * Folio-style vertical rail: a weather pill on top and the dock as a tall pill below. As the
 * dock is moved lower, the weather pill grows into the space, up to the size of the boxes.
 */
@Composable
private fun SideRail(vm: LauncherViewModel, actions: HeaderActions, boxSize: Dp?) {
    val p = LocalTiles.current
    val pill = RoundedCornerShape(36.dp)
    val density = LocalDensity.current
    // Dock height (Settings): 0 = straight under the weather pill … 100 = bottom of the screen.
    val f = vm.prefs.dockHeight.coerceIn(0, 100) / 100f
    var dockH by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.width(68.dp).fillMaxHeight()) {
        val compact = 112.dp
        val gap = 12.dp
        val minTop = compact + gap
        val maxTop = (maxHeight - dockH).coerceAtLeast(minTop)
        val dockTop = minTop + (maxTop - minTop) * f
        val target = if (boxSize != null) (dockTop - gap).coerceIn(compact, maxOf(compact, boxSize)) else compact
        val pillH by animateDpAsState(target, label = "weatherPill")
        val tall = pillH >= 150.dp

        Tile(
            Modifier.fillMaxWidth().height(pillH), onClick = actions.weather, onLongClick = actions.weatherLongPress,
            padding = 0.dp, shape = pill,
        ) {
            Column(
                Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val w = vm.weather
                if (w == null) {
                    Text("🌤️", fontSize = 24.sp)
                    Text(if (vm.weatherLoading) "…" else "Set up", color = p.subtle, fontSize = 11.sp)
                } else {
                    Text(WeatherRepository.emoji(w.code, w.isDay), fontSize = if (tall) 30.sp else 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(Fmt.temp(w.tempC, vm.prefs.fahrenheit), color = p.fg, fontSize = if (tall) 24.sp else 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${Fmt.temp(w.maxC, vm.prefs.fahrenheit)} ${Fmt.temp(w.minC, vm.prefs.fahrenheit)}",
                        color = p.subtle, fontSize = 11.sp, maxLines = 1,
                    )
                    if (tall) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            WeatherRepository.describe(w.code), color = p.fg, fontSize = 11.sp, maxLines = 2,
                            textAlign = TextAlign.Center, lineHeight = 13.sp,
                        )
                        if (w.place.isNotBlank()) {
                            Text(
                                w.place, color = p.subtle, fontSize = 10.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        val dock = vm.prefs.dock.mapNotNull { vm.appsByKey[it] }
        if (dock.isNotEmpty()) {
            Column(
                Modifier
                    .offset(y = dockTop)
                    .fillMaxWidth()
                    .onSizeChanged { dockH = with(density) { it.height.toDp() } }
                    .surface(pill, p)
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
    }
}

/** Glass for glass-style palettes, a plain filled surface for Light / Dark box styles. */
fun Modifier.surface(shape: androidx.compose.ui.graphics.Shape, p: TilePalette): Modifier =
    if (p.glass) this.glass(shape, p).clip(shape)
    else this.clip(shape).background(p.bg).border(0.75.dp, p.border, shape)

/** Bottom bar: one glass pill holding settings, search and all apps. */
@Composable
private fun SearchBar(onSearch: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit) {
    val p = LocalTiles.current
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.82f)
                .height(50.dp)
                .surface(RoundedCornerShape(50), p)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Settings, "Settings", tint = p.subtle, modifier = Modifier.size(21.dp)) }
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).clickable(onClick = onSearch),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = p.fg, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search", color = p.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onApps),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Apps, "All apps", tint = p.subtle, modifier = Modifier.size(21.dp)) }
        }
    }
}

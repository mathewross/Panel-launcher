package com.panel.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.PickTarget
import com.panel.launcher.data.CalendarRepository
import com.panel.launcher.data.DockPosition
import com.panel.launcher.data.HeaderStyle
import com.panel.launcher.data.TileStyle
import com.panel.launcher.data.WeatherRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size

private val Subtle = Color(0xFF9A9AA0)
private val Blue = Color(0xFF0A84FF)

@Composable
fun BoxScope.SettingsScreen(
    visible: Boolean,
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onPick: (PickTarget) -> Unit,
    requestLocation: () -> Unit,
    requestCalendar: () -> Unit,
    pickWallpaper: () -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it / 3 } + fadeIn(),
        exit = slideOutVertically { it / 3 } + fadeOut(),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done", color = Blue, fontSize = 17.sp) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                SettingsBody(vm, onPick, requestLocation, requestCalendar, pickWallpaper)
            }
        }
    }
}

@Composable
private fun SettingsBody(
    vm: LauncherViewModel,
    onPick: (PickTarget) -> Unit,
    requestLocation: () -> Unit,
    requestCalendar: () -> Unit,
    pickWallpaper: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val isDefault = remember(vm.now) {
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        )?.activityInfo?.packageName == ctx.packageName
    }
    if (!isDefault) {
        Group(null) {
            ActionRow("Set Panel as your home app", "Makes the Home button open Panel", Blue) {
                vm.start(ctx, Intent(SystemSettings.ACTION_HOME_SETTINGS))
            }
        }
    }

    Group("Home screen") {
        ChoiceRow(
            "Top section",
            listOf("Boxes" to HeaderStyle.BOXES, "Glance" to HeaderStyle.GLANCE, "Big clock" to HeaderStyle.CLOCK),
            p.headerStyle,
        ) { p.headerStyle = it }
        Divider()
        ChoiceRow("Box style", listOf("Glass" to TileStyle.GLASS, "Light" to TileStyle.LIGHT, "Dark" to TileStyle.DARK), p.tileStyle) { p.tileStyle = it }
        Divider()
        ChoiceRow(
            "Text on wallpaper",
            listOf("Auto" to "auto", "Dark text" to "light", "White text" to "dark"),
            p.wallpaperTone,
        ) { p.wallpaperTone = it }
        Divider()
        SliderRow("Grid columns", p.columns, 3..7, "${p.columns} across", steps = 3) { p.columns = it }
        Divider()
        SliderRow("Icon size", p.iconScale, 70..130, "${p.iconScale}%") { p.iconScale = it }
        Divider()
        SliderRow("Icon roundness", p.iconRoundness, 0..50, if (p.iconRoundness >= 50) "Circle" else "${p.iconRoundness}%") {
            p.iconRoundness = it
        }
        Divider()
        SwitchRow("Show app names", p.showLabels) { p.showLabels = it }
        Divider()
        ChoiceRow(
            "Dock",
            listOf("Left" to DockPosition.LEFT, "Right" to DockPosition.RIGHT, "Bottom" to DockPosition.BOTTOM),
            p.dockPosition,
        ) { p.dockPosition = it }
        if (p.dockPosition != DockPosition.BOTTOM) {
            Divider()
            SliderRow(
                "Dock height", p.dockHeight, 0..100,
                when {
                    p.dockHeight <= 2 -> "Top"
                    p.dockHeight >= 98 -> "Bottom"
                    else -> "${p.dockHeight}%"
                },
            ) { p.dockHeight = it }
        }
        Divider()
        SwitchRow("Show Up next box", p.showUpNext) { p.showUpNext = it }
        Divider()
        SwitchRow("Hide status bar on home screen", p.hideStatusBar) { p.hideStatusBar = it }

        Text(
            "Android can't hide just the clock, so this hides the whole bar (battery and signal too) " +
                "while you're on the home screen. Swipe down from the top to peek at it.",
            color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        Divider()
        SwitchRow("Always portrait", p.lockPortrait) { p.lockPortrait = it }
        Divider()
        SwitchRow("Swipe down for notifications", p.swipeDownShade) { p.swipeDownShade = it }
        Divider()
        SwitchRow("Swipe up for app panel", p.swipeUpPanel) { p.swipeUpPanel = it }
        Divider()
        SwitchRow("Open animations", p.openAnimation) { p.openAnimation = it }
        Divider()
        ChoiceRow("App panel slides from", listOf("Left" to true, "Right" to false), p.sidebarOnLeft) { p.sidebarOnLeft = it }
        Divider()
        SwitchRow("Recent apps at top of app panel", p.showRecents) { p.showRecents = it }
        Divider()
        SwitchRow("Show next alarm", p.showAlarm) { p.showAlarm = it; vm.onResume() }
    }

    Group("Look") {
        SwitchRow("Pixel font (Google Sans)", p.pixelFont) { p.pixelFont = it }
        Divider()
        Text(
            when {
                vm.wallpaper != null -> "Frosted glass is using your Panel wallpaper photo."
                vm.canReadSystemWallpaper -> "Frosted glass is using your phone's wallpaper."
                else -> "For real frosted glass, Panel needs to see your wallpaper. Either choose your " +
                    "wallpaper photo in Panel (Big clock & wallpaper, below; you can also set it as the " +
                    "phone's wallpaper from there), or allow Panel to read the phone's wallpaper. " +
                    "Android only allows that with \"All files access\"."
            },
            color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
        )
        if (vm.wallpaper == null && !vm.canReadSystemWallpaper && android.os.Build.VERSION.SDK_INT >= 30) {
            Divider()
            ActionRow("Use phone wallpaper for glass", "Opens Android's \"All files access\" page for Panel", Blue) {
                vm.start(
                    ctx,
                    Intent(
                        SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${ctx.packageName}"),
                    ),
                )
            }
        }
    }

    Group("Icons") {
        ChoiceRow(
            "Icon style",
            listOf("Original" to "original", "Themed" to "themed", "Icon pack" to "pack"),
            p.iconStyle,
        ) { vm.setIconStyle(it) }
        Divider()
        SliderRow("Pastel", p.iconPastel, 0..100, if (p.iconPastel == 0) "Off" else "${p.iconPastel}%") { p.iconPastel = it }
        Divider()
        ChoiceRow(
            "Shape",
            listOf("Rounded" to "rounded", "Squircle" to "squircle", "Circle" to "circle"),
            p.iconShape,
        ) { p.iconShape = it }
        Divider()
        SwitchRow("Soft backgrounds", p.softIconBackgrounds) {
            p.softIconBackgrounds = it
            vm.refreshApps()
        }
        Text(
            "Gives each icon's background (and the white plate behind older app icons) a gentle " +
                "tint of its own colour, so mismatched icons sit together better.",
            color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        if (p.iconStyle == "themed") {
            Text(
                "Pixel-style themed icons in your wallpaper colours. Apps that don't provide one keep their normal icon.",
                color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        if (p.iconStyle == "pack") {
            val packs = remember { vm.iconPacks() }
            if (packs.isEmpty()) {
                Text(
                    "No icon packs installed. Get one from the Play Store (search \"icon pack\"; Nova/ADW " +
                        "compatible packs work), then pick it here.",
                    color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            packs.forEach { pack ->
                Divider()
                ActionRow(
                    (if (p.iconPack == pack.packageName) "✓  " else "") + pack.label,
                    null, if (p.iconPack == pack.packageName) Blue else null,
                ) { vm.setIconStyle("pack", pack.packageName) }
            }
        }
    }

    Group("Time & date box numbers") {
        SliderRow("Size", p.boxNumberScale, 70..150, "${p.boxNumberScale}%") { p.boxNumberScale = it }
        Divider()
        ChoiceRow(
            "Weight",
            listOf("Light" to 300, "Regular" to 400, "Medium" to 500, "Bold" to 700, "Black" to 900),
            p.boxNumberWeight,
        ) { p.boxNumberWeight = it }
        Divider()
        ChoiceRow("Font", listOf("Default" to "default") + ClockFonts.filter { it.first != "bold" }.map { it.second to it.first }, p.boxNumberFont) {
            p.boxNumberFont = it
        }
        Text(
            "Very large sizes shrink automatically so the time always fits its box.",
            color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
    }

    Group("Big clock & wallpaper") {
        Text(
            "Pick a photo and Panel cuts out its subject so it can sit in front of the clock, " +
                "like the iOS lock screen. Choose \"Big clock\" as the top section to see it.",
            color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
        )
        Divider()
        ActionRow(
            if (vm.wallpaper == null) "Choose wallpaper photo" else "Change wallpaper photo",
            vm.wallpaperStatus ?: if (vm.wallpaper == null) "Uses the photo picker — no storage permission"
            else if (vm.wallpaperSubject != null) "Depth effect ready" else "No subject cut-out",
            Blue, pickWallpaper,
        )
        if (vm.wallpaper != null) {
            Divider()
            SwitchRow("Depth effect", p.depthEffect) { p.depthEffect = it }
            Divider()
            SwitchRow("Also set as phone wallpaper", p.syncPhoneWallpaper) { p.syncPhoneWallpaper = it; if (it) vm.useAsSystemWallpaper() }
            Divider()
            ActionRow("Remove photo", "Go back to your phone's wallpaper", Color(0xFFFF453A)) { vm.clearWallpaper() }
        }
        Divider()
        SliderRow("Clock size", p.clockSize, 60..200, "${p.clockSize}") { p.clockSize = it }
        Divider()
        SliderRow("Clock height", p.clockStretch, 100..220, "${p.clockStretch}%") { p.clockStretch = it }
        Divider()
        SliderRow("Clock position", p.clockOffset, 0..260, "${p.clockOffset}") { p.clockOffset = it }
        Divider()
        SliderRow("Clock opacity", p.clockOpacity, 30..100, "${p.clockOpacity}%") { p.clockOpacity = it }
        Divider()
        ChoiceRow("Font", ClockFonts.map { it.second to it.first }, p.clockFont) { p.clockFont = it }
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Colour", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            ClockColors.forEach { (argb, name) ->
                val selected = p.clockColor == argb
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(argb))
                        .border(
                            if (selected) 2.5.dp else 1.dp,
                            if (selected) Blue else Color(0xFF48484A),
                            androidx.compose.foundation.shape.CircleShape,
                        )
                        .clickable { p.clockColor = argb },
                )
            }
        }
    }

    if (vm.calendars.isNotEmpty()) {
        Group("Up next calendars") {
            Text(
                "Choose which calendars appear in Up next. Turned-off calendars still show in the agenda" +
                    if (p.filterAgenda) " — unless the option at the bottom is on." else ".",
                color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
            )
            val hidden = p.upNextHiddenCalendars.toSet()
            vm.calendars.groupBy { it.account }.forEach { (account, cals) ->
                Divider()
                Text(
                    account.ifBlank { "On this phone" }, color = Subtle, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                )
                cals.forEach { cal ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.setCalendarInUpNext(cal.id, cal.id.toString() in hidden) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(cal.color or 0xFF000000.toInt())),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(cal.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = cal.id.toString() !in hidden,
                            onCheckedChange = { vm.setCalendarInUpNext(cal.id, it) },
                        )
                    }
                }
            }
            Divider()
            SwitchRow("Show all-day events in Up next", p.upNextAllDay) { p.upNextAllDay = it }
            Divider()
            SwitchRow("Filter the agenda too", p.filterAgenda) { p.filterAgenda = it }
        }
    }

    Group("Notifications") {
        SwitchRow("Glow & previews on icons", p.showBadges) { p.showBadges = it }
        Divider()
        ActionRow(
            if (vm.hasNotificationAccess) "Notification access: on" else "Allow notification access",
            if (vm.hasNotificationAccess) "Icons glow, and long-press shows notifications you can open, reply to or clear. Tap to turn off."
            else "Needed for icon glow, previews and replies. Optional, and the kind of access some banking apps dislike.",
            if (vm.hasNotificationAccess) null else Blue,
        ) { vm.openNotificationAccess(ctx) }
        Text(
            "App shortcuts in the long-press sheet need no permission, only that Panel is your default home app. " +
                "If Android says \"Restricted setting\" when allowing access: App info › ⋮ › Allow restricted settings.",
            color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }

    Group("Music") {
        SwitchRow("Show music controls", p.showMusic) { p.showMusic = it }
        Divider()
        Text(
            "Spotify (no extra permissions): in Spotify, open Settings and turn on " +
                "\"Device broadcast status\". Panel can then show the song and art, with " +
                "play/pause, next and previous.",
            color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
        )
        if (vm.apps.any { it.packageName == com.panel.launcher.data.MediaRepository.SPOTIFY_PACKAGE }) {
            Divider()
            ActionRow("Open Spotify", "To turn on Device broadcast status", Blue) {
                vm.apps.firstOrNull { it.packageName == com.panel.launcher.data.MediaRepository.SPOTIFY_PACKAGE }
                    ?.let { vm.launch(it) }
            }
        }
        Divider()
        ActionRow(
            if (vm.hasNotificationAccess) "Full controls: on" else "Full controls for any music app",
            if (vm.hasNotificationAccess) "Using notification access. Tap to turn it off."
            else "Adds shuffle/like buttons and works with any app, but needs notification access, " +
                "which some banking apps dislike. Optional.",
            if (vm.hasNotificationAccess) null else Blue,
        ) {
            val component = android.content.ComponentName(ctx, com.panel.launcher.MediaListener::class.java)
            val detail = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(SystemSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            } else null
            if (detail == null || !vm.start(ctx, detail)) {
                vm.start(ctx, Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        if (!vm.hasNotificationAccess) {
            Text(
                "If Android says \"Restricted setting\" (it does for apps not from the Play Store): " +
                    "open Android app settings below, tap ⋮ › Allow restricted settings, then try again.",
                color = Subtle, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }

    Group("Weather") {
        ChoiceRow("Units", listOf("°C" to false, "°F" to true), p.fahrenheit) { p.fahrenheit = it }
        Divider()
        CityRow(vm)
        if (p.cityLat.isBlank() && !WeatherRepository.hasLocationPermission(ctx)) {
            Divider()
            ActionRow("Use my location", "Approximate location only, used just for the forecast", Blue, requestLocation)
        }
        Divider()
        ActionRow("Weather tile opens", vm.appsByKey[p.weatherApp]?.label ?: "Not chosen yet", null) { onPick(PickTarget.WEATHER) }
        Divider()
        val w = vm.weather
        ActionRow(
            "Refresh now",
            vm.weatherError ?: w?.let { "Updated ${Fmt.shortTime(ctx, it.fetchedAt)}" + (it.place.takeIf { s -> s.isNotBlank() }?.let { s -> " · $s" } ?: "") } ?: "—",
            Blue,
        ) { vm.refreshWeather(force = true) }
    }

    Group("What the boxes open") {
        ActionRow("Clock", vm.appsByKey[p.clockApp]?.label ?: "System default", null) { onPick(PickTarget.CLOCK) }
        Divider()
        ActionRow("Calendar", vm.appsByKey[p.calendarApp]?.label ?: "System default", null) { onPick(PickTarget.CALENDAR) }
    }

    Group("Permissions") {
        val cal = CalendarRepository.hasPermission(ctx)
        ActionRow("Calendar", if (cal) "Allowed — showing upcoming events" else "Tap to allow, for Up next & agenda", if (cal) null else Blue) {
            if (!cal) requestCalendar()
        }
        Divider()
        ActionRow("Open Android app settings", "Change or revoke Panel's permissions", null) {
            vm.start(ctx, Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }

    Group("Privacy") {
        Text(
            "Panel doesn't use an accessibility service or screen overlays, so banking apps that block " +
                "those keep working. It asks for calendar (Up next), approximate location (weather — or " +
                "type a city instead) and internet (weather, album art). Notification access is only " +
                "used if you turn on full music controls, and is off by default." +
                if (vm.hasNotificationAccess) " It is currently ON." else "",
            color = Subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CityRow(vm: LauncherViewModel) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(vm.prefs.city) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("City", color = Color.White, fontSize = 16.sp)
        Text("Leave empty to use your phone's location", color = Subtle, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("e.g. London") }, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                scope.launch {
                    status = "Looking up…"
                    status = vm.setCity(text) ?: if (text.isBlank()) "Using your location" else "Saved: ${vm.prefs.city}"
                    if (text.isNotBlank()) text = vm.prefs.city
                }
            }) { Text("Save", color = Blue) }
        }
        status?.let { Text(it, color = Subtle, fontSize = 12.sp) }
    }
}

@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            title.uppercase(), color = Subtle, fontSize = 12.sp, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
        )
    } else Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetColor), content = content)
}

@Composable
private fun Divider() = HorizontalDivider(color = Color(0xFF38383A), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))

@Composable
private fun ActionRow(title: String, subtitle: String?, titleColor: Color?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = titleColor ?: Color.White, fontSize = 16.sp)
        subtitle?.let { Text(it, color = Subtle, fontSize = 13.sp) }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    label: String,
    steps: Int = 0,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(label, color = Subtle, fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, current: T, onChange: (T) -> Unit) {
    val chips = @Composable {
        Row(
            Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF2C2C2E)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (label, value) ->
                val selected = value == current
                Text(
                    label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (selected) Color(0xFF636366) else Color.Transparent)
                        .clickable { onChange(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    if (options.size > 3 || options.sumOf { it.first.length } > 18) {
        // Too many to sit beside the title: stack them underneath.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            chips()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            chips()
        }
    }
}

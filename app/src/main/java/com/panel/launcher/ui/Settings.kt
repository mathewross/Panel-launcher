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
                SettingsBody(vm, onPick, requestLocation, requestCalendar)
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
        ChoiceRow("Top section", listOf("Boxes" to HeaderStyle.BOXES, "Glance" to HeaderStyle.GLANCE), p.headerStyle) { p.headerStyle = it }
        Divider()
        ChoiceRow("Box style", listOf("Glass" to TileStyle.GLASS, "Light" to TileStyle.LIGHT, "Dark" to TileStyle.DARK), p.tileStyle) { p.tileStyle = it }
        Divider()
        ChoiceRow("Columns", listOf("3" to 3, "4" to 4, "5" to 5), p.columns) { p.columns = it }
        Divider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Icon roundness", color = Color.White, fontSize = 16.sp)
            Slider(
                value = p.iconRoundness.toFloat(), onValueChange = { p.iconRoundness = it.toInt() },
                valueRange = 0f..50f,
            )
        }
        Divider()
        SwitchRow("Show app names", p.showLabels) { p.showLabels = it }
        Divider()
        ChoiceRow(
            "Dock",
            listOf("Left" to DockPosition.LEFT, "Right" to DockPosition.RIGHT, "Bottom" to DockPosition.BOTTOM),
            p.dockPosition,
        ) { p.dockPosition = it }
        Divider()
        SwitchRow("Show Up next box", p.showUpNext) { p.showUpNext = it }
        Divider()
        ChoiceRow("App panel slides from", listOf("Left" to true, "Right" to false), p.sidebarOnLeft) { p.sidebarOnLeft = it }
        Divider()
        SwitchRow("Show next alarm", p.showAlarm) { p.showAlarm = it; vm.onResume() }
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
            "Panel doesn't use an accessibility service, screen overlays or notification access, so banking " +
                "apps that block those keep working. It only asks for calendar (Up next), approximate " +
                "location (weather — or type a city instead) and internet (weather from open-meteo.com).",
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
}

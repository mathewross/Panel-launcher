package com.panel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.CalEvent
import com.panel.launcher.data.DockPosition
import com.panel.launcher.data.HeaderStyle
import com.panel.launcher.data.WeatherRepository

class HeaderActions(
    val clock: () -> Unit,
    val calendar: () -> Unit,
    val weather: () -> Unit,
    val weatherLongPress: () -> Unit,
    val agenda: () -> Unit,
    val event: (CalEvent) -> Unit,
    val connectCalendar: () -> Unit,
)

@Composable
fun Header(vm: LauncherViewModel, actions: HeaderActions, modifier: Modifier = Modifier) {
    when (vm.prefs.headerStyle) {
        HeaderStyle.BOXES -> BoxesHeader(vm, actions, modifier)
        HeaderStyle.GLANCE -> GlanceHeader(vm, actions, modifier)
        HeaderStyle.CLOCK -> Unit // drawn full-width by HomeScreen, between wallpaper and subject
    }
}

/** Events worth showing in "Up next": anything not finished yet, timed events first within a day. */
private fun upNext(events: List<CalEvent>, max: Int) = events.take(max)

// ---------------------------------------------------------------- Folio-style boxes

@Composable
private fun BoxesHeader(vm: LauncherViewModel, a: HeaderActions, modifier: Modifier) {
    val ctx = LocalContext.current
    val now = vm.now
    val is24 = DateFormat.is24HourFormat(ctx)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The date box already names the day, so the time box only adds AM/PM and the next alarm.
            val clockSub = listOfNotNull(
                if (is24) null else Fmt.pattern(now, "a"),
                vm.nextAlarm?.let { "⏰ " + Fmt.time(ctx, it) },
            ).joinToString(" · ")
            val time = Fmt.time(ctx, now)
            val (family, _) = clockFamily(vm.prefs.boxNumberFont)
            val numberStyle = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight(vm.prefs.boxNumberWeight.coerceIn(100, 900)),
                letterSpacing = (-1).sp,
            )
            val scale = vm.prefs.boxNumberScale / 100f
            FolioBox("Local time", time, clockSub, a.clock, null, Modifier.weight(1f), numberStyle, scale, fitTo = time)
            FolioBox(
                Fmt.pattern(now, "EEEE"), Fmt.pattern(now, "d"), Fmt.pattern(now, "MMMM"),
                a.calendar, null, Modifier.weight(1f), numberStyle, scale, fitTo = time,
            )
            if (vm.prefs.dockPosition == DockPosition.BOTTOM) {
                // No side rail in this layout, so weather gets a box of its own.
                val w = vm.weather
                FolioBox(
                    w?.place?.takeIf { it.isNotBlank() } ?: "Weather",
                    w?.let { Fmt.temp(it.tempC, vm.prefs.fahrenheit) } ?: "—",
                    w?.let { WeatherRepository.emoji(it.code, it.isDay) + " " + WeatherRepository.describe(it.code) }
                        ?: "Tap to set up",
                    a.weather, a.weatherLongPress, Modifier.weight(1f),
                )
            }
        }
        if (vm.prefs.showUpNext || vm.musicAvailable) UpNextBox(vm, a)
    }
}

/** Square Folio-style box: small caps label, one big value, a quiet subtitle. */
@Composable
private fun FolioBox(
    label: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    numberStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
    scale: Float = 1f,
    fitTo: String = value,
) {
    val p = LocalTiles.current
    val measurer = rememberTextMeasurer()
    Tile(modifier.aspectRatio(1f), onClick = onClick, onLongClick = onLongClick, padding = 16.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Chosen size, shrunk if needed so [fitTo] (the time, for both boxes) fits the width.
            var size = (maxWidth.value * 0.33f * scale).coerceIn(18f, 110f)
            while (size > 18f) {
                val w = measurer.measure(fitTo, numberStyle.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width
                if (w <= constraints.maxWidth) break
                size -= 2f
            }
            val big = size.sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    label.uppercase(), color = p.subtle, fontSize = 11.sp, letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value, style = numberStyle, color = p.fg, fontSize = big,
                    maxLines = 1, softWrap = false, lineHeight = big,
                )
                Text(subtitle, color = p.subtle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Niagara-style "what's next" strip under the boxes; tap for the full agenda. */
@Composable
private fun UpNextBox(vm: LauncherViewModel, a: HeaderActions) {
    val p = LocalTiles.current
    val music = vm.nowPlaying?.takeIf { vm.musicAvailable }
    // With Up next turned off, the box only appears for music, so there's nothing to toggle.
    val showMusic = music != null && (vm.showMusicTab || !vm.prefs.showUpNext)
    Tile(
        Modifier.fillMaxWidth(),
        onClick = when {
            showMusic -> vm::openMusicApp
            vm.hasCalendar -> a.agenda
            else -> a.connectCalendar
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (music != null && vm.prefs.showUpNext) {
                    UpNextMusicToggle(vm)
                    Spacer(Modifier.weight(1f))
                } else {
                    SectionLabel(if (showMusic) "Now playing" else "Up next", p.subtle, Modifier.weight(1f))
                }
                if (!showMusic && vm.hasCalendar) {
                    Text("Agenda ›", color = p.subtle, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            val list = upNext(vm.upNextEvents, 2)
            when {
                showMusic && music != null -> MusicPlayer(vm, music)
                !vm.hasCalendar -> Text("Tap to show your calendar events here", color = p.fg, fontSize = 14.sp)
                list.isEmpty() -> Text("Nothing coming up", color = p.subtle, fontSize = 14.sp)
                else -> list.forEach { e -> EventLine(e, vm.now, p.fg, p.subtle) { a.event(e) } }
            }
        }
    }
}

@Composable
private fun EventLine(e: CalEvent, now: Long, fg: Color, subtle: Color, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val soon = !e.allDay && e.begin - now in 0..(60 * 60_000L)
            Text(
                Fmt.relative(ctx, e, now) + (e.location?.let { " · $it" } ?: ""),
                color = if (soon || e.begin <= now && !e.allDay) Accent else subtle,
                fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- Niagara-style glance

@Composable
private fun GlanceHeader(vm: LauncherViewModel, a: HeaderActions, modifier: Modifier) {
    val ctx = LocalContext.current
    val now = vm.now
    val sub = WallpaperText.copy(color = WallpaperText.color.copy(alpha = 0.85f))
    Column(modifier.padding(horizontal = 8.dp)) {
        Text(
            Fmt.time(ctx, now),
            style = WallpaperText, fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp,
            lineHeight = 80.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = a.clock),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.pattern(now, "EEEE, d MMMM"), style = sub, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = a.calendar).padding(vertical = 2.dp),
            )
            val w = vm.weather
            Text("  ·  ", style = sub, fontSize = 17.sp)
            Text(
                if (w != null) "${WeatherRepository.emoji(w.code, w.isDay)} ${Fmt.temp(w.tempC, vm.prefs.fahrenheit)}" else "🌤️ Weather",
                style = sub, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = a.weather).padding(vertical = 2.dp),
            )
        }
        vm.nextAlarm?.let {
            Text("⏰ " + Fmt.shortTime(ctx, it), style = sub, fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp).clickable(onClick = a.clock))
        }
        CompactMusic(vm, sub, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        if (!vm.hasCalendar) {
            Text("📅  Tap to show upcoming events", style = sub, fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = a.connectCalendar))
        } else {
            val list = upNext(vm.upNextEvents, 2)
            if (list.isEmpty()) {
                Text("Nothing coming up", style = sub, fontSize = 15.sp, modifier = Modifier.clickable(onClick = a.agenda))
            }
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = a.agenda),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color(e.color or 0xFF000000.toInt())))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(e.title, style = WallpaperText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.relative(ctx, e, now), style = sub, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

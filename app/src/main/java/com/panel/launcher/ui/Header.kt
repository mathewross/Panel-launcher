package com.panel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.CalEvent
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
    }
}

/** Events worth showing in "Up next": anything not finished yet, timed events first within a day. */
private fun upNext(events: List<CalEvent>, max: Int) = events.take(max)

// ---------------------------------------------------------------- Folio-style boxes

@Composable
private fun BoxesHeader(vm: LauncherViewModel, a: HeaderActions, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalTiles.current
    val now = vm.now
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Clock box -> clock app
            Tile(Modifier.weight(1.2f).fillMaxHeight(), onClick = a.clock) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(Fmt.pattern(now, "EEEE"), Accent)
                    Text(
                        Fmt.time(ctx, now),
                        color = p.fg, fontSize = 54.sp, fontWeight = FontWeight.Medium,
                        letterSpacing = (-2).sp, lineHeight = 56.sp, maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.pattern(now, "d MMMM"),
                            color = p.subtle, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = a.calendar),
                        )
                        vm.nextAlarm?.let {
                            Spacer(Modifier.weight(1f))
                            Text("⏰ " + Fmt.shortTime(ctx, it), color = p.subtle, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
            // Weather box -> weather app
            Tile(Modifier.weight(1f).fillMaxHeight(), onClick = a.weather, onLongClick = a.weatherLongPress) {
                val w = vm.weather
                if (w == null) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("🌤️", fontSize = 30.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (vm.weatherLoading) "Loading…" else vm.weatherError ?: "Tap to set up weather",
                            color = p.subtle, fontSize = 13.sp, maxLines = 3,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            w.place.ifBlank { "Weather" }, color = p.fg, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Fmt.temp(w.tempC, vm.prefs.fahrenheit), color = p.fg, fontSize = 44.sp,
                            fontWeight = FontWeight.Light, lineHeight = 46.sp,
                        )
                        Column {
                            Text(WeatherRepository.emoji(w.code, w.isDay), fontSize = 18.sp)
                            Text(WeatherRepository.describe(w.code), color = p.fg, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "H:${Fmt.temp(w.maxC, vm.prefs.fahrenheit)}  L:${Fmt.temp(w.minC, vm.prefs.fahrenheit)}",
                                color = p.subtle, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        // Calendar / up-next box -> agenda
        Tile(Modifier.fillMaxWidth(), onClick = if (vm.hasCalendar) a.agenda else a.connectCalendar) {
            Row {
                Column(
                    Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = a.calendar),
                ) {
                    Text(Fmt.pattern(now, "EEE").uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(Fmt.pattern(now, "d"), color = p.fg, fontSize = 38.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!vm.hasCalendar) {
                        SectionLabel("Up next", p.subtle)
                        Text("Tap to show your calendar events here", color = p.fg, fontSize = 14.sp)
                    } else {
                        val list = upNext(vm.events, 3)
                        if (list.isEmpty()) {
                            SectionLabel("Up next", p.subtle)
                            Text("No upcoming events", color = p.subtle, fontSize = 14.sp)
                        } else {
                            list.forEach { e -> EventLine(e, now, p.fg, p.subtle) { a.event(e) } }
                        }
                    }
                }
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
    val sub = WallpaperText.copy(color = Color.White.copy(alpha = 0.85f))
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
        Spacer(Modifier.height(14.dp))
        if (!vm.hasCalendar) {
            Text("📅  Tap to show upcoming events", style = sub, fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = a.connectCalendar))
        } else {
            val list = upNext(vm.events, 2)
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

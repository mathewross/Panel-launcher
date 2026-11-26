package com.panel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.panel.launcher.data.WeatherRepository

/** Day-by-day list of what's coming up; tap an event to open it in the calendar app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaSheet(vm: LauncherViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = vm.now
    val today = Fmt.day(now)
    val grouped: Map<java.time.LocalDate, List<CalEvent>> = vm.events
        .groupBy { if (it.begin < now) today else Fmt.day(it.begin) }
        .toSortedMap()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.pattern(now, "EEEE").uppercase(), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(Fmt.pattern(now, "d MMMM"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    vm.weather?.let { w ->
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { if (vm.openWeather()) onDismiss() }.padding(6.dp),
                        ) {
                            Text("${WeatherRepository.emoji(w.code, w.isDay)} ${Fmt.temp(w.tempC, vm.prefs.fahrenheit)}",
                                color = Color.White, fontSize = 22.sp)
                            Text("H:${Fmt.temp(w.maxC, vm.prefs.fahrenheit)} L:${Fmt.temp(w.minC, vm.prefs.fahrenheit)}",
                                color = Color(0xFF9A9AA0), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { vm.openCalendar(ctx); onDismiss() }) { Text("Open calendar") }
                    FilledTonalButton(onClick = { vm.newEvent(ctx); onDismiss() }) { Text("+ New event") }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (grouped.isEmpty()) {
                item {
                    Text(
                        "Nothing in the next two weeks 🎉", color = Color(0xFF9A9AA0), fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            grouped.forEach { (date, list) ->
                item(key = "h$date") {
                    Text(
                        Fmt.dayLabel(date, today),
                        color = if (date == today) Accent else Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                    )
                }
                items(list, key = { "e${it.id}_${it.begin}" }) { e -> AgendaRow(e, now) { vm.openEvent(ctx, e); onDismiss() } }
            }
        }
    }
}

@Composable
private fun AgendaRow(e: CalEvent, now: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SheetRaised)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Fmt.eventRange(ctx, e), e.location).joinToString(" · "),
                color = Color(0xFF9A9AA0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val ongoing = e.begin <= now && !e.allDay
        val soon = !e.allDay && e.begin - now in 0..(3 * 60 * 60_000L)
        if (ongoing || soon) {
            Spacer(Modifier.width(8.dp))
            Text(Fmt.relative(ctx, e, now).substringBefore(" ·"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

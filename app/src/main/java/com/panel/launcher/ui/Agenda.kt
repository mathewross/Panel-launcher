package com.panel.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
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

/**
 * Niagara-style agenda: a panel about three-quarters of the screen tall, over the blurred home
 * screen. Drag the handle (or tap outside) to close. Tap an event to open it in your calendar.
 */
@Composable
fun BoxScope.AgendaPanel(vm: LauncherViewModel, open: Boolean, onDismiss: () -> Unit) {
    val t by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 380f), label = "agenda")
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(open) { if (open) drag.snapTo(0f) }
    BackHandler(enabled = open) { onDismiss() }
    if (t <= 0.01f) return

    val ctx = LocalContext.current
    val now = vm.now
    val today = Fmt.day(now)
    val grouped: Map<java.time.LocalDate, List<CalEvent>> = vm.agendaEvents
        .groupBy { if (it.begin < now) today else Fmt.day(it.begin) }
        .toSortedMap()

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = t.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    )
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val dismissPx = with(density) { 110.dp.toPx() }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.76f)
            .graphicsLayer { translationY = (1f - t) * size.height + drag.value }
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xE01C1C1E))
            .border(0.75.dp, Color(0x1FFFFFFF), shape)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Drag area: handle + date/weather header.
        Column(
            Modifier
                .fillMaxWidth()
                .draggable(
                    rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drag.value > dismissPx || v > 1400f) onDismiss()
                        else drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                    },
                )
                .padding(start = 22.dp, end = 22.dp, bottom = 6.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(Color(0x4DFFFFFF)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.pattern(now, "EEEE").uppercase(), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(Fmt.pattern(now, "d MMMM"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = PixelDisplay)
                }
                vm.weather?.let { w ->
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { if (vm.openWeather()) onDismiss() }.padding(6.dp),
                    ) {
                        Text("${WeatherRepository.emoji(w.code, w.isDay)} ${Fmt.temp(w.tempC, vm.prefs.fahrenheit)}",
                            color = Color.White, fontSize = 24.sp)
                        Text("H:${Fmt.temp(w.maxC, vm.prefs.fahrenheit)} L:${Fmt.temp(w.minC, vm.prefs.fahrenheit)}",
                            color = Color(0xFF9A9AA0), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgendaButton("Open calendar") { vm.openCalendar(ctx); onDismiss() }
                AgendaButton("+ New event") { vm.newEvent(ctx); onDismiss() }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
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
private fun AgendaButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x24FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun AgendaRow(e: CalEvent, now: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x17FFFFFF))
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

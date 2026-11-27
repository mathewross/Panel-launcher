package com.panel.launcher.ui

import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.WeatherRepository
import kotlin.math.roundToInt

/** Clock typefaces available on every Android phone (no downloads). */
val ClockFonts: List<Pair<String, String>> = listOf(
    "condensed" to "Tall",
    "thin" to "Thin",
    "regular" to "Classic",
    "bold" to "Bold",
    "serif" to "Serif",
)

internal fun clockFamily(key: String): Pair<FontFamily, FontWeight> = when (key) {
    "default" -> PixelDisplay to FontWeight.Medium
    "thin" -> FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL)) to FontWeight.Thin
    "regular" -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL)) to FontWeight.Light
    "bold" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)) to FontWeight.Bold
    "serif" -> FontFamily(Typeface.create("serif", Typeface.NORMAL)) to FontWeight.Normal
    else -> FontFamily(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)) to FontWeight.Light
}

val ClockColors: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFFD6ECFF.toInt() to "Ice",
    0xFFFFE6C7.toInt() to "Warm",
    0xFFFFC7D6.toInt() to "Rose",
    0xFF1C1C1E.toInt() to "Black",
)

/** Stretches content vertically (and reserves the extra height), like iOS's tall clock digits. */
private fun Modifier.stretchY(factor: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val h = (p.height * factor).roundToInt()
    layout(p.width, h) {
        p.placeWithLayer(0, 0) {
            scaleY = factor
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

/** iOS lock-screen style clock: date above, huge clock, then weather + what's next. */
@Composable
fun BigClock(vm: LauncherViewModel, a: HeaderActions, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val now = vm.now
    val (family, weight) = clockFamily(p.clockFont)
    val color = Color(p.clockColor).copy(alpha = p.clockOpacity / 100f)
    val info = WallpaperText.copy(color = WallpaperText.color.copy(alpha = 0.92f))

    Column(modifier.padding(top = p.clockOffset.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (p.showStatus) StatusIcons(vm.status, info.color, Modifier.padding(bottom = 6.dp), size = 13.dp)
        Text(
            Fmt.pattern(now, "EEE d MMM"), style = info, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = a.calendar).padding(horizontal = 6.dp),
        )
        Text(
            Fmt.time(ctx, now),
            style = TextStyle(
                color = color,
                fontSize = p.clockSize.sp,
                lineHeight = p.clockSize.sp,
                fontFamily = family,
                fontWeight = weight,
                letterSpacing = (-0.02 * p.clockSize).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .stretchY(p.clockStretch / 100f)
                .clickable(onClick = a.clock),
        )
        // Niagara-style line: weather · next event
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp)) {
            val w = vm.weather
            Text(
                if (w != null) "${WeatherRepository.emoji(w.code, w.isDay)} ${Fmt.temp(w.tempC, p.fahrenheit)}" else "🌤️",
                style = info, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = a.weather).padding(4.dp),
            )
            val next = vm.upNextEvents.firstOrNull()
            val text = when {
                !vm.hasCalendar -> "Show calendar"
                next == null -> vm.nextAlarm?.let { "⏰ " + Fmt.shortTime(ctx, it) } ?: "Nothing coming up"
                else -> "${next.title} · ${Fmt.relative(ctx, next, now)}"
            }
            Text("  ·  ", style = info, fontSize = 16.sp)
            Text(
                text, style = info, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = if (vm.hasCalendar) a.agenda else a.connectCalendar).padding(4.dp),
            )
        }
        CompactMusic(vm, info, Modifier.padding(horizontal = 24.dp))
    }
}

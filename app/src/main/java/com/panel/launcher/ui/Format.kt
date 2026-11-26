package com.panel.launcher.ui

import android.content.Context
import android.text.format.DateFormat
import com.panel.launcher.data.CalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    private val zone get() = ZoneId.systemDefault()

    fun time(context: Context, millis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    /** Time with am/pm suffix when the phone uses 12-hour time. */
    fun shortTime(context: Context, millis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    fun pattern(millis: Long, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        in 2..6 -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }

    fun eventRange(context: Context, e: CalEvent): String =
        if (e.allDay) "All day" else "${shortTime(context, e.begin)} – ${shortTime(context, e.end)}"

    /** Niagara-style countdown: "in 25 min", "now · until 3:00 PM", "Tomorrow 9:00 AM". */
    fun relative(context: Context, e: CalEvent, now: Long): String {
        val today = day(now)
        if (e.allDay) {
            return if (e.begin <= now) "Today · all day" else "${dayLabel(day(e.begin), today)} · all day"
        }
        if (e.begin <= now) return "Now · until ${shortTime(context, e.end)}"
        val mins = ((e.begin - now) / 60_000.0).roundToInt().coerceAtLeast(1)
        return when {
            mins < 60 -> "in $mins min"
            mins < 6 * 60 -> {
                val h = mins / 60; val m = mins % 60
                if (m == 0) "in $h h" else "in $h h $m min"
            }
            else -> "${dayLabel(day(e.begin), today)} ${shortTime(context, e.begin)}"
        }
    }

    fun temp(celsius: Double, fahrenheit: Boolean): String {
        val v = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        return "${v.roundToInt()}°"
    }
}

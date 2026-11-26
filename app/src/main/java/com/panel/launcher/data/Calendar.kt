package com.panel.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
    val calendarName: String?,
)

object CalendarRepository {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the start of today until [days] days ahead that haven't finished yet. */
    suspend fun upcoming(context: Context, days: Int = 14): List<CalEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() - DAY
        val end = now + days * DAY

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val allDay = c.getInt(4) == 1
                    var b = c.getLong(2)
                    var e = c.getLong(3)
                    if (allDay) { // all-day events are stored as UTC midnights
                        b = utcToLocalMidnight(b, zone)
                        e = utcToLocalMidnight(e, zone)
                    }
                    if (e <= now) continue
                    out += CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = b, end = e, allDay = allDay,
                        location = c.getString(5)?.takeIf { it.isNotBlank() },
                        color = c.getInt(6),
                        calendarName = c.getString(7),
                    )
                }
            }
        }
        out.sortedWith(compareBy<CalEvent> { it.begin }.thenBy { !it.allDay })
    }

    private fun utcToLocalMidnight(utc: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(utc).atOffset(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    const val DAY = 24L * 60 * 60 * 1000
}

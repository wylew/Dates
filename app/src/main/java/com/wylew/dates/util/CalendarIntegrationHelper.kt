package com.wylew.dates.util

import android.content.Context
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

object CalendarIntegrationHelper {
    data class SystemCalendar(
        val id: Long,
        val accountName: String,
        val accountType: String,
        val displayName: String,
        val color: Int
    )

    data class SystemCalendarEvent(
        val id: Long,
        val calendarId: Long,
        val title: String,
        val description: String,
        val dtstart: Long,
        val dtend: Long,
        val allDay: Boolean,
        val rrule: String?
    )

    fun getSystemCalendars(context: Context): List<SystemCalendar> {
        val calendars = mutableListOf<SystemCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val accountCol = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeCol = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val nameCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val colorCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val account = if (accountCol != -1) cursor.getString(accountCol) ?: "Local Account" else "Local Account"
                    val type = if (typeCol != -1) cursor.getString(typeCol) ?: "" else ""
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) ?: "Calendar" else "Calendar"
                    val color = if (colorCol != -1) cursor.getInt(colorCol) else Color.Blue.toArgb()
                    calendars.add(SystemCalendar(id, account, type, displayName, color))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return calendars
    }

    fun getEventsForCalendar(context: Context, calendarId: Long): List<SystemCalendarEvent> {
        val events = mutableListOf<SystemCalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Events._ID)
                val calIdCol = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val descCol = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayCol = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val calId = if (calIdCol != -1) cursor.getLong(calIdCol) else calendarId
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Untitled Event" else "Untitled Event"
                    val description = if (descCol != -1) cursor.getString(descCol) ?: "" else ""
                    val dtstart = if (startCol != -1) cursor.getLong(startCol) else System.currentTimeMillis()
                    val dtend = if (endCol != -1) cursor.getLong(endCol) else dtstart
                    val allDay = if (allDayCol != -1) cursor.getInt(allDayCol) == 1 else false
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null

                    events.add(SystemCalendarEvent(id, calId, title, description, dtstart, dtend, allDay, rrule))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }

    fun parseRRule(rrule: String?): Recurrence {
        if (rrule.isNullOrBlank()) return Recurrence.None

        val parts = rrule.split(";")
        var freq: Frequency? = null
        var interval = 1
        var dayOfWeek: Int? = null
        var dayOfMonth: Int? = null
        var monthOfYear: Int? = null

        for (part in parts) {
            val kv = part.split("=")
            if (kv.size == 2) {
                val key = kv[0].trim().uppercase()
                val value = kv[1].trim()
                when (key) {
                    "FREQ" -> {
                        freq = when (value.uppercase()) {
                            "DAILY" -> Frequency.DAILY
                            "WEEKLY" -> Frequency.WEEKLY
                            "MONTHLY" -> Frequency.MONTHLY
                            "YEARLY" -> Frequency.YEARLY
                            else -> null
                        }
                    }
                    "INTERVAL" -> {
                        interval = value.toIntOrNull() ?: 1
                    }
                    "BYMONTH" -> {
                        monthOfYear = value.toIntOrNull()
                    }
                    "BYMONTHDAY" -> {
                        dayOfMonth = value.toIntOrNull()
                    }
                    "BYDAY" -> {
                        val dayStr = value.takeLast(2).uppercase()
                        dayOfWeek = when (dayStr) {
                            "MO" -> 1
                            "TU" -> 2
                            "WE" -> 3
                            "TH" -> 4
                            "FR" -> 5
                            "SA" -> 6
                            "SU" -> 7
                            else -> null
                        }
                    }
                }
            }
        }

        if (freq == null) return Recurrence.None

        return if (interval == 1 && dayOfWeek == null && dayOfMonth == null && monthOfYear == null) {
            when (freq) {
                Frequency.DAILY -> Recurrence.Daily
                Frequency.WEEKLY -> Recurrence.Weekly
                Frequency.MONTHLY -> Recurrence.Monthly
                Frequency.YEARLY -> Recurrence.Yearly
            }
        } else {
            Recurrence.Custom(
                frequency = freq,
                interval = interval,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth,
                monthOfYear = monthOfYear
            )
        }
    }

    fun convertToDateEvent(sysEvent: SystemCalendarEvent): DateEvent {
        val startDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(sysEvent.dtstart),
            ZoneId.systemDefault()
        )
        val endDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(sysEvent.dtend),
            ZoneId.systemDefault()
        )
        val recurrence = parseRRule(sysEvent.rrule)
        return DateEvent(
            title = sysEvent.title,
            description = sysEvent.description,
            dateTime = startDateTime,
            endDateTime = endDateTime,
            isAllDay = sysEvent.allDay,
            recurrence = recurrence,
            emoji = "📅",
            customColor = randomEventColor()
        )
    }

    // Bright, saturated color with a random hue so imported events aren't all the calendar's color
    private fun randomEventColor(): Color =
        Color.hsv(hue = Random.nextFloat() * 360f, saturation = 0.85f, value = 0.9f)
}

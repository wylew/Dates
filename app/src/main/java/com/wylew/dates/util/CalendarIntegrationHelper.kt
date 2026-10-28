package com.wylew.dates.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
        /** First occurrence of the series (or the only occurrence). */
        val dtstart: Long,
        /** End of the first occurrence; derived from DURATION for recurring events. */
        val dtend: Long,
        val allDay: Boolean,
        val rrule: String?,
        /** Start of the next upcoming instance from the Instances table, if the event repeats. */
        val nextInstanceStart: Long? = null,
        /** Start of the most recent past instance, used when a series has ended. */
        val lastInstanceStart: Long? = null
    ) {
        val isRecurring: Boolean get() = !rrule.isNullOrBlank()

        /** The occurrence the app should count down to (UTC millis). */
        val displayStart: Long get() = nextInstanceStart ?: lastInstanceStart ?: dtstart

        val durationMillis: Long get() = (dtend - dtstart).coerceAtLeast(0)

        /** True for a repeating series that has no instances left (UNTIL/COUNT in the past). */
        val seriesEnded: Boolean get() = isRecurring && nextInstanceStart == null && lastInstanceStart != null
    }

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
                while (cursor.moveToNext()) {
                    calendars.add(
                        SystemCalendar(
                            id = cursor.long(CalendarContract.Calendars._ID) ?: 0L,
                            accountName = cursor.string(CalendarContract.Calendars.ACCOUNT_NAME) ?: "Local Account",
                            accountType = cursor.string(CalendarContract.Calendars.ACCOUNT_TYPE) ?: "",
                            displayName = cursor.string(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME) ?: "Calendar",
                            color = cursor.int(CalendarContract.Calendars.CALENDAR_COLOR) ?: Color.Blue.toArgb()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return calendars
    }

    /**
     * Returns one row per event *series* in the calendar.
     *
     * The Events table also holds rows that aren't standalone events, and importing those
     * is what produced "old" one-off countdowns:
     *  - exceptions to a series (one occurrence of a birthday that was edited or moved) have
     *    ORIGINAL_ID / ORIGINAL_SYNC_ID set, no RRULE, and the date of that single occurrence;
     *  - deleted or cancelled rows that haven't been purged by sync yet.
     * Those are filtered out here, and each recurring series gets its next real occurrence
     * from the Instances table so the import shows (and stores) the upcoming date.
     */
    fun getEventsForCalendar(context: Context, calendarId: Long): List<SystemCalendarEvent> {
        val events = mutableListOf<SystemCalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE
        )
        val selection = buildString {
            append("${CalendarContract.Events.CALENDAR_ID} = ?")
            append(" AND ${CalendarContract.Events.DELETED} = 0")
            append(" AND ${CalendarContract.Events.ORIGINAL_ID} IS NULL")
            append(" AND ${CalendarContract.Events.ORIGINAL_SYNC_ID} IS NULL")
            append(" AND (${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})")
        }
        val selectionArgs = arrayOf(calendarId.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val dtstart = cursor.long(CalendarContract.Events.DTSTART) ?: -1L
                    if (dtstart < 0) continue
                    val allDay = (cursor.int(CalendarContract.Events.ALL_DAY) ?: 0) == 1
                    // Recurring events store DURATION and leave DTEND null (getLong would return 0 → 1970).
                    val dtend = cursor.long(CalendarContract.Events.DTEND)?.takeIf { it > 0 }
                        ?: RRuleParser.parseDuration(cursor.string(CalendarContract.Events.DURATION))
                            ?.let { dtstart + it.toMillis() }
                        ?: if (allDay) dtstart + DAY_MILLIS else dtstart

                    events.add(
                        SystemCalendarEvent(
                            id = cursor.long(CalendarContract.Events._ID) ?: 0L,
                            calendarId = cursor.long(CalendarContract.Events.CALENDAR_ID) ?: calendarId,
                            title = cursor.string(CalendarContract.Events.TITLE)?.takeIf { it.isNotBlank() } ?: "Untitled Event",
                            description = cursor.string(CalendarContract.Events.DESCRIPTION) ?: "",
                            dtstart = dtstart,
                            dtend = dtend,
                            allDay = allDay,
                            rrule = cursor.string(CalendarContract.Events.RRULE)?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val cutoff = System.currentTimeMillis() - DAY_MILLIS
        val withInstances = attachInstances(context, calendarId, events)
        // Upcoming soonest-first, then past one-offs most-recent-first.
        val (upcoming, past) = withInstances.partition { it.displayStart >= cutoff }
        return upcoming.sortedBy { it.displayStart } + past.sortedByDescending { it.displayStart }
    }

    /** Looks up the next (and last) real occurrence of every recurring series in one Instances query each. */
    private fun attachInstances(
        context: Context,
        calendarId: Long,
        events: List<SystemCalendarEvent>
    ): List<SystemCalendarEvent> {
        if (events.none { it.isRecurring }) return events
        val now = System.currentTimeMillis()
        // Include today for all-day events (their instances begin at UTC midnight).
        val from = now - DAY_MILLIS
        val next = queryInstanceStarts(context, calendarId, from, from + LOOKAHEAD_MILLIS, earliest = true)
        val needsLast = events.filter { it.isRecurring && it.id !in next }
        val last = if (needsLast.isEmpty()) emptyMap()
        else queryInstanceStarts(context, calendarId, needsLast.minOf { it.dtstart }, from, earliest = false)

        return events.map { ev ->
            if (!ev.isRecurring) ev
            else ev.copy(nextInstanceStart = next[ev.id], lastInstanceStart = last[ev.id])
        }
    }

    private fun queryInstanceStarts(
        context: Context,
        calendarId: Long,
        begin: Long,
        end: Long,
        earliest: Boolean
    ): Map<Long, Long> {
        if (end <= begin) return emptyMap()
        val result = HashMap<Long, Long>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                "${CalendarContract.Instances.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString()),
                null
            )?.use { cursor ->
                val today = LocalDate.now()
                while (cursor.moveToNext()) {
                    val eventId = cursor.long(CalendarContract.Instances.EVENT_ID) ?: -1L
                    val beginMillis = cursor.long(CalendarContract.Instances.BEGIN) ?: -1L
                    if (eventId < 0 || beginMillis < 0) continue
                    val allDay = (cursor.int(CalendarContract.Instances.ALL_DAY) ?: 0) == 1
                    // The window starts a day early to catch today's all-day instances; drop timed ones already past.
                    val isUpcoming = if (allDay) !toLocal(beginMillis, true).toLocalDate().isBefore(today)
                    else beginMillis >= System.currentTimeMillis()
                    if (earliest && !isUpcoming) continue
                    val current = result[eventId]
                    if (current == null || (earliest && beginMillis < current) || (!earliest && beginMillis > current)) {
                        result[eventId] = beginMillis
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * Calendar storage keeps all-day events at UTC midnight. Converting those with the
     * device zone shifts them to the previous evening west of UTC (a March 12 birthday
     * became March 11, 5 PM in Seattle), so all-day times are read in UTC.
     */
    fun toLocal(millis: Long, allDay: Boolean): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), if (allDay) ZoneOffset.UTC else ZoneId.systemDefault())

    fun parseRRule(rrule: String?, anchor: LocalDateTime): Recurrence =
        RRuleParser.toRecurrence(RRuleParser.parse(rrule), anchor)

    fun convertToDateEvent(sysEvent: SystemCalendarEvent): DateEvent {
        // Store the upcoming occurrence, not the date the series was first created, so the
        // countdown is right immediately and the recurrence rolls it forward from there.
        val start = toLocal(sysEvent.displayStart, sysEvent.allDay)
        val end = toLocal(sysEvent.displayStart + sysEvent.durationMillis, sysEvent.allDay)
        val recurrence = if (sysEvent.seriesEnded) Recurrence.None else parseRRule(sysEvent.rrule, start)

        return DateEvent(
            title = sysEvent.title,
            description = sysEvent.description,
            dateTime = if (sysEvent.allDay) start.toLocalDate().atStartOfDay() else start,
            endDateTime = if (sysEvent.allDay) null else end,
            isAllDay = sysEvent.allDay,
            recurrence = recurrence,
            emoji = "📅",
            customColor = randomEventColor()
        )
    }

    // Bright, saturated color with a random hue so imported events aren't all the calendar's color
    private fun randomEventColor(): Color =
        Color.hsv(hue = Random.nextFloat() * 360f, saturation = 0.85f, value = 0.9f)

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    /** Long enough to find the next instance of anything up to "every 4 years" (leap-day birthdays, elections). */
    private const val LOOKAHEAD_MILLIS = 5L * 366 * DAY_MILLIS

    private fun Cursor.index(column: String): Int? = getColumnIndex(column).takeIf { it >= 0 }
    private fun Cursor.long(column: String): Long? = index(column)?.takeIf { !isNull(it) }?.let { getLong(it) }
    private fun Cursor.int(column: String): Int? = index(column)?.takeIf { !isNull(it) }?.let { getInt(it) }
    private fun Cursor.string(column: String): String? = index(column)?.takeIf { !isNull(it) }?.let { getString(it) }
}

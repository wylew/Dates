package com.wylew.dates.util

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object DateUtils {

    fun getNextOccurrence(event: DateEvent): LocalDateTime = getNextOccurrence(event, LocalDateTime.now())

    /**
     * Next time [event] happens at or after [now].
     *
     * Every occurrence is computed from the original anchor (start + n × period)
     * rather than by stepping from the previous result, so a Jan 31 monthly event
     * doesn't decay to the 28th after February and a Feb 29 birthday comes back
     * on Feb 29 in leap years.
     *
     * All-day events count as current for the whole day, so a birthday reads
     * "today" instead of jumping to next year at midnight.
     */
    fun getNextOccurrence(event: DateEvent, now: LocalDateTime): LocalDateTime {
        val start = event.dateTime
        val threshold = if (event.isAllDay) now.toLocalDate().atStartOfDay() else now
        if (!start.isBefore(threshold)) return start

        return when (val r = event.recurrence) {
            Recurrence.None -> start
            Recurrence.Daily -> stepFixed(start, threshold, ChronoUnit.DAYS, 1)
            Recurrence.Weekly -> stepFixed(start, threshold, ChronoUnit.WEEKS, 1)
            Recurrence.Monthly -> stepCalendar(start, threshold, ChronoUnit.MONTHS, 1) { it }
            Recurrence.Yearly -> stepCalendar(start, threshold, ChronoUnit.YEARS, 1) { it }
            is Recurrence.Custom -> nextCustom(start, r, threshold)
        }
    }

    /** Days/weeks: exact arithmetic, no looping over years of history. */
    private fun stepFixed(start: LocalDateTime, threshold: LocalDateTime, unit: ChronoUnit, interval: Int): LocalDateTime {
        val step = interval.coerceAtLeast(1).toLong()
        val elapsed = unit.between(start, threshold)
        var n = (elapsed / step).coerceAtLeast(0)
        var candidate = start.plus(n * step, unit)
        while (candidate.isBefore(threshold)) {
            n++
            candidate = start.plus(n * step, unit)
        }
        return candidate
    }

    /**
     * Months/years: candidate period k is start + k×interval, then [resolve] picks the
     * actual day inside that period (or returns null to skip a period that doesn't
     * contain it, e.g. "the 31st" in a 30-day month — same as Google Calendar).
     */
    private fun stepCalendar(
        start: LocalDateTime,
        threshold: LocalDateTime,
        unit: ChronoUnit,
        interval: Int,
        resolve: (LocalDateTime) -> LocalDateTime?
    ): LocalDateTime {
        val step = interval.coerceAtLeast(1).toLong()
        var k = (unit.between(start, threshold) / step - 1).coerceAtLeast(0)
        repeat(2_000) {
            val period = start.plus(k * step, unit)
            val candidate = resolve(period)
            if (candidate != null && !candidate.isBefore(threshold) && !candidate.isBefore(start)) {
                return candidate
            }
            k++
        }
        return start
    }

    private fun nextCustom(start: LocalDateTime, r: Recurrence.Custom, threshold: LocalDateTime): LocalDateTime {
        val time = start.toLocalTime()
        val dow = r.dayOfWeek?.let { DayOfWeek.of(it.coerceIn(1, 7)) }

        return when (r.frequency) {
            Frequency.DAILY -> stepFixed(start, threshold, ChronoUnit.DAYS, r.interval)

            Frequency.WEEKLY -> {
                val aligned = if (dow != null) start.with(TemporalAdjusters.nextOrSame(dow)) else start
                if (!aligned.isBefore(threshold)) aligned
                else stepFixed(aligned, threshold, ChronoUnit.WEEKS, r.interval)
            }

            Frequency.MONTHLY -> stepCalendar(start, threshold, ChronoUnit.MONTHS, r.interval) { period ->
                dayInMonth(YearMonth.from(period), r.dayOfMonth, r.weekOfMonth, dow, fallbackDay = start.dayOfMonth)
                    ?.atTime(time)
            }

            Frequency.YEARLY -> stepCalendar(start, threshold, ChronoUnit.YEARS, r.interval) { period ->
                val month = r.monthOfYear ?: start.monthValue
                dayInMonth(YearMonth.of(period.year, month), r.dayOfMonth, r.weekOfMonth, dow, fallbackDay = start.dayOfMonth)
                    ?.atTime(time)
            }
        }
    }

    /**
     * Resolves a day inside [ym]:
     *  - weekOfMonth + dayOfWeek: "2nd Tuesday", "-1" = last ("last Friday")
     *  - dayOfMonth: 1..31, or negative from the end (-1 = last day)
     *  - otherwise the anchor's own day of month
     * Returns null when that day doesn't exist in this month.
     */
    internal fun dayInMonth(
        ym: YearMonth,
        dayOfMonth: Int?,
        weekOfMonth: Int?,
        dow: DayOfWeek?,
        fallbackDay: Int
    ): LocalDate? {
        if (weekOfMonth != null && dow != null) {
            val d = ym.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(weekOfMonth, dow))
            return if (YearMonth.from(d) == ym) d else null // a "5th Monday" that doesn't exist
        }
        val day = dayOfMonth ?: fallbackDay
        val resolved = if (day < 0) ym.lengthOfMonth() + day + 1 else day
        return if (resolved in 1..ym.lengthOfMonth()) ym.atDay(resolved) else null
    }

    /** Human label for a recurrence, e.g. "Yearly", "Every 2 weeks on Tue", "Monthly on the last Fri". */
    fun describe(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.None -> "Does not repeat"
        Recurrence.Daily -> "Daily"
        Recurrence.Weekly -> "Weekly"
        Recurrence.Monthly -> "Monthly"
        Recurrence.Yearly -> "Yearly"
        is Recurrence.Custom -> {
            val unit = when (recurrence.frequency) {
                Frequency.DAILY -> "day"
                Frequency.WEEKLY -> "week"
                Frequency.MONTHLY -> "month"
                Frequency.YEARLY -> "year"
            }
            val base = if (recurrence.interval <= 1) {
                when (recurrence.frequency) {
                    Frequency.DAILY -> "Daily"
                    Frequency.WEEKLY -> "Weekly"
                    Frequency.MONTHLY -> "Monthly"
                    Frequency.YEARLY -> "Yearly"
                }
            } else "Every ${recurrence.interval} ${unit}s"
            val day = recurrence.dayOfWeek?.let {
                DayOfWeek.of(it.coerceIn(1, 7)).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            }
            val month = recurrence.monthOfYear?.let {
                java.time.Month.of(it.coerceIn(1, 12)).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            }
            val ordinal = when (recurrence.weekOfMonth) {
                null -> null
                -1 -> "last"
                1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"
                else -> "${recurrence.weekOfMonth}th"
            }
            val detail = when {
                ordinal != null && day != null -> "on the $ordinal $day" + (month?.let { " of $it" } ?: "")
                recurrence.dayOfMonth != null && recurrence.dayOfMonth < 0 -> "on the last day" + (month?.let { " of $it" } ?: "")
                recurrence.dayOfMonth != null -> "on " + (month?.let { "$it " } ?: "day ") + recurrence.dayOfMonth
                day != null -> "on $day"
                month != null -> "in $month"
                else -> null
            }
            if (detail == null) base else "$base $detail"
        }
    }

    /** One-time events whose date is before today; these roll into "days past" */
    fun isPast(event: DateEvent): Boolean =
        event.recurrence is Recurrence.None && event.dateTime.toLocalDate().isBefore(LocalDate.now())

    /** Whole days since a past event, or null if it isn't past */
    fun daysPast(event: DateEvent): Long? =
        if (isPast(event)) ChronoUnit.DAYS.between(event.dateTime.toLocalDate(), LocalDate.now()) else null

    /** Calendar days from today to the next occurrence (0 = today), never negative */
    fun daysUntil(event: DateEvent): Long =
        maxOf(0L, ChronoUnit.DAYS.between(LocalDate.now(), getNextOccurrence(event).toLocalDate()))

    fun formatCountdown(nextOccurrence: LocalDateTime): String {
        val now = LocalDateTime.now()
        if (nextOccurrence.toLocalDate().isBefore(now.toLocalDate())) {
            val days = ChronoUnit.DAYS.between(nextOccurrence.toLocalDate(), now.toLocalDate())
            return if (days == 1L) "1 day ago" else "$days days ago"
        }
        // Calendar days, so an event tomorrow morning reads "1 day left" even if it's under 24 hours away
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), nextOccurrence.toLocalDate())
        val duration = Duration.between(now, nextOccurrence)
        val hours = duration.toHours()
        val minutes = duration.toMinutes()

        return when {
            days > 1 -> "$days days left"
            days == 1L -> "1 day left"
            hours > 0 -> if (hours == 1L) "1 hour left" else "$hours hours left"
            minutes > 0 -> if (minutes == 1L) "1 minute left" else "$minutes minutes left"
            else -> "Today"
        }
    }
}

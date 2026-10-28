package com.wylew.dates.util

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object DateUtils {

    fun getNextOccurrence(event: DateEvent): LocalDateTime {
        // All-day events stay "today" for the whole day instead of rolling over at midnight
        val now = if (event.isAllDay) LocalDate.now().atStartOfDay() else LocalDateTime.now()
        var next = event.dateTime

        if (next.isAfter(now)) return next

        return when (val recurrence = event.recurrence) {
            Recurrence.None -> next
            Recurrence.Daily -> {
                while (next.isBefore(now)) {
                    next = next.plusDays(1)
                }
                next
            }
            Recurrence.Weekly -> {
                while (next.isBefore(now)) {
                    next = next.plusWeeks(1)
                }
                next
            }
            Recurrence.Monthly -> {
                while (next.isBefore(now)) {
                    next = next.plusMonths(1)
                }
                next
            }
            Recurrence.Yearly -> {
                while (next.isBefore(now)) {
                    next = next.plusYears(1)
                }
                next
            }
            is Recurrence.Custom -> calculateCustomRecurrence(next, recurrence, now)
        }
    }

    private fun calculateCustomRecurrence(
        start: LocalDateTime,
        recurrence: Recurrence.Custom,
        now: LocalDateTime
    ): LocalDateTime {
        var next = start
        while (next.isBefore(now)) {
            next = when (recurrence.frequency) {
                Frequency.DAILY -> next.plusDays(recurrence.interval.toLong())
                Frequency.WEEKLY -> {
                    if (recurrence.dayOfWeek != null) {
                        // Logic for "every X weeks on Tuesday"
                        val targetDay = java.time.DayOfWeek.of(recurrence.dayOfWeek)
                        next.plusWeeks(recurrence.interval.toLong())
                            .with(TemporalAdjusters.nextOrSame(targetDay))
                    } else {
                        next.plusWeeks(recurrence.interval.toLong())
                    }
                }
                Frequency.MONTHLY -> {
                    if (recurrence.weekOfMonth != null && recurrence.dayOfWeek != null) {
                        // Logic for "first Thursday of each month"
                        val targetDay = java.time.DayOfWeek.of(recurrence.dayOfWeek)
                        val nextMonth = next.plusMonths(recurrence.interval.toLong()).withDayOfMonth(1)
                        nextMonth.with(TemporalAdjusters.dayOfWeekInMonth(recurrence.weekOfMonth, targetDay))
                    } else if (recurrence.dayOfMonth != null) {
                        next.plusMonths(recurrence.interval.toLong()).withDayOfMonth(recurrence.dayOfMonth)
                    } else {
                        next.plusMonths(recurrence.interval.toLong())
                    }
                }
                Frequency.YEARLY -> {
                    if (recurrence.monthOfYear != null) {
                        if (recurrence.weekOfMonth != null && recurrence.dayOfWeek != null) {
                            // Logic for "3rd Thursday of every November"
                            val targetDay = java.time.DayOfWeek.of(recurrence.dayOfWeek)
                            val nextYear = next.plusYears(recurrence.interval.toLong()).withMonth(recurrence.monthOfYear).withDayOfMonth(1)
                            nextYear.with(TemporalAdjusters.dayOfWeekInMonth(recurrence.weekOfMonth, targetDay))
                        } else if (recurrence.dayOfMonth != null) {
                            next.plusYears(recurrence.interval.toLong()).withMonth(recurrence.monthOfYear).withDayOfMonth(recurrence.dayOfMonth)
                        } else {
                            next.plusYears(recurrence.interval.toLong()).withMonth(recurrence.monthOfYear)
                        }
                    } else {
                        next.plusYears(recurrence.interval.toLong())
                    }
                }
            }
        }
        return next
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

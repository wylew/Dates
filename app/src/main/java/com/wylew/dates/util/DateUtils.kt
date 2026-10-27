package com.wylew.dates.util

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

object DateUtils {

    fun getNextOccurrence(event: DateEvent): LocalDateTime {
        val now = LocalDateTime.now()
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

    fun formatCountdown(nextOccurrence: LocalDateTime): String {
        val now = LocalDateTime.now()
        val duration = Duration.between(now, nextOccurrence)
        
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60

        return when {
            days > 0 -> "$days days left"
            hours > 0 -> "$hours hours left"
            minutes > 0 -> "$minutes minutes left"
            else -> "Happening now"
        }
    }
}

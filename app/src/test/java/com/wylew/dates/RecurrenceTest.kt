package com.wylew.dates

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.RRuleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {

    private fun ev(start: LocalDateTime, r: Recurrence, allDay: Boolean = false) =
        DateEvent(title = "t", dateTime = start, recurrence = r, isAllDay = allDay)

    private val now = LocalDateTime.of(2026, 10, 27, 16, 47)

    // --- RRULE parsing -------------------------------------------------------------

    @Test fun googleBirthdayRuleIsYearly() {
        val anchor = LocalDateTime.of(2027, 3, 12, 0, 0)
        assertEquals(Recurrence.Yearly, RRuleParser.toRecurrence(RRuleParser.parse("FREQ=YEARLY;WKST=SU"), anchor))
        // Exchange/Outlook style restating the date still collapses to plain Yearly
        assertEquals(Recurrence.Yearly, RRuleParser.toRecurrence(RRuleParser.parse("FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=12"), anchor))
        assertEquals(Recurrence.Yearly, RRuleParser.toRecurrence(RRuleParser.parse("RRULE:FREQ=YEARLY"), anchor))
    }

    @Test fun nthWeekdayKeepsOrdinal() {
        val anchor = LocalDateTime.of(2026, 11, 26, 0, 0)
        val r = RRuleParser.toRecurrence(RRuleParser.parse("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH"), anchor) as Recurrence.Custom
        assertEquals(Frequency.YEARLY, r.frequency)
        assertEquals(4, r.weekOfMonth)
        assertEquals(4, r.dayOfWeek)
        assertEquals(11, r.monthOfYear)

        val last = RRuleParser.toRecurrence(RRuleParser.parse("FREQ=MONTHLY;BYDAY=-1FR"), anchor) as Recurrence.Custom
        assertEquals(-1, last.weekOfMonth)
        assertEquals(5, last.dayOfWeek)

        val setPos = RRuleParser.toRecurrence(RRuleParser.parse("FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2"), anchor) as Recurrence.Custom
        assertEquals(2, setPos.weekOfMonth)
        assertEquals(2, setPos.dayOfWeek)
    }

    @Test fun intervalAndEnd() {
        val rule = RRuleParser.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU;UNTIL=20250101T000000Z")!!
        assertEquals(2, rule.interval)
        assertEquals(true, rule.hasEnd)
        assertNull(RRuleParser.parse(""))
        assertNull(RRuleParser.parse("FREQ=HOURLY"))
    }

    @Test fun durations() {
        assertEquals(Duration.ofDays(1), RRuleParser.parseDuration("P1D"))
        assertEquals(Duration.ofSeconds(3600), RRuleParser.parseDuration("P3600S"))
        assertEquals(Duration.ofMinutes(90), RRuleParser.parseDuration("PT1H30M"))
        assertEquals(Duration.ofDays(7), RRuleParser.parseDuration("P1W"))
        assertNull(RRuleParser.parseDuration(null))
    }

    // --- Next occurrence -----------------------------------------------------------

    @Test fun yearlyBirthdayCreatedLastYearRollsForward() {
        val e = ev(LocalDateTime.of(2025, 3, 12, 0, 0), Recurrence.Yearly, allDay = true)
        assertEquals(LocalDateTime.of(2027, 3, 12, 0, 0), DateUtils.getNextOccurrence(e, now))
    }

    @Test fun allDayEventIsStillTodayAfterMidnight() {
        val e = ev(LocalDateTime.of(2020, 10, 27, 0, 0), Recurrence.Yearly, allDay = true)
        assertEquals(LocalDate.of(2026, 10, 27), DateUtils.getNextOccurrence(e, now).toLocalDate())
    }

    @Test fun monthlyOn31stDoesNotDrift() {
        val e = ev(LocalDateTime.of(2026, 1, 31, 9, 0), Recurrence.Monthly)
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), DateUtils.getNextOccurrence(e, now))
    }

    @Test fun leapDayBirthday() {
        val e = ev(LocalDateTime.of(2024, 2, 29, 0, 0), Recurrence.Custom(Frequency.YEARLY, monthOfYear = 2, dayOfMonth = 29), allDay = true)
        assertEquals(LocalDate.of(2028, 2, 29), DateUtils.getNextOccurrence(e, now).toLocalDate())
    }

    @Test fun thanksgiving() {
        val e = ev(
            LocalDateTime.of(2020, 11, 26, 0, 0),
            Recurrence.Custom(Frequency.YEARLY, monthOfYear = 11, weekOfMonth = 4, dayOfWeek = 4),
            allDay = true
        )
        assertEquals(LocalDate.of(2026, 11, 26), DateUtils.getNextOccurrence(e, now).toLocalDate())
    }

    @Test fun lastFridayMonthly() {
        val e = ev(LocalDateTime.of(2026, 1, 30, 17, 0), Recurrence.Custom(Frequency.MONTHLY, weekOfMonth = -1, dayOfWeek = 5))
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), DateUtils.getNextOccurrence(e, now))
    }

    @Test fun everyOtherWeek() {
        val e = ev(LocalDateTime.of(2026, 1, 6, 10, 0), Recurrence.Custom(Frequency.WEEKLY, interval = 2, dayOfWeek = 2))
        // Jan 6 2026 is a Tuesday; the Oct 27 occurrence (10 AM) has passed at 4:47 PM → Nov 10
        assertEquals(LocalDateTime.of(2026, 11, 10, 10, 0), DateUtils.getNextOccurrence(e, now))
    }

    @Test fun pastOneOffStaysPut() {
        val start = LocalDateTime.of(2025, 5, 1, 12, 0)
        assertEquals(start, DateUtils.getNextOccurrence(ev(start, Recurrence.None), now))
    }

    @Test fun describeLabels() {
        assertEquals("Yearly", DateUtils.describe(Recurrence.Yearly))
        assertEquals("Monthly on the last Fri", DateUtils.describe(Recurrence.Custom(Frequency.MONTHLY, weekOfMonth = -1, dayOfWeek = 5)))
        assertEquals("Every 2 weeks on Tue", DateUtils.describe(Recurrence.Custom(Frequency.WEEKLY, interval = 2, dayOfWeek = 2)))
    }
}

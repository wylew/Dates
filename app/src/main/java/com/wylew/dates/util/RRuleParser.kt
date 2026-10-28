package com.wylew.dates.util

import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import java.time.Duration
import java.time.LocalDateTime

/**
 * Maps an RFC 5545 RRULE (as stored by Google Calendar / CalendarContract) onto the
 * app's [Recurrence] model. Pure Kotlin so it can be unit tested on the JVM.
 */
object RRuleParser {

    data class Rule(
        val frequency: Frequency,
        val interval: Int = 1,
        /** (ordinal, ISO day 1=Mon..7=Sun); ordinal null = every such day, -1 = last */
        val byDay: List<Pair<Int?, Int>> = emptyList(),
        val byMonthDay: Int? = null,
        val byMonth: Int? = null,
        val bySetPos: Int? = null,
        val hasEnd: Boolean = false
    )

    private val dayCodes = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
    private val byDayPattern = Regex("^([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)$")

    fun parse(rrule: String?): Rule? {
        if (rrule.isNullOrBlank()) return null
        // Some providers store several rules separated by newlines, or include the "RRULE:" prefix.
        val line = rrule.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val body = line.removePrefix("RRULE:")

        val parts = body.split(";").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim().uppercase() to part.substring(i + 1).trim()
        }.toMap()

        val freq = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> Frequency.DAILY
            "WEEKLY" -> Frequency.WEEKLY
            "MONTHLY" -> Frequency.MONTHLY
            "YEARLY" -> Frequency.YEARLY
            else -> return null // SECONDLY/MINUTELY/HOURLY aren't meaningful for countdowns
        }

        val byDay = parts["BYDAY"]?.split(",")?.mapNotNull { token ->
            byDayPattern.find(token.trim().uppercase())?.let { m ->
                m.groupValues[1].takeIf { it.isNotEmpty() }?.removePrefix("+")?.toIntOrNull() to dayCodes.getValue(m.groupValues[2])
            }
        } ?: emptyList()

        return Rule(
            frequency = freq,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            byDay = byDay,
            byMonthDay = parts["BYMONTHDAY"]?.split(",")?.firstOrNull()?.trim()?.toIntOrNull(),
            byMonth = parts["BYMONTH"]?.split(",")?.firstOrNull()?.trim()?.toIntOrNull(),
            bySetPos = parts["BYSETPOS"]?.split(",")?.firstOrNull()?.trim()?.toIntOrNull(),
            hasEnd = parts.containsKey("UNTIL") || parts.containsKey("COUNT")
        )
    }

    /**
     * Converts a rule to the app model. [anchor] is the occurrence the event will be
     * stored with; rules that just restate the anchor ("yearly on March 12" for an
     * event on March 12) collapse to the simple Yearly/Monthly/Weekly forms.
     */
    fun toRecurrence(rule: Rule?, anchor: LocalDateTime): Recurrence {
        if (rule == null) return Recurrence.None

        val single = rule.byDay.singleOrNull()
        val dayOfWeek = when {
            single != null -> single.second
            // Multi-day weekly rules ("MO,WE,FR") can't be represented; keep the anchor's weekday.
            rule.byDay.size > 1 -> anchor.dayOfWeek.value
            else -> null
        }
        val weekOfMonth = single?.first ?: rule.bySetPos?.takeIf { single != null }

        val restatesAnchor =
            (rule.byMonth == null || rule.byMonth == anchor.monthValue) &&
                (rule.byMonthDay == null || rule.byMonthDay == anchor.dayOfMonth) &&
                weekOfMonth == null &&
                (dayOfWeek == null || (rule.frequency == Frequency.WEEKLY && dayOfWeek == anchor.dayOfWeek.value))

        if (rule.interval == 1 && restatesAnchor) {
            return when (rule.frequency) {
                Frequency.DAILY -> Recurrence.Daily
                Frequency.WEEKLY -> Recurrence.Weekly
                Frequency.MONTHLY -> Recurrence.Monthly
                Frequency.YEARLY -> Recurrence.Yearly
            }
        }

        return Recurrence.Custom(
            frequency = rule.frequency,
            interval = rule.interval,
            dayOfWeek = dayOfWeek,
            dayOfMonth = if (weekOfMonth != null) null else rule.byMonthDay,
            weekOfMonth = weekOfMonth,
            monthOfYear = rule.byMonth
        )
    }

    /**
     * Parses an RFC 5545 DURATION (CalendarContract.Events.DURATION), which recurring
     * events use instead of DTEND. Handles "P1D", "PT1H30M", "P3600S", "P1W", "-PT15M".
     */
    fun parseDuration(value: String?): Duration? {
        if (value.isNullOrBlank()) return null
        val v = value.trim().uppercase()
        val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(v) ?: return null
        val (sign, w, d, h, min, s) = m.destructured
        if (listOf(w, d, h, min, s).all { it.isEmpty() }) return null
        var dur = Duration.ofDays((w.toLongOrNull() ?: 0) * 7 + (d.toLongOrNull() ?: 0))
            .plusHours(h.toLongOrNull() ?: 0)
            .plusMinutes(min.toLongOrNull() ?: 0)
            .plusSeconds(s.toLongOrNull() ?: 0)
        if (sign == "-") dur = dur.negated()
        return dur
    }
}

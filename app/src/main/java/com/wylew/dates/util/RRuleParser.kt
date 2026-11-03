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
        // Several plain weekdays ("TU,TH" = every Tuesday and Thursday)
        val multipleDays = if (rule.byDay.size > 1 && rule.byDay.all { it.first == null }) {
            rule.byDay.map { it.second }.distinct().sorted()
        } else emptyList()
        val dayOfWeek = when {
            single != null -> single.second
            // Several days with ordinals ("1MO,3MO") can't be represented; keep the anchor's weekday.
            rule.byDay.size > 1 && multipleDays.isEmpty() -> anchor.dayOfWeek.value
            else -> null
        }
        val weekOfMonth = single?.first ?: rule.bySetPos?.takeIf { single != null }

        val restatesAnchor =
            (rule.byMonth == null || rule.byMonth == anchor.monthValue) &&
                (rule.byMonthDay == null || rule.byMonthDay == anchor.dayOfMonth) &&
                weekOfMonth == null &&
                multipleDays.isEmpty() &&
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
            monthOfYear = rule.byMonth,
            daysOfWeek = multipleDays
        )
    }

    /**
     * The RRULE (without the "RRULE:" prefix) for [recurrence], or null when it doesn't repeat.
     * [start] is the event's first occurrence, which simple rules take their day from. The
     * inverse of [toRecurrence]: parsing the result with the same start gives the rule back.
     */
    fun format(recurrence: Recurrence, start: LocalDateTime): String? {
        val dayCodeOf = dayCodes.entries.associate { (code, day) -> day to code }
        return when (recurrence) {
            Recurrence.None -> null
            Recurrence.Daily -> "FREQ=DAILY"
            Recurrence.Weekly -> "FREQ=WEEKLY"
            Recurrence.Monthly -> "FREQ=MONTHLY"
            Recurrence.Yearly -> "FREQ=YEARLY"
            is Recurrence.Custom -> buildList {
                add("FREQ=${recurrence.frequency.name}")
                if (recurrence.interval > 1) add("INTERVAL=${recurrence.interval}")
                when (recurrence.frequency) {
                    Frequency.DAILY -> Unit
                    Frequency.WEEKLY ->
                        add("BYDAY=" + DateUtils.weeklyDays(recurrence, start).joinToString(",") { dayCodeOf.getValue(it) })
                    Frequency.MONTHLY, Frequency.YEARLY -> {
                        if (recurrence.frequency == Frequency.YEARLY) add("BYMONTH=${recurrence.monthOfYear ?: start.monthValue}")
                        val nth = recurrence.weekOfMonth
                        if (nth != null) {
                            add("BYDAY=$nth${dayCodeOf.getValue((recurrence.dayOfWeek ?: start.dayOfWeek.value).coerceIn(1, 7))}")
                        } else {
                            add("BYMONTHDAY=${recurrence.dayOfMonth ?: start.dayOfMonth}")
                        }
                    }
                }
            }.joinToString(";")
        }
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

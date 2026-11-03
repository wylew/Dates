package com.wylew.dates.util

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Decides which calendar events being imported are already in the list, so imports skip them.
 *
 * Imported dates remember their source (the event's iCal UID, plus the calendar and event row IDs
 * as a fallback), which is matched exactly. Dates imported before that was recorded are matched by
 * looking the same: same title ignoring emoji, punctuation and case, same kind of repeat, and the
 * same next date (a day's leeway for all-day events, which older imports could shift by a day).
 */
object ImportDuplicates {

    enum class Kind {
        /** Not in the list yet */
        NEW,
        /** Matches a date already in the list */
        ALREADY_IMPORTED,
        /** Same as an earlier event in this import (e.g. a birthday in two calendars) */
        DUPLICATE_IN_IMPORT
    }

    /**
     * [existing] is the matching date in the list; [link] holds that date with the candidate's
     * source recorded, when it was matched by looks and should remember its source from now on.
     */
    data class Verdict(val kind: Kind, val existing: DateEvent? = null, val link: DateEvent? = null)

    fun check(
        candidates: List<DateEvent>,
        existing: List<DateEvent>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<Verdict> {
        val byUid = existing.filter { it.sourceUid != null }.associateBy { it.sourceUid }
        val byRow = existing.filter { it.sourceEventId != null }.associateBy { it.sourceCalendarId to it.sourceEventId }
        val unlinked = existing.filter { it.sourceUid == null && it.sourceEventId == null }
        val claimed = mutableSetOf<String>()
        // Every earlier candidate, matched or new, so a second copy in the same import is caught too
        val seen = mutableListOf<DateEvent>()

        return candidates.map { candidate ->
            val verdict = verdictFor(candidate, seen, byUid, byRow, unlinked, claimed, now)
            seen += candidate
            verdict
        }
    }

    private fun verdictFor(
        candidate: DateEvent,
        seen: List<DateEvent>,
        byUid: Map<String?, DateEvent>,
        byRow: Map<Pair<Long?, Long?>, DateEvent>,
        unlinked: List<DateEvent>,
        claimed: MutableSet<String>,
        now: LocalDateTime
    ): Verdict {
        val exact = candidate.sourceUid?.let(byUid::get)
            ?: candidate.sourceEventId?.let { byRow[candidate.sourceCalendarId to it] }
        if (exact != null) return Verdict(Kind.ALREADY_IMPORTED, exact)

        val lookalike = unlinked.firstOrNull { it.id !in claimed && looksSame(it, candidate, now) }
        if (lookalike != null) {
            claimed += lookalike.id
            val linked = lookalike.copy(
                sourceUid = candidate.sourceUid,
                sourceCalendarId = candidate.sourceCalendarId,
                sourceEventId = candidate.sourceEventId
            )
            return Verdict(Kind.ALREADY_IMPORTED, lookalike, linked)
        }

        val inBatch = seen.any { other ->
            (candidate.sourceUid != null && candidate.sourceUid == other.sourceUid) || looksSame(other, candidate, now)
        }
        return if (inBatch) Verdict(Kind.DUPLICATE_IN_IMPORT) else Verdict(Kind.NEW)
    }

    fun looksSame(a: DateEvent, b: DateEvent, now: LocalDateTime): Boolean {
        val title = normalizedTitle(a.title)
        if (title.isEmpty() || title != normalizedTitle(b.title)) return false
        if (repeatKind(a.recurrence) != repeatKind(b.recurrence)) return false
        val gap = abs(
            ChronoUnit.DAYS.between(
                DateUtils.getNextOccurrence(a, now).toLocalDate(),
                DateUtils.getNextOccurrence(b, now).toLocalDate()
            )
        )
        return gap <= if (a.isAllDay || b.isAllDay) 1 else 0
    }

    /** Letters and digits only, lowercased: drops emoji, punctuation and spacing differences */
    fun normalizedTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    private fun repeatKind(recurrence: Recurrence): String = when (recurrence) {
        is Recurrence.Custom -> "custom-${recurrence.frequency}"
        else -> recurrence::class.simpleName.orEmpty()
    }
}

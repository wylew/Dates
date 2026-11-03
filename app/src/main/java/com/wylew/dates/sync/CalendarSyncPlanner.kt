package com.wylew.dates.sync

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.EmojiLibrary
import com.wylew.dates.util.ImportDuplicates
import com.wylew.dates.util.RRuleParser
import com.wylew.dates.util.TitleEmoji
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/** The synced columns of one event series in the sync calendar */
data class CalendarRow(
    val id: Long,
    val title: String,
    val description: String,
    /** Start of the series (or the only occurrence), UTC millis; all-day events sit at UTC midnight */
    val dtstart: Long,
    /** End of the first occurrence */
    val dtend: Long,
    val allDay: Boolean,
    val rrule: String?,
    val uid: String? = null,
    val colorKey: String? = null,
    /** For a repeating series that has ended, the start of its last occurrence */
    val endedAt: Long? = null
)

/** When an event happens, as the calendar stores it */
data class Schedule(
    val dtstart: Long,
    val durationMillis: Long,
    val allDay: Boolean,
    val timezone: String,
    val rrule: String?
)

/** Columns to write to a calendar event; null fields stay as they are */
data class RowWrite(
    val title: String? = null,
    val description: String? = null,
    val schedule: Schedule? = null,
    /** ARGB color the event should show in the calendar, written as the calendar's closest color */
    val color: Int? = null
) {
    val changesText: Boolean get() = title != null || description != null || schedule != null
}

sealed interface SyncOp {
    data class Insert(val appId: String, val write: RowWrite) : SyncOp
    data class Update(val rowId: Long, val appId: String, val write: RowWrite, val currentColorKey: String?) : SyncOp
    data class Delete(val rowId: Long) : SyncOp
}

data class SyncPlan(
    val ops: List<SyncOp>,
    /** Dates whose calendar events were deleted, to remove from the app */
    val removeFromApp: Set<String>,
    /** Synced dates missing from the calendar that were put back, since too many vanished at once to be deliberate */
    val restored: Int,
    /** Dates written to the calendar */
    val sent: Int,
    /** Dates added or changed in the app from the calendar */
    val received: Int
)

/** How emoji are read from titles and drawn; the app's emoji libraries, or a fake in tests */
interface EmojiStyler {
    val defaultLibrary: EmojiLibrary
    /** The first emoji in a title and the title without it */
    fun titleEmoji(title: String): TitleEmoji.Match?
    /** The image that draws [emoji] in [library]'s style, or null to use the system font */
    fun imageFor(emoji: String, library: EmojiLibrary): String?
}

/**
 * Two-way sync between the app's dates and the events of one calendar, worked out without touching
 * either: [plan] decides what to write to the calendar and what the app should drop, and [finals]
 * turns the calendar's events (re-read after writing) into the dates the app should hold.
 *
 * Each synced event's title is "emoji title" and its description ends with a [SyncBlock] holding the
 * date's id, emoji style, pattern and colors. A date remembers a fingerprint of how it looked when
 * last synced, so a side whose fingerprint changed is the side that was edited. When both were
 * edited, the calendar wins (app edits are written as soon as they're saved, so that's rare).
 *
 * Only columns the app owns are written, and the repeat rule is rewritten only when the repeat
 * itself changed in the app, so calendar-only details (end dates, skipped occurrences, locations,
 * guests, reminders) survive.
 */
class CalendarSyncPlanner(
    private val styler: EmojiStyler,
    private val calendarId: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: LocalDateTime = LocalDateTime.now(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val randomColor: () -> Color = { Color.hsv(Random.nextFloat() * 360f, 0.85f, 0.9f) }
) {

    fun plan(events: List<DateEvent>, rows: List<CalendarRow>, deletedIds: Set<String>): SyncPlan {
        val ops = mutableListOf<SyncOp>()
        val byId = mutableMapOf<String, Pair<CalendarRow, SyncBlockCodec.Parsed>>()
        // Events no date has claimed: made on the web, or copies of another date's event
        val loose = mutableListOf<Pair<CalendarRow, SyncBlockCodec.Parsed>>()
        for (row in rows) {
            val parsed = SyncBlockCodec.parse(row.description)
            val id = parsed.block?.id
            when {
                id != null && id in deletedIds -> ops += SyncOp.Delete(row.id)
                id != null && id !in byId -> byId[id] = row to parsed
                else -> loose += row to parsed
            }
        }

        var sent = 0
        var received = 0
        val missing = mutableListOf<DateEvent>()
        for (event in events) {
            if (event.id in deletedIds) continue
            val pair = byId.remove(event.id) ?: claimLoose(event, loose)
            if (pair == null) {
                if (event.syncFingerprint != null) missing += event
                else {
                    ops += SyncOp.Insert(event.id, changes(event, null, null))
                    sent++
                }
                continue
            }
            val (row, parsed) = pair
            val current = toEvent(row, event.id, parsed)
            val known = event.syncFingerprint
            val winner = when {
                known == null -> firstLink(event, current)
                fingerprint(event) == known -> current
                fingerprint(current) == known -> event
                else -> current
            }
            val write = changes(winner, current, row)
            if (fingerprint(winner) != fingerprint(current)) sent++
            if (fingerprint(winner) != fingerprint(event)) received++
            ops += SyncOp.Update(row.id, event.id, write, row.colorKey)
        }

        // Events new to the app: keep the id another device gave them, unless it's taken
        val taken = events.map { it.id }.toMutableSet()
        for ((row, parsed) in byId.values + loose) {
            val id = parsed.block?.id?.takeIf { it !in taken } ?: newId()
            taken += id
            val event = toEvent(row, id, parsed)
            ops += SyncOp.Update(row.id, id, changes(event, event, row), row.colorKey)
            received++
        }

        // Synced dates gone from the calendar were deleted there, unless so many went at once that
        // the calendar was more likely emptied or swapped; then they're written back instead
        val syncedCount = events.count { it.syncFingerprint != null }
        val massLoss = missing.size > 3 && missing.size * 2 > syncedCount
        if (massLoss) missing.forEach { ops += SyncOp.Insert(it.id, changes(it, null, null)) }

        return SyncPlan(
            ops = ops,
            removeFromApp = if (massLoss) emptySet() else missing.map { it.id }.toSet(),
            restored = if (massLoss) missing.size else 0,
            sent = sent + if (massLoss) missing.size else 0,
            received = received
        )
    }

    /**
     * The dates the calendar's events stand for, by id, each with its synced fingerprint. Events
     * without a block (writing it failed) and later copies of the same date are skipped.
     */
    fun finals(rows: List<CalendarRow>, deletedIds: Set<String>): Map<String, DateEvent> {
        val result = LinkedHashMap<String, DateEvent>()
        for (row in rows) {
            val parsed = SyncBlockCodec.parse(row.description)
            val id = parsed.block?.id ?: continue
            if (id in deletedIds || id in result) continue
            val event = toEvent(row, id, parsed)
            result[id] = event.copy(syncFingerprint = fingerprint(event))
        }
        return result
    }

    /**
     * The app's dates after a sync: [current] is the list now, [before] the list the plan was made
     * from. Dates edited while the sync ran keep the edit (with the new fingerprint, so the next sync
     * sends it); dates deleted meanwhile stay deleted.
     */
    fun merge(
        current: List<DateEvent>,
        before: List<DateEvent>,
        finals: Map<String, DateEvent>,
        removed: Set<String>
    ): List<DateEvent> {
        val beforeById = before.associateBy { it.id }
        val kept = current.mapNotNull { event ->
            val final = finals[event.id]
            when {
                beforeById[event.id] != event -> final?.let { event.copy(syncFingerprint = it.syncFingerprint) } ?: event
                event.id in removed -> null
                else -> final ?: event
            }
        }
        val known = current.map { it.id }.toSet()
        return kept + finals.values.filter { it.id !in known && it.id !in beforeById }
    }

    /** A calendar event as a date */
    fun toEvent(row: CalendarRow, id: String, parsed: SyncBlockCodec.Parsed = SyncBlockCodec.parse(row.description)): DateEvent {
        val startMillis = row.endedAt ?: row.dtstart
        val start = local(startMillis, row.allDay)
        val dateTime = if (row.allDay) start.toLocalDate().atStartOfDay() else start
        val end = if (row.allDay) null
        else local(startMillis + (row.dtend - row.dtstart).coerceAtLeast(0), false).takeIf { it.isAfter(dateTime) }
        val recurrence = if (row.endedAt != null) Recurrence.None
        else RRuleParser.toRecurrence(RRuleParser.parse(row.rrule), dateTime)
        val block = parsed.block
        val look = appearance(row.title.trim(), block)
        return DateEvent(
            id = id,
            title = look.title,
            description = parsed.notes,
            dateTime = dateTime,
            endDateTime = end,
            isAllDay = row.allDay,
            useEmojiPattern = block?.u ?: false,
            patternEmojis = block?.p ?: emptyList(),
            recurrence = recurrence,
            imageUri = look.imageUri,
            emoji = look.emoji,
            customColor = if (block == null) randomColor() else block.c?.let(::Color),
            autoColor = block?.a?.let(::Color),
            sourceUid = row.uid,
            sourceCalendarId = calendarId,
            sourceEventId = row.id
        )
    }

    /** What decides whether a date changed: everything sync carries, nothing else */
    fun fingerprint(event: DateEvent): String {
        val key = listOf(
            event.title.trim(),
            event.emoji,
            event.imageUri,
            event.description.trim(),
            event.dateTime,
            event.endDateTime,
            event.isAllDay,
            json.encodeToString(Recurrence.serializer(), event.recurrence),
            event.useEmojiPattern,
            event.patternEmojis.joinToString("\u0001"),
            event.customColor?.toArgb(),
            event.autoColor?.toArgb()
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** The calendar title for a date: its emoji, then its title */
    fun titleFor(event: DateEvent): String =
        listOfNotNull(event.emoji?.takeIf { it.isNotBlank() }, event.title.trim()).joinToString(" ")

    private fun blockFor(event: DateEvent) = SyncBlock(
        id = event.id,
        e = event.emoji,
        i = event.imageUri,
        p = event.patternEmojis,
        u = event.useEmojiPattern,
        c = event.customColor?.toArgb(),
        a = event.autoColor?.toArgb()
    )

    /**
     * A date first linked to an existing calendar event keeps its look and title from the app, and
     * takes when it happens from the calendar: imported dates store their next occurrence, while
     * the calendar knows the series' real start and rule.
     */
    private fun firstLink(app: DateEvent, calendar: DateEvent) = app.copy(
        dateTime = calendar.dateTime,
        endDateTime = calendar.endDateTime,
        isAllDay = calendar.isAllDay,
        recurrence = calendar.recurrence,
        description = app.description.ifBlank { calendar.description },
        sourceUid = calendar.sourceUid,
        sourceCalendarId = calendar.sourceCalendarId,
        sourceEventId = calendar.sourceEventId
    )

    /** An unclaimed web event that is this date: the event it was imported from, or one that looks the same */
    private fun claimLoose(
        event: DateEvent,
        loose: MutableList<Pair<CalendarRow, SyncBlockCodec.Parsed>>
    ): Pair<CalendarRow, SyncBlockCodec.Parsed>? {
        val unblocked = loose.filter { it.second.block == null }
        val match = unblocked.firstOrNull { (row, _) ->
            (event.sourceCalendarId == calendarId && event.sourceEventId == row.id) ||
                (event.sourceUid != null && event.sourceUid == row.uid)
        } ?: unblocked.takeIf { event.syncFingerprint == null }?.firstOrNull { (row, parsed) ->
            ImportDuplicates.looksSame(event, toEvent(row, event.id, parsed), now)
        }
        return match?.also { loose.remove(it) }
    }

    /**
     * Columns to write so the calendar event shows [target]. [current] is the date the event stands
     * for now ([row] parsed), or null for a new event, which gets every column.
     */
    private fun changes(target: DateEvent, current: DateEvent?, row: CalendarRow?): RowWrite {
        val title = titleFor(target)
        val block = blockFor(target)
        val parsed = row?.let { SyncBlockCodec.parse(it.description) }
        val recurrenceChanged = current == null || target.recurrence != current.recurrence
        val scheduleChanged = recurrenceChanged ||
            target.dateTime != current!!.dateTime ||
            target.isAllDay != current.isAllDay ||
            target.endDateTime != current.endDateTime
        val start = firstOccurrence(target)
        val rrule = if (recurrenceChanged) RRuleParser.format(target.recurrence, start) else row?.rrule
        return RowWrite(
            title = title.takeIf { row == null || row.title.trim() != it },
            description = SyncBlockCodec.description(target.description, block)
                .takeIf { parsed == null || parsed.block != block || parsed.notes != target.description.trim() },
            schedule = if (scheduleChanged) scheduleFor(target, start, rrule) else null,
            color = (target.customColor ?: target.autoColor)?.toArgb()
        )
    }

    // A calendar series' start is itself an occurrence, so custom rules start on their first real date
    private fun firstOccurrence(event: DateEvent): LocalDateTime =
        if (event.recurrence is Recurrence.Custom) DateUtils.getNextOccurrence(event, event.dateTime) else event.dateTime

    private fun scheduleFor(event: DateEvent, start: LocalDateTime, rrule: String?): Schedule =
        if (event.isAllDay) {
            Schedule(start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), DAY_MILLIS, true, "UTC", rrule)
        } else {
            val length = event.endDateTime?.let { Duration.between(event.dateTime, it).toMillis() }?.coerceAtLeast(0) ?: 0
            Schedule(start.atZone(zone).toInstant().toEpochMilli(), length, false, zone.id, rrule)
        }

    // All-day events are stored at UTC midnight; reading them in the device zone would shift the day
    private fun local(millis: Long, allDay: Boolean): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), if (allDay) ZoneOffset.UTC else zone)

    private data class Look(val title: String, val emoji: String?, val imageUri: String?)

    /**
     * The emoji and title a calendar title stands for. The app writes "emoji title" and the block
     * says how that emoji is drawn; an emoji changed on the web is drawn in the same library's style.
     */
    private fun appearance(title: String, block: SyncBlock?): Look {
        val blockEmoji = block?.e
        if (blockEmoji != null) {
            stripLeadingEmoji(title, blockEmoji)?.let { rest -> return Look(rest.ifBlank { UNTITLED }, blockEmoji, block.i) }
        }
        val found = styler.titleEmoji(title)
        if (found != null) {
            if (blockEmoji != null && bare(found.emoji) == bare(blockEmoji)) return Look(found.title, blockEmoji, block.i)
            val library = if (block == null) styler.defaultLibrary else libraryOf(block.i)
            return Look(found.title, found.emoji, styler.imageFor(found.emoji, library))
        }
        if (block != null) return Look(title.ifBlank { UNTITLED }, block.e, block.i)
        return Look(title.ifBlank { UNTITLED }, DEFAULT_EMOJI, styler.imageFor(DEFAULT_EMOJI, styler.defaultLibrary))
    }

    private fun libraryOf(imageUri: String?): EmojiLibrary = when {
        imageUri == null -> EmojiLibrary.SYSTEM
        imageUri == "font:mona" -> EmojiLibrary.MONA
        "/fluent_emojis/" in imageUri -> EmojiLibrary.FLUENT
        "/segoe_svgs/" in imageUri -> EmojiLibrary.SEGOE
        "/openmoji_emojis/" in imageUri -> EmojiLibrary.OPENMOJI
        else -> styler.defaultLibrary
    }

    companion object {
        const val DEFAULT_EMOJI = "📅"
        private const val UNTITLED = "Untitled"
        private const val VS16 = '️'
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private val json = Json

        private fun bare(emoji: String) = emoji.filter { it != VS16 }

        /**
         * [title] without [emoji] at its start (variation selectors ignored), or null if it doesn't
         * start with that emoji followed by a space or the end.
         */
        fun stripLeadingEmoji(title: String, emoji: String): String? {
            var i = 0
            for (c in emoji) {
                if (c == VS16) continue
                while (i < title.length && title[i] == VS16) i++
                if (i >= title.length || title[i] != c) return null
                i++
            }
            while (i < title.length && title[i] == VS16) i++
            return if (i == title.length || title[i].isWhitespace()) title.substring(i).trim() else null
        }
    }
}

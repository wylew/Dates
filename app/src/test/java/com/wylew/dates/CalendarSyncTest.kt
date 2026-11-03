package com.wylew.dates

import androidx.compose.ui.graphics.Color
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import com.wylew.dates.sync.CalendarRow
import com.wylew.dates.sync.CalendarSyncPlanner
import com.wylew.dates.sync.EmojiStyler
import com.wylew.dates.sync.SyncBlock
import com.wylew.dates.sync.SyncBlockCodec
import com.wylew.dates.sync.SyncOp
import com.wylew.dates.sync.SyncPlan
import com.wylew.dates.util.EmojiLibrary
import com.wylew.dates.util.RRuleParser
import com.wylew.dates.util.TitleEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarSyncTest {
    private val known = listOf("🎨", "🎂", "🎃", "🎉", "📅", "✈️", "👋").associateBy { it.replace("️", "") }

    private val styler = object : EmojiStyler {
        override val defaultLibrary = EmojiLibrary.FLUENT
        override fun titleEmoji(title: String) = TitleEmoji.extract(title, known)
        override fun imageFor(emoji: String, library: EmojiLibrary) =
            if (library == EmojiLibrary.FLUENT) "file:///android_asset/fluent_emojis/$emoji.webp" else null
    }

    private val now = LocalDateTime.of(2026, 11, 3, 12, 0)
    private var ids = 0
    private val planner = CalendarSyncPlanner(
        styler, calendarId = 99, zone = ZoneId.of("America/Los_Angeles"), now = now,
        newId = { "new-${++ids}" }, randomColor = { Color(0xFF336699.toInt()) }
    )

    /** A stand-in for the calendar provider: applies a plan's writes to its rows */
    private inner class FakeCalendar(var rows: List<CalendarRow> = emptyList()) {
        private var nextId = 1000L

        fun apply(plan: SyncPlan) {
            for (op in plan.ops) when (op) {
                is SyncOp.Insert -> rows = rows + write(CalendarRow(nextId++, "", "", 0, 0, false, null), op.write)
                is SyncOp.Update -> rows = rows.map { if (it.id == op.rowId) write(it, op.write) else it }
                is SyncOp.Delete -> rows = rows.filterNot { it.id == op.rowId }
            }
        }

        private fun write(row: CalendarRow, w: com.wylew.dates.sync.RowWrite): CalendarRow {
            var r = row
            w.title?.let { r = r.copy(title = it) }
            w.description?.let { r = r.copy(description = it) }
            w.schedule?.let { s -> r = r.copy(dtstart = s.dtstart, dtend = s.dtstart + s.durationMillis, allDay = s.allDay, rrule = s.rrule) }
            return r
        }

        /** One full sync of [events], returning the app's dates afterwards */
        fun sync(events: List<DateEvent>, deleted: Set<String> = emptySet()): Pair<SyncPlan, List<DateEvent>> {
            val plan = planner.plan(events, rows, deleted)
            apply(plan)
            val finals = planner.finals(rows, deleted)
            return plan to planner.merge(events, events, finals, plan.removeFromApp)
        }
    }

    private val birthday = DateEvent(
        id = "bday",
        title = "Mom",
        dateTime = LocalDateTime.of(1990, 3, 12, 0, 0),
        isAllDay = true,
        recurrence = Recurrence.Yearly,
        emoji = "🎂",
        imageUri = "file:///android_asset/fluent_emojis/🎂.webp",
        customColor = Color(0xFFFF7A3D.toInt()),
        useEmojiPattern = true,
        patternEmojis = listOf("🎂", "file:///android_asset/fluent_emojis/🎉.webp")
    )

    private fun synced(fields: DateEvent) = fields.copy(syncFingerprint = planner.fingerprint(fields))

    private fun texts(plan: SyncPlan) = plan.ops.filter {
        it is SyncOp.Insert || it is SyncOp.Delete || (it is SyncOp.Update && it.write.changesText)
    }

    // Sending app dates to the calendar

    @Test fun newDateIsAddedWithEmojiTitleAndBlock() {
        val plan = planner.plan(listOf(birthday), emptyList(), emptySet())
        val insert = plan.ops.single() as SyncOp.Insert
        assertEquals("🎂 Mom", insert.write.title)
        assertEquals("FREQ=YEARLY", insert.write.schedule?.rrule)
        assertTrue(insert.write.schedule!!.allDay)
        val block = SyncBlockCodec.parse(insert.write.description!!).block
        assertEquals("bday", block?.id)
        assertEquals(birthday.patternEmojis, block?.p)
        assertEquals(1, plan.sent)
    }

    @Test fun roundTripIsStableAndQuiet() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        val date = after.single()
        assertEquals(birthday.copy(sourceCalendarId = 99, sourceEventId = 1000, syncFingerprint = date.syncFingerprint), date)
        // A second sync has nothing to do
        val (again, afterAgain) = calendar.sync(after)
        assertTrue(texts(again).isEmpty())
        assertEquals(after, afterAgain)
    }

    @Test fun appEditIsSent() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        val edited = after.single().copy(title = "Mom's birthday", emoji = "🎉")
        val (plan, _) = calendar.sync(listOf(edited))
        assertEquals("🎉 Mom's birthday", calendar.rows.single().title)
        assertEquals(1, plan.sent)
    }

    // Receiving calendar changes

    private fun webRow(id: Long, title: String, description: String = "", rrule: String? = null) = CalendarRow(
        id = id, title = title, description = description,
        dtstart = LocalDateTime.of(2026, 12, 25, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
        dtend = LocalDateTime.of(2026, 12, 26, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
        allDay = true, rrule = rrule
    )

    @Test fun webEventBecomesDateWithItsTitleEmoji() {
        val calendar = FakeCalendar(listOf(webRow(5, "🎉 Party", "Bring snacks")))
        val (plan, after) = calendar.sync(emptyList())
        val date = after.single()
        assertEquals("Party", date.title)
        assertEquals("🎉", date.emoji)
        assertEquals("file:///android_asset/fluent_emojis/🎉.webp", date.imageUri)
        assertEquals("Bring snacks", date.description)
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), date.dateTime)
        assertEquals(1, plan.received)
        // The event keeps its title and notes, and gains the block
        val row = calendar.rows.single()
        assertEquals("🎉 Party", row.title)
        assertEquals("Bring snacks", SyncBlockCodec.parse(row.description).notes)
        assertEquals(date.id, SyncBlockCodec.parse(row.description).block?.id)
    }

    @Test fun webEventWithoutEmojiGetsTheDefault() {
        val calendar = FakeCalendar(listOf(webRow(5, "Dentist")))
        val (_, after) = calendar.sync(emptyList())
        assertEquals("📅", after.single().emoji)
        assertEquals("📅 Dentist", calendar.rows.single().title)
    }

    @Test fun emojiChangedOnTheWebKeepsTheLibraryStyle() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        calendar.rows = calendar.rows.map { it.copy(title = "🎃 Mom") }
        val (_, updated) = calendar.sync(after)
        assertEquals("🎃", updated.single().emoji)
        assertEquals("file:///android_asset/fluent_emojis/🎃.webp", updated.single().imageUri)
        assertEquals("Mom", updated.single().title)
    }

    @Test fun calendarEditIsReceivedAndNotSentBack() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        calendar.rows = calendar.rows.map { it.copy(title = "🎂 Mother") }
        val (plan, updated) = calendar.sync(after)
        assertEquals("Mother", updated.single().title)
        assertTrue(texts(plan).isEmpty())
        assertEquals(1, plan.received)
    }

    @Test fun bothEditedCalendarWins() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        calendar.rows = calendar.rows.map { it.copy(title = "🎂 Mother") }
        val (_, updated) = calendar.sync(listOf(after.single().copy(title = "Mama")))
        assertEquals("Mother", updated.single().title)
    }

    @Test fun htmlDescriptionFromTheWebIsRead() {
        val code = SyncBlockCodec.encode(SyncBlock("bday", "🎂"))
        val html = "<b>Call</b> her &amp; Dad<br><br>${SyncBlockCodec.LABEL.replace("—", "&mdash;")}<br>$code"
        val parsed = SyncBlockCodec.parse(html)
        assertEquals("bday", parsed.block?.id)
        assertEquals("Call her & Dad", parsed.notes)
    }

    // Deleting

    @Test fun deletedOnTheWebIsRemovedFromTheApp() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        calendar.rows = emptyList()
        val (plan, updated) = calendar.sync(after)
        assertEquals(setOf("bday"), plan.removeFromApp)
        assertTrue(updated.isEmpty())
    }

    @Test fun manyVanishingAtOnceAreWrittenBack() {
        val calendar = FakeCalendar()
        val dates = (1..5).map { birthday.copy(id = "d$it", title = "Date $it") }
        val (_, after) = calendar.sync(dates)
        calendar.rows = emptyList()
        val (plan, updated) = calendar.sync(after)
        assertTrue(plan.removeFromApp.isEmpty())
        assertEquals(5, plan.restored)
        assertEquals(5, calendar.rows.size)
        assertEquals(5, updated.size)
    }

    @Test fun deletedInTheAppIsDeletedFromTheCalendar() {
        val calendar = FakeCalendar()
        calendar.sync(listOf(birthday))
        val (plan, _) = calendar.sync(emptyList(), deleted = setOf("bday"))
        assertTrue(plan.ops.single() is SyncOp.Delete)
        assertTrue(calendar.rows.isEmpty())
    }

    @Test fun copiedEventOnTheWebBecomesItsOwnDate() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        calendar.rows = calendar.rows + calendar.rows.single().copy(id = 2000)
        val (_, updated) = calendar.sync(after)
        assertEquals(2, updated.size)
        assertEquals(2, calendar.rows.map { SyncBlockCodec.parse(it.description).block?.id }.toSet().size)
    }

    // Linking dates imported before sync

    @Test fun importedDateLinksToItsEventAndTakesTheSeriesStart() {
        val row = webRow(7, "Mom", rrule = "FREQ=YEARLY").copy(
            dtstart = LocalDateTime.of(1990, 3, 12, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
            dtend = LocalDateTime.of(1990, 3, 13, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        )
        val imported = birthday.copy(dateTime = LocalDateTime.of(2027, 3, 12, 0, 0), sourceCalendarId = 99, sourceEventId = 7)
        val calendar = FakeCalendar(listOf(row))
        val (plan, after) = calendar.sync(listOf(imported))
        assertTrue(plan.ops.none { it is SyncOp.Insert })
        assertEquals(LocalDateTime.of(1990, 3, 12, 0, 0), after.single().dateTime)
        assertEquals("🎂 Mom", calendar.rows.single().title)
        assertEquals("FREQ=YEARLY", calendar.rows.single().rrule)
    }

    @Test fun lookalikeDateLinksInsteadOfDuplicating() {
        val calendar = FakeCalendar(listOf(webRow(8, "Christmas")))
        val existing = DateEvent(title = "Christmas", dateTime = LocalDateTime.of(2026, 12, 25, 0, 0), isAllDay = true, emoji = "🎄")
        val (plan, after) = calendar.sync(listOf(existing))
        assertTrue(plan.ops.none { it is SyncOp.Insert })
        assertEquals(1, after.size)
        assertEquals("🎄 Christmas", calendar.rows.single().title)
    }

    // Repeat rules

    @Test fun calendarOnlyRuleDetailsSurviveAppEdits() {
        val row = webRow(9, "Class", rrule = "FREQ=WEEKLY;COUNT=10")
        val calendar = FakeCalendar(listOf(row))
        val (_, after) = calendar.sync(emptyList())
        // Renaming leaves the schedule alone
        calendar.sync(listOf(after.single().copy(title = "Pottery class")))
        assertEquals("FREQ=WEEKLY;COUNT=10", calendar.rows.single().rrule)
        // Moving the date keeps the rule as written
        val (_, moved) = calendar.sync(calendar.sync(listOf(after.single().copy(title = "Pottery class"))).second)
        calendar.sync(listOf(moved.single().copy(dateTime = LocalDateTime.of(2026, 12, 26, 0, 0))))
        assertEquals("FREQ=WEEKLY;COUNT=10", calendar.rows.single().rrule)
    }

    @Test fun customWeeklyRuleStartsOnItsFirstDay() {
        val event = DateEvent(
            id = "gym", title = "Gym", emoji = "🎉",
            dateTime = LocalDateTime.of(2026, 11, 2, 18, 0), // a Monday
            recurrence = Recurrence.Custom(Frequency.WEEKLY, daysOfWeek = listOf(2, 4))
        )
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(event))
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", calendar.rows.single().rrule)
        assertEquals(LocalDateTime.of(2026, 11, 3, 18, 0), after.single().dateTime)
        val (again, _) = calendar.sync(after)
        assertTrue(texts(again).isEmpty())
    }

    @Test fun rulesFormatAndParseBack() {
        val start = LocalDateTime.of(2026, 11, 5, 9, 0) // 1st Thursday
        val rules = listOf(
            Recurrence.Custom(Frequency.DAILY, interval = 3),
            Recurrence.Custom(Frequency.WEEKLY, interval = 2, daysOfWeek = listOf(1, 3, 5)),
            Recurrence.Custom(Frequency.MONTHLY, weekOfMonth = 1, dayOfWeek = 4),
            Recurrence.Custom(Frequency.MONTHLY, weekOfMonth = -1, dayOfWeek = 5),
            Recurrence.Custom(Frequency.YEARLY, interval = 4, dayOfMonth = 5, monthOfYear = 11)
        )
        for (rule in rules) {
            val back = RRuleParser.toRecurrence(RRuleParser.parse(RRuleParser.format(rule, start)), start)
            val event = DateEvent(title = "", dateTime = start, recurrence = rule)
            val other = event.copy(recurrence = back)
            assertEquals(rule.toString(), com.wylew.dates.util.DateUtils.getNextOccurrence(event, now),
                com.wylew.dates.util.DateUtils.getNextOccurrence(other, now))
        }
        assertNull(RRuleParser.format(Recurrence.None, start))
    }

    // Titles

    @Test fun leadingEmojiStripping() {
        assertEquals("Mom", CalendarSyncPlanner.stripLeadingEmoji("🎂 Mom", "🎂"))
        assertEquals("Trip", CalendarSyncPlanner.stripLeadingEmoji("✈ Trip", "✈️"))
        assertEquals("Hi", CalendarSyncPlanner.stripLeadingEmoji("👋🏽 Hi", "👋🏽"))
        assertNull(CalendarSyncPlanner.stripLeadingEmoji("👋🏽 Hi", "👋"))
        assertNull(CalendarSyncPlanner.stripLeadingEmoji("Mom 🎂", "🎂"))
    }

    @Test fun textDefaultEmojiDoesNotGrowTheTitle() {
        val heart = birthday.copy(emoji = "❤", imageUri = null)
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(heart))
        val (_, again) = calendar.sync(after)
        assertEquals("❤ Mom", calendar.rows.single().title)
        assertEquals("Mom", again.single().title)
    }

    @Test fun blockSurvivesEncoding() {
        val block = SyncBlock("id-1", "🎂", "file:///android_asset/openmoji_emojis/1F382.svg", listOf("🎉"), true, -16777216, 123)
        val parsed = SyncBlockCodec.parse(SyncBlockCodec.description("Notes\nline two", block))
        assertEquals(block, parsed.block)
        assertEquals("Notes\nline two", parsed.notes)
        assertNull(SyncBlockCodec.parse("dates:v1:garbage!!").block)
    }

    // Edits made while a sync runs

    @Test fun editDuringSyncIsKeptAndSentNext() {
        val calendar = FakeCalendar()
        val (_, after) = calendar.sync(listOf(birthday))
        val before = after
        val plan = planner.plan(before, calendar.rows, emptySet())
        calendar.apply(plan)
        val editedMeanwhile = listOf(before.single().copy(title = "Mum"))
        val merged = planner.merge(editedMeanwhile, before, planner.finals(calendar.rows, emptySet()), plan.removeFromApp)
        assertEquals("Mum", merged.single().title)
        calendar.sync(merged)
        assertEquals("🎂 Mum", calendar.rows.single().title)
    }

    @Test fun syncedFingerprintMatchesHelper() {
        assertEquals(planner.fingerprint(birthday), synced(birthday).syncFingerprint)
    }
}

package com.wylew.dates

import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import com.wylew.dates.util.ImportDuplicates
import com.wylew.dates.util.ImportDuplicates.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ImportDuplicatesTest {
    private val now = LocalDateTime.of(2026, 11, 3, 9, 0)

    private fun birthday(
        title: String = "Arlen's Birthday",
        start: LocalDateTime = LocalDateTime.of(2026, 11, 16, 0, 0),
        uid: String? = null,
        calendar: Long? = null,
        row: Long? = null
    ) = DateEvent(
        title = title, dateTime = start, isAllDay = true, recurrence = Recurrence.Yearly,
        sourceUid = uid, sourceCalendarId = calendar, sourceEventId = row
    )

    private fun kinds(candidates: List<DateEvent>, existing: List<DateEvent>) =
        ImportDuplicates.check(candidates, existing, now).map { it.kind }

    @Test fun newEventIsNew() = assertEquals(listOf(Kind.NEW), kinds(listOf(birthday(uid = "a")), emptyList()))

    @Test fun sameUidIsAlreadyImported() =
        assertEquals(listOf(Kind.ALREADY_IMPORTED), kinds(listOf(birthday(uid = "a")), listOf(birthday(title = "Renamed", uid = "a"))))

    @Test fun sameCalendarRowIsAlreadyImported() =
        assertEquals(listOf(Kind.ALREADY_IMPORTED), kinds(listOf(birthday(calendar = 11, row = 5)), listOf(birthday(title = "Renamed", calendar = 11, row = 5))))

    @Test fun olderImportMatchedByLooksAndLinked() {
        // Imported before sources were recorded: emoji still in the title, all-day shifted to the previous evening
        val old = birthday(title = "🎨 Arlen's Birthday!", start = LocalDateTime.of(2025, 11, 15, 17, 0))
        val verdict = ImportDuplicates.check(listOf(birthday(uid = "a", calendar = 11, row = 5)), listOf(old), now).single()
        assertEquals(Kind.ALREADY_IMPORTED, verdict.kind)
        assertEquals(old.id, verdict.link?.id)
        assertEquals("a", verdict.link?.sourceUid)
        assertEquals(5L, verdict.link?.sourceEventId)
    }

    @Test fun differentDateIsNotADuplicate() =
        assertEquals(listOf(Kind.NEW), kinds(listOf(birthday(start = LocalDateTime.of(2026, 12, 16, 0, 0))), listOf(birthday())))

    @Test fun differentRepeatIsNotADuplicate() =
        assertEquals(listOf(Kind.NEW), kinds(listOf(birthday()), listOf(birthday().copy(recurrence = Recurrence.None))))

    @Test fun sameEventTwiceInOneImport() =
        assertEquals(listOf(Kind.NEW, Kind.DUPLICATE_IN_IMPORT), kinds(listOf(birthday(uid = "a"), birthday(uid = "b")), emptyList()))

    @Test fun secondCopyOfAnAlreadyImportedEventIsSkipped() =
        assertEquals(
            listOf(Kind.ALREADY_IMPORTED, Kind.DUPLICATE_IN_IMPORT),
            kinds(listOf(birthday(uid = "a"), birthday(uid = "b")), listOf(birthday()))
        )

    @Test fun exactMatchNeedsNoLink() =
        assertNull(ImportDuplicates.check(listOf(birthday(uid = "a")), listOf(birthday(uid = "a")), now).single().link)
}

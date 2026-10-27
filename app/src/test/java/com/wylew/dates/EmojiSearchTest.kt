package com.wylew.dates

import com.wylew.dates.util.EmojiSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSearchTest {
    private data class E(val name: String, val keywords: List<String>, val emoji: String = "")

    private val items = listOf(
        E("Handball", listOf("ball", "sport")),
        E("Handbag", listOf("bag", "purse")),
        E("Waving Hand", listOf("hand", "wave", "hello")),
        E("Thumbs Up", listOf("+1", "hand", "like", "thumb"), "👍"),
        E("Vacation Beach", listOf("beach", "summer")),
        E("Cat", listOf("pet", "animal"), "🐈"),
        E("Grinning Cat", listOf("cat", "smile")),
        E("Eggplant", listOf("aubergine", "vegetable")),
    )

    private fun search(q: String) = EmojiSearch.rank(items, q, { it.name }, { it.keywords }, { it.emoji }).map { it.name }

    @Test fun wholeWordBeatsSubstring() {
        val r = search("hand")
        assertEquals(listOf("Waving Hand", "Thumbs Up"), r.take(2))
        assertTrue(r.indexOf("Handbag") > 1)
    }

    @Test fun pluralsMatch() = assertEquals("Waving Hand", search("hands").first())

    @Test fun shortQueriesDontSubstringMatch() {
        val r = search("cat")
        assertEquals(listOf("Cat", "Grinning Cat"), r) // not "Vacation Beach"
    }

    @Test fun keywordsAndSynonyms() = assertEquals(listOf("Eggplant"), search("aubergine"))

    @Test fun multiWordNeedsEveryWord() = assertEquals(listOf("Thumbs Up"), search("thumbs like"))

    @Test fun pastedEmoji() = assertEquals("Thumbs Up", search("👍").first())
}

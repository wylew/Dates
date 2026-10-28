package com.wylew.dates

import com.wylew.dates.util.TitleEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleEmojiTest {
    // Bare (no variation selector) -> fully-qualified, like emoji_index.json provides
    private val known = listOf("🎨", "🎂", "👋", "👨‍👩‍👧", "🇺🇸", "❤️", "©️", "1️⃣", "✈️")
        .associateBy { it.replace("\uFE0F", "") }

    @Test fun leadingEmoji() = assertEquals("🎨", TitleEmoji.firstEmoji("🎨 Arlen's Birthday", known))

    @Test fun trailingEmoji() = assertEquals("🎂", TitleEmoji.firstEmoji("Mom's birthday 🎂", known))

    @Test fun firstOfSeveral() = assertEquals("🎂", TitleEmoji.firstEmoji("Party 🎂🎨", known))

    @Test fun noEmoji() = assertNull(TitleEmoji.firstEmoji("Dentist appointment", known))

    @Test fun zwjSequenceStaysWhole() = assertEquals("👨‍👩‍👧", TitleEmoji.firstEmoji("Family 👨‍👩‍👧 trip", known))

    @Test fun flag() = assertEquals("🇺🇸", TitleEmoji.firstEmoji("July 4th 🇺🇸", known))

    @Test fun skinToneKept() = assertEquals("👋🏽", TitleEmoji.firstEmoji("Welcome 👋🏽", known))

    @Test fun textSymbolsIgnored() = assertNull(TitleEmoji.firstEmoji("Acme© meeting, room 1", known))

    @Test fun textDefaultWithSelectorMatches() = assertEquals("✈️", TitleEmoji.firstEmoji("✈️ Santa Barbara", known))

    @Test fun textDefaultWithoutSelectorIgnored() = assertNull(TitleEmoji.firstEmoji("I ❤ NY", known))

    @Test fun emojiMissingFromIndexIgnored() = assertNull(TitleEmoji.firstEmoji("Mahjong 🀄 night", known))

    // Removing the emoji from the title

    private fun cleaned(title: String) = TitleEmoji.extract(title, known)?.title

    @Test fun removesLeadingEmoji() = assertEquals("Arlen's Birthday", cleaned("🎨 Arlen's Birthday"))

    @Test fun removesTrailingEmoji() = assertEquals("Mom's birthday", cleaned("Mom's birthday 🎂"))

    @Test fun removesMiddleEmojiAndCollapsesSpaces() = assertEquals("Family trip", cleaned("Family 👨‍👩‍👧 trip"))

    @Test fun removesDanglingSeparator() = assertEquals("Mom's Birthday", cleaned("🎂 - Mom's Birthday"))

    @Test fun removesOnlyTheFirstEmoji() = assertEquals("Party 🎨", cleaned("Party 🎂 🎨"))

    @Test fun emojiOnlyTitleKeepsText() = assertEquals("🎂", cleaned("🎂"))

    @Test fun keepsInnerPunctuation() = assertEquals("Q3 review: budget", cleaned("🎨 Q3 review: budget"))

    @Test fun noEmojiNoMatch() = assertNull(TitleEmoji.extract("Dentist appointment", known))
}

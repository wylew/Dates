package com.wylew.dates.util

import java.text.BreakIterator

/** Finds an emoji someone put in an event title, e.g. "🎨 Arlen's Birthday" -> "🎨". */
object TitleEmoji {
    // Variation selector 16, which asks for the emoji (rather than text) form of a symbol
    private const val VS16 = "\uFE0F"
    private val SKIN_TONES = (0x1F3FB..0x1F3FF).map { String(Character.toChars(it)) }

    // Separators people put between an emoji and the title ("🎂 - Mom", "Trip | ✈️")
    private val EDGE_SEPARATORS = Regex("^[\\s\\-–—|:·•]+|[\\s\\-–—|:·•]+$")
    private val WHITESPACE = Regex("\\s+")

    /** An emoji found in a title, and the title with that emoji taken out */
    data class Match(val emoji: String, val title: String)

    /**
     * The first emoji in [title], or null when there isn't one that can be matched cleanly.
     *
     * The title is walked in grapheme clusters, so multi-codepoint emoji (ZWJ families, flags,
     * keycaps, skin tones) stay whole. [knownEmoji] maps an emoji with variation selectors removed
     * to its fully-qualified form (from emoji_index.json). A cluster counts when it's a known emoji,
     * or a known emoji plus skin-tone modifiers. Symbols that only become emoji with a variation
     * selector (©, ™, ↔, plain ❤) are skipped unless the title includes the selector, so ordinary
     * text punctuation isn't mistaken for an emoji.
     */
    fun firstEmoji(title: String, knownEmoji: Map<String, String>): String? = locate(title, knownEmoji)?.first

    /**
     * Like [firstEmoji], also returning the title without that emoji: surrounding spaces are
     * collapsed and separators left dangling at either end are trimmed. A title that is nothing but
     * the emoji keeps its original text.
     */
    fun extract(title: String, knownEmoji: Map<String, String>): Match? {
        val (emoji, range) = locate(title, knownEmoji) ?: return null
        val cleaned = (title.substring(0, range.first) + " " + title.substring(range.last + 1))
            .replace(WHITESPACE, " ")
            .replace(EDGE_SEPARATORS, "")
        return Match(emoji, cleaned.ifBlank { title })
    }

    /** The first matching emoji and the characters it occupies in [title] */
    private fun locate(title: String, knownEmoji: Map<String, String>): Pair<String, IntRange>? {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(title) }
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            match(title.substring(start, end), knownEmoji)?.let { return it to (start until end) }
            start = end
            end = breaks.next()
        }
        return null
    }

    private fun match(cluster: String, knownEmoji: Map<String, String>): String? {
        val bare = cluster.replace(VS16, "")
        knownEmoji[bare]?.let { qualified ->
            // Text-default symbol typed without its emoji selector: treat as text
            if (VS16 in qualified && VS16 !in cluster) return null
            return qualified
        }
        val withoutTones = SKIN_TONES.fold(bare) { s, tone -> s.replace(tone, "") }
        if (withoutTones != bare && withoutTones in knownEmoji) return cluster
        return null
    }
}

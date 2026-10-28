package com.wylew.dates.util

/**
 * Ranked emoji search shared by every picker tab.
 *
 * Every word in the query has to hit something (name, keyword, or the emoji
 * itself). Whole-word and prefix hits outrank substring hits, so "hand" puts
 * the hand gestures first instead of "handball" and "handbag", and "cat"
 * doesn't surface "communication" or "vacation".
 */
object EmojiSearch {

    private val splitter = Regex("[^\\p{L}\\p{N}+]+")

    fun tokenize(text: String): List<String> =
        text.lowercase().split(splitter).filter { it.isNotBlank() }

    /** Crude singular form so "hands", "cakes", "flags" match their singular keywords. */
    private fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 3 && word.endsWith("es") && (word.endsWith("shes") || word.endsWith("ches") || word.endsWith("xes")) -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /**
     * @return a relevance score, or null when the item doesn't match the query.
     */
    fun score(query: String, name: String, keywords: List<String>, emoji: String = ""): Int? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        if (emoji.isNotEmpty() && q.replace("️", "") == emoji.replace("️", "")) {
            return 10_000 // pasted the emoji itself
        }

        val nameLower = name.lowercase()
        val nameWords = tokenize(nameLower).map(::stem)
        val keywordLower = keywords.map { it.lowercase() }
        val keywordWords = keywordLower.flatMap { tokenize(it) }.map(::stem)

        val tokens = tokenize(q)
        if (tokens.isEmpty()) return null // symbols-only query that isn't this emoji

        var total = 0
        if (nameLower == q) total += 1_000
        else if (nameLower.startsWith("$q ")) total += 400 // query is the name's leading word(s)
        else if (nameLower.startsWith(q)) total += 20
        if (keywordLower.any { it == q }) total += 150

        for (raw in tokens) {
            val t = stem(raw)
            val best = when {
                nameWords.any { it == t } -> 100
                nameWords.any { it.startsWith(t) } -> 60
                keywordWords.any { it == t } -> 50
                keywordWords.any { it.startsWith(t) } -> 30
                t.length >= 4 && nameLower.contains(t) -> 10
                t.length >= 4 && keywordLower.any { it.contains(t) } -> 5
                else -> return null
            }
            total += best
        }
        // Shorter names are usually the "canonical" emoji for a word (Cat vs Cat with Wry Smile).
        return total - nameWords.size
    }

    /** Filters and sorts [items] by relevance; ties keep the incoming order. */
    fun <T> rank(
        items: List<T>,
        query: String,
        name: (T) -> String,
        keywords: (T) -> List<String> = { emptyList() },
        emoji: (T) -> String = { "" }
    ): List<T> {
        if (query.isBlank()) return items
        return items.mapIndexedNotNull { index, item ->
            score(query, name(item), keywords(item), emoji(item))?.let { Triple(item, it, index) }
        }
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }
}

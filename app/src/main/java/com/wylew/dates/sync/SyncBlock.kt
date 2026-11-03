package com.wylew.dates.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * What a synced calendar event can't hold in its own fields: which date in the app it is, and its
 * emoji style, background pattern and colors. Stored as one coded line at the end of the event's
 * description, under a label telling people on the web to leave it alone.
 *
 * Image paths are the app's bundled emoji assets, so they're the same on every device.
 */
@Serializable
data class SyncBlock(
    /** The app's id for the date */
    val id: String,
    /** The emoji the date shows (also the first thing in the calendar title) */
    val e: String? = null,
    /** The image that draws [e] in a library's style, or "font:mona"; null for the system font */
    val i: String? = null,
    /** Background pattern emojis or images */
    val p: List<String> = emptyList(),
    /** Whether the background pattern is on */
    val u: Boolean = false,
    /** Chosen card color, ARGB */
    val c: Int? = null,
    /** Color taken from the emoji image, ARGB */
    val a: Int? = null
)

object SyncBlockCodec {
    const val LABEL = "— Dates app: emoji & colors (keep the line below) —"
    private const val PREFIX = "dates:v1:"
    private val CODE = Regex("dates:v1:([A-Za-z0-9_-]+)")
    private const val ASSET_ROOT = "file:///android_asset/"

    private val json = Json { ignoreUnknownKeys = true }

    /** A description's own text, without the app's block, and the block if it has a readable one */
    data class Parsed(val notes: String, val block: SyncBlock?)

    fun encode(block: SyncBlock): String {
        val compact = block.copy(i = block.i?.let(::shorten), p = block.p.map(::shorten))
        val bytes = json.encodeToString(SyncBlock.serializer(), compact).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The description to write: the notes, then the label and the coded line */
    fun description(notes: String, block: SyncBlock): String {
        val trimmed = notes.trim()
        val code = "$LABEL\n${encode(block)}"
        return if (trimmed.isEmpty()) code else "$trimmed\n\n$code"
    }

    /**
     * Splits a calendar description into the person's notes and the app's block. Descriptions
     * edited on calendar.google.com can come back as HTML, so that's flattened to text first.
     * A damaged block reads as no block; its line is still removed from the notes.
     */
    fun parse(description: String): Parsed {
        val text = if (looksLikeHtml(description)) htmlToText(description) else description
        val match = CODE.find(text)
        val block = match?.let { decode(it.groupValues[1]) }
        val notes = text.lines()
            .filterNot { line -> line.contains(PREFIX) || line.trim() == LABEL }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return Parsed(notes, block)
    }

    private fun decode(code: String): SyncBlock? = runCatching {
        val text = String(Base64.getUrlDecoder().decode(code), Charsets.UTF_8)
        val block = json.decodeFromString(SyncBlock.serializer(), text)
        block.copy(i = block.i?.let(::expand), p = block.p.map(::expand))
    }.getOrNull()?.takeIf { it.id.isNotBlank() }

    // Asset paths are long and repeated; "@" stands in for the asset root
    private fun shorten(uri: String) = if (uri.startsWith(ASSET_ROOT)) "@" + uri.removePrefix(ASSET_ROOT) else uri
    private fun expand(uri: String) = if (uri.startsWith("@")) ASSET_ROOT + uri.removePrefix("@") else uri

    private val HTML_TAG = Regex("<\\s*/?\\s*(br|p|div|span|b|i|u|a|ul|ol|li|strong|em)\\b[^>]*>", RegexOption.IGNORE_CASE)

    private fun looksLikeHtml(text: String) = HTML_TAG.containsMatchIn(text)

    internal fun htmlToText(html: String): String = html
        .replace(Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<\\s*/\\s*(p|div|li)\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("&#(x[0-9a-fA-F]+|\\d+);")) { m ->
            val code = m.groupValues[1]
            val value = if (code.startsWith("x")) code.drop(1).toIntOrNull(16) else code.toIntOrNull()
            value?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
        }
        .replace("&amp;", "&")
}

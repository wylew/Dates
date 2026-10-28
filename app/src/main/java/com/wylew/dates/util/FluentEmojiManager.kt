package com.wylew.dates.util

import android.content.Context
import java.io.File

/**
 * Microsoft Fluent 3D emoji (still WebPs) bundled in assets/fluent_emojis, named by codepoint.
 * Rebuild them with tools/import_fluent_emoji.py.
 */
object FluentEmojiManager {
    const val ASSET_DIR = "fluent_emojis"

    fun assetFiles(context: Context): List<String> =
        try {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".webp") }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

    fun isPackDownloaded(context: Context): Boolean = assetFiles(context).isNotEmpty()

    /** Older builds copied (placeholder) Fluent files into internal storage; reclaim that space */
    fun deleteLegacyCopies(context: Context) {
        File(context.filesDir, ASSET_DIR).takeIf { it.exists() }?.deleteRecursively()
    }
}

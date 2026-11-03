package com.wylew.dates.util

import android.content.Context
import java.io.File

/**
 * OpenMoji color SVGs (https://openmoji.org, CC BY-SA 4.0) bundled in assets/openmoji_emojis, named
 * by codepoint, plus extras.json naming OpenMoji's own extras. Rebuild with tools/import_openmoji.py.
 */
object OpenMojiManager {
    const val ASSET_DIR = "openmoji_emojis"
    const val EXTRAS_FILE = "extras.json"

    fun assetFiles(context: Context): List<String> =
        try {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".svg") }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

    fun isPackDownloaded(context: Context): Boolean = assetFiles(context).isNotEmpty()

    /** Older builds copied OpenMoji files into internal storage; reclaim that space */
    fun deleteLegacyCopies(context: Context) {
        File(context.filesDir, ASSET_DIR).takeIf { it.exists() }?.deleteRecursively()
    }
}

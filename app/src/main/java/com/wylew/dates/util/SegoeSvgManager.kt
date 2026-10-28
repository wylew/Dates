package com.wylew.dates.util

import android.content.Context

/** Segoe UI emoji SVGs bundled in assets/segoe_svgs; EmojiLibraries reads their metadata.json */
object SegoeSvgManager {
    private const val ASSET_DIR = "segoe_svgs"

    fun isPackDownloaded(context: Context): Boolean {
        return try {
            val list = context.assets.list(ASSET_DIR)
            !list.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }
}

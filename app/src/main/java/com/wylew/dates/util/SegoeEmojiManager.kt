package com.wylew.dates.util

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.FileOutputStream

object SegoeEmojiManager {
    fun getFontFile(context: Context): File {
        val dir = File(context.filesDir, "fonts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "SEGUIEMJ.TTF")
        if (!file.exists() || file.length() == 0L) {
            try {
                context.assets.open("fonts/SEGUIEMJ.TTF").use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return file
    }

    fun isFontDownloaded(context: Context): Boolean {
        val file = getFontFile(context)
        return file.exists() && file.length() > 0L
    }

    fun getSegoeFontFamily(context: Context): FontFamily? {
        val file = getFontFile(context)
        return if (file.exists() && file.length() > 0L) {
            try {
                FontFamily(Font(file))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }
}

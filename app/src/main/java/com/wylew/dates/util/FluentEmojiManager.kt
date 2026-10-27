package com.wylew.dates.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class FluentEmojiItem(
    val id: String,
    val name: String,
    val fileUri: String,
    val filePath: String,
    val category: String = "Fluent 3D"
)

object FluentEmojiManager {
    fun getEmojiDir(context: Context): File {
        val dir = File(context.filesDir, "fluent_emojis")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        if (dir.listFiles().isNullOrEmpty()) {
            try {
                val assetFiles = context.assets.list("fluent_emojis") ?: emptyArray()
                for (fileName in assetFiles) {
                    context.assets.open("fluent_emojis/$fileName").use { input ->
                        FileOutputStream(File(dir, fileName)).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                // Assets folder might not exist yet
            }
        }
        return dir
    }

    fun isPackDownloaded(context: Context): Boolean {
        val dir = getEmojiDir(context)
        val files = dir.listFiles { _, name ->
            (name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".png"))
        }
        return !files.isNullOrEmpty() && files.any { it.length() > 0 }
    }

    fun getDownloadedEmojis(context: Context): List<FluentEmojiItem> {
        val dir = getEmojiDir(context)
        val files = dir.listFiles { _, name ->
            (name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".png")) && File(dir, name).length() > 0
        } ?: return emptyList()

        return files.sortedBy { it.name }.map { file ->
            val cleanName = file.nameWithoutExtension
                .replace("_3d", "")
                .replace("_animated", "")
                .replace("-", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { char -> char.uppercase() } }

            FluentEmojiItem(
                id = file.name,
                name = cleanName,
                fileUri = file.toURI().toString(),
                filePath = file.absolutePath
            )
        }
    }

    fun deletePack(context: Context) {
        val dir = getEmojiDir(context)
        dir.deleteRecursively()
    }
}

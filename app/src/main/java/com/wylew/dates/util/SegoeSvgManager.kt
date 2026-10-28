package com.wylew.dates.util

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

@Serializable
data class SvgMetadataEntry(
    val file: String,
    val name: String,
    val category: String = "Custom SVG",
    val keywords: List<String> = emptyList()
)

data class SegoeSvgItem(
    val id: String,
    val name: String,
    val fileUri: String,
    val filePath: String,
    val category: String,
    val keywords: List<String>
)

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

    fun getDownloadedEmojis(context: Context): List<SegoeSvgItem> {
        // metadata.json is stored in Unicode emoji order, grouped by category
        val metadataList = try {
            context.assets.open("$ASSET_DIR/metadata.json").use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val jsonString = reader.readText()
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<SvgMetadataEntry>>(jsonString)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        val metadataEntries = metadataList.associateBy { it.file }
        val metadataOrder = metadataList.withIndex().associate { it.value.file to it.index }

        val assetFiles = try {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".svg", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return assetFiles.sortedWith(compareBy({ metadataOrder[it] ?: Int.MAX_VALUE }, { it })).map { fileName ->
            val metadata = metadataEntries[fileName]
            val cleanName = metadata?.name ?: fileName.removeSuffix(".svg").replace("-", " ").replace("_", " ")
            val category = metadata?.category ?: "Segoe Emojis"
            val keywords = metadata?.keywords ?: fileName.removeSuffix(".svg").split("-", "_").map { it.lowercase() }
            val assetUri = "file:///android_asset/$ASSET_DIR/$fileName"

            SegoeSvgItem(
                id = fileName,
                name = cleanName,
                fileUri = assetUri,
                filePath = assetUri,
                category = category,
                keywords = keywords
            )
        }
    }

    fun deletePack(_context: Context) {
        // No-op for bundled assets
    }
}

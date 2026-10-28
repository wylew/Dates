package com.wylew.dates.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import com.wylew.dates.model.DateEvent
import com.wylew.dates.util.MonaFontManager
import java.io.File
import java.io.InputStream
import java.net.URI

/**
 * Widgets are RemoteViews and can't host Coil/Lottie/AndroidView, so image-based emojis
 * (Segoe SVGs, Fluent/OpenMoji images, Mona font glyphs) are rasterized to a bitmap here.
 * Returns null for plain text emojis (and unsupported formats), which render as Text instead.
 */
object WidgetEmojiRenderer {

    fun render(context: Context, event: DateEvent, sizePx: Int, glyphColor: Int): Bitmap? {
        val uri = event.imageUri ?: return null
        return try {
            when {
                uri == "font:mona" -> renderFontGlyph(event.emoji ?: return null, MonaFontManager.getFontFile(context), sizePx, glyphColor)
                uri.endsWith(".json", ignoreCase = true) -> null // Lottie animations
                uri.endsWith(".svg", ignoreCase = true) -> openStream(context, uri)?.use { renderSvg(it, sizePx) }
                else -> openStream(context, uri)?.use { BitmapFactory.decodeStream(it) }
                    ?.let { Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openStream(context: Context, uri: String): InputStream? = when {
        uri.startsWith("file:///android_asset/") -> context.assets.open(uri.removePrefix("file:///android_asset/"))
        uri.startsWith("asset://") -> context.assets.open(uri.removePrefix("asset:///").removePrefix("asset://"))
        uri.startsWith("file:") -> File(URI(uri)).takeIf { it.exists() }?.inputStream()
        uri.startsWith("/") -> File(uri).takeIf { it.exists() }?.inputStream()
        else -> null
    }

    private fun renderSvg(input: InputStream, sizePx: Int): Bitmap {
        val svg = SVG.getFromInputStream(input)
        svg.setDocumentWidth(sizePx.toFloat())
        svg.setDocumentHeight(sizePx.toFloat())
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
        return bitmap
    }

    private fun renderFontGlyph(emoji: String, fontFile: File, sizePx: Int, color: Int): Bitmap? {
        if (!fontFile.exists() || fontFile.length() == 0L) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.createFromFile(fontFile)
            textSize = sizePx * 0.8f
            textAlign = Paint.Align.CENTER
            this.color = color
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(emoji, sizePx / 2f, baseline, paint)
        return bitmap
    }
}

package com.wylew.dates.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import com.wylew.dates.model.DateEvent
import java.io.File
import java.io.InputStream
import java.net.URI

/**
 * Draws an event's tiled, rotated emoji pattern onto a plain Android canvas, so the same pattern
 * can back Compose cards/sheets and be baked into bitmaps for home screen widgets.
 * All sizes are in dp and scaled by [pxPerDp].
 */
object EmojiPatternRenderer {
    private const val EMOJI_SIZE_DP = 28f
    private const val TEXT_SIZE_DP = 22f
    private const val SPACING_X_DP = 40f
    private const val SPACING_Y_DP = 44f
    private const val ROTATION_DEGREES = 15f
    private const val ALPHA = (0.35f * 255).toInt()

    private fun isFileEmoji(em: String) = em.startsWith("/") || em.startsWith("file:") || em.startsWith("asset:")

    /** Image-based pattern emojis rasterized at [pxPerDp]; null entries are drawn as text */
    fun loadEmojiBitmaps(context: Context, patternEmojis: List<String>, pxPerDp: Float): List<Bitmap?> {
        val sizePx = (EMOJI_SIZE_DP * pxPerDp).toInt()
        return patternEmojis.map { em -> if (isFileEmoji(em)) loadEmojiBitmap(context, em, sizePx) else null }
    }

    private fun loadEmojiBitmap(context: Context, em: String, sizePx: Int): Bitmap? {
        fun openStream(): InputStream? = when {
            em.startsWith("file:///android_asset/") -> context.assets.open(em.removePrefix("file:///android_asset/"))
            em.startsWith("asset://") -> context.assets.open(em.removePrefix("asset:///").removePrefix("asset://"))
            em.startsWith("file:") -> File(URI(em)).takeIf { it.exists() }?.inputStream()
            em.startsWith("/") -> File(em).takeIf { it.exists() }?.inputStream()
            else -> null
        }
        // Try SVG first, then fall back to raster decode (PNG/JPG/WEBP)
        try {
            openStream()?.use { input ->
                val svg = SVG.getFromInputStream(input)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
                return bitmap
            }
        } catch (_: Exception) {
        }
        return try {
            openStream()?.use { input ->
                val src = BitmapFactory.decodeStream(input) ?: return null
                Bitmap.createScaledBitmap(src, sizePx, sizePx, true).also { if (it != src) src.recycle() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createTextPaint(context: Context, event: DateEvent, pxPerDp: Float, color: Int): Paint {
        val fontFile = when (event.imageUri) {
            "font:mona" -> MonaFontManager.getFontFile(context)
            else -> if (event.imageUri?.startsWith("/") == true) File(event.imageUri) else null
        }
        return Paint().apply {
            textSize = TEXT_SIZE_DP * pxPerDp
            isAntiAlias = true
            this.color = color
            textAlign = Paint.Align.CENTER
            alpha = ALPHA
            if (fontFile != null && fontFile.exists() && fontFile.length() > 0L) {
                try {
                    typeface = Typeface.createFromFile(fontFile)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun createBitmapPaint(): Paint = Paint().apply {
        alpha = ALPHA
        isAntiAlias = true
        isFilterBitmap = true
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        pxPerDp: Float,
        patternEmojis: List<String>,
        emojiBitmaps: List<Bitmap?>,
        textPaint: Paint,
        bitmapPaint: Paint
    ) {
        if (patternEmojis.isEmpty()) return
        canvas.save()
        canvas.rotate(ROTATION_DEGREES, width / 2f, height / 2f)

        val spacingX = SPACING_X_DP * pxPerDp
        val spacingY = SPACING_Y_DP * pxPerDp
        val textOffsetY = (textPaint.descent() - textPaint.ascent()) / 3f

        var row = 0
        var y = -height
        while (y < height * 2f) {
            var col = 0
            var x = -width + if (row % 2 == 1) spacingX / 2f else 0f
            while (x < width * 2f) {
                val index = (row + col) % patternEmojis.size
                val em = patternEmojis[index]
                val bitmap = emojiBitmaps[index]
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x - bitmap.width / 2f, y - bitmap.height / 2f, bitmapPaint)
                } else if (!isFileEmoji(em)) {
                    canvas.drawText(em, x, y + textOffsetY, textPaint)
                }
                col++
                x += spacingX
            }
            row++
            y += spacingY
        }
        canvas.restore()
    }

    /** The pattern as a standalone transparent bitmap, or null if the event has no pattern */
    fun renderBitmap(context: Context, event: DateEvent, widthPx: Int, heightPx: Int, pxPerDp: Float, color: Int): Bitmap? {
        if (!event.useEmojiPattern || event.patternEmojis.isEmpty() || widthPx <= 0 || heightPx <= 0) return null
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        draw(
            Canvas(bitmap), widthPx.toFloat(), heightPx.toFloat(), pxPerDp, event.patternEmojis,
            loadEmojiBitmaps(context, event.patternEmojis, pxPerDp),
            createTextPaint(context, event, pxPerDp, color),
            createBitmapPaint()
        )
        return bitmap
    }
}

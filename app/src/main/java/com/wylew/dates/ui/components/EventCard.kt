package com.wylew.dates.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wylew.dates.model.DateEvent
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.MonaFontManager
import com.caverock.androidsvg.SVG
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val nextOccurrence = DateUtils.getNextOccurrence(event)
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")
    
    val now = LocalDateTime.now()
    val duration = Duration.between(now, nextOccurrence)
    val daysLeft = maxOf(0L, duration.toDays())

    // Interaction state for press scaling feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    // Material 3 expressive surface & container colors
    val defaultContainer = if (isCompact) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardColorRaw = event.customColor ?: event.autoColor ?: defaultContainer
    
    val cardColor by animateColorAsState(
        targetValue = cardColorRaw,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardColor"
    )

    // Calculate readable text color
    val defaultContentColor = contentColorFor(cardColor)
    val contentColor = remember(cardColor, defaultContentColor, event.customColor, event.autoColor) {
        if (event.customColor != null || event.autoColor != null) {
            if (cardColor.luminance() > 0.45f) Color.Black else Color.White
        } else {
            defaultContentColor
        }
    }

    val defaultBadgeContainer = MaterialTheme.colorScheme.primaryContainer
    val defaultBadgeContent = MaterialTheme.colorScheme.onPrimaryContainer

    val badgeContainerColor = remember(cardColor, contentColor, defaultBadgeContainer, event.customColor, event.autoColor) {
        if (event.customColor != null || event.autoColor != null) {
            contentColor.copy(alpha = 0.15f)
        } else {
            defaultBadgeContainer
        }
    }

    val badgeContentColor = remember(cardColor, contentColor, defaultBadgeContent, event.customColor, event.autoColor) {
        if (event.customColor != null || event.autoColor != null) {
            contentColor
        } else {
            defaultBadgeContent
        }
    }

    val gradientBrush = remember(cardColor) {
        Brush.horizontalGradient(
            colors = listOf(
                cardColor,
                cardColor.copy(alpha = 0.82f),
                cardColor
            )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = if (isCompact) ExpressiveCompactCardShape else ExpressiveCardShape,
        color = Color.Transparent,
        contentColor = contentColor,
        tonalElevation = if (event.customColor != null) 0.dp else if (isCompact) 2.dp else 4.dp,
        shadowElevation = 0.dp,
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
        ) {
            // Emoji Pattern Matrix Overlay (Tighter, horizontal/gentle angle, honoring custom font database)
            if (event.useEmojiPattern && event.patternEmojis.isNotEmpty()) {
                val context = LocalContext.current
                val fontFile = remember(event.imageUri) {
                    when (event.imageUri) {
                        "font:mona" -> MonaFontManager.getFontFile(context)
                        else -> if (event.imageUri?.startsWith("/") == true) File(event.imageUri) else null
                    }
                }

                val patternBitmaps = remember(event.patternEmojis) {
                    val density = context.resources.displayMetrics.density
                    val sizePx = (28 * density).toInt()

                    event.patternEmojis.map { em ->
                        if (em.startsWith("/") || em.startsWith("file:") || em.startsWith("asset:")) {
                            try {
                                var loadedBitmap: android.graphics.Bitmap? = null

                                fun getStream(): java.io.InputStream? {
                                    return when {
                                        em.startsWith("file:///android_asset/") -> {
                                            val path = em.removePrefix("file:///android_asset/")
                                            context.assets.open(path)
                                        }
                                        em.startsWith("asset:///") || em.startsWith("asset://") -> {
                                            val path = em.removePrefix("asset:///").removePrefix("asset://")
                                            context.assets.open(path)
                                        }
                                        em.startsWith("file:") -> {
                                            val uri = java.net.URI(em)
                                            val file = File(uri)
                                            if (file.exists()) file.inputStream() else null
                                        }
                                        em.startsWith("/") -> {
                                            val file = File(em)
                                            if (file.exists()) file.inputStream() else null
                                        }
                                        else -> null
                                    }
                                }

                                // Try SVG first
                                try {
                                    getStream()?.use { inputStream ->
                                        val svg = SVG.getFromInputStream(inputStream)
                                        val b = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
                                        val c = android.graphics.Canvas(b)
                                        val renderBounds = android.graphics.RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
                                        svg.setDocumentWidth(sizePx.toFloat())
                                        svg.setDocumentHeight(sizePx.toFloat())
                                        svg.renderToCanvas(c, renderBounds)
                                        loadedBitmap = b
                                    }
                                } catch (_: Exception) {
                                    // Fallback to raster decode (PNG/JPG/WEBP)
                                    try {
                                        getStream()?.use { inputStream ->
                                            val options = android.graphics.BitmapFactory.Options().apply {
                                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                                            }
                                            val srcBitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                                            if (srcBitmap != null) {
                                                val scaled = android.graphics.Bitmap.createScaledBitmap(srcBitmap, sizePx, sizePx, true)
                                                if (scaled != srcBitmap) {
                                                    srcBitmap.recycle()
                                                }
                                                loadedBitmap = scaled
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                                loadedBitmap
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }
                }

                val density = LocalDensity.current
                val paint = remember(fontFile, density, contentColor) {
                    val fontSizePx = with(density) { 22.sp.toPx() }
                    android.graphics.Paint().apply {
                        textSize = fontSizePx
                        isAntiAlias = true
                        color = contentColor.toArgb()
                        textAlign = android.graphics.Paint.Align.CENTER
                        alpha = (0.35f * 255).toInt()
                        if (fontFile != null && fontFile.exists() && fontFile.length() > 0L) {
                            try {
                                typeface = android.graphics.Typeface.createFromFile(fontFile)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                val bPaint = remember {
                    android.graphics.Paint().apply {
                        alpha = (0.35f * 255).toInt()
                        isAntiAlias = true
                        isFilterBitmap = true
                    }
                }

                Canvas(modifier = Modifier.matchParentSize()) {
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        nativeCanvas.save()

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        nativeCanvas.rotate(15f, cx, cy)

                        val spacingX = 40.dp.toPx()
                        val spacingY = 44.dp.toPx()
                        val textOffsetY = (paint.descent() - paint.ascent()) / 3f

                        val startX = -size.width
                        val endX = size.width * 2f
                        val startY = -size.height
                        val endY = size.height * 2f

                        var row = 0
                        var y = startY
                        while (y < endY) {
                            var col = 0
                            val offsetX = if (row % 2 == 1) spacingX / 2f else 0f
                            var x = startX + offsetX

                            while (x < endX) {
                                val patternIndex = (row + col) % event.patternEmojis.size
                                val em = event.patternEmojis[patternIndex]
                                val bitmap = patternBitmaps[patternIndex]

                                if (bitmap != null) {
                                    nativeCanvas.drawBitmap(bitmap, x - bitmap.width / 2f, y - bitmap.height / 2f, bPaint)
                                } else if (!em.startsWith("/") && !em.startsWith("file:") && !em.startsWith("asset:")) {
                                    nativeCanvas.drawText(em, x, y + textOffsetY, paint)
                                }

                                col++
                                x += spacingX
                            }
                            row++
                            y += spacingY
                        }
                        nativeCanvas.restore()
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hero Icon / Emoji Tonal Container
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 56.dp else 74.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    if (event.imageUri == "font:mona") {
                        CustomFontEmojiView(
                            emoji = event.emoji ?: "🎂",
                            fontFile = MonaFontManager.getFontFile(context),
                            modifier = Modifier.fillMaxSize(),
                            fontSizeSp = if (isCompact) 34f else 45f
                        )
                    } else if (event.imageUri != null) {
                        FluentEmojiView(
                            imageUri = event.imageUri,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isCompact) 2.dp else 4.dp)
                        )
                    } else if (event.emoji != null) {
                        Text(
                            text = event.emoji,
                            fontSize = if (isCompact) 34.sp else 45.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 14.dp))

                // Title and Date Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = event.title,
                        style = if (isCompact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = nextOccurrence.format(dateFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Expressive Countdown Pill Badge
                Surface(
                    shape = ExpressivePillShape,
                    color = badgeContainerColor,
                    contentColor = badgeContentColor,
                    border = BorderStroke(1.5.dp, badgeContentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.defaultMinSize(minWidth = if (isCompact) 46.dp else 56.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 8.dp else 11.dp,
                            vertical = if (isCompact) 4.dp else 6.dp
                        )
                    ) {
                        if (daysLeft > 0) {
                            Text(
                                text = daysLeft.toString(),
                                style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                lineHeight = if (isCompact) 16.sp else 22.sp
                            )
                            Text(
                                text = if (daysLeft == 1L) "day" else "days",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                color = badgeContentColor.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = "TODAY",
                                style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = badgeContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

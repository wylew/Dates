package com.wylew.dates.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.ImageProvider
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wylew.dates.model.DateEvent
import com.wylew.dates.ui.theme.accentShadeOf
import com.wylew.dates.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** Colors for one widget surface; mirrors the in-app card styling. */
data class WidgetPalette(
    val background: ColorProvider,
    val content: ColorProvider,
    val subContent: ColorProvider,
    val bubble: ColorProvider,
    /** Tint for monochrome font glyphs that get baked into a bitmap */
    val glyphColor: Int
)

object DateWidgetUtils {

    // Neutral surfaces for uncolored events and list containers, from the system (wallpaper)
    // palette on Android 12+ or the standard Material baseline, matching the app theme
    fun neutralPalette(context: Context, theme: WidgetTheme): WidgetPalette {
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val light = if (dynamic) dynamicLightColorScheme(context) else lightColorScheme()
        val dark = if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme()
        fun pick(token: (ColorScheme) -> Color): ColorProvider = when (theme) {
            WidgetTheme.SYSTEM -> DayNightColorProvider(day = token(light), night = token(dark))
            WidgetTheme.LIGHT -> ColorProvider(token(light))
            WidgetTheme.DARK -> ColorProvider(token(dark))
        }
        return WidgetPalette(
            background = pick { it.surfaceContainer },
            content = pick { it.onSurface },
            subContent = pick { it.onSurfaceVariant },
            bubble = pick { it.surfaceContainerHighest },
            // Baked into bitmaps, so it can't follow day/night; outline reads on both
            glyphColor = when (theme) {
                WidgetTheme.LIGHT -> light.onSurfaceVariant
                WidgetTheme.DARK -> dark.onSurfaceVariant
                WidgetTheme.SYSTEM -> light.outline
            }.toArgb()
        )
    }

    fun paletteFor(context: Context, event: DateEvent, theme: WidgetTheme): WidgetPalette {
        val color = event.customColor ?: event.autoColor ?: return neutralPalette(context, theme)
        val onColor = if (color.luminance() > 0.45f) Color.Black else Color.White
        return WidgetPalette(
            background = ColorProvider(color),
            content = ColorProvider(onColor),
            subContent = ColorProvider(onColor.copy(alpha = 0.75f)),
            bubble = ColorProvider(accentShadeOf(color)),
            glyphColor = onColor.toArgb()
        )
    }

    // Same day math as EventCard so widgets and the app agree
    fun daysLeft(event: DateEvent): Long = DateUtils.daysUntil(event)

    /** False for one-off events whose date has passed */
    fun isUpcoming(event: DateEvent): Boolean =
        !DateUtils.getNextOccurrence(event).toLocalDate().isBefore(LocalDate.now())

    fun upcomingEvents(events: List<DateEvent>): List<DateEvent> =
        events.filter(::isUpcoming).sortedBy { DateUtils.getNextOccurrence(it) }

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    @Composable
    fun EmojiBubble(event: DateEvent, emojiBitmap: Bitmap?, size: Dp, palette: WidgetPalette) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(palette.bubble)
                .cornerRadius(size / 2),
            contentAlignment = Alignment.Center
        ) {
            if (emojiBitmap != null) {
                Image(
                    provider = ImageProvider(emojiBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(size * 0.66f)
                )
            } else {
                Text(text = event.emoji ?: "📅", style = TextStyle(fontSize = (size.value * 0.48f).sp))
            }
        }
    }

    @Composable
    fun DaysBubble(daysLeft: Long, size: Dp, palette: WidgetPalette) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(palette.bubble)
                .cornerRadius(size / 2),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (daysLeft > 0) {
                    Text(
                        text = daysLeft.toString(),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.32f).sp,
                            color = palette.content
                        )
                    )
                    Text(
                        text = if (daysLeft == 1L) "day" else "days",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 9.sp, color = palette.subContent)
                    )
                } else {
                    Text(
                        text = "TODAY",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.2f).sp,
                            color = palette.content
                        )
                    )
                }
            }
        }
    }

    /** Single-line countdown ("12 days", "Today") for Small rows */
    @Composable
    fun DaysPill(daysLeft: Long, palette: WidgetPalette) {
        Box(
            modifier = GlanceModifier
                .background(palette.bubble)
                .cornerRadius(50.dp)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    daysLeft == 0L -> "Today"
                    daysLeft == 1L -> "1 day"
                    else -> "$daysLeft days"
                },
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.content),
                maxLines = 1
            )
        }
    }

    /** Emoji bubble, title (+ date), and countdown — the in-app list card layout */
    @Composable
    fun EventRowContent(event: DateEvent, emojiBitmap: Bitmap?, palette: WidgetPalette, style: RowStyle) {
        val bubbleSize = style.bubbleSize
        val showDate = style.showDate
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiBubble(event, emojiBitmap, bubbleSize, palette)
            Spacer(modifier = GlanceModifier.width(if (style.pillCounter) 8.dp else 10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = event.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (showDate) 15.sp else if (style.pillCounter) 13.sp else 14.sp,
                        color = palette.content
                    ),
                    maxLines = 1
                )
                if (showDate) Text(
                    text = DateUtils.getNextOccurrence(event).format(dateFormatter),
                    style = TextStyle(fontSize = 11.sp, color = palette.subContent),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (style.pillCounter) DaysPill(daysLeft(event), palette) else DaysBubble(daysLeft(event), bubbleSize, palette)
        }
    }

    /** Emoji pattern baked to a bitmap, stretched over its parent behind the content */
    @Composable
    fun PatternLayer(patternBitmap: Bitmap?, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
        if (patternBitmap != null) {
            Image(
                provider = ImageProvider(patternBitmap),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = modifier
            )
        }
    }

    /** Whole-widget list card for a single event */
    @Composable
    fun EventRowCard(
        event: DateEvent,
        emojiBitmap: Bitmap?,
        patternBitmap: Bitmap?,
        palette: WidgetPalette,
        layout: ListLayout,
        modifier: GlanceModifier
    ) {
        val style = layout.rowStyle
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(palette.background)
                .cornerRadius(if (layout == ListLayout.SMALL) 16.dp else 24.dp)
                .then(modifier)
        ) {
            PatternLayer(patternBitmap)
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = style.padding),
                contentAlignment = Alignment.CenterStart
            ) {
                EventRowContent(event, emojiBitmap, palette, style)
            }
        }
    }

    /** Whole-widget square card: emoji on top, title, then days until — all centered */
    @Composable
    fun EventSquareCard(event: DateEvent, emojiBitmap: Bitmap?, patternBitmap: Bitmap?, palette: WidgetPalette, side: Dp, modifier: GlanceModifier) {
        val daysLeft = daysLeft(event)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(palette.background)
                .cornerRadius(24.dp)
                .then(modifier)
        ) {
            PatternLayer(patternBitmap)
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBubble(event, emojiBitmap, side * 0.4f, palette)
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = event.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.content,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                if (daysLeft > 0) {
                    Text(
                        text = daysLeft.toString(),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (side.value * 0.17f).sp,
                            color = palette.content
                        )
                    )
                    Text(
                        text = if (daysLeft == 1L) "day" else "days",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = palette.subContent)
                    )
                } else {
                    Text(
                        text = "TODAY",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (side.value * 0.12f).sp,
                            color = palette.content
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun EmptyState(message: String, theme: WidgetTheme, modifier: GlanceModifier = GlanceModifier) {
        val palette = neutralPalette(LocalContext.current, theme)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(palette.background)
                .cornerRadius(24.dp)
                .then(modifier)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📅", style = TextStyle(fontSize = 24.sp))
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = message,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.subContent,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

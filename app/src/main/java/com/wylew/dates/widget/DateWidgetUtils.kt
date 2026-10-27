package com.wylew.dates.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wylew.dates.model.DateEvent
import com.wylew.dates.util.DateUtils
import java.time.Duration
import java.time.LocalDateTime

object DateWidgetUtils {

    /**
     * Calculates a high-contrast color (Black or White) for text on a given background color.
     * Uses the standard formula for relative luminance.
     */
    private fun getContrastColor(backgroundColor: Color): Color {
        val luminance = 0.2126 * backgroundColor.red + 0.7152 * backgroundColor.green + 0.0722 * backgroundColor.blue
        // Threshold adjusted slightly to favor white text on medium-dark backgrounds
        return if (luminance > 0.45) Color.Black else Color.White
    }

    @Composable
    fun WidgetContainer(
        backgroundColor: Color,
        modifier: GlanceModifier = GlanceModifier,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .then(modifier)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    @Composable
    fun EmptyState(
        message: String,
        modifier: GlanceModifier = GlanceModifier
    ) {
        WidgetContainer(
            backgroundColor = Color.White,
            modifier = modifier
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅",
                    style = TextStyle(fontSize = 28.sp)
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = message,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(Color.Gray),
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.glance.text.TextAlign.Center
                    )
                )
            }
        }
    }

    @Composable
    fun EventItem(
        event: DateEvent,
        isCompact: Boolean = false,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val themeColor = event.customColor ?: Color(0xFFE0E0E0)
        val contentColor = getContrastColor(themeColor)
        // Sub-content color with enough contrast
        val subContentColor = if (contentColor == Color.Black) Color(0xFF555555) else Color(0xFFDDDDDD)

        val nextOccurrence = DateUtils.getNextOccurrence(event)
        val duration = Duration.between(LocalDateTime.now(), nextOccurrence)
        val daysLeft = maxOf(0L, duration.toDays())

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(themeColor)
                .cornerRadius(12.dp)
                .then(modifier)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(if (isCompact) 36.dp else 44.dp)
                    .background(contentColor.copy(alpha = 0.2f))
                    .cornerRadius(if (isCompact) 18.dp else 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = event.emoji ?: "📅",
                    style = TextStyle(fontSize = if (isCompact) 18.sp else 22.sp)
                )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = event.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 14.sp else 16.sp,
                        color = ColorProvider(contentColor)
                    ),
                    maxLines = 1
                )
                Text(
                    text = if (daysLeft == 0L) "Happening Today!" else "Upcoming",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(subContentColor)
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = daysLeft.toString(),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 22.sp else 26.sp,
                        color = ColorProvider(contentColor)
                    )
                )
                if (daysLeft > 0L) {
                    Text(
                        text = if (daysLeft == 1L) "day" else "days",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(subContentColor)
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun SingleEventCard(
        event: DateEvent,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val themeColor = event.customColor ?: Color(0xFFE0E0E0)
        val contentColor = getContrastColor(themeColor)
        val subContentColor = if (contentColor == Color.Black) Color(0xFF444444) else Color(0xFFEEEEEE)

        val nextOccurrence = DateUtils.getNextOccurrence(event)
        val duration = Duration.between(LocalDateTime.now(), nextOccurrence)
        val daysLeft = maxOf(0L, duration.toDays())

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(themeColor)
                .cornerRadius(24.dp)
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                
                // Hero Icon - Maximized
                Box(
                    modifier = GlanceModifier
                        .size(86.dp)
                        .background(contentColor.copy(alpha = 0.15f))
                        .cornerRadius(43.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = event.emoji ?: "📅",
                        style = TextStyle(fontSize = 50.sp)
                    )
                }
                
                Spacer(modifier = GlanceModifier.height(8.dp))
                
                Text(
                    text = event.title,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ColorProvider(contentColor),
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 1
                )
                
                Spacer(modifier = GlanceModifier.height(2.dp))
                
                if (daysLeft == 0L) {
                    Text(
                        text = "TODAY",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = ColorProvider(contentColor)
                        )
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = daysLeft.toString(),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp,
                                color = ColorProvider(contentColor)
                            )
                        )
                        Text(
                            text = if (daysLeft == 1L) "day" else "days",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(subContentColor)
                            )
                        )
                    }
                }
                
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

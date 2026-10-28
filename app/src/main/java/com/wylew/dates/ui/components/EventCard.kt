package com.wylew.dates.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wylew.dates.model.DateEvent
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.ListDensity
import com.wylew.dates.ui.theme.accentShadeOf
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.EmojiPatternRenderer
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.MonaFontManager
import java.time.format.DateTimeFormatter

@Composable
fun EventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    density: ListDensity = ListDensity.EXPANDED,
    shape: Shape = if (density == ListDensity.EXPANDED) ExpressiveCardShape else ExpressiveCompactCardShape
) {
    val isCompact = density != ListDensity.EXPANDED
    val isSmall = density == ListDensity.SMALL
    val nextOccurrence = DateUtils.getNextOccurrence(event)
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")

    val daysLeft = DateUtils.daysUntil(event)
    val daysPast = DateUtils.daysPast(event)

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

    // Emoji and countdown bubbles: a shade of the event color, or a faint tint on uncolored cards
    val bubbleColor = if (event.customColor != null || event.autoColor != null) {
        accentShadeOf(cardColor)
    } else {
        contentColor.copy(alpha = 0.12f)
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
        shape = shape,
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
                EmojiPatternBackground(event, contentColor, Modifier.matchParentSize())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isSmall) 10.dp else if (isCompact) 10.dp else 14.dp,
                        vertical = if (isSmall) 6.dp else if (isCompact) 10.dp else 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hero Icon / Emoji Tonal Container
                EmojiInCircle(
                    emoji = event.emoji,
                    imageUri = event.imageUri,
                    size = if (isSmall) 40.dp else if (isCompact) 56.dp else 74.dp,
                    background = bubbleColor
                )

                Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 14.dp))

                // Title and Date Column (Small drops the date)
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
                    if (!isSmall) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = nextOccurrence.format(dateFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (isSmall) {
                    // Single-line countdown pill keeps Small rows thin
                    Box(
                        modifier = Modifier
                            .clip(ExpressivePillShape)
                            .background(bubbleColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                daysPast != null -> if (daysPast == 1L) "1 day ago" else "$daysPast days ago"
                                daysLeft > 0 -> if (daysLeft == 1L) "1 day" else "$daysLeft days"
                                else -> "Today"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            color = contentColor
                        )
                    }
                } else {
                    // Countdown in the same tonal bubble as the emoji
                    Box(
                        modifier = Modifier
                            .size(if (isSmall) 40.dp else if (isCompact) 56.dp else 74.dp)
                            .clip(CircleShape)
                            .background(bubbleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val count = daysPast ?: daysLeft
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = if (isCompact) 16.sp else 22.sp
                                )
                                Text(
                                    text = when {
                                        daysPast != null -> if (count == 1L) "day ago" else "days ago"
                                        else -> if (count == 1L) "day" else "days"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    text = "TODAY",
                                    style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// Share of a circle's diameter a square fits in without its corners being clipped (1/√2, with margin)
private const val CIRCLE_CONTENT_FRACTION = 0.68f

/**
 * An event's emoji (image, Mona font, or plain text) centered in a circle and sized to sit inside
 * it, so square image corners aren't shaved off.
 */
@Composable
fun EmojiInCircle(emoji: String?, imageUri: String?, size: Dp, background: Color, fallback: String = "📅") {
    val content = size * CIRCLE_CONTENT_FRACTION
    val density = LocalDensity.current
    // Emoji glyphs are drawn slightly larger than their font size. Converting from dp cancels the
    // system font scale, so large text settings can't push the emoji past the circle's edge.
    val fontSize = with(density) { (content * 0.82f).toSp() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageUri == "font:mona" -> CustomFontEmojiView(
                emoji = emoji ?: fallback,
                fontFile = MonaFontManager.getFontFile(LocalContext.current),
                modifier = Modifier.size(content),
                fontSizeSp = fontSize.value
            )
            imageUri != null -> FluentEmojiView(imageUri = imageUri, modifier = Modifier.size(content))
            else -> Text(text = emoji ?: fallback, fontSize = fontSize, lineHeight = fontSize)
        }
    }
}

// Tiled, rotated emoji pattern used behind event cards and the details sheet
@Composable
fun EmojiPatternBackground(event: DateEvent, contentColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pxPerDp = LocalDensity.current.density
    val emojiBitmaps = remember(event.patternEmojis, pxPerDp) {
        EmojiPatternRenderer.loadEmojiBitmaps(context, event.patternEmojis, pxPerDp)
    }
    val textPaint = remember(event.imageUri, pxPerDp, contentColor) {
        EmojiPatternRenderer.createTextPaint(context, event, pxPerDp, contentColor.toArgb())
    }
    val bitmapPaint = remember { EmojiPatternRenderer.createBitmapPaint() }

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            EmojiPatternRenderer.draw(
                canvas.nativeCanvas, size.width, size.height, pxPerDp,
                event.patternEmojis, emojiBitmaps, textPaint, bitmapPaint
            )
        }
    }
}

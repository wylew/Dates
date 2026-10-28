package com.wylew.dates.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.ui.theme.accentShadeOf
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.MonaFontManager
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EventDetailsSheet(
    event: DateEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val nextOccurrence = DateUtils.getNextOccurrence(event)
    val countdown = DateUtils.formatCountdown(nextOccurrence)
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    val defaultContainer = MaterialTheme.colorScheme.primaryContainer
    val heroColor = event.customColor ?: event.autoColor ?: defaultContainer
    
    val defaultOnHero = contentColorFor(heroColor)
    val onHeroColor = remember(heroColor, defaultOnHero) {
        if (event.customColor != null || event.autoColor != null) {
            if (heroColor.luminance() > 0.45f) Color.Black else Color.White
        } else {
            defaultOnHero
        }
    }

    // Colored events tint the whole sheet; uncolored ones keep the theme surfaces
    val hasEventColor = event.customColor != null || event.autoColor != null
    val sheetColor = if (hasEventColor) heroColor else MaterialTheme.colorScheme.surfaceContainerLow
    val onSheetColor = if (hasEventColor) onHeroColor else MaterialTheme.colorScheme.onSurface
    val onSheetVariant = if (hasEventColor) onHeroColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentContainer = remember(heroColor) { accentShadeOf(heroColor) }
    val tonalContainer = if (hasEventColor) accentContainer else MaterialTheme.colorScheme.surfaceContainer
    // Icon bubbles sit on the accent container, so shade one step further to stay visible
    val tonalContainerHigh = if (hasEventColor) {
        remember(accentContainer) { accentShadeOf(accentContainer.copy(alpha = 1f)) }
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val accentColor = if (hasEventColor) onHeroColor else MaterialTheme.colorScheme.primary

    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        shape = ExpressiveSheetShape,
        containerColor = sheetColor,
        contentColor = onSheetColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = onSheetVariant.copy(alpha = 0.4f)) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (event.useEmojiPattern && event.patternEmojis.isNotEmpty()) {
                EmojiPatternBackground(event, onSheetColor, Modifier.matchParentSize())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Emoji Badge
                EmojiInCircle(
                    emoji = event.emoji,
                    imageUri = event.imageUri,
                    size = 114.dp,
                    background = if (hasEventColor) accentContainer else heroColor.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSheetColor,
                    textAlign = TextAlign.Center
                )

                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSheetVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Expressive Countdown Hero Surface
                Surface(
                    color = if (hasEventColor) accentContainer else heroColor,
                    contentColor = onHeroColor,
                    shape = ExpressiveCardShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (DateUtils.isPast(event)) "SINCE IT HAPPENED" else "UNTIL NEXT OCCURRENCE",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = onHeroColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Details Section Tonal Container
                Surface(
                    color = tonalContainer,
                    shape = ExpressiveCompactCardShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveDetailRow(
                            icon = Icons.Outlined.CalendarMonth,
                            label = if (DateUtils.isPast(event)) "Date" else "Next Date",
                            bubbleColor = tonalContainerHigh,
                            iconTint = accentColor,
                            labelColor = onSheetVariant,
                            valueColor = onSheetColor,
                            value = nextOccurrence.format(dateFormatter)
                        )
                        ExpressiveDetailRow(
                            icon = Icons.Outlined.Schedule,
                            label = "Timing",
                            bubbleColor = tonalContainerHigh,
                            iconTint = accentColor,
                            labelColor = onSheetVariant,
                            valueColor = onSheetColor,
                            value = if (event.isAllDay) "All Day Event" else nextOccurrence.format(timeFormatter)
                        )
                        ExpressiveDetailRow(
                            icon = Icons.Outlined.Repeat,
                            label = "Repeats",
                            bubbleColor = tonalContainerHigh,
                            iconTint = accentColor,
                            labelColor = onSheetVariant,
                            valueColor = onSheetColor,
                            value = when (event.recurrence) {
                                is Recurrence.None -> "Does not repeat"
                                is Recurrence.Daily -> "Daily"
                                is Recurrence.Weekly -> "Weekly"
                                is Recurrence.Monthly -> "Monthly"
                                is Recurrence.Yearly -> "Yearly"
                                is Recurrence.Custom -> "Custom repetition"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Connected button group (Delete & Edit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    ExpressiveActionButton(
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(
                            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                            pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape
                        ),
                        onClick = { showDeleteConfirm = true },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        icon = Icons.Default.Delete,
                        label = "Delete"
                    )

                    ExpressiveActionButton(
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(
                            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                            pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape
                        ),
                        onClick = onEdit,
                        containerColor = if (hasEventColor) onHeroColor else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (hasEventColor) heroColor else MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = Icons.Default.Edit,
                        label = "Edit"
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = ExpressiveCompactCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete \"${event.title}\"?", textAlign = TextAlign.Center) },
            text = { Text("This event will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExpressiveDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    bubbleColor: Color,
    iconTint: Color,
    labelColor: Color,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bubbleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveActionButton(
    modifier: Modifier = Modifier,
    shapes: ButtonShapes,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    label: String
) {
    // Shape morphs toward the pressed shape on touch (Material 3 Expressive)
    Button(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = ButtonDefaults.MediumContentPadding
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.MediumIconSpacing))
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

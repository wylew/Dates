package com.wylew.dates.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.MonaFontManager
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ExpressiveSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Emoji Badge
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .clip(CircleShape)
                    .background(heroColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                if (event.imageUri == "font:mona") {
                    CustomFontEmojiView(
                        emoji = event.emoji ?: "🎂",
                        fontFile = MonaFontManager.getFontFile(context),
                        modifier = Modifier.fillMaxSize(),
                        fontSizeSp = 54f
                    )
                } else if (event.imageUri != null) {
                    FluentEmojiView(
                        imageUri = event.imageUri,
                        modifier = Modifier.size(78.dp)
                    )
                } else {
                    Text(text = event.emoji ?: "📅", fontSize = 63.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Expressive Countdown Hero Surface
            Surface(
                color = heroColor,
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
                        text = "UNTIL NEXT OCCURRENCE",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = onHeroColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Details Section Tonal Container
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = ExpressiveCompactCardShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveDetailRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Next Date",
                        value = nextOccurrence.format(dateFormatter)
                    )
                    ExpressiveDetailRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Timing",
                        value = if (event.isAllDay) "All Day Event" else nextOccurrence.format(timeFormatter)
                    )
                    ExpressiveDetailRow(
                        icon = Icons.Outlined.Repeat,
                        label = "Repeats",
                        value = DateUtils.describe(event.recurrence)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Expressive Action Buttons (Delete & Edit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Default.Delete,
                    label = "Delete"
                )

                ExpressiveActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = onEdit,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Default.Edit,
                    label = "Edit"
                )
            }
        }
    }
}

@Composable
private fun ExpressiveDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ExpressiveActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    label: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(52.dp),
        shape = ExpressivePillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall)
    }
}

package com.wylew.dates.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.MonaFontManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PresetColors = listOf(
    null, // System Dynamic / Default
    Color(0xFFE53935), // Vibrant Crimson
    Color(0xFFFF6D00), // Sunset Orange
    Color(0xFFFFB300), // Rich Amber
    Color(0xFF00C853), // Emerald Green
    Color(0xFF00E5FF), // Electric Cyan
    Color(0xFF2979FF), // Deep Cobalt
    Color(0xFF651FFF), // Electric Violet
    Color(0xFFF50057)  // Bold Magenta
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddEventSheet(
    eventToEdit: DateEvent? = null,
    onDismiss: () -> Unit,
    onSave: (DateEvent) -> Unit
) {
    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var description by remember { mutableStateOf(eventToEdit?.description ?: "") }
    var emoji by remember { mutableStateOf<String?>(eventToEdit?.emoji ?: if (eventToEdit?.imageUri == null) "📅" else null) }
    var imageUri by remember { mutableStateOf(eventToEdit?.imageUri) }
    var selectedDateTime by remember { mutableStateOf(eventToEdit?.dateTime ?: LocalDateTime.now()) }
    var isAllDay by remember { mutableStateOf(eventToEdit?.isAllDay ?: false) }
    var useEmojiPattern by remember { mutableStateOf(eventToEdit?.useEmojiPattern ?: false) }
    var patternEmojis by remember { mutableStateOf(eventToEdit?.patternEmojis ?: emptyList()) }
    var selectedRecurrence by remember { mutableStateOf(eventToEdit?.recurrence ?: Recurrence.None) }
    var showPatternEmojiPicker by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(eventToEdit?.customColor) }
    var showCustomColorPicker by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        shape = ExpressiveSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (eventToEdit == null) "New Countdown" else "Edit Countdown",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event Title") },
                leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                shape = ExpressiveCompactCardShape,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Description Input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                shape = ExpressiveCompactCardShape,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tonal Selection Cards (Emoji & Date)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Emoji Trigger Card
                ExpressiveTriggerCard(
                    modifier = Modifier.weight(1f),
                    onClick = { showEmojiPicker = true }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "EMOJI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (imageUri == "font:mona") {
                            CustomFontEmojiView(
                                emoji = emoji ?: "🎂",
                                fontFile = MonaFontManager.getFontFile(LocalContext.current),
                                modifier = Modifier.size(40.dp),
                                fontSizeSp = 24f
                            )
                        } else if (imageUri != null) {
                            FluentEmojiView(
                                imageUri = imageUri!!,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Text(text = emoji ?: "📅", style = MaterialTheme.typography.displaySmall)
                        }
                    }
                }

                // Date Trigger Card
                ExpressiveTriggerCard(
                    modifier = Modifier.weight(2f),
                    onClick = { showDatePicker = true }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "DATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedDateTime.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // All-Day Event Switch Card
            Surface(
                shape = ExpressiveCompactCardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All-Day Event",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAllDay) "Happens all day (no specific time)" else "Happens at a specific time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Emoji Pattern Overlay Switch Card
            Surface(
                shape = ExpressiveCompactCardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emoji Background Pattern",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (useEmojiPattern) "Pattern overlay enabled" else "No background pattern",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = useEmojiPattern,
                            onCheckedChange = { useEmojiPattern = it }
                        )
                    }

                    if (useEmojiPattern) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select up to 3 pattern emojis (${patternEmojis.size}/3):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            patternEmojis.forEachIndexed { index, em ->
                                Surface(
                                    shape = ExpressivePillShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        patternEmojis = patternEmojis.toMutableList().also { it.removeAt(index) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (em.startsWith("/") || em.startsWith("file:")) {
                                            FluentEmojiView(
                                                imageUri = em,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Text(text = em, style = MaterialTheme.typography.bodyLarge)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "✕", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (patternEmojis.size < 3) {
                                OutlinedButton(
                                    onClick = { showPatternEmojiPicker = true },
                                    shapes = ButtonDefaults.shapes(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("+ Add Emoji")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recurrence Selection Chips
            Text(
                text = "Repeats",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val recurrenceOptions = remember {
                listOf(
                    "None" to Recurrence.None,
                    "Daily" to Recurrence.Daily,
                    "Weekly" to Recurrence.Weekly,
                    "Monthly" to Recurrence.Monthly,
                    "Yearly" to Recurrence.Yearly
                )
            }

            // Expressive button group: pressed items widen, extras collapse into an overflow menu
            ButtonGroup(
                overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
                modifier = Modifier.fillMaxWidth()
            ) {
                recurrenceOptions.forEach { (label, recurrence) ->
                    toggleableItem(
                        checked = selectedRecurrence::class == recurrence::class,
                        label = label,
                        onCheckedChange = { selectedRecurrence = recurrence },
                        weight = 1f
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Color Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme Color",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showCustomColorPicker = !showCustomColorPicker }) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showCustomColorPicker) "Presets" else "Custom Spectrum")
                }
            }

            Crossfade(targetState = showCustomColorPicker, label = "ColorPickerFade") { isCustom ->
                if (isCustom) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        HueColorPicker(
                            initialColor = selectedColor ?: Color(0xFFE53935),
                            onColorSelected = { selectedColor = it },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(selectedColor ?: MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(PresetColors) { color ->
                            val isSelected = selectedColor == color
                            
                            val chipScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.12f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "chipScale"
                            )

                            Box(
                                modifier = Modifier
                                    .scale(chipScale)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { selectedColor = color }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isSelected,
                                    enter = scaleIn() + fadeIn(),
                                    exit = scaleOut() + fadeOut()
                                ) {
                                    val iconTint = if (color != null) {
                                        if (color.luminance() > 0.45f) Color.Black else Color.White
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Save / Update Pill Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val event = (eventToEdit ?: DateEvent(
                            title = title,
                            dateTime = selectedDateTime,
                            isAllDay = isAllDay,
                            useEmojiPattern = useEmojiPattern,
                            patternEmojis = patternEmojis,
                            recurrence = selectedRecurrence,
                            emoji = emoji,
                            imageUri = imageUri,
                            customColor = selectedColor
                        )).copy(
                            title = title,
                            description = description,
                            dateTime = selectedDateTime,
                            isAllDay = isAllDay,
                            useEmojiPattern = useEmojiPattern,
                            patternEmojis = patternEmojis,
                            recurrence = selectedRecurrence,
                            emoji = emoji,
                            imageUri = imageUri,
                            customColor = selectedColor
                        )
                        onSave(event)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                enabled = title.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding
            ) {
                Text(
                    text = if (eventToEdit == null) "Create Countdown" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(it),
                            ZoneId.systemDefault()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Emoji Search Dialog (System vs Fluent 3D)
    if (showEmojiPicker) {
        EmojiSearchDialog(
            onDismissRequest = { showEmojiPicker = false },
            onEmojiSelected = { selectedEmoji, selectedImageUri ->
                if (selectedImageUri != null) {
                    imageUri = selectedImageUri
                    emoji = selectedEmoji
                } else if (selectedEmoji != null) {
                    emoji = selectedEmoji
                    imageUri = null
                }
                showEmojiPicker = false
            }
        )
    }

    // Pattern Emoji Picker Dialog
    if (showPatternEmojiPicker) {
        EmojiSearchDialog(
            onDismissRequest = { showPatternEmojiPicker = false },
            onEmojiSelected = { selectedEmoji, selectedImageUri ->
                val chosen = selectedImageUri ?: selectedEmoji
                if (chosen != null && patternEmojis.size < 3) {
                    patternEmojis = patternEmojis + chosen
                }
                showPatternEmojiPicker = false
            }
        )
    }
}

@Composable
private fun ExpressiveTriggerCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "triggerCardScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = ExpressiveCompactCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource
    ) {
        content()
    }
}

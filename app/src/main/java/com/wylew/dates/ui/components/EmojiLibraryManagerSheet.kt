package com.wylew.dates.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.util.AppSettings
import com.wylew.dates.util.EmojiLibraries
import com.wylew.dates.util.EmojiLibrary
import com.wylew.dates.util.LibraryEmoji
import com.wylew.dates.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EmojiLibrary.description: String
    get() = when (this) {
        EmojiLibrary.SYSTEM -> "Your phone's built-in emoji font"
        EmojiLibrary.FLUENT -> "Microsoft's animated 3D emoji, with skin tones"
        EmojiLibrary.MONA -> "Mona emoji font"
        EmojiLibrary.OPENMOJI -> "Open-source colour emoji"
        EmojiLibrary.SEGOE -> "Microsoft Segoe UI emoji (SVG)"
    }

// Emojis shown as a preview of each library's style
private val PREVIEW_EMOJIS = listOf("🎂", "🎉", "🚀", "😀", "❤️")

/**
 * Shows each emoji library's install status and lets the user pick the default library, which the
 * picker opens on and new and imported events use.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmojiLibraryManagerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultLibrary by AppSettings.defaultEmojiLibrary(context).collectAsState(initial = null)
    // Events that would change if existing dates switch to the newly chosen default
    var restylePrompt by remember { mutableStateOf<Pair<EmojiLibrary, List<DateEvent>>?>(null) }
    val repository = remember { DateEventRepository(context) }

    fun chooseDefault(library: EmojiLibrary) {
        if (library == defaultLibrary) return
        scope.launch {
            AppSettings.setDefaultEmojiLibrary(context, library)
            val restyled = withContext(Dispatchers.Default) {
                EmojiLibraries.restyle(context, repository.events.first(), library)
            }
            if (restyled.isNotEmpty()) restylePrompt = library to restyled
        }
    }

    val libraries by produceState<Map<EmojiLibrary, List<LibraryEmoji>>?>(null) {
        value = withContext(Dispatchers.Default) {
            EmojiLibrary.entries.associateWith { EmojiLibraries.load(context, it) }
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
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Emoji Libraries",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The default library is where the emoji picker opens, and the style new and imported dates start with.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            val loaded = libraries
            if (loaded == null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                return@Column
            }

            EmojiLibrary.entries.forEachIndexed { index, library ->
                val items = loaded.getValue(library)
                val installed = EmojiLibraries.isAvailable(context, library)
                val isDefault = library == defaultLibrary
                ListItem(
                    selected = isDefault,
                    onClick = { chooseDefault(library) },
                    enabled = installed,
                    shapes = ListItemDefaults.segmentedShapes(index, EmojiLibrary.entries.size),
                    colors = ListItemDefaults.segmentedColors(),
                    leadingContent = {
                        RadioButton(
                            selected = isDefault,
                            onClick = { chooseDefault(library) },
                            enabled = installed
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(library.description)
                            Text(
                                if (installed) "${items.size} emojis" else "Not installed — add files to ${library.assetPath}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            if (installed) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LibraryPreview(library, items)
                            }
                        }
                    },
                    trailingContent = if (isDefault) {
                        { Text("Default", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                ) {
                    Text(library.label)
                }
            }
        }
    }

    restylePrompt?.let { (library, restyled) ->
        RestyleDialog(
            library = library,
            count = restyled.size,
            onConfirm = {
                restylePrompt = null
                scope.launch {
                    // Re-read so edits made while the prompt was open aren't overwritten
                    val byId = restyled.associateBy { it.id }
                    repository.saveEvents(repository.events.first().map { byId[it.id] ?: it })
                    WidgetUpdater.updateAllWidgets(context)
                }
            },
            onDismiss = { restylePrompt = null }
        )
    }
}

@Composable
private fun RestyleDialog(
    library: EmojiLibrary,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(library.icon, contentDescription = null) },
        title = { Text("Switch existing dates to ${library.label}?") },
        text = {
            Text(
                (if (count == 1) "1 date has an emoji" else "$count dates have emojis") +
                    " that ${library.label} also has, including background patterns. Switch them to the " +
                    "${library.label} style? Emojis without an exact match stay as they are."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (count == 1) "Switch 1 date" else "Switch $count dates") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep current") }
        }
    )
}

@Composable
private fun LibraryPreview(library: EmojiLibrary, items: List<LibraryEmoji>) {
    val previews = remember(items) {
        PREVIEW_EMOJIS.mapNotNull { emoji -> items.firstOrNull { it.emoji?.replace("️", "") == emoji.replace("️", "") } }
            .ifEmpty { items.take(PREVIEW_EMOJIS.size) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        previews.forEach { item ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                EmojiTile(library, item.emoji, item.imageUri)
            }
        }
    }
}

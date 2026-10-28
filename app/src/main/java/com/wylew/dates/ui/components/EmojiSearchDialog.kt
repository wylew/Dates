package com.wylew.dates.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.util.AppSettings
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.EmojiLibraries
import com.wylew.dates.util.EmojiLibrary
import com.wylew.dates.util.EmojiSearch
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.LibraryEmoji
import com.wylew.dates.util.MonaFontManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val EmojiLibrary.icon: ImageVector
    get() = when (this) {
        EmojiLibrary.SYSTEM -> Icons.Default.Mood
        EmojiLibrary.FLUENT -> Icons.Default.AutoAwesome
        EmojiLibrary.MONA -> Icons.Default.FontDownload
        EmojiLibrary.OPENMOJI -> Icons.Default.Palette
        EmojiLibrary.SEGOE -> Icons.Default.Window
    }

/** Where a library's files go in the project when they're missing */
val EmojiLibrary.assetPath: String
    get() = when (this) {
        EmojiLibrary.SYSTEM -> ""
        EmojiLibrary.FLUENT -> "app/src/main/assets/fluent_emojis/"
        EmojiLibrary.MONA -> "app/src/main/assets/fonts/Mona.ttf"
        EmojiLibrary.OPENMOJI -> "app/src/main/assets/openmoji_emojis/"
        EmojiLibrary.SEGOE -> "app/src/main/assets/segoe_svgs/"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiSearchDialog(
    onDismissRequest: () -> Unit,
    onEmojiSelected: (emoji: String?, imageUri: String?) -> Unit
) {
    val context = LocalContext.current

    // Opens on the default library (when its assets are present)
    var selectedLibrary by remember { mutableStateOf<EmojiLibrary?>(null) }
    LaunchedEffect(Unit) {
        val default = AppSettings.currentDefaultEmojiLibrary(context)
        selectedLibrary = if (EmojiLibraries.isAvailable(context, default)) default else EmojiLibrary.SYSTEM
    }
    var showLibraryManager by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = ExpressiveCompactCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Emoji Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showLibraryManager = true }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Manage libraries",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ScrollableTabRow(
                    selectedTabIndex = selectedLibrary?.ordinal ?: 0,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    EmojiLibrary.entries.forEach { library ->
                        Tab(
                            selected = selectedLibrary == library,
                            onClick = { selectedLibrary = library },
                            text = { Text(library.label) },
                            icon = { Icon(library.icon, contentDescription = null) }
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((LocalConfiguration.current.screenHeightDp * 0.6f).dp)
            ) {
                val library = selectedLibrary
                if (library == null) {
                    CenteredLoading()
                } else if (!EmojiLibraries.isAvailable(context, library)) {
                    AssetFolderPromptCard(
                        icon = library.icon,
                        title = "${library.label} isn't installed",
                        description = "Add the library's files to this project folder and rebuild:",
                        folderPath = library.assetPath
                    )
                } else {
                    val items by produceState<List<LibraryEmoji>?>(null, library) {
                        value = withContext(Dispatchers.Default) { EmojiLibraries.load(context, library) }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = ExpressivePillShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val loaded = items
                    if (loaded == null) {
                        CenteredLoading()
                    } else {
                        val filtered = remember(loaded, searchQuery) { filterEmojis(loaded, searchQuery) }
                        // Fresh grid state (scroll position, sections) per library
                        key(library) {
                            SectionedEmojiGrid(filtered, library) { item, imageUri ->
                                onEmojiSelected(item.emoji, imageUri)
                                onDismissRequest()
                            }
                        }
                    }
                }
            }
        }
    )

    if (showLibraryManager) {
        EmojiLibraryManagerSheet(
            onDismiss = { showLibraryManager = false }
        )
    }
}

// Ranked (whole word > prefix > substring); sections keep Unicode order when not searching
private fun filterEmojis(items: List<LibraryEmoji>, query: String): List<LibraryEmoji> =
    EmojiSearch.rank(items, query, name = { it.name }, keywords = { it.keywords }, emoji = { it.emoji.orEmpty() })

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * Emoji grid split into Unicode sections, with jump chips that track the visible section.
 * Long-pressing an emoji with skin tones offers its tone variants.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionedEmojiGrid(
    items: List<LibraryEmoji>,
    library: EmojiLibrary,
    onPick: (item: LibraryEmoji, imageUri: String?) -> Unit
) {
    // groupBy keeps load order, so sections follow the Unicode emoji groups
    val sections = remember(items) { items.groupBy { it.category } }
    val headerIndexes = remember(sections) {
        var index = 0
        sections.mapValues { (_, sectionItems) -> index.also { index += sectionItems.size + 1 } }
    }
    val gridState = rememberLazyGridState()
    val currentSection by remember(headerIndexes) {
        derivedStateOf { headerIndexes.entries.lastOrNull { it.value <= gridState.firstVisibleItemIndex }?.key }
    }
    val scope = rememberCoroutineScope()
    var tonePickerFor by remember { mutableStateOf<String?>(null) }

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(sections.keys.toList()) { category ->
            FilterChip(
                selected = currentSection == category,
                onClick = { scope.launch { gridState.animateScrollToItem(headerIndexes.getValue(category)) } },
                label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                shape = ExpressivePillShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEach { (category, sectionItems) ->
            item(key = "header:$category", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                )
            }
            items(sectionItems, key = { it.id }) { item ->
                // Fixed-size wrapper so the tone dot sits on the tile, not the (wider) grid cell
                Box(modifier = Modifier.size(52.dp)) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .combinedClickable(
                                onClick = { onPick(item, item.imageUri) },
                                onLongClick = if (item.tones.isNotEmpty()) {
                                    { tonePickerFor = item.id }
                                } else null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        EmojiTile(library, item.emoji, item.imageUri)
                    }
                    if (item.tones.isNotEmpty()) {
                        // Hint that a long-press offers skin tones
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    DropdownMenu(
                        expanded = tonePickerFor == item.id,
                        onDismissRequest = { tonePickerFor = null }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.tones.forEach { tone ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            tonePickerFor = null
                                            onPick(item, tone)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmojiTile(library, item.emoji, tone)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Renders one emoji in its library's style */
@Composable
fun EmojiTile(library: EmojiLibrary, emoji: String?, imageUri: String?) {
    when {
        library == EmojiLibrary.MONA -> CustomFontEmojiView(
            emoji = emoji ?: "",
            fontFile = MonaFontManager.getFontFile(LocalContext.current),
            modifier = Modifier.size(36.dp),
            fontSizeSp = 22f
        )
        imageUri != null -> FluentEmojiView(imageUri = imageUri, modifier = Modifier.size(36.dp))
        else -> Text(text = emoji ?: "", fontSize = 24.sp)
    }
}

@Composable
private fun AssetFolderPromptCard(
    icon: ImageVector,
    title: String,
    description: String,
    folderPath: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = ExpressiveCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = ExpressivePillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

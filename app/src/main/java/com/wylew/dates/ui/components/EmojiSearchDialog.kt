package com.wylew.dates.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.util.CustomFontEmojiView
import com.wylew.dates.util.FluentEmojiManager
import com.wylew.dates.util.FluentEmojiView
import com.wylew.dates.util.FontEmojiLoader
import com.wylew.dates.util.MonaFontManager
import com.wylew.dates.util.OpenMojiManager
import com.wylew.dates.util.SegoeSvgManager

data class EmojiItem(
    val emoji: String,
    val name: String,
    val category: String,
    val keywords: List<String>
)

object EmojiRepository {
    val items = listOf(
        // Celebrations & Events
        EmojiItem("🎂", "Birthday Cake", "Celebrations", listOf("birthday", "cake", "party", "celebration", "sweet", "candle", "age")),
        EmojiItem("🎉", "Party Popper", "Celebrations", listOf("party", "popper", "confetti", "celebration", "cheer", "event", "new year")),
        EmojiItem("🥳", "Partying Face", "Celebrations", listOf("party", "face", "hat", "horn", "celebration", "happy", "joy")),
        EmojiItem("🎈", "Balloon", "Celebrations", listOf("balloon", "party", "celebration", "red", "decor")),
        EmojiItem("🎁", "Gift", "Celebrations", listOf("gift", "present", "box", "birthday", "christmas", "surprise")),
        EmojiItem("🎄", "Christmas Tree", "Celebrations", listOf("christmas", "tree", "holiday", "xmas", "winter", "december")),
        EmojiItem("🎆", "Fireworks", "Celebrations", listOf("fireworks", "sparks", "celebration", "july 4", "new year")),
        EmojiItem("🥂", "Clinking Glasses", "Celebrations", listOf("cheers", "champagne", "toast", "drink", "wedding", "party")),
        EmojiItem("💍", "Ring", "Celebrations", listOf("ring", "diamond", "wedding", "marriage", "engagement", "love")),
        EmojiItem("🎃", "Jack-o-Lantern", "Celebrations", listOf("halloween", "pumpkin", "spooky", "october")),

        // Work, Tech & Milestones
        EmojiItem("💻", "Laptop", "Work & Tech", listOf("laptop", "computer", "tech", "work", "coding", "sync", "office")),
        EmojiItem("🤝", "Handshake", "Work & Tech", listOf("meeting", "handshake", "deal", "business", "sync", "agreement")),
        EmojiItem("🏠", "House", "Work & Tech", listOf("house", "home", "rent", "mortgage", "living", "real estate")),
        EmojiItem("🎓", "Graduation Cap", "Work & Tech", listOf("graduation", "school", "degree", "college", "education", "diploma")),
        EmojiItem("🏆", "Trophy", "Work & Tech", listOf("trophy", "winner", "award", "achievement", "first", "gold")),
        EmojiItem("🚀", "Rocket", "Work & Tech", listOf("rocket", "launch", "space", "startup", "fast", "goal")),
        EmojiItem("💼", "Briefcase", "Work & Tech", listOf("briefcase", "work", "job", "office", "business")),
        EmojiItem("📝", "Memo", "Work & Tech", listOf("note", "memo", "paper", "write", "task", "todo")),
        EmojiItem("💡", "Light Bulb", "Work & Tech", listOf("idea", "bulb", "light", "bright", "smart", "brain")),
        EmojiItem("✈️", "Airplane", "Travel & Places", listOf("airplane", "flight", "travel", "vacation", "trip", "holiday", "fly")),

        // Fitness & Activities
        EmojiItem("🏋️", "Weight Lifting", "Fitness", listOf("gym", "workout", "fitness", "weights", "exercise", "lifting")),
        EmojiItem("🏃", "Running", "Fitness", listOf("run", "runner", "marathon", "jog", "sprint", "sport")),
        EmojiItem("⚽", "Soccer Ball", "Fitness", listOf("soccer", "football", "ball", "sport", "game", "match")),
        EmojiItem("🏀", "Basketball", "Fitness", listOf("basketball", "ball", "sport", "hoops", "game")),
        EmojiItem("🚴", "Biking", "Fitness", listOf("bike", "bicycle", "cycling", "ride", "sport", "outdoors")),
        EmojiItem("🧘", "Yoga", "Fitness", listOf("yoga", "meditation", "relax", "zen", "wellness", "mindfulness")),
        EmojiItem("🏖️", "Beach", "Travel & Places", listOf("beach", "vacation", "summer", "sand", "sea", "ocean", "sun")),
        EmojiItem("🏔️", "Mountain", "Travel & Places", listOf("mountain", "hike", "nature", "climb", "trip", "snow")),
        EmojiItem("🚗", "Car", "Travel & Places", listOf("car", "drive", "roadtrip", "auto", "vehicle", "travel")),
        EmojiItem("🛳️", "Ship", "Travel & Places", listOf("cruise", "ship", "boat", "ocean", "travel", "sailing")),

        // Food & Drinks
        EmojiItem("🍕", "Pizza", "Food & Drink", listOf("pizza", "food", "dinner", "slice", "cheese", "italian")),
        EmojiItem("☕", "Coffee", "Food & Drink", listOf("coffee", "cafe", "espresso", "morning", "drink", "latte")),
        EmojiItem("🍷", "Wine", "Food & Drink", listOf("wine", "drink", "glass", "dinner", "alcohol", "night")),
        EmojiItem("🍣", "Sushi", "Food & Drink", listOf("sushi", "japanese", "fish", "food", "dinner")),
        EmojiItem("🍔", "Burger", "Food & Drink", listOf("burger", "fastfood", "hamburger", "lunch", "food")),
        EmojiItem("🍦", "Ice Cream", "Food & Drink", listOf("icecream", "dessert", "sweet", "summer", "treat")),

        // Time & Health
        EmojiItem("⏰", "Alarm Clock", "Time & Health", listOf("alarm", "clock", "time", "deadline", "reminder", "morning")),
        EmojiItem("🩺", "Stethoscope", "Time & Health", listOf("doctor", "health", "hospital", "checkup", "medical")),
        EmojiItem("❤️", "Red Heart", "Symbols", listOf("heart", "love", "romance", "red", "valentine", "anniversary")),
        EmojiItem("⭐", "Star", "Symbols", listOf("star", "favorite", "gold", "shine", "milestone", "top")),
        EmojiItem("🔥", "Fire", "Symbols", listOf("fire", "hot", "streak", "flame", "lit", "popular")),
        EmojiItem("💰", "Money Bag", "Symbols", listOf("money", "cash", "finance", "pay", "dollar", "wealth"))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiSearchDialog(
    onDismissRequest: () -> Unit,
    onEmojiSelected: (emoji: String?, imageUri: String?) -> Unit
) {
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showLibraryManager by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Celebrations", "Work & Tech", "Fitness", "Travel & Places", "Food & Drink", "Time & Health", "Symbols")
    }

    val filteredSystemEmojis = remember(searchQuery, selectedCategory) {
        EmojiRepository.items.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category == selectedCategory
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val queryLower = searchQuery.trim().lowercase()
                item.name.lowercase().contains(queryLower) ||
                        item.keywords.any { it.lowercase().contains(queryLower) } ||
                        item.emoji.contains(queryLower)
            }
            matchesCategory && matchesSearch
        }
    }

    // Fluent 3D
    val fluentDownloadedItems = remember { FluentEmojiManager.getDownloadedEmojis(context) }
    val isFluentDownloaded = FluentEmojiManager.isPackDownloaded(context)

    val filteredFluentEmojis = remember(searchQuery, fluentDownloadedItems) {
        if (searchQuery.isBlank()) {
            fluentDownloadedItems
        } else {
            val queryLower = searchQuery.trim().lowercase()
            fluentDownloadedItems.filter { it.name.lowercase().contains(queryLower) }
        }
    }

    // Mona Font (loaded dynamically from TTF)
    val isMonaDownloaded = MonaFontManager.isFontDownloaded(context)
    val monaFontEmojis = remember(isMonaDownloaded) {
        if (isMonaDownloaded) FontEmojiLoader.loadEmojisFromFont(context, MonaFontManager.getFontFile(context)) else emptyList()
    }
    val filteredMonaEmojis = remember(searchQuery, monaFontEmojis) {
        if (searchQuery.isBlank()) monaFontEmojis
        else {
            val queryLower = searchQuery.trim().lowercase()
            monaFontEmojis.filter { it.emoji.contains(queryLower) || it.name.lowercase().contains(queryLower) }
        }
    }

    // OpenMoji Color
    val openMojiDownloadedItems = remember { OpenMojiManager.getDownloadedEmojis(context) }
    val isOpenMojiDownloaded = OpenMojiManager.isPackDownloaded(context)

    val filteredOpenMoji = remember(searchQuery, openMojiDownloadedItems) {
        if (searchQuery.isBlank()) {
            openMojiDownloadedItems
        } else {
            val queryLower = searchQuery.trim().lowercase()
            openMojiDownloadedItems.filter { it.name.lowercase().contains(queryLower) }
        }
    }

    // Segoe UI SVGs
    val isSegoeDownloaded = SegoeSvgManager.isPackDownloaded(context)
    val segoeSvgEmojis = remember(isSegoeDownloaded) {
        if (isSegoeDownloaded) SegoeSvgManager.getDownloadedEmojis(context) else emptyList()
    }
    val filteredSegoeEmojis = remember(searchQuery, segoeSvgEmojis) {
        if (searchQuery.isBlank()) segoeSvgEmojis
        else {
            val queryLower = searchQuery.trim().lowercase()
            segoeSvgEmojis.filter { item ->
                item.name.lowercase().contains(queryLower) ||
                        item.keywords.any { it.lowercase().contains(queryLower) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
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
                            contentDescription = "Manage Libraries",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("System") },
                        icon = { Icon(Icons.Default.Mood, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Fluent 3D") },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Mona Font") },
                        icon = { Icon(Icons.Default.FontDownload, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("OpenMoji") },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Segoe UI") },
                        icon = { Icon(Icons.Default.Window, contentDescription = null) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // System Tab
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

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = selectedCategory == category
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = category },
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

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredSystemEmojis) { item ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            onEmojiSelected(item.emoji, null)
                                            onDismissRequest()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.emoji,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // Fluent 3D Tab
                        if (isFluentDownloaded) {
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

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 52.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredFluentEmojis) { item ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                onEmojiSelected(null, item.filePath)
                                                onDismissRequest()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FluentEmojiView(
                                            imageUri = item.filePath,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            AssetFolderPromptCard(
                                icon = Icons.Default.AutoAwesome,
                                title = "Fluent Animated Pack",
                                description = "Place asset files into:",
                                folderPath = "files/fluent_emojis/"
                            )
                        }
                    }

                    2 -> {
                        // Mona Font Tab
                        if (isMonaDownloaded) {
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

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 44.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredMonaEmojis) { item ->
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                onEmojiSelected(item.emoji, "font:mona")
                                                onDismissRequest()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CustomFontEmojiView(
                                            emoji = item.emoji,
                                            fontFile = MonaFontManager.getFontFile(context),
                                            modifier = Modifier.size(36.dp),
                                            fontSizeSp = 22f
                                        )
                                    }
                                }
                            }
                        } else {
                            AssetFolderPromptCard(
                                icon = Icons.Default.FontDownload,
                                title = "Mona Font Emojis",
                                description = "Place Mona.ttf font file into:",
                                folderPath = "files/fonts/Mona.ttf"
                            )
                        }
                    }

                    3 -> {
                        // OpenMoji Tab
                        if (isOpenMojiDownloaded) {
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

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 52.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredOpenMoji) { item ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                onEmojiSelected(null, item.filePath)
                                                onDismissRequest()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FluentEmojiView(
                                            imageUri = item.filePath,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            AssetFolderPromptCard(
                                icon = Icons.Default.Palette,
                                title = "OpenMoji Color Pack",
                                description = "Place asset files into:",
                                folderPath = "files/openmoji_emojis/"
                            )
                        }
                    }

                    4 -> {
                        // Segoe UI Tab
                        if (isSegoeDownloaded) {
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

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 44.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredSegoeEmojis) { item ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                onEmojiSelected(null, item.filePath)
                                                onDismissRequest()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FluentEmojiView(
                                            imageUri = item.filePath,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            AssetFolderPromptCard(
                                icon = Icons.Default.Window,
                                title = "Microsoft Segoe UI Emoji",
                                description = "Place asset files into:",
                                folderPath = "files/segoe_svgs/"
                            )
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

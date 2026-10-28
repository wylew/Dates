package com.wylew.dates

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.wylew.dates.model.DateEvent
import com.wylew.dates.ui.DateViewModel
import com.wylew.dates.ui.ListDensity
import com.wylew.dates.ui.SortCriterion
import com.wylew.dates.ui.ThemeMode
import com.wylew.dates.ui.components.AddEventSheet
import com.wylew.dates.ui.components.CalendarImportSheet
import com.wylew.dates.ui.components.EmojiLibraryManagerSheet
import com.wylew.dates.ui.components.EventCard
import com.wylew.dates.ui.components.EventDetailsSheet
import com.wylew.dates.ui.components.ViewOptionsMenu
import com.wylew.dates.ui.theme.DatesTheme
import com.wylew.dates.util.DateUtils
import com.wylew.dates.util.FluentEmojiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.wylew.dates.ui.theme.ExpressiveCardShape

class MainActivity : ComponentActivity() {
    private val viewModel: DateViewModel by viewModels()

    // Event to open from a widget tap; state so taps arriving via onNewIntent are picked up too
    private var widgetEventId by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetEventId = intent?.getStringExtra("event_id")

        // Configure global Coil ImageLoader to support Animated WebP and Animated GIF/PNG decoding
        val animatedImageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(animatedImageLoader)
        lifecycleScope.launch(Dispatchers.IO) { FluentEmojiManager.deleteLegacyCopies(applicationContext) }

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep status/navigation bar icons readable when the in-app theme overrides the system
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme }
                )
                onDispose {}
            }

            DatesTheme(darkTheme = darkTheme) {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val events by viewModel.events.collectAsState()
                val listDensity by viewModel.listDensity.collectAsState()
                val sortCriterion by viewModel.sortCriterion.collectAsState()
                
                var showAddSheet by remember { mutableStateOf(false) }
                var showCalendarImportSheet by remember { mutableStateOf(false) }
                var selectedEventForDetails by remember { mutableStateOf<DateEvent?>(null) }
                var eventToEdit by remember { mutableStateOf<DateEvent?>(null) }

                // Handle intent extra to show event details from Widgets
                LaunchedEffect(events, widgetEventId) {
                    val eventId = widgetEventId ?: return@LaunchedEffect
                    events.find { it.id == eventId }?.let {
                        selectedEventForDetails = it
                        widgetEventId = null
                    }
                }

                var showMenu by remember { mutableStateOf(false) }
                var showDeletePastConfirm by remember { mutableStateOf(false) }
                var showEmojiLibraries by remember { mutableStateOf(false) }
                val pastEvents = remember(events) { events.filter(DateUtils::isPast) }
                var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
                BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    topBar = {
                        LargeFlexibleTopAppBar(
                            title = { Text("Dates") },
                            subtitle = {
                                Text(if (events.size == 1) "1 date" else "${events.size} dates")
                            },
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            actions = {
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        shapes = IconButtonDefaults.shapes()
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "View options")
                                    }
                                    ViewOptionsMenu(
                                        expanded = showMenu,
                                        onDismiss = { showMenu = false },
                                        sortCriterion = sortCriterion,
                                        onSortCriterion = viewModel::setSortCriterion,
                                        listDensity = listDensity,
                                        onListDensity = viewModel::setListDensity,
                                        pastEventCount = pastEvents.size,
                                        onDeletePastEvents = { showDeletePastConfirm = true },
                                        onOpenEmojiLibraries = { showEmojiLibraries = true },
                                        themeMode = themeMode,
                                        onThemeMode = viewModel::setThemeMode
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButtonMenu(
                            expanded = fabMenuExpanded,
                            button = {
                                ToggleFloatingActionButton(
                                    checked = fabMenuExpanded,
                                    onCheckedChange = { fabMenuExpanded = it }
                                ) {
                                    val icon by remember {
                                        derivedStateOf { if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add }
                                    }
                                    Icon(
                                        painter = rememberVectorPainter(icon),
                                        contentDescription = if (fabMenuExpanded) "Close menu" else "Add",
                                        modifier = Modifier.animateIcon({ checkedProgress })
                                    )
                                }
                            }
                        ) {
                            FloatingActionButtonMenuItem(
                                onClick = {
                                    fabMenuExpanded = false
                                    showCalendarImportSheet = true
                                },
                                icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                                text = { Text("Import from calendar") }
                            )
                            FloatingActionButtonMenuItem(
                                onClick = {
                                    fabMenuExpanded = false
                                    showAddSheet = true
                                },
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                text = { Text("New date") }
                            )
                        }
                    }
                ) { innerPadding ->
                    if (events.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = ExpressiveCardShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(MaterialShapes.Cookie9Sided.toShape())
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "No Countdowns Yet",
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Start tracking birthdays, vacations, milestones, or recurring deadlines.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = { showAddSheet = true },
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                        Text("Create Countdown")
                                    }
                                }
                            }
                        }
                    } else {
                        // Expressive segmented lists: upcoming and past each join into one group
                        val outerCorner = when (listDensity) {
                            ListDensity.EXPANDED -> 28.dp
                            ListDensity.COMPACT -> 20.dp
                            ListDensity.SMALL -> 16.dp
                        }
                        val innerCorner = if (listDensity == ListDensity.SMALL) 4.dp else 8.dp
                        val upcomingEvents = events.filterNot(DateUtils::isPast)
                        fun groupShape(index: Int, count: Int): RoundedCornerShape {
                            val top = if (index == 0) outerCorner else innerCorner
                            val bottom = if (index == count - 1) outerCorner else innerCorner
                            return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            itemsIndexed(upcomingEvents, key = { _, event -> event.id }) { index, event ->
                                EventCard(
                                    event = event,
                                    onClick = { selectedEventForDetails = event },
                                    density = listDensity,
                                    shape = groupShape(index, upcomingEvents.size),
                                    modifier = Modifier.animateItem()
                                )
                            }
                            if (pastEvents.isNotEmpty()) {
                                item(key = "past-header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, top = if (upcomingEvents.isEmpty()) 0.dp else 16.dp)
                                            .animateItem(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Days past",
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { showDeletePastConfirm = true },
                                            shapes = ButtonDefaults.shapes()
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                            Text("Delete all")
                                        }
                                    }
                                }
                                itemsIndexed(pastEvents, key = { _, event -> event.id }) { index, event ->
                                    EventCard(
                                        event = event,
                                        onClick = { selectedEventForDetails = event },
                                        density = listDensity,
                                        shape = groupShape(index, pastEvents.size),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }

                    if (showEmojiLibraries) {
                        EmojiLibraryManagerSheet(onDismiss = { showEmojiLibraries = false })
                    }

                    if (showDeletePastConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeletePastConfirm = false },
                            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                            title = {
                                Text(if (pastEvents.size == 1) "Delete 1 past event?" else "Delete ${pastEvents.size} past events?")
                            },
                            text = { Text("One-time events that have already happened will be permanently removed. Repeating events are kept.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeletePastConfirm = false
                                        viewModel.deletePastEvents()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeletePastConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    // Add Event Sheet
                    if (showAddSheet) {
                        AddEventSheet(
                            onDismiss = { showAddSheet = false },
                            onSave = { newEvent ->
                                viewModel.addEvent(newEvent)
                                showAddSheet = false
                            }
                        )
                    }

                    // Calendar Import Sheet
                    if (showCalendarImportSheet) {
                        CalendarImportSheet(
                            onDismiss = { showCalendarImportSheet = false },
                            onImportEvents = { importedEvents ->
                                viewModel.addEvents(importedEvents)
                                showCalendarImportSheet = false
                            }
                        )
                    }

                    // Event Details Sheet
                    selectedEventForDetails?.let { event ->
                        EventDetailsSheet(
                            event = event,
                            onDismiss = { selectedEventForDetails = null },
                            onEdit = { 
                                eventToEdit = event
                                selectedEventForDetails = null
                            },
                            onDelete = {
                                viewModel.deleteEvent(event.id)
                                selectedEventForDetails = null
                            }
                        )
                    }

                    // Edit Event Sheet
                    eventToEdit?.let { event ->
                        AddEventSheet(
                            eventToEdit = event,
                            onDismiss = { eventToEdit = null },
                            onSave = { updatedEvent ->
                                viewModel.updateEvent(updatedEvent)
                                eventToEdit = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("event_id")?.let { widgetEventId = it }
    }
}

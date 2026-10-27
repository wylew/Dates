package com.wylew.dates

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.wylew.dates.model.DateEvent
import com.wylew.dates.ui.DateViewModel
import com.wylew.dates.ui.SortCriterion
import com.wylew.dates.ui.ThemeMode
import com.wylew.dates.ui.components.AddEventSheet
import com.wylew.dates.ui.components.CalendarImportSheet
import com.wylew.dates.ui.components.EventCard
import com.wylew.dates.ui.components.EventDetailsSheet
import com.wylew.dates.ui.theme.DatesTheme
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape

class MainActivity : ComponentActivity() {
    private val viewModel: DateViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DatesTheme(darkTheme = darkTheme) {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val events by viewModel.events.collectAsState()
                val isCompactMode by viewModel.isCompactMode.collectAsState()
                val sortCriterion by viewModel.sortCriterion.collectAsState()
                
                var showAddSheet by remember { mutableStateOf(false) }
                var showCalendarImportSheet by remember { mutableStateOf(false) }
                var selectedEventForDetails by remember { mutableStateOf<DateEvent?>(null) }
                var eventToEdit by remember { mutableStateOf<DateEvent?>(null) }

                // Handle intent extra to show event details from Widgets
                LaunchedEffect(events) {
                    val eventId = intent?.getStringExtra("event_id")
                    if (eventId != null && selectedEventForDetails == null) {
                        events.find { it.id == eventId }?.let {
                            selectedEventForDetails = it
                            intent.removeExtra("event_id")
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    topBar = {
                        LargeTopAppBar(
                            title = { 
                                Text(
                                    "Dates",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold
                                ) 
                            },
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            actions = {
                                FilterChip(
                                    selected = false,
                                    onClick = { showCalendarImportSheet = true },
                                    label = { 
                                        Text("Import") 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.CalendarMonth,
                                            contentDescription = "Import from Calendar",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = ExpressivePillShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        val nextCriterion = when (sortCriterion) {
                                            SortCriterion.NEXT_OCCURRENCE -> SortCriterion.ALPHA
                                            SortCriterion.ALPHA -> SortCriterion.NEXT_OCCURRENCE
                                        }
                                        viewModel.setSortCriterion(nextCriterion)
                                    },
                                    label = { 
                                        Text(
                                            when (sortCriterion) {
                                                SortCriterion.NEXT_OCCURRENCE -> "Days Left"
                                                SortCriterion.ALPHA -> "Alphabetical"
                                            }
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sort order",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = ExpressivePillShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                val nextThemeMode = when (themeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.SYSTEM
                                }
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.setThemeMode(nextThemeMode) },
                                    label = { 
                                        Text(
                                            when (themeMode) {
                                                ThemeMode.SYSTEM -> "System"
                                                ThemeMode.LIGHT -> "Light"
                                                ThemeMode.DARK -> "Dark"
                                            }
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            when (themeMode) {
                                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                                ThemeMode.DARK -> Icons.Default.DarkMode
                                            },
                                            contentDescription = "Toggle theme",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = ExpressivePillShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                FilterChip(
                                    selected = isCompactMode,
                                    onClick = { viewModel.setCompactMode(!isCompactMode) },
                                    label = { 
                                        Text(if (isCompactMode) "Compact" else "Expanded") 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isCompactMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                            contentDescription = "Toggle view mode",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = ExpressivePillShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddSheet = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = ExpressiveCardShape
                        ) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = "Add Event",
                                modifier = Modifier.size(20.dp)
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
                                            .clip(CircleShape)
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
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Start tracking birthdays, vacations, milestones, or recurring deadlines.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = { showAddSheet = true },
                                        shape = ExpressivePillShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Countdown", style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactMode) 8.dp else 12.dp)
                        ) {
                            items(
                                items = events,
                                key = { it.id }
                            ) { event ->
                                EventCard(
                                    event = event,
                                    onClick = { selectedEventForDetails = event },
                                    isCompact = isCompactMode,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
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
    }
}

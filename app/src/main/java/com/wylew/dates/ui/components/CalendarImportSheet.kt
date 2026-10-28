package com.wylew.dates.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Recurrence
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.util.CalendarIntegrationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarImportSheet(
    onDismiss: () -> Unit,
    onImportEvents: (List<DateEvent>) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var calendars by remember { mutableStateOf<List<CalendarIntegrationHelper.SystemCalendar>>(emptyList()) }
    var selectedCalendar by remember { mutableStateOf<CalendarIntegrationHelper.SystemCalendar?>(null) }
    var calendarEvents by remember { mutableStateOf<List<CalendarIntegrationHelper.SystemCalendarEvent>>(emptyList()) }
    var selectedEventIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoadingEvents by remember { mutableStateOf(false) }
    // Set when some chosen events are past one-offs; holds the rest awaiting confirmation
    var pendingImport by remember { mutableStateOf<List<DateEvent>?>(null) }
    var skippedPastCount by remember { mutableIntStateOf(0) }

    fun requestImport(sysEvents: List<CalendarIntegrationHelper.SystemCalendarEvent>) {
        val today = LocalDate.now()
        val (pastOneOffs, toImport) = sysEvents
            .map { CalendarIntegrationHelper.convertToDateEvent(it) }
            .partition { it.recurrence == Recurrence.None && (it.endDateTime ?: it.dateTime).toLocalDate().isBefore(today) }
        if (pastOneOffs.isEmpty()) {
            onImportEvents(toImport)
        } else {
            skippedPastCount = pastOneOffs.size
            pendingImport = toImport
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            calendars = CalendarIntegrationHelper.getSystemCalendars(context)
        }
    }

    LaunchedEffect(selectedCalendar) {
        selectedCalendar?.let { cal ->
            isLoadingEvents = true
            val events = CalendarIntegrationHelper.getEventsForCalendar(context, cal.id)
            calendarEvents = events
            selectedEventIds = events.asSequence().map { it.id }.toSet()
            isLoadingEvents = false
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
                .fillMaxHeight(0.85f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedCalendar != null) {
                        IconButton(onClick = { selectedCalendar = null }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calendars")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = selectedCalendar?.displayName ?: "Import System Calendars",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                // Permission Request State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = ExpressiveCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Calendar Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dates needs access to your system calendars (Google account, Exchange, local) to let you browse and import events and sub-calendars.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            } else if (selectedCalendar == null) {
                // Calendar List State
                if (calendars.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No system calendars found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Select a sub-calendar to view and import events:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        itemsIndexed(calendars, key = { _, cal -> cal.id }) { index, cal ->
                            ListItem(
                                onClick = { selectedCalendar = cal },
                                shapes = ListItemDefaults.segmentedShapes(index, calendars.size),
                                colors = ListItemDefaults.segmentedColors(),
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(cal.color))
                                    )
                                },
                                supportingContent = { Text(cal.accountName) }
                            ) {
                                Text(cal.displayName)
                            }
                        }
                    }
                }
            } else {
                // Events List State
                if (isLoadingEvents) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                } else if (calendarEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No events found in this calendar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${calendarEvents.size} events found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick bulk action buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { selectedEventIds = calendarEvents.asSequence().map { it.id }.toSet() }
                                ) {
                                    Text("Select All")
                                }
                                TextButton(
                                    onClick = { selectedEventIds = emptySet() }
                                ) {
                                    Text("Deselect All")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bulk Import All Button
                        Button(
                            onClick = {
                                requestImport(calendarEvents)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Import All (${calendarEvents.size} Events)")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            itemsIndexed(calendarEvents, key = { _, ev -> ev.id }) { index, ev ->
                                val isSelected = selectedEventIds.contains(ev.id)
                                val startDt = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(ev.dtstart),
                                    ZoneId.systemDefault()
                                )
                                val endDt = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(ev.dtend),
                                    ZoneId.systemDefault()
                                )
                                val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

                                val toggle = {
                                    selectedEventIds = if (isSelected) selectedEventIds - ev.id else selectedEventIds + ev.id
                                }
                                val dateStr = if (ev.allDay) {
                                    "${startDt.format(dateFormatter)} (All Day)"
                                } else {
                                    "${startDt.format(dateFormatter)} ${startDt.format(timeFormatter)} - ${endDt.format(timeFormatter)}"
                                }
                                ListItem(
                                    selected = isSelected,
                                    onClick = toggle,
                                    shapes = ListItemDefaults.segmentedShapes(index, calendarEvents.size),
                                    colors = ListItemDefaults.segmentedColors(),
                                    leadingContent = {
                                        Checkbox(checked = isSelected, onCheckedChange = { toggle() })
                                    },
                                    supportingContent = {
                                        Column {
                                            Text(dateStr)
                                            if (ev.description.isNotBlank()) {
                                                Text(ev.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                ) {
                                    Text(ev.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Selected Button
                        Button(
                            onClick = {
                                requestImport(calendarEvents.filter { selectedEventIds.contains(it.id) })
                            },
                            enabled = selectedEventIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text("Import Selected (${selectedEventIds.size} Events)")
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { remaining ->
        val skippedLabel = if (skippedPastCount == 1) "1 event has" else "$skippedPastCount events have"
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            shape = ExpressiveCompactCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("Skip past events?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (remaining.isEmpty()) {
                        "$skippedLabel already happened and won't repeat, so there's nothing to import."
                    } else {
                        "$skippedLabel already happened and won't repeat, so they'll be skipped. " +
                            "Import the remaining ${remaining.size}?"
                    }
                )
            },
            confirmButton = {
                if (remaining.isEmpty()) {
                    TextButton(onClick = { pendingImport = null }) { Text("OK") }
                } else {
                    TextButton(onClick = {
                        pendingImport = null
                        onImportEvents(remaining)
                    }) { Text("Import ${remaining.size}") }
                }
            },
            dismissButton = if (remaining.isEmpty()) null else {
                { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
            }
        )
    }
}

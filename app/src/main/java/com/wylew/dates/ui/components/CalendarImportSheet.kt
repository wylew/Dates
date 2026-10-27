package com.wylew.dates.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.util.CalendarIntegrationHelper
import com.wylew.dates.util.DateUtils
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
                        IconButton(onClick = { selectedCalendar = null }) {
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
                                shape = ExpressivePillShape
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(calendars, key = { it.id }) { cal ->
                            Surface(
                                shape = ExpressiveCardShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCalendar = cal }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(cal.color))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cal.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cal.accountName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Events List State
                val calColor = selectedCalendar!!.color
                if (isLoadingEvents) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                                val dateEvents = calendarEvents.map {
                                    CalendarIntegrationHelper.convertToDateEvent(it, calColor)
                                }
                                onImportEvents(dateEvents)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressivePillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import All (${calendarEvents.size} Events)", style = MaterialTheme.typography.titleSmall)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(calendarEvents, key = { it.id }) { ev ->
                                val isSelected = selectedEventIds.contains(ev.id)
                                val startDt = CalendarIntegrationHelper.toLocal(ev.displayStart, ev.allDay)
                                val endDt = CalendarIntegrationHelper.toLocal(ev.displayStart + ev.durationMillis, ev.allDay)
                                val repeatLabel = remember(ev.id, ev.rrule, ev.displayStart) {
                                    when {
                                        !ev.isRecurring -> null
                                        ev.seriesEnded -> "Series ended"
                                        else -> DateUtils.describe(CalendarIntegrationHelper.parseRRule(ev.rrule, startDt))
                                    }
                                }
                                val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

                                Surface(
                                    shape = ExpressiveCardShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedEventIds = if (isSelected) {
                                                selectedEventIds - ev.id
                                            } else {
                                                selectedEventIds + ev.id
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedEventIds = if (checked) {
                                                    selectedEventIds + ev.id
                                                } else {
                                                    selectedEventIds - ev.id
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ev.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val whenStr = if (ev.allDay) {
                                                "${startDt.format(dateFormatter)} (All Day)"
                                            } else {
                                                "${startDt.format(dateFormatter)} ${startDt.format(timeFormatter)} - ${endDt.format(timeFormatter)}"
                                            }
                                            val dateStr = when {
                                                repeatLabel == null -> whenStr
                                                ev.nextInstanceStart != null -> "Next: $whenStr · $repeatLabel"
                                                else -> "$whenStr · $repeatLabel"
                                            }
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (ev.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = ev.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Selected Button
                        Button(
                            onClick = {
                                val eventsToImport = calendarEvents
                                    .filter { selectedEventIds.contains(it.id) }
                                    .map { CalendarIntegrationHelper.convertToDateEvent(it, calColor) }
                                onImportEvents(eventsToImport)
                            },
                            enabled = selectedEventIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressivePillShape
                        ) {
                            Text("Import Selected (${selectedEventIds.size} Events)", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

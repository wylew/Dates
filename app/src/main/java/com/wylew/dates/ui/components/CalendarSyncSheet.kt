package com.wylew.dates.ui.components

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract
import android.text.format.DateUtils as AndroidDateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wylew.dates.sync.CalendarSync
import com.wylew.dates.sync.SyncCalendar
import com.wylew.dates.sync.SyncTarget
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CREATE_CALENDAR_URL = "https://calendar.google.com/calendar/r/settings/createcalendar"

/**
 * Turns two-way sync with a calendar on and off. Picking a calendar of its own ("Dates") keeps
 * dates apart from everyday events; it's made on the web, since apps can't create calendars in a
 * Google account.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarSyncSheet(dateCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val target by remember { CalendarSync.target(context) }.collectAsState(initial = null)
    val lastSync by remember { CalendarSync.lastSync(context) }.collectAsState(initial = null)
    val status by CalendarSync.status.collectAsState()

    var hasPermission by remember { mutableStateOf(CalendarSync.hasPermission(context)) }
    var calendars by remember { mutableStateOf<List<SyncCalendar>?>(null) }
    var confirmCalendar by remember { mutableStateOf<SyncCalendar?>(null) }
    var confirmStop by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = CalendarSync.hasPermission(context)
    }

    suspend fun loadCalendars() {
        calendars = withContext(Dispatchers.IO) { CalendarSync.writableCalendars(context) }
    }

    // Coming back from the browser after making a calendar: look again
    LifecycleResumeEffect(hasPermission) {
        hasPermission = CalendarSync.hasPermission(context)
        if (hasPermission) scope.launch { loadCalendars() }
        onPauseOrDispose {}
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ExpressiveSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item {
                Text("Calendar sync", style = MaterialTheme.typography.headlineMediumEmphasized)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keep your dates in a calendar of your Google account. They're backed up with it, show up " +
                        "on your other devices, and events you add on calendar.google.com appear here too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            val current = target
            when {
                !hasPermission -> item {
                    Button(
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        },
                        shapes = ButtonDefaults.shapes()
                    ) { Text("Allow calendar access") }
                }

                current != null -> item {
                    SyncStatusCard(
                        target = current,
                        running = status.running,
                        error = status.error,
                        lastSync = lastSync,
                        onSyncNow = { CalendarSync.request(context, fromGoogle = true) },
                        onStop = { confirmStop = true },
                        onOpenWeb = { uriHandler.openUri("https://calendar.google.com") }
                    )
                }

                else -> {
                    item {
                        Surface(
                            shape = ExpressiveCompactCardShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Tip: give dates a calendar of their own", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Create a calendar named “Dates” on the web, then pick it below. It can take a " +
                                        "minute to reach your phone.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { uriHandler.openUri(CREATE_CALENDAR_URL) }, shapes = ButtonDefaults.shapes()) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Create a calendar")
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        ) {
                            Text(
                                "Choose a calendar",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    refreshAccounts(calendars.orEmpty())
                                    scope.launch { loadCalendars() }
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh calendars") }
                        }
                    }
                    val list = calendars
                    if (list == null) {
                        item { LoadingIndicator() }
                    } else if (list.isEmpty()) {
                        item {
                            Text(
                                "No calendars you can add events to. Create one on the web, then refresh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        itemsIndexed(list, key = { _, cal -> cal.target.calendarId }) { index, cal ->
                            ListItem(
                                onClick = { confirmCalendar = cal },
                                shapes = ListItemDefaults.segmentedShapes(index, list.size),
                                colors = ListItemDefaults.segmentedColors(),
                                leadingContent = {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(cal.color)))
                                },
                                supportingContent = {
                                    Text(
                                        if (cal.isPrimary) "${cal.target.accountName} · main calendar, all its events would be added"
                                        else cal.target.accountName
                                    )
                                }
                            ) { Text(cal.target.displayName) }
                        }
                    }
                }
            }
        }
    }

    confirmCalendar?.let { cal ->
        AlertDialog(
            onDismissRequest = { confirmCalendar = null },
            icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
            title = { Text("Sync with ${cal.target.displayName}?") },
            text = {
                Text(
                    buildString {
                        append(if (dateCount == 1) "Your date will be added to " else "Your $dateCount dates will be added to ")
                        append("${cal.target.displayName}, and its events will be added to Dates. ")
                        append("From then on, new dates, edits and deletions go both ways.")
                        if (cal.isPrimary) append("\n\nThis is your main calendar, so every event in it, meetings included, will become a date.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCalendar = null
                    scope.launch { CalendarSync.enable(context, cal.target) }
                }) { Text("Start syncing") }
            },
            dismissButton = { TextButton(onClick = { confirmCalendar = null }) { Text("Cancel") } }
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            icon = { Icon(Icons.Default.SyncDisabled, contentDescription = null) },
            title = { Text("Stop syncing?") },
            text = { Text("Dates and the calendar both keep their events; they just stop updating each other.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    scope.launch { CalendarSync.disable(context) }
                }) { Text("Stop syncing") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncStatusCard(
    target: SyncTarget,
    running: Boolean,
    error: String?,
    lastSync: CalendarSync.LastSync?,
    onSyncNow: () -> Unit,
    onStop: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Surface(
        shape = ExpressiveCompactCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Syncing with ${target.displayName}", style = MaterialTheme.typography.titleMedium)
                    Text(target.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            val statusText = when {
                running -> "Syncing…"
                lastSync != null -> "Last synced ${
                    AndroidDateUtils.getRelativeTimeSpanString(lastSync.millis, System.currentTimeMillis(), AndroidDateUtils.MINUTE_IN_MILLIS)
                        .toString().lowercase()
                        .let { if (it == "0 minutes ago" || it == "in 0 minutes") "just now" else it }
                } · ${lastSync.summary}"
                else -> "Waiting for the first sync"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSyncNow, enabled = !running, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Sync now")
                }
                OutlinedButton(onClick = onStop, shapes = ButtonDefaults.shapes()) { Text("Stop") }
                if (running) LoadingIndicator(Modifier.size(32.dp))
            }
            TextButton(onClick = onOpenWeb, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Open Google Calendar")
            }
        }
    }
}

/** Asks the accounts to fetch their calendars now, so one just made on the web shows up sooner */
private fun refreshAccounts(calendars: List<SyncCalendar>) {
    val extras = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
    }
    calendars.map { it.target }.filterNot { it.isLocal }.distinctBy { it.accountName to it.accountType }.forEach {
        runCatching { ContentResolver.requestSync(Account(it.accountName, it.accountType), CalendarContract.AUTHORITY, extras) }
    }
}

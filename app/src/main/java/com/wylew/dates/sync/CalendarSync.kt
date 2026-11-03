package com.wylew.dates.sync

import android.Manifest
import android.accounts.Account
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.AppSettings
import com.wylew.dates.util.CalendarIntegrationHelper
import com.wylew.dates.util.EmojiLibraries
import com.wylew.dates.util.EmojiLibrary
import com.wylew.dates.util.TitleEmoji
import com.wylew.dates.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_sync")

/**
 * The calendar dates sync with. Row ids differ between devices, so it's also known by account and
 * name; a restored backup finds the same calendar on a new phone that way.
 */
@Serializable
data class SyncTarget(val calendarId: Long, val accountName: String, val accountType: String, val displayName: String) {
    val isLocal: Boolean get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
}

/** A calendar the app can sync with: writable and synced to its account */
data class SyncCalendar(
    val target: SyncTarget,
    val color: Int,
    /** The account's main calendar, which holds all of its everyday events */
    val isPrimary: Boolean
)

/**
 * Keeps the app's dates and one calendar the same, both ways (see [CalendarSyncPlanner] for how).
 * Syncs when the app opens, after every change in the app, and in the background when the
 * calendar changes (Google's own sync brings web edits down to the phone, then this job runs).
 */
object CalendarSync {
    private const val TAG = "CalendarSync"
    // WorkManager (used by the widgets) numbers its jobs up from 0, so stay far above its range
    private const val JOB_ID = 2_041_200_001
    private const val REQUEST_DELAY_MS = 600L

    private val TARGET = stringPreferencesKey("target")
    private val DELETED = stringSetPreferencesKey("deleted_ids")
    private val LAST_SYNC = longPreferencesKey("last_sync")
    private val LAST_SUMMARY = stringPreferencesKey("last_summary")

    data class Status(val running: Boolean = false, val error: String? = null)

    data class LastSync(val millis: Long, val summary: String)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = AtomicBoolean(false)

    fun target(context: Context): Flow<SyncTarget?> = context.syncDataStore.data.map { prefs ->
        prefs[TARGET]?.let { runCatching { Json.decodeFromString(SyncTarget.serializer(), it) }.getOrNull() }
    }

    suspend fun currentTarget(context: Context): SyncTarget? = target(context).first()

    fun lastSync(context: Context): Flow<LastSync?> = context.syncDataStore.data.map { prefs ->
        prefs[LAST_SYNC]?.let { LastSync(it, prefs[LAST_SUMMARY].orEmpty()) }
    }

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Calendars dates can sync with: ones the phone syncs and the account can add events to */
    fun writableCalendars(context: Context): List<SyncCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val result = mutableListOf<SyncCalendar>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val access = c.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL) ?: 0
                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    if (c.int(CalendarContract.Calendars.SYNC_EVENTS) == 0) continue
                    val account = c.string(CalendarContract.Calendars.ACCOUNT_NAME) ?: continue
                    val name = c.string(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME) ?: account
                    result += SyncCalendar(
                        target = SyncTarget(
                            calendarId = c.long(CalendarContract.Calendars._ID) ?: continue,
                            accountName = account,
                            accountType = c.string(CalendarContract.Calendars.ACCOUNT_TYPE).orEmpty(),
                            displayName = name
                        ),
                        color = c.int(CalendarContract.Calendars.CALENDAR_COLOR) ?: 0,
                        // Android marks every phone-only calendar primary; only an account's main calendar matters here
                        isPrimary = c.string(CalendarContract.Calendars.ACCOUNT_TYPE) != CalendarContract.ACCOUNT_TYPE_LOCAL &&
                            (c.int(CalendarContract.Calendars.IS_PRIMARY) == 1 || name == account)
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Couldn't list calendars", it) }
        // Google calendars first, then by account and name
        return result.sortedWith(
            compareBy<SyncCalendar>({ it.target.accountType != "com.google" }, { it.target.accountName }, { it.target.displayName.lowercase() })
        )
    }

    /** Starts syncing with [calendar]: every date is matched with its events from scratch */
    suspend fun enable(context: Context, calendar: SyncTarget) {
        DateEventRepository(context).update { events -> events.map { it.copy(syncFingerprint = null) } }
        context.syncDataStore.edit {
            it[TARGET] = Json.encodeToString(SyncTarget.serializer(), calendar)
            it.remove(DELETED)
            it.remove(LAST_SYNC)
            it.remove(LAST_SUMMARY)
        }
        _status.value = Status()
        scheduleWatch(context)
        request(context)
    }

    /** Stops syncing. The app and the calendar both keep their events. */
    suspend fun disable(context: Context) {
        mutex.withLock {
            context.syncDataStore.edit {
                it.remove(TARGET)
                it.remove(DELETED)
                it.remove(LAST_SYNC)
                it.remove(LAST_SUMMARY)
            }
            DateEventRepository(context).update { events -> events.map { it.copy(syncFingerprint = null) } }
        }
        cancelWatch(context)
        _status.value = Status()
    }

    /** Remembers dates deleted in the app, so the next sync deletes their calendar events too */
    suspend fun markDeleted(context: Context, ids: Collection<String>) {
        if (ids.isEmpty() || currentTarget(context) == null) return
        context.syncDataStore.edit { it[DELETED] = it[DELETED].orEmpty() + ids }
    }

    /**
     * Syncs soon, in the background; requests that arrive together run once. [fromGoogle] also asks
     * the account to fetch changes made elsewhere (the calendar job picks those up when they land).
     */
    fun request(context: Context, fromGoogle: Boolean = false) {
        val app = context.applicationContext
        if (fromGoogle) scope.launch { currentTarget(app)?.let { requestAccountSync(it) } }
        if (pending.getAndSet(true)) return
        scope.launch {
            delay(REQUEST_DELAY_MS)
            pending.set(false)
            syncNow(app)
        }
    }

    /**
     * A sync the person asked for (pull to refresh): also asks the account to fetch changes from
     * the web, and returns once the sync is done. False if sync is off or it couldn't finish.
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = currentTarget(context) ?: return@withContext false
        requestAccountSync(target)
        syncNow(context.applicationContext)
    }

    /** One full sync; false if sync is off or it couldn't finish */
    suspend fun syncNow(context: Context): Boolean = mutex.withLock {
        val saved = currentTarget(context) ?: return false
        if (!hasPermission(context)) {
            _status.value = Status(error = "Dates needs calendar access to sync. Allow it to continue.")
            return false
        }
        _status.update { it.copy(running = true) }
        try {
            val target = resolve(context, saved) ?: run {
                _status.value = Status(error = "“${saved.displayName}” isn't on this phone anymore. Choose a calendar again.")
                return false
            }
            if (target != saved) context.syncDataStore.edit { it[TARGET] = Json.encodeToString(SyncTarget.serializer(), target) }

            val repository = DateEventRepository(context)
            val before = repository.events.first()
            val deleted = context.syncDataStore.data.first()[DELETED].orEmpty()
            val styler = AppEmojiStyler(context, AppSettings.currentDefaultEmojiLibrary(context))
            val planner = CalendarSyncPlanner(styler, target.calendarId)

            val rows = readRows(context, target.calendarId)
            val plan = planner.plan(before, rows, deleted)
            val wrote = write(context, target, plan)
            val finals = planner.finals(if (wrote) readRows(context, target.calendarId) else rows, deleted)
            val changed = repository.update { current -> planner.merge(current, before, finals, plan.removeFromApp) }

            context.syncDataStore.edit {
                it[DELETED] = it[DELETED].orEmpty() - deleted
                it[LAST_SYNC] = System.currentTimeMillis()
                it[LAST_SUMMARY] = summary(plan, deleted.size)
            }
            if (wrote) requestAccountSync(target)
            if (changed) WidgetUpdater.updateAllWidgets(context)
            _status.value = Status()
            Log.d(TAG, "Synced ${target.displayName}: ${summary(plan, deleted.size)}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            _status.value = Status(error = "Couldn't sync: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    private fun summary(plan: SyncPlan, deletedInApp: Int): String {
        val parts = buildList {
            if (plan.sent > 0) add("${plan.sent} sent")
            if (plan.received > 0) add("${plan.received} received")
            val removed = plan.removeFromApp.size + deletedInApp
            if (removed > 0) add("$removed deleted")
            if (plan.restored > 0) add("${plan.restored} restored")
        }
        return if (parts.isEmpty()) "Up to date" else parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    /** [saved], found again by account and name if its row id now points elsewhere */
    private fun resolve(context: Context, saved: SyncTarget): SyncTarget? {
        val calendars = writableCalendars(context).map { it.target }
        fun same(t: SyncTarget) = t.accountName == saved.accountName && t.accountType == saved.accountType && t.displayName == saved.displayName
        return calendars.firstOrNull { it.calendarId == saved.calendarId && same(it) } ?: calendars.firstOrNull(::same)
    }

    private fun readRows(context: Context, calendarId: Long): List<CalendarRow> =
        CalendarIntegrationHelper.queryEvents(context, calendarId).map { e ->
            CalendarRow(
                id = e.id,
                title = e.title,
                description = e.description,
                dtstart = e.dtstart,
                dtend = e.dtend,
                allDay = e.allDay,
                rrule = e.rrule,
                uid = e.uid,
                colorKey = e.colorKey,
                endedAt = if (e.seriesEnded) e.lastInstanceStart else null
            )
        }

    /** Applies [plan]'s writes; true if anything was written */
    private fun write(context: Context, target: SyncTarget, plan: SyncPlan): Boolean {
        if (plan.ops.isEmpty()) return false
        val resolver = context.contentResolver
        val colors = eventColors(context, target)
        var wrote = false
        for (op in plan.ops) {
            when (op) {
                is SyncOp.Insert -> {
                    val values = values(op.write, colors, null).apply { put(Events.CALENDAR_ID, target.calendarId) }
                    resolver.insert(Events.CONTENT_URI, values) ?: error("the calendar didn't accept “${op.write.title}”")
                    wrote = true
                }
                is SyncOp.Update -> {
                    val values = values(op.write, colors, op.currentColorKey)
                    if (values.size() > 0) {
                        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, op.rowId), values, null, null)
                        wrote = true
                    }
                }
                is SyncOp.Delete -> {
                    resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, op.rowId), null, null)
                    wrote = true
                }
            }
        }
        return wrote
    }

    private fun values(write: RowWrite, colors: Map<String, Int>, currentColorKey: String?) = ContentValues().apply {
        write.title?.let { put(Events.TITLE, it) }
        write.description?.let { put(Events.DESCRIPTION, it) }
        write.schedule?.let { s ->
            put(Events.DTSTART, s.dtstart)
            put(Events.ALL_DAY, if (s.allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, s.timezone)
            // Repeating events have a duration instead of an end
            if (s.rrule != null) {
                put(Events.RRULE, s.rrule)
                put(Events.DURATION, durationOf(s))
                putNull(Events.DTEND)
            } else {
                putNull(Events.RRULE)
                putNull(Events.DURATION)
                put(Events.DTEND, s.dtstart + s.durationMillis)
            }
        }
        write.color?.let { nearestColorKey(colors, it) }?.takeIf { it != currentColorKey }?.let { put(Events.EVENT_COLOR_KEY, it) }
    }

    private fun durationOf(s: Schedule): String =
        if (s.allDay) "P${(s.durationMillis / DAY_MILLIS).coerceAtLeast(1)}D" else "P${s.durationMillis / 1000}S"

    /** The account's event colors (Google offers 11), by key */
    private fun eventColors(context: Context, target: SyncTarget): Map<String, Int> {
        val colors = mutableMapOf<String, Int>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
                "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND ${CalendarContract.Colors.ACCOUNT_TYPE} = ? AND " +
                    "${CalendarContract.Colors.COLOR_TYPE} = ${CalendarContract.Colors.TYPE_EVENT}",
                arrayOf(target.accountName, target.accountType),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.string(CalendarContract.Colors.COLOR_KEY) ?: continue
                    colors[key] = c.int(CalendarContract.Colors.COLOR) ?: continue
                }
            }
        }.onFailure { Log.w(TAG, "Couldn't read event colors", it) }
        return colors
    }

    private fun nearestColorKey(colors: Map<String, Int>, argb: Int): String? = colors.minByOrNull { (_, color) ->
        val dr = ((color shr 16) and 0xFF) - ((argb shr 16) and 0xFF)
        val dg = ((color shr 8) and 0xFF) - ((argb shr 8) and 0xFF)
        val db = (color and 0xFF) - (argb and 0xFF)
        // Weighted for how strongly each channel reads to the eye
        2 * dr * dr + 4 * dg * dg + 3 * db * db
    }?.key

    /** Asks the account to send and fetch calendar changes now rather than whenever it next syncs */
    private fun requestAccountSync(target: SyncTarget) {
        if (target.isLocal) return
        runCatching {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(Account(target.accountName, target.accountType), CalendarContract.AUTHORITY, extras)
        }.onFailure { Log.w(TAG, "Couldn't request account sync", it) }
    }

    /** Runs a sync whenever calendar data changes, even with the app closed */
    fun scheduleWatch(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarSyncJobService::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(3_000)
            .setTriggerContentMaxDelay(30_000)
            .build()
        runCatching { context.getSystemService(JobScheduler::class.java).schedule(job) }
            .onFailure { Log.w(TAG, "Couldn't schedule the calendar watch", it) }
    }

    private fun cancelWatch(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    private fun Cursor.index(column: String): Int? = getColumnIndex(column).takeIf { it >= 0 }
    private fun Cursor.long(column: String): Long? = index(column)?.takeIf { !isNull(it) }?.let { getLong(it) }
    private fun Cursor.int(column: String): Int? = index(column)?.takeIf { !isNull(it) }?.let { getInt(it) }
    private fun Cursor.string(column: String): String? = index(column)?.takeIf { !isNull(it) }?.let { getString(it) }
}

/** The app's emoji libraries, for reading and drawing emoji in synced titles */
private class AppEmojiStyler(private val context: Context, override val defaultLibrary: EmojiLibrary) : EmojiStyler {
    override fun titleEmoji(title: String): TitleEmoji.Match? = EmojiLibraries.emojiInTitle(context, title)
    override fun imageFor(emoji: String, library: EmojiLibrary): String? = EmojiLibraries.exactMatch(context, library, emoji)?.second
}

/** Syncs after calendar data changes (an edit on the web arriving, another app's change), then watches again */
class CalendarSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                CalendarSync.syncNow(applicationContext)
            } finally {
                jobFinished(params, false)
                if (CalendarSync.currentTarget(applicationContext) != null) CalendarSync.scheduleWatch(applicationContext)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

/** Content-change jobs don't survive a restart, so the watch is set up again after boot */
class CalendarSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (CalendarSync.currentTarget(context) != null) CalendarSync.scheduleWatch(context)
            } finally {
                result.finish()
            }
        }
    }
}

package com.wylew.dates.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.AppSettings
import com.wylew.dates.util.DateUtils
import com.wylew.dates.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class SortCriterion {
    ALPHA, NEXT_OCCURRENCE
}

enum class ListDensity {
    EXPANDED, COMPACT, SMALL
}

class DateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DateEventRepository(application)

    /**
     * A view option saved in AppSettings so it survives restarts. The first value is read
     * up front so the app opens with the saved theme and layout instead of flashing the defaults.
     */
    private inline fun <reified E : Enum<E>> savedSetting(key: String, default: E): StateFlow<E> {
        val flow = AppSettings.enumSetting(getApplication(), key, default)
        return flow.stateIn(viewModelScope, SharingStarted.Eagerly, runBlocking { flow.first() })
    }

    private fun <E : Enum<E>> save(key: String, value: E) {
        viewModelScope.launch { AppSettings.setEnumSetting(getApplication(), key, value) }
    }

    val sortCriterion: StateFlow<SortCriterion> = savedSetting(SORT_KEY, SortCriterion.NEXT_OCCURRENCE)
    val listDensity: StateFlow<ListDensity> = savedSetting(DENSITY_KEY, ListDensity.COMPACT)
    val themeMode: StateFlow<ThemeMode> = savedSetting(THEME_KEY, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) = save(THEME_KEY, mode)

    fun setSortCriterion(criterion: SortCriterion) = save(SORT_KEY, criterion)

    fun setListDensity(density: ListDensity) = save(DENSITY_KEY, density)

    // Upcoming events first, then past one-time events (most recent first when sorting by date)
    val events: StateFlow<List<DateEvent>> = combine(repository.events, sortCriterion) { eventList, criterion ->
        val (past, upcoming) = eventList.partition(DateUtils::isPast)
        when (criterion) {
            SortCriterion.ALPHA -> upcoming.sortedBy { it.title } + past.sortedBy { it.title }
            SortCriterion.NEXT_OCCURRENCE ->
                upcoming.sortedBy { DateUtils.getNextOccurrence(it) } + past.sortedByDescending { it.dateTime }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.events.collect {
                WidgetUpdater.updateAllWidgets(getApplication())
            }
        }
    }

    fun addEvent(event: DateEvent) {
        viewModelScope.launch {
            val current = events.value.toMutableList()
            current.add(event)
            repository.saveEvents(current)
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    /** Adds [newEvents] and replaces [updated] (matched by id) in a single save */
    fun importEvents(newEvents: List<DateEvent>, updated: List<DateEvent>) {
        viewModelScope.launch {
            val byId = updated.associateBy { it.id }
            repository.saveEvents(events.value.map { byId[it.id] ?: it } + newEvents)
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun addEvents(newEvents: List<DateEvent>) {
        viewModelScope.launch {
            val current = events.value.toMutableList()
            current.addAll(newEvents)
            repository.saveEvents(current)
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun updateEvent(updatedEvent: DateEvent) {
        viewModelScope.launch {
            val current = events.value.map {
                if (it.id == updatedEvent.id) updatedEvent else it
            }
            repository.saveEvents(current)
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            val current = events.value.filter { it.id != eventId }
            repository.saveEvents(current)
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun deletePastEvents() {
        viewModelScope.launch {
            repository.saveEvents(events.value.filterNot(DateUtils::isPast))
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val SORT_KEY = "sort_criterion"
        const val DENSITY_KEY = "list_density"
    }
}

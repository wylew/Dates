package com.wylew.dates.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.DateUtils
import com.wylew.dates.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _sortCriterion = MutableStateFlow(SortCriterion.NEXT_OCCURRENCE)
    val sortCriterion: StateFlow<SortCriterion> = _sortCriterion.asStateFlow()

    // Upcoming events first, then past one-time events (most recent first when sorting by date)
    val events: StateFlow<List<DateEvent>> = combine(repository.events, _sortCriterion) { eventList, criterion ->
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

    private val _listDensity = MutableStateFlow(ListDensity.COMPACT)
    val listDensity: StateFlow<ListDensity> = _listDensity.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setSortCriterion(criterion: SortCriterion) {
        _sortCriterion.value = criterion
    }

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

    fun setListDensity(density: ListDensity) {
        _listDensity.value = density
    }

    fun deletePastEvents() {
        viewModelScope.launch {
            repository.saveEvents(events.value.filterNot(DateUtils::isPast))
            WidgetUpdater.updateAllWidgets(getApplication())
        }
    }
}

package com.wylew.dates.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import com.wylew.dates.util.DateUtils
import com.wylew.dates.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class SortCriterion {
    ALPHA, NEXT_OCCURRENCE
}

class DateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DateEventRepository(application)

    private val _sortCriterion = MutableStateFlow(SortCriterion.NEXT_OCCURRENCE)
    val sortCriterion: StateFlow<SortCriterion> = _sortCriterion.asStateFlow()

    val events: StateFlow<List<DateEvent>> = combine(repository.events, _sortCriterion) { eventList, criterion ->
        when (criterion) {
            SortCriterion.ALPHA -> eventList.sortedBy { it.title }
            SortCriterion.NEXT_OCCURRENCE -> eventList.sortedBy { DateUtils.getNextOccurrence(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isCompactMode = MutableStateFlow(true)
    val isCompactMode: StateFlow<Boolean> = _isCompactMode.asStateFlow()

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
            repository.events.collect { currentEvents ->
                if (currentEvents.isEmpty()) {
                    initializeSampleData()
                }
                WidgetUpdater.updateAllWidgets(getApplication())
            }
        }
    }

    private suspend fun initializeSampleData() {
        val now = LocalDateTime.now()
        val sampleEvents = listOf(
            DateEvent(
                title = "Birthday",
                dateTime = now.plusMonths(4),
                recurrence = Recurrence.Yearly,
                emoji = "🎂",
                customColor = Color(0xFFF50057) // Bold Magenta
            ),
            DateEvent(
                title = "Weekly Meeting",
                dateTime = now.plusDays(2),
                recurrence = Recurrence.Weekly,
                emoji = "🤝",
                customColor = Color(0xFF2979FF) // Deep Cobalt
            ),
            DateEvent(
                title = "Monthly Rent",
                dateTime = now.plusDays(5),
                recurrence = Recurrence.Monthly,
                emoji = "🏠",
                customColor = Color(0xFFFF6D00) // Sunset Orange
            ),
            DateEvent(
                title = "Tech Sync",
                dateTime = now.plusDays(1),
                recurrence = Recurrence.Custom(
                    frequency = Frequency.MONTHLY,
                    interval = 1,
                    dayOfWeek = 4, // Thursday
                    weekOfMonth = 1 // First
                ),
                emoji = "💻",
                customColor = Color(0xFF00C853) // Emerald Green
            )
        )
        repository.saveEvents(sampleEvents)
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

    fun setCompactMode(enabled: Boolean) {
        _isCompactMode.value = enabled
    }
}

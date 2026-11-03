package com.wylew.dates.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.Frequency
import com.wylew.dates.model.Recurrence
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ORDINALS = listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", -1 to "Last")
private val WEEKDAYS = (1..7).map { it to DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
private val FREQUENCIES = listOf(Frequency.DAILY to "Day", Frequency.WEEKLY to "Week", Frequency.MONTHLY to "Month", Frequency.YEARLY to "Year")
private const val MAX_INTERVAL = 99

/** A sensible starting custom rule for [start]: every week on its weekday */
fun defaultCustomRecurrence(start: LocalDateTime) =
    Recurrence.Custom(Frequency.WEEKLY, interval = 1, daysOfWeek = listOf(start.dayOfWeek.value))

/** Which nth weekday of its month [date] is, as a [Recurrence.Custom.weekOfMonth] (1-4, -1 for the last) */
private fun weekOfMonthOf(date: LocalDateTime): Int {
    val nth = (date.dayOfMonth - 1) / 7 + 1
    return if (nth >= 5) -1 else nth
}

/**
 * Standard calendar "custom repeat" options: every N days/weeks/months/years; for weeks, any set of
 * weekdays ("every Tue and Thu"); for months and years, a day of the month or an nth weekday
 * ("the 1st Thursday", "the last Friday"). [start] is the event's date, which months and years
 * take their day (and month) from.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomRecurrenceEditor(
    recurrence: Recurrence.Custom,
    start: LocalDateTime,
    onChange: (Recurrence.Custom) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthly = recurrence.frequency == Frequency.MONTHLY || recurrence.frequency == Frequency.YEARLY
    val onNthWeekday = monthly && recurrence.weekOfMonth != null

    Surface(
        shape = ExpressiveCompactCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Every N <unit>
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = { onChange(recurrence.copy(interval = (recurrence.interval - 1).coerceAtLeast(1))) },
                    enabled = recurrence.interval > 1,
                    shapes = IconButtonDefaults.shapes()
                ) { Icon(Icons.Default.Remove, contentDescription = "Fewer") }
                Text(
                    recurrence.interval.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 36.dp)
                )
                FilledTonalIconButton(
                    onClick = { onChange(recurrence.copy(interval = (recurrence.interval + 1).coerceAtMost(MAX_INTERVAL))) },
                    enabled = recurrence.interval < MAX_INTERVAL,
                    shapes = IconButtonDefaults.shapes()
                ) { Icon(Icons.Default.Add, contentDescription = "More") }
            }
            ConnectedChoices(
                options = FREQUENCIES.map { (freq, label) -> freq to (if (recurrence.interval > 1) "${label}s" else label) },
                selected = recurrence.frequency,
                onSelect = { onChange(customFor(it, recurrence.interval, start)) }
            )

            when (recurrence.frequency) {
                Frequency.WEEKLY -> {
                    Text("On", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val days = DateUtils.weeklyDays(recurrence, start)
                    // Multi-select: any combination of weekdays, at least one
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        WEEKDAYS.forEachIndexed { index, (day, label) ->
                            ToggleButton(
                                checked = day in days,
                                onCheckedChange = { checked ->
                                    val next = if (checked) days + day else days - day
                                    if (next.isNotEmpty()) onChange(recurrence.copy(dayOfWeek = null, daysOfWeek = next.sorted()))
                                },
                                shapes = connectedShapes(index, WEEKDAYS.size),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text(label.take(3), maxLines = 1) }
                        }
                    }
                }

                Frequency.MONTHLY, Frequency.YEARLY -> {
                    val monthName = start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    val dayOption = if (recurrence.frequency == Frequency.YEARLY) "On $monthName ${start.dayOfMonth}" else "On day ${start.dayOfMonth}"
                    val nthOption = if (recurrence.frequency == Frequency.YEARLY) "On the … of $monthName" else "On the …"
                    ConnectedChoices(
                        options = listOf(false to dayOption, true to nthOption),
                        selected = onNthWeekday,
                        onSelect = { nth ->
                            val base = customFor(recurrence.frequency, recurrence.interval, start)
                            onChange(
                                if (nth) base.copy(dayOfMonth = null, weekOfMonth = weekOfMonthOf(start), dayOfWeek = start.dayOfWeek.value)
                                else base
                            )
                        }
                    )
                    if (onNthWeekday) {
                        ConnectedChoices(
                            options = ORDINALS,
                            selected = recurrence.weekOfMonth,
                            onSelect = { onChange(recurrence.copy(weekOfMonth = it)) }
                        )
                        ConnectedChoices(
                            options = WEEKDAYS.map { (day, label) -> day to label.take(3) },
                            selected = recurrence.dayOfWeek ?: start.dayOfWeek.value,
                            onSelect = { onChange(recurrence.copy(dayOfWeek = it)) },
                            compact = true
                        )
                    }
                }

                Frequency.DAILY -> Unit
            }

            // What the rule means, and when it next happens
            val next = DateUtils.getNextOccurrence(DateEvent(title = "", dateTime = start, recurrence = recurrence))
            Text(
                "${DateUtils.describe(recurrence)} · next ${next.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** The plain rule for [frequency]: weekly on the start's weekday; monthly/yearly on its date */
private fun customFor(frequency: Frequency, interval: Int, start: LocalDateTime) = when (frequency) {
    Frequency.DAILY -> Recurrence.Custom(Frequency.DAILY, interval)
    Frequency.WEEKLY -> Recurrence.Custom(Frequency.WEEKLY, interval, daysOfWeek = listOf(start.dayOfWeek.value))
    Frequency.MONTHLY -> Recurrence.Custom(Frequency.MONTHLY, interval, dayOfMonth = start.dayOfMonth)
    Frequency.YEARLY -> Recurrence.Custom(Frequency.YEARLY, interval, dayOfMonth = start.dayOfMonth, monthOfYear = start.monthValue)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(index: Int, count: Int) = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/** Single-choice connected toggle buttons, scrolling sideways if they don't fit */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoices(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                shapes = connectedShapes(index, options.size),
                contentPadding = if (compact) PaddingValues(horizontal = 10.dp) else ButtonDefaults.ContentPadding,
                modifier = Modifier.semantics { role = Role.RadioButton }
            ) { Text(label, maxLines = 1) }
        }
    }
}

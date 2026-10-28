package com.wylew.dates.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget

/** Per-widget options, stored in each widget's Glance preferences state */
enum class WidgetTheme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class ListLayout(val label: String) { STANDARD("Standard"), COMPACT("Compact"), SMALL("Small") }

/** Row sizing for each [ListLayout]; Small uses a one-line counter pill to keep rows thin */
data class RowStyle(val bubbleSize: Dp, val padding: Dp, val corner: Dp, val showDate: Boolean, val pillCounter: Boolean)

val ListLayout.rowStyle: RowStyle
    get() = when (this) {
        ListLayout.STANDARD -> RowStyle(bubbleSize = 44.dp, padding = 8.dp, corner = 20.dp, showDate = true, pillCounter = false)
        ListLayout.COMPACT -> RowStyle(bubbleSize = 36.dp, padding = 6.dp, corner = 18.dp, showDate = false, pillCounter = false)
        ListLayout.SMALL -> RowStyle(bubbleSize = 28.dp, padding = 4.dp, corner = 14.dp, showDate = false, pillCounter = true)
    }

val SELECTED_EVENT_ID_KEY = stringPreferencesKey("selected_event_id")
val WIDGET_THEME_KEY = stringPreferencesKey("widget_theme")
val LIST_LAYOUT_KEY = stringPreferencesKey("list_layout")

fun Preferences.widgetTheme(): WidgetTheme =
    this[WIDGET_THEME_KEY]?.let { name -> WidgetTheme.entries.find { it.name == name } } ?: WidgetTheme.SYSTEM

fun Preferences.listLayout(): ListLayout =
    this[LIST_LAYOUT_KEY]?.let { name -> ListLayout.entries.find { it.name == name } } ?: ListLayout.STANDARD

/** Which kind of widget a receiver provides, so the settings screen can show the relevant options */
enum class WidgetKind(val receiverClass: Class<*>, val createWidget: () -> GlanceAppWidget) {
    LIST(DateEventsWidgetReceiver::class.java, ::DateEventsWidget),
    NEXT(NextEventWidgetReceiver::class.java, { NextEventWidget() }),
    NEXT_SQUARE(NextEventSquareWidgetReceiver::class.java, ::NextEventSquareWidget),
    SELECTED(SelectedEventWidgetReceiver::class.java, { SelectedEventWidget() }),
    SELECTED_SQUARE(SelectedEventSquareWidgetReceiver::class.java, ::SelectedEventSquareWidget);

    val picksEvent get() = this == SELECTED || this == SELECTED_SQUARE

    /** Widgets drawn as list rows, which offer the Standard/Compact/Small styles */
    val hasRowLayout get() = this == LIST || this == NEXT || this == SELECTED

    companion object {
        fun fromReceiver(className: String?) = entries.find { it.receiverClass.name == className }
    }
}

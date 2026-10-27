package com.wylew.dates.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        DateEventsWidget().updateAll(context)
        NextEventWidget().updateAll(context)
        SelectedEventWidget().updateAll(context)
    }
}

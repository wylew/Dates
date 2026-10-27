package com.wylew.dates.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.wylew.dates.MainActivity
import com.wylew.dates.model.DateEventRepository
import kotlinx.coroutines.flow.first

class SelectedEventWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val eventId = prefs[stringPreferencesKey("selected_event_id")]
        
        val repository = DateEventRepository(context)
        val events = repository.events.first()
        val selectedEvent = events.find { it.id == eventId }

        provideContent {
            val modifier = if (selectedEvent != null) {
                GlanceModifier.clickable(
                    actionStartActivity<MainActivity>(
                        parameters = actionParametersOf(
                            ActionParameters.Key<String>("event_id") to selectedEvent.id
                        )
                    )
                )
            } else {
                GlanceModifier.clickable(actionStartActivity<MainActivity>())
            }

            if (selectedEvent == null) {
                DateWidgetUtils.EmptyState(
                    message = "Tap to select an event",
                    modifier = modifier
                )
            } else {
                DateWidgetUtils.SingleEventCard(
                    event = selectedEvent,
                    modifier = modifier
                )
            }
        }
    }
}

class SelectedEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelectedEventWidget()
}

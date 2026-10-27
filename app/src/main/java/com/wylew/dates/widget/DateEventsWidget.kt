package com.wylew.dates.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wylew.dates.MainActivity
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.DateUtils
import kotlinx.coroutines.flow.first

class DateEventsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DateEventRepository(context)
        val events = repository.events.first()
            .sortedBy { DateUtils.getNextOccurrence(it) }

        provideContent {
            // Using a very light gray/off-white for the list container background
            val containerBg = Color(0xFFF8F9FA)
            
            DateWidgetUtils.WidgetContainer(
                backgroundColor = containerBg,
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
            ) {
                if (events.isEmpty()) {
                    Text("No upcoming events", style = TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(events) { event ->
                            DateWidgetUtils.EventItem(
                                event = event, 
                                isCompact = true,
                                modifier = GlanceModifier.clickable(
                                    actionStartActivity<MainActivity>(
                                        parameters = androidx.glance.action.actionParametersOf(
                                            androidx.glance.action.ActionParameters.Key<String>("event_id") to event.id
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class DateEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateEventsWidget()
}

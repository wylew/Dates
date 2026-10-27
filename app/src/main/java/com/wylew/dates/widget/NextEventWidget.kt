package com.wylew.dates.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import com.wylew.dates.MainActivity
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.DateUtils
import kotlinx.coroutines.flow.first

class NextEventWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DateEventRepository(context)
        val nextEvent = repository.events.first()
            .sortedBy { DateUtils.getNextOccurrence(it) }
            .firstOrNull { DateUtils.getNextOccurrence(it).isAfter(java.time.LocalDateTime.now()) }

        provideContent {
            if (nextEvent == null) {
                DateWidgetUtils.WidgetContainer(
                    backgroundColor = androidx.compose.ui.graphics.Color.White,
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                ) {
                    Text("No upcoming events")
                }
            } else {
                DateWidgetUtils.SingleEventCard(
                    event = nextEvent,
                    modifier = GlanceModifier.clickable(
                        actionStartActivity<MainActivity>(
                            parameters = actionParametersOf(
                                ActionParameters.Key<String>("event_id") to nextEvent.id
                            )
                        )
                    )
                )
            }
        }
    }
}

class NextEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventWidget()
}

package com.wylew.dates.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wylew.dates.MainActivity
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.EmojiPatternRenderer
import kotlinx.coroutines.flow.first

private const val MAX_LIST_EVENTS = 20

// Patterns are rendered at reduced resolution to stay within the widget bitmap memory budget
private const val LIST_PATTERN_SCALE = 1.25f

/** Widget 1: scrolling list of upcoming dates, in standard, compact, or small rows */
class DateEventsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DateEventRepository(context)
        val initialEvents = repository.events.first()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val density = context.resources.displayMetrics.density

        provideContent {
            val allEvents by repository.events.collectAsState(initialEvents)
            val prefs = currentState<Preferences>()
            val theme = prefs.widgetTheme()
            val style = prefs.listLayout().rowStyle
            val events = remember(allEvents) { DateWidgetUtils.upcomingEvents(allEvents).take(MAX_LIST_EVENTS) }

            if (events.isEmpty()) {
                DateWidgetUtils.EmptyState(
                    message = "No upcoming dates",
                    theme = theme,
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
                return@provideContent
            }

            val rowHeight = style.bubbleSize + style.padding * 2
            val rowWidth = LocalSize.current.width - 20.dp

            val palettes = events.associate { it.id to DateWidgetUtils.paletteFor(context, it, theme) }
            val emojiBitmaps = remember(events, theme) {
                events.associate { event ->
                    event.id to WidgetEmojiRenderer.render(
                        context, event, (40 * density).toInt(), palettes.getValue(event.id).glyphColor
                    )
                }
            }
            val patternBitmaps = remember(events, theme, rowWidth, rowHeight) {
                events.associate { event ->
                    event.id to EmojiPatternRenderer.renderBitmap(
                        context, event,
                        (rowWidth.value * LIST_PATTERN_SCALE).toInt(), (rowHeight.value * LIST_PATTERN_SCALE).toInt(),
                        LIST_PATTERN_SCALE, palettes.getValue(event.id).glyphColor
                    )
                }
            }

            val palette = DateWidgetUtils.neutralPalette(context, theme)
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(palette.background)
                    .cornerRadius(24.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.content),
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())
                    )
                    Text(
                        text = "⚙",
                        style = TextStyle(fontSize = 16.sp, color = palette.subContent),
                        modifier = GlanceModifier.clickable(openWidgetSettingsAction(context, appWidgetId))
                    )
                }
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(events, itemId = { it.id.hashCode().toLong() }) { event ->
                        val eventPalette = palettes.getValue(event.id)
                        Box(modifier = GlanceModifier.padding(vertical = if (style.showDate) 3.dp else 2.dp)) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .background(eventPalette.background)
                                    .cornerRadius(style.corner)
                                    .clickable(
                                        actionStartActivity<MainActivity>(
                                            actionParametersOf(ActionParameters.Key<String>("event_id") to event.id)
                                        )
                                    )
                            ) {
                                DateWidgetUtils.PatternLayer(patternBitmaps[event.id])
                                Box(
                                    modifier = GlanceModifier.fillMaxSize().padding(style.padding),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    DateWidgetUtils.EventRowContent(
                                        event, emojiBitmaps[event.id], eventPalette,
                                        style
                                    )
                                }
                            }
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

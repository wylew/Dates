package com.wylew.dates.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.wylew.dates.MainActivity
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.util.EmojiPatternRenderer
import kotlinx.coroutines.flow.first

// Below this height a Standard/Compact row doesn't fit, so row widgets switch to Small
private val SMALL_ROW_MAX_HEIGHT = 60.dp

/** Opens the widget settings screen for one widget; unique data keeps each widget's PendingIntent distinct */
fun openWidgetSettingsAction(context: Context, appWidgetId: Int): Action = actionStartIntent(
    Intent(context, WidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("dates://widget/configure/$appWidgetId"))
)

/** Shared rendering for widgets that show one event, as a list-style row card or a square card */
abstract class SingleEventWidget(private val square: Boolean) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    protected abstract fun pickEvent(prefs: Preferences, events: List<DateEvent>): DateEvent?

    /** Shown when there is no event; tapping it runs [emptyAction] */
    protected abstract val emptyMessage: String
    protected open fun emptyAction(context: Context, appWidgetId: Int): Action =
        actionStartActivity<MainActivity>()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DateEventRepository(context)
        val initialEvents = repository.events.first()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val density = context.resources.displayMetrics.density

        provideContent {
            // Read settings and events here (not above) so saved changes show up on the next update
            val events by repository.events.collectAsState(initialEvents)
            val prefs = currentState<Preferences>()
            val theme = prefs.widgetTheme()
            val event = pickEvent(prefs, events)

            if (event == null) {
                DateWidgetUtils.EmptyState(emptyMessage, theme, GlanceModifier.clickable(emptyAction(context, appWidgetId)))
                return@provideContent
            }

            val palette = DateWidgetUtils.paletteFor(context, event, theme)
            val size = LocalSize.current
            val emojiBitmap = remember(event, palette.glyphColor) {
                val emojiSizeDp = if (square) 72 else 40
                WidgetEmojiRenderer.render(context, event, (emojiSizeDp * density).toInt(), palette.glyphColor)
            }
            val patternBitmap = remember(event, size, palette.glyphColor) {
                val scale = minOf(density, 2f)
                EmojiPatternRenderer.renderBitmap(
                    context, event, (size.width.value * scale).toInt(), (size.height.value * scale).toInt(),
                    scale, palette.glyphColor
                )
            }
            val openEvent = GlanceModifier.clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(ActionParameters.Key<String>("event_id") to event.id)
                )
            )
            if (square) {
                DateWidgetUtils.EventSquareCard(event, emojiBitmap, patternBitmap, palette, min(size.width, size.height), openEvent)
            } else {
                // Fall back to the thin Small row when the widget is resized too short for the chosen style
                val layout = if (size.height < SMALL_ROW_MAX_HEIGHT) ListLayout.SMALL else prefs.listLayout()
                DateWidgetUtils.EventRowCard(event, emojiBitmap, patternBitmap, palette, layout, openEvent)
            }
        }
    }
}

/** Widgets 2 & 4: the soonest upcoming date */
open class NextEventWidget(square: Boolean = false) : SingleEventWidget(square) {
    override val emptyMessage = "No upcoming dates"

    override fun pickEvent(prefs: Preferences, events: List<DateEvent>) =
        DateWidgetUtils.upcomingEvents(events).firstOrNull()
}

class NextEventSquareWidget : NextEventWidget(square = true)

/** Widgets 3 & 4: an event the user picked in [WidgetConfigurationActivity] */
open class SelectedEventWidget(square: Boolean = false) : SingleEventWidget(square) {
    override val emptyMessage = "Tap to choose a date"

    override fun pickEvent(prefs: Preferences, events: List<DateEvent>): DateEvent? {
        val eventId = prefs[SELECTED_EVENT_ID_KEY]
        return events.find { it.id == eventId }
    }

    // Reopen the picker so a widget whose event was deleted can be pointed at another one
    override fun emptyAction(context: Context, appWidgetId: Int) = openWidgetSettingsAction(context, appWidgetId)
}

class SelectedEventSquareWidget : SelectedEventWidget(square = true)

class NextEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventWidget()
}

class NextEventSquareWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventSquareWidget()
}

class SelectedEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelectedEventWidget()
}

class SelectedEventSquareWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelectedEventSquareWidget()
}

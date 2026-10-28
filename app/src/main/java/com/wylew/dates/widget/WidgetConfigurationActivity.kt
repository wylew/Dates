package com.wylew.dates.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.wylew.dates.model.DateEvent
import com.wylew.dates.model.DateEventRepository
import com.wylew.dates.ui.theme.DatesTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings for a single widget instance: theme for all widgets, row style for the list widget,
 * and which event to show for the selected-event widgets. Opened when a widget is added or
 * reconfigured, or from the widget itself.
 */
class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val kind = WidgetKind.fromReceiver(
            AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) {
            finish()
            return
        }

        setContent {
            DatesTheme {
                WidgetSettingsScreen(kind)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun WidgetSettingsScreen(kind: WidgetKind) {
        var events by remember { mutableStateOf(emptyList<DateEvent>()) }
        var prefs by remember { mutableStateOf<Preferences?>(null) }
        var theme by remember { mutableStateOf(WidgetTheme.SYSTEM) }
        var listLayout by remember { mutableStateOf(ListLayout.STANDARD) }
        var selectedEventId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            events = DateEventRepository(applicationContext).events.first()
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
            val current = getAppWidgetState(this@WidgetConfigurationActivity, PreferencesGlanceStateDefinition, glanceId)
            theme = current.widgetTheme()
            listLayout = current.listLayout()
            selectedEventId = current[SELECTED_EVENT_ID_KEY]
            prefs = current
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Widget Settings") }) },
            bottomBar = {
                Button(
                    onClick = { save(kind, theme, listLayout, selectedEventId) },
                    enabled = prefs != null && (!kind.picksEvent || events.any { it.id == selectedEventId }),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .heightIn(min = ButtonDefaults.MediumContainerHeight)
                ) {
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SectionTitle("Appearance")
                    ChoiceRow(WidgetTheme.entries, theme, { it.label }) { theme = it }
                    Text(
                        "System follows your phone's light/dark setting, separate from the app's theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (kind.hasRowLayout) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionTitle("List Style")
                        ChoiceRow(ListLayout.entries, listLayout, { it.label }) { listLayout = it }
                        Text(
                            if (kind == WidgetKind.LIST) {
                                "Compact hides the date to fit more events. Small is a thin row with a one-line counter."
                            } else {
                                "Compact hides the date. Small is a thin row with a one-line counter, and is used automatically when the widget is resized very short."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                if (kind.picksEvent) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionTitle("Event")
                    }
                    itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                        val isSelected = event.id == selectedEventId
                        ListItem(
                            selected = isSelected,
                            onClick = { selectedEventId = event.id },
                            shapes = ListItemDefaults.segmentedShapes(index, events.size),
                            colors = ListItemDefaults.segmentedColors(),
                            leadingContent = {
                                RadioButton(selected = isSelected, onClick = { selectedEventId = event.id })
                            },
                            modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap)
                        ) {
                            Text(event.title)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    /** Connected toggle button group (Material 3 Expressive replacement for segmented buttons) */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton }
                ) {
                    Text(label(option))
                }
            }
        }
    }

    private fun save(kind: WidgetKind, theme: WidgetTheme, listLayout: ListLayout, selectedEventId: String?) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigurationActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(WIDGET_THEME_KEY, theme.name)
                    set(LIST_LAYOUT_KEY, listLayout.name)
                    if (selectedEventId != null) set(SELECTED_EVENT_ID_KEY, selectedEventId)
                }
            }
            kind.createWidget().update(this@WidgetConfigurationActivity, glanceId)

            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

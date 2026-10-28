package com.wylew.dates.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wylew.dates.ui.ListDensity
import com.wylew.dates.ui.SortCriterion
import com.wylew.dates.ui.ThemeMode

private data class MenuOption(val label: String, val icon: ImageVector, val selected: Boolean, val onSelect: () -> Unit)

/** Expressive grouped menu for sorting, list density, and theme */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortCriterion: SortCriterion,
    onSortCriterion: (SortCriterion) -> Unit,
    listDensity: ListDensity,
    onListDensity: (ListDensity) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    pastEventCount: Int,
    onDeletePastEvents: () -> Unit
) {
    val groups = listOf(
        "Sort by" to listOf(
            MenuOption("Days left", Icons.AutoMirrored.Filled.Sort, sortCriterion == SortCriterion.NEXT_OCCURRENCE) {
                onSortCriterion(SortCriterion.NEXT_OCCURRENCE)
            },
            MenuOption("Alphabetical", Icons.Default.SortByAlpha, sortCriterion == SortCriterion.ALPHA) {
                onSortCriterion(SortCriterion.ALPHA)
            }
        ),
        "Layout" to listOf(
            MenuOption("Expanded", Icons.Default.GridView, listDensity == ListDensity.EXPANDED) { onListDensity(ListDensity.EXPANDED) },
            MenuOption("Compact", Icons.AutoMirrored.Filled.ViewList, listDensity == ListDensity.COMPACT) { onListDensity(ListDensity.COMPACT) },
            MenuOption("Small", Icons.Default.ViewHeadline, listDensity == ListDensity.SMALL) { onListDensity(ListDensity.SMALL) }
        ),
        "Theme" to listOf(
            MenuOption("System", Icons.Default.BrightnessAuto, themeMode == ThemeMode.SYSTEM) { onThemeMode(ThemeMode.SYSTEM) },
            MenuOption("Light", Icons.Default.LightMode, themeMode == ThemeMode.LIGHT) { onThemeMode(ThemeMode.LIGHT) },
            MenuOption("Dark", Icons.Default.DarkMode, themeMode == ThemeMode.DARK) { onThemeMode(ThemeMode.DARK) }
        )
    )

    val groupCount = groups.size + 1 // plus the past-events action group
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        groups.forEachIndexed { groupIndex, (title, options) ->
            if (groupIndex > 0) Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groupCount)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                options.forEachIndexed { index, option ->
                    SelectableDropdownMenuItem(
                        selected = option.selected,
                        onClick = option.onSelect,
                        text = { Text(option.label) },
                        shapes = MenuDefaults.itemShape(index, options.size),
                        leadingIcon = { Icon(option.icon, contentDescription = null) },
                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupCount - 1, groupCount)) {
            DropdownMenuItem(
                onClick = {
                    onDismiss()
                    onDeletePastEvents()
                },
                text = { Text(if (pastEventCount > 0) "Delete past events ($pastEventCount)" else "Delete past events") },
                shape = MenuDefaults.itemShape(0, 1).shape,
                enabled = pastEventCount > 0,
                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
            )
        }
    }
}

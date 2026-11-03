package com.wylew.dates.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape

/** Something the app uses, with its copyright holder and license */
private data class Credit(
    val name: String,
    val holder: String,
    val license: String,
    val url: String,
    /** Full license text in assets/licenses, or null when there's no license to show */
    val licenseFile: String? = null,
    /** Extra context shown under the license (attribution text, notes) */
    val note: String? = null
)

private val THIS_APP = listOf(
    Credit(
        name = "Dates",
        holder = "© 2026 Wyatt Lewin",
        license = "Apache License 2.0",
        url = "https://github.com/wylew/Dates",
        licenseFile = "apache-2.0.txt",
        note = "Open source. The emoji artwork, data and libraries below keep their own licenses."
    )
)

private val EMOJI_ARTWORK = listOf(
    Credit(
        name = "Fluent Emoji",
        holder = "© Microsoft Corporation",
        license = "MIT License",
        url = "https://github.com/microsoft/fluentui-emoji",
        licenseFile = "fluentui-emoji-mit.txt",
        note = "3D emoji images, resized for the app."
    ),
    Credit(
        name = "OpenMoji",
        holder = "© OpenMoji contributors, HfG Schwäbisch Gmünd",
        license = "CC BY-SA 4.0",
        url = "https://openmoji.org",
        licenseFile = "cc-by-sa-4.0.txt",
        note = "All emojis designed by OpenMoji – the open-source emoji and icon project. License: CC BY-SA 4.0."
    ),
    Credit(
        name = "Mona12 Color Emoji",
        holder = "© 2025 Monad ABXY",
        license = "SIL Open Font License 1.1",
        url = "https://monadabxy.com/",
        licenseFile = "mona12-ofl-1.1.txt"
    ),
    Credit(
        name = "Segoe UI Emoji",
        holder = "© Microsoft Corporation",
        license = "Proprietary (not open source)",
        url = "https://learn.microsoft.com/typography/font-list/segoe-ui-emoji",
        note = "Segoe UI Emoji is part of Windows and isn't released under an open-source license."
    )
)

private val EMOJI_DATA = listOf(
    Credit(
        name = "Unicode Emoji & CLDR data",
        holder = "© Unicode, Inc.",
        license = "Unicode License v3",
        url = "https://www.unicode.org/emoji/",
        licenseFile = "unicode-license-v3.txt",
        note = "Emoji names, groups and keywords."
    ),
    Credit(
        name = "Emojibase",
        holder = "© Miles Johnson",
        license = "MIT License",
        url = "https://emojibase.dev",
        licenseFile = "emojibase-mit.txt",
        note = "Emoji keyword data used to build the Segoe UI emoji index."
    )
)

private const val APACHE = "Apache License 2.0"
private const val APACHE_FILE = "apache-2.0.txt"

private val LIBRARIES = listOf(
    Credit("Android Jetpack (AndroidX)", "© The Android Open Source Project", APACHE, "https://developer.android.com/jetpack/androidx", APACHE_FILE,
        "Core, Activity, Lifecycle, DataStore, Palette and Emoji2."),
    Credit("Jetpack Compose & Material 3", "© The Android Open Source Project", APACHE, "https://developer.android.com/compose", APACHE_FILE,
        "Compose UI, Foundation, Material 3 and Material Icons."),
    Credit("Jetpack Glance", "© The Android Open Source Project", APACHE, "https://developer.android.com/jetpack/androidx/releases/glance", APACHE_FILE,
        "Home screen widgets."),
    Credit("Kotlin, Coroutines & Serialization", "© JetBrains s.r.o. and Kotlin contributors", APACHE, "https://kotlinlang.org", APACHE_FILE),
    Credit("Coil", "© Coil Contributors", APACHE, "https://github.com/coil-kt/coil", APACHE_FILE, "Image loading, including GIF and SVG."),
    Credit("Lottie for Android", "© Airbnb, Inc.", APACHE, "https://github.com/airbnb/lottie-android", APACHE_FILE),
    Credit("AndroidSVG", "© Paul LeBeau, Cave Rock Software Ltd.", APACHE, "https://github.com/BigBadaboom/androidsvg", APACHE_FILE),
    Credit("APNG4Android", "© penfeizhou", APACHE, "https://github.com/penfeizhou/APNG4Android", APACHE_FILE)
)

/** App version, and credits and licenses for the emoji artwork, data and libraries the app uses */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var openCredit by remember { mutableStateOf<Credit?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        shape = ExpressiveSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item {
                Text("Dates", style = MaterialTheme.typography.headlineMediumEmphasized)
                if (version != null) {
                    Text(
                        "Version $version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            creditSection("This app", THIS_APP) { openCredit = it }
            creditSection("Emoji artwork", EMOJI_ARTWORK) { openCredit = it }
            creditSection("Emoji data", EMOJI_DATA) { openCredit = it }
            creditSection("Open-source libraries", LIBRARIES) { openCredit = it }
        }
    }

    openCredit?.let { credit -> LicenseDialog(credit, onDismiss = { openCredit = null }) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.creditSection(
    title: String,
    credits: List<Credit>,
    onOpen: (Credit) -> Unit
) {
    item(key = "header:$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
        )
    }
    itemsIndexed(credits, key = { _, credit -> "$title:${credit.name}" }) { index, credit ->
        ListItem(
            onClick = { onOpen(credit) },
            shapes = ListItemDefaults.segmentedShapes(index, credits.size),
            colors = ListItemDefaults.segmentedColors(),
            supportingContent = { Text("${credit.license} · ${credit.holder}") }
        ) {
            Text(credit.name)
        }
    }
}

/**
 * License files are hard-wrapped for 80-column terminals; join each paragraph's lines so the text
 * wraps to the dialog instead. Paragraph breaks are kept; divider lines ("=====") are dropped.
 */
private fun reflow(text: String): String =
    text.trim().split(Regex("\\n\\s*\\n")).joinToString("\n\n") { paragraph ->
        paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.all { c -> c == '=' || c == '-' } }
            .joinToString(" ")
    }.replace(Regex("(\\n\\n)+"), "\n\n").trim()

/** A credit's attribution and full license text, with a link to the project */
@Composable
private fun LicenseDialog(credit: Credit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val licenseText = remember(credit.licenseFile) {
        credit.licenseFile?.let { file ->
            runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExpressiveCompactCardShape,
        title = { Text(credit.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(credit.holder, style = MaterialTheme.typography.bodyMedium)
                Text(credit.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                credit.note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                licenseText?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(reflow(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { uriHandler.openUri(credit.url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Website")
            }
        }
    )
}

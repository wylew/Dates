package com.wylew.dates.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wylew.dates.ui.theme.ExpressiveCardShape
import com.wylew.dates.ui.theme.ExpressiveCompactCardShape
import com.wylew.dates.ui.theme.ExpressivePillShape
import com.wylew.dates.ui.theme.ExpressiveSheetShape
import com.wylew.dates.util.FluentEmojiManager
import com.wylew.dates.util.MonaFontManager
import com.wylew.dates.util.OpenMojiManager
import com.wylew.dates.util.SegoeSvgManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiLibraryManagerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val isFluentDownloaded = FluentEmojiManager.isPackDownloaded(context)
    val isMonaDownloaded = MonaFontManager.isFontDownloaded(context)
    val isOpenMojiDownloaded = OpenMojiManager.isPackDownloaded(context)
    val isSegoeDownloaded = SegoeSvgManager.isPackDownloaded(context)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ExpressiveSheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Emoji Library Manager",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manually place your custom emoji and font asset files into the app's internal storage directories below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. System Standard
            LibraryItemCard(
                icon = Icons.Default.Mood,
                title = "System Standard",
                subtitle = "Native Android system emojis using device default font.",
                pathInfo = "Pre-installed system emojis",
                isReady = true,
                previewContent = {
                    Text(text = "🎂 🎉 🚀 🥳", fontSize = 22.sp)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Microsoft Fluent 3D
            LibraryItemCard(
                icon = Icons.Default.AutoAwesome,
                title = "Fluent Animated 3D",
                subtitle = "Expressive 3D animated emojis (.webp / .gif / .png)",
                pathInfo = "Folder: files/fluent_emojis/",
                isReady = isFluentDownloaded,
                previewContent = {
                    Text(text = "✨ 🚀 🎂 🥳", fontSize = 22.sp)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Mona Font
            LibraryItemCard(
                icon = Icons.Default.FontDownload,
                title = "Mona Font Emojis",
                subtitle = "Monad Mona vector typography font emojis",
                pathInfo = "File: files/fonts/Mona.ttf",
                isReady = isMonaDownloaded,
                previewContent = {
                    Text(text = "✦ ✧ ❖ ⚡ 👾", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. OpenMoji Color
            LibraryItemCard(
                icon = Icons.Default.Palette,
                title = "OpenMoji Color",
                subtitle = "High-resolution colorful open-source emojis",
                pathInfo = "Folder: files/openmoji_emojis/",
                isReady = isOpenMojiDownloaded,
                previewContent = {
                    Text(text = "🎈 🍕 🏖️ 🚗", fontSize = 22.sp)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Microsoft Segoe UI
            LibraryItemCard(
                icon = Icons.Default.Window,
                title = "Microsoft Segoe UI Emoji",
                subtitle = "Microsoft Segoe UI Emoji SVG pack",
                pathInfo = "Folder: files/segoe_svgs/",
                isReady = isSegoeDownloaded,
                previewContent = {
                    Text(text = "🎂 🎉 🚀 🥳", fontSize = 22.sp)
                }
            )
        }
    }
}

@Composable
private fun LibraryItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    pathInfo: String,
    isReady: Boolean,
    previewContent: @Composable () -> Unit
) {
    Surface(
        shape = ExpressiveCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pathInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(ExpressiveCompactCardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    previewContent()
                }

                AssistChip(
                    onClick = {},
                    label = { Text(if (isReady) "Ready" else "Missing Assets") },
                    leadingIcon = {
                        if (isReady) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    shape = ExpressivePillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        labelColor = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

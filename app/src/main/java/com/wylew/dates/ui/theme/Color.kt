package com.wylew.dates.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// Nearby shade of an event color (darker for light colors, lighter for dark ones), nearly opaque
fun accentShadeOf(color: Color): Color {
    val shifted = if (color.luminance() > 0.45f) lerp(color, Color.Black, 0.14f) else lerp(color, Color.White, 0.18f)
    return shifted.copy(alpha = 0.92f)
}

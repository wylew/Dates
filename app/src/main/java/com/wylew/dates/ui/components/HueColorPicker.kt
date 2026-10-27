package com.wylew.dates.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun HueColorPicker(
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    initialColor: Color = Color(0xFFE53935)
) {
    ColorWheelPicker(
        initialColor = initialColor,
        onColorSelected = onColorSelected,
        modifier = modifier
    )
}

@Composable
fun ColorWheelPicker(
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    initialColor: Color = Color(0xFFE53935)
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).apply {
            android.graphics.Color.colorToHSV(initialColor.toArgb(), this)
        }
    }

    var hue by remember { mutableFloatStateOf(if (initialHsv[0] == 0f) 0f else initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(if (initialHsv[1] == 0f) 0.85f else initialHsv[1]) }
    var value by remember { mutableFloatStateOf(if (initialHsv[2] == 0f) 0.95f else initialHsv[2].coerceIn(0.2f, 1f)) }

    var isDraggingWheel by remember { mutableStateOf(false) }

    val currentColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    LaunchedEffect(currentColor) {
        onColorSelected(currentColor)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 2D Color Wheel Canvas
        Box(
            modifier = Modifier
                .size(210.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                isDraggingWheel = true
                                val (h, s) = calculateHueSat(offset, size.width.toFloat(), size.height.toFloat())
                                hue = h
                                saturation = s
                                tryAwaitRelease()
                                isDraggingWheel = false
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDraggingWheel = true },
                            onDragEnd = { isDraggingWheel = false },
                            onDragCancel = { isDraggingWheel = false }
                        ) { change, _ ->
                            val (h, s) = calculateHueSat(change.position, size.width.toFloat(), size.height.toFloat())
                            hue = h
                            saturation = s
                        }
                    }
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Render Hue Sweep Gradient + Radial Saturation Gradient
                val sweepShader = android.graphics.SweepGradient(
                    center.x, center.y,
                    intArrayOf(
                        android.graphics.Color.RED,
                        android.graphics.Color.YELLOW,
                        android.graphics.Color.GREEN,
                        android.graphics.Color.CYAN,
                        android.graphics.Color.BLUE,
                        android.graphics.Color.MAGENTA,
                        android.graphics.Color.RED
                    ),
                    null
                )

                val radialShader = android.graphics.RadialGradient(
                    center.x, center.y, radius,
                    intArrayOf(android.graphics.Color.WHITE, 0x00FFFFFF),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )

                val composeShader = android.graphics.ComposeShader(
                    sweepShader,
                    radialShader,
                    android.graphics.PorterDuff.Mode.SRC_OVER
                )

                val paint = android.graphics.Paint().apply {
                    shader = composeShader
                    isAntiAlias = true
                }

                drawContext.canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)

                // Calculate current thumb position
                val angleRad = Math.toRadians(hue.toDouble())
                val thumbDist = saturation * radius
                val thumbX = center.x + (thumbDist * cos(angleRad)).toFloat()
                val thumbY = center.y + (thumbDist * sin(angleRad)).toFloat()

                val thumbRadius = if (isDraggingWheel) 16.dp.toPx() else 12.dp.toPx()

                // Draw Thumb Handle
                drawCircle(
                    color = Color.Black.copy(alpha = 0.3f),
                    radius = thumbRadius + 3.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, thumbY)
                )
                drawCircle(
                    color = currentColor,
                    radius = thumbRadius - 3.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Brightness / Value Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Brightness",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0.1f..1.0f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun calculateHueSat(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = minOf(width, height) / 2f

    val dx = offset.x - centerX
    val dy = offset.y - centerY
    val dist = hypot(dx, dy)

    val saturation = (dist / radius).coerceIn(0f, 1f)

    var angleDegrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angleDegrees < 0) {
        angleDegrees += 360f
    }

    return Pair(angleDegrees, saturation)
}

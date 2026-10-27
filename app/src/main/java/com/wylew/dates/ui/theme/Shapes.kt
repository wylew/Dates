package com.wylew.dates.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ExpressivePillShape = RoundedCornerShape(50)
val ExpressiveCardShape = RoundedCornerShape(28.dp)
val ExpressiveCompactCardShape = RoundedCornerShape(20.dp)
val ExpressiveSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
val ExpressiveChipShape = RoundedCornerShape(16.dp)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = ExpressiveCardShape,
    extraLarge = ExpressiveSheetShape
)

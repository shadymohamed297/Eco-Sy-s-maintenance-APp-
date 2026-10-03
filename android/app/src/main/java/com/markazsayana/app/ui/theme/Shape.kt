package com.markazsayana.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii mirror the design: 10 (chips), 12 (buttons/inputs), 14 (cards), 16 (large cards).
val ChipShape = RoundedCornerShape(10.dp)
val ControlShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(14.dp)
val LargeCardShape = RoundedCornerShape(16.dp)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ChipShape,
    medium = ControlShape,
    large = CardShape,
    extraLarge = LargeCardShape,
)

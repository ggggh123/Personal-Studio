package com.example.personal_studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 天光 corner radii: 9 timetable block · 14 small card · 20 card · 28 sheet. */
val TgShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    // Material derives the top-only sheet shape from this; dialogs use it whole.
    extraLarge = RoundedCornerShape(28.dp),
)

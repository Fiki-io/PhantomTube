package com.phantom.tube.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultBorderColor = Color.White.copy(alpha = 0.12f)

fun Modifier.phantomSurface(
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 1.dp,
    tintColor: Color = YouTubeSurface,
    surfaceAlpha: Float = 0.95f,
    accentGlow: Color? = null
): Modifier = this
    .clip(shape)
    .background(
        color = tintColor.copy(alpha = surfaceAlpha),
        shape = shape
    )
    .border(
        width = borderWidth,
        color = accentGlow?.copy(alpha = 0.45f) ?: DefaultBorderColor,
        shape = shape
    )

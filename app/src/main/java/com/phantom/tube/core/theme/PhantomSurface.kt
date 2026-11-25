package com.phantom.tube.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultBorderColor = Color.White.copy(alpha = 0.12f)

private val MiniGlassBgBrush = Brush.verticalGradient(
    colors = listOf(BubbleDockBg, BubbleDockBgBottom)
)
private val MiniGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(BubbleDockTopHighlight, BubbleDockEdgeHighlight, BubbleDockBottomRim)
)
private val MiniGlassSheenBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to BubbleDockInnerSheen,
        0.48f to Color.Transparent
    )
)

private val SheetGlassBgBrush = Brush.verticalGradient(
    colors = listOf(GlassSheetBgTop, GlassSheetBgBottom)
)
private val SheetGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(GlassSheetTopRim, GlassSheetEdgeRim, GlassSheetBottomRim)
)
private val SheetGlassSheenBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to GlassSheetInnerSheen,
        0.22f to Color.Transparent
    )
)

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

fun Modifier.phantomGlassSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    shadowElevation: Dp = 12.dp,
    borderWidth: Dp = 0.85.dp
): Modifier = this
    .shadow(
        elevation = shadowElevation,
        shape = shape,
        ambientColor = BubbleDockAmbientShadow,
        spotColor = BubbleDockAmbientShadow
    )
    .clip(shape)
    .background(brush = MiniGlassBgBrush, shape = shape)
    .drawBehind {
        drawRect(brush = MiniGlassSheenBrush)
    }
    .border(
        width = borderWidth,
        brush = MiniGlassBorderBrush,
        shape = shape
    )

fun Modifier.phantomGlassSheet(
    shape: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    borderWidth: Dp = 0.85.dp
): Modifier = this
    .clip(shape)
    .background(brush = SheetGlassBgBrush, shape = shape)
    .drawBehind {
        drawRect(brush = SheetGlassSheenBrush)
    }
    .border(
        width = borderWidth,
        brush = SheetGlassBorderBrush,
        shape = shape
    )

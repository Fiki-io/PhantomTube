package com.phantom.tube.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.BrandMarkAmbientCrimson
import com.phantom.tube.core.theme.BrandMarkAmbientWhite
import com.phantom.tube.core.theme.BrandMarkBgBottom
import com.phantom.tube.core.theme.BrandMarkBgTop
import com.phantom.tube.core.theme.BrandMarkFillBottom
import com.phantom.tube.core.theme.BrandMarkFillMid
import com.phantom.tube.core.theme.BrandMarkFillTop
import com.phantom.tube.core.theme.BrandMarkGhostFill
import com.phantom.tube.core.theme.BrandMarkGlassSheen
import com.phantom.tube.core.theme.BrandMarkInnerRim
import com.phantom.tube.core.theme.BrandMarkRimCrimson
import com.phantom.tube.core.theme.BrandMarkRimMid
import com.phantom.tube.core.theme.BrandMarkRimTop
import com.phantom.tube.core.theme.BrandTextFillBottom
import com.phantom.tube.core.theme.BrandTextFillMid
import com.phantom.tube.core.theme.BrandTextFillTop
import com.phantom.tube.core.theme.BrandTextGlowCrimson
import com.phantom.tube.core.theme.BrandTextGlowInner
import com.phantom.tube.core.theme.BrandTextGlowOuter
import com.phantom.tube.core.theme.BrandTextRimBright
import com.phantom.tube.core.theme.BrandTextRimSoft
import com.phantom.tube.core.theme.BrandTextSheenPeak
import com.phantom.tube.core.theme.PhantomBrandFontFamily

private const val SHEEN_ACTIVE_WINDOW = 0.34f
private const val EMBLEM_GRID_SIZE = 108f

@Composable
fun PhantomBrandHeader(
    modifier: Modifier = Modifier,
    markSize: Dp = 34.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "phantom_brand_sheen")
    val sheenCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PhantomMotion.BrandSheenDurationMs,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phantom_sheen_cycle"
    )

    Row(
        modifier = modifier.testTag("top_bar_brand_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhantomBrandMark(
            size = markSize,
            sheenCycle = sheenCycle,
            contentDescription = stringResource(R.string.app_logo_desc)
        )

        Spacer(modifier = Modifier.width(10.dp))

        PhantomWordmark(
            text = stringResource(R.string.app_brand_name),
            sheenCycle = sheenCycle
        )
    }
}

@Composable
fun PhantomBrandMark(
    size: Dp,
    sheenCycle: Float,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val emblemPath = remember { buildPhantomMonogramPath() }
    val markSheenProgress = (sheenCycle / (SHEEN_ACTIVE_WINDOW * 0.45f)).coerceIn(0f, 1.5f)

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
            .drawBehind {
                val radius = this.size.minDimension * 0.78f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BrandMarkAmbientWhite, Color.Transparent),
                        center = Offset(this.size.width * 0.32f, this.size.height * 0.28f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(this.size.width * 0.32f, this.size.height * 0.28f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BrandMarkAmbientCrimson, Color.Transparent),
                        center = Offset(this.size.width * 0.72f, this.size.height * 0.76f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(this.size.width * 0.72f, this.size.height * 0.76f)
                )
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val corner = CornerRadius(w * 0.29f, h * 0.29f)
        val borderStroke = (w * 0.036f).coerceAtLeast(1.2f)
        val inset = borderStroke / 2f
        val innerSize = Size(w - borderStroke, h - borderStroke)

        // Smoked liquid-glass squircle base
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(BrandMarkBgTop, BrandMarkBgBottom),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset(inset, inset),
            size = innerSize,
            cornerRadius = corner
        )

        // Upper-left diagonal glass reflection
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(BrandMarkGlassSheen, Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(w * 0.78f, h * 0.68f)
            ),
            topLeft = Offset(inset, inset),
            size = innerSize,
            cornerRadius = corner
        )

        val scaleX = w / EMBLEM_GRID_SIZE
        val scaleY = h / EMBLEM_GRID_SIZE

        // Offset crimson-tinted "phantom" afterimage layer
        withTransform({
            translate(left = w * 0.052f, top = h * 0.032f)
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
        }) {
            drawPath(
                path = emblemPath,
                color = BrandMarkGhostFill
            )
        }

        // Primary geometric "P + Play" monogram with metallic fill & luminous rim
        withTransform({
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
        }) {
            drawPath(
                path = emblemPath,
                brush = Brush.verticalGradient(
                    colors = listOf(BrandMarkFillTop, BrandMarkFillMid, BrandMarkFillBottom),
                    startY = 26f,
                    endY = 84f
                )
            )
            drawPath(
                path = emblemPath,
                color = BrandMarkInnerRim,
                style = Stroke(width = 1.6f, join = StrokeJoin.Round)
            )
        }

        // Edge-lit outer squircle rim border
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(BrandMarkRimTop, BrandMarkRimMid, BrandMarkRimCrimson),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(inset, inset),
            size = innerSize,
            cornerRadius = corner,
            style = Stroke(width = borderStroke)
        )

        // Traveling specular glint along the glass badge rim
        if (markSheenProgress in 0.01f..1.25f) {
            val glintX = w * (markSheenProgress - 0.15f)
            val band = w * 0.38f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, BrandTextSheenPeak.copy(alpha = 0.85f), Color.Transparent),
                    start = Offset(glintX - band, 0f),
                    end = Offset(glintX + band, h * 0.85f)
                ),
                topLeft = Offset(inset, inset),
                size = innerSize,
                cornerRadius = corner,
                style = Stroke(width = borderStroke * 1.15f)
            )
        }
    }
}

@Composable
private fun PhantomWordmark(
    text: String,
    sheenCycle: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var textWidthPx by remember { mutableFloatStateOf(320f) }
    var textHeightPx by remember { mutableFloatStateOf(64f) }

    val rimStrokePx = with(density) { 1.15.dp.toPx() }
    val outerGlowBlurPx = with(density) { 8.dp.toPx() }
    val crimsonGlowBlurPx = with(density) { 6.dp.toPx() }
    val crimsonOffsetPx = with(density) { 0.8.dp.toPx() }

    val baseStyle = remember {
        TextStyle(
            fontFamily = PhantomBrandFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.5.sp,
            letterSpacing = 2.6.sp
        )
    }

    val sweepProgress = ((sheenCycle - 0.04f) / SHEEN_ACTIVE_WINDOW).coerceIn(-0.4f, 1.5f)
    val bandHalfWidth = (textWidthPx * 0.22f).coerceAtLeast(36f)
    val bandCenterX = -bandHalfWidth + (textWidthPx + bandHalfWidth * 2f) * sweepProgress

    val sheenBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            BrandTextSheenPeak.copy(alpha = 0.50f),
            BrandTextSheenPeak,
            BrandTextSheenPeak.copy(alpha = 0.50f),
            Color.Transparent
        ),
        start = Offset(bandCenterX - bandHalfWidth, 0f),
        end = Offset(bandCenterX + bandHalfWidth, textHeightPx)
    )

    val fillBrush = remember(textHeightPx) {
        Brush.verticalGradient(
            colors = listOf(BrandTextFillTop, BrandTextFillMid, BrandTextFillBottom),
            startY = 0f,
            endY = textHeightPx.coerceAtLeast(1f)
        )
    }

    val rimBrush = remember(textHeightPx) {
        Brush.verticalGradient(
            colors = listOf(BrandTextRimBright, BrandTextRimSoft, BrandTextRimBright.copy(alpha = 0.55f)),
            startY = 0f,
            endY = textHeightPx.coerceAtLeast(1f)
        )
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0) textWidthPx = size.width.toFloat()
            if (size.height > 0) textHeightPx = size.height.toFloat()
        },
        contentAlignment = Alignment.CenterStart
    ) {
        // Layer 1: Subtle warm crimson chromatic edge glow
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                color = BrandTextGlowCrimson.copy(alpha = 0.16f),
                shadow = Shadow(
                    color = BrandTextGlowCrimson,
                    offset = Offset(crimsonOffsetPx, crimsonOffsetPx),
                    blurRadius = crimsonGlowBlurPx
                )
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )

        // Layer 2: Soft white luminous outer edge halo
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                color = BrandTextGlowInner,
                shadow = Shadow(
                    color = BrandTextGlowOuter,
                    offset = Offset.Zero,
                    blurRadius = outerGlowBlurPx
                )
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )

        // Layer 3: Crisp luminous rim stroke peeking around letter edges
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                brush = rimBrush,
                drawStyle = Stroke(
                    width = rimStrokePx,
                    join = StrokeJoin.Round
                )
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )

        // Layer 4: Liquid chrome metallic fill (primary accessible label)
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                brush = fillBrush
            )
        )

        // Layer 5: Slow diagonal specular light sweep across the wordmark
        Text(
            text = text,
            maxLines = 1,
            style = baseStyle.copy(
                brush = sheenBrush
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

private fun buildPhantomMonogramPath(): Path {
    return Path().apply {
        fillType = PathFillType.EvenOdd
        // Outer sculpted geometric "P" contour with tapered play-apex bowl & diagonal blade stem
        moveTo(27f, 82f)
        lineTo(27f, 29f)
        quadraticBezierTo(27f, 25f, 31f, 25f)
        lineTo(54f, 25f)
        quadraticBezierTo(58f, 25f, 61.5f, 27.2f)
        lineTo(82.5f, 41.2f)
        quadraticBezierTo(86.5f, 44f, 82.5f, 46.8f)
        lineTo(61.5f, 60.8f)
        quadraticBezierTo(58f, 63f, 54f, 63f)
        lineTo(41f, 63f)
        lineTo(41f, 73.5f)
        quadraticBezierTo(41f, 75.8f, 39f, 77.2f)
        lineTo(30.2f, 83.2f)
        quadraticBezierTo(27f, 85.2f, 27f, 82f)
        close()

        // Inner negative-space Play triangle cutout
        moveTo(41f, 35.5f)
        lineTo(41f, 52.5f)
        lineTo(65.5f, 44f)
        close()
    }
}

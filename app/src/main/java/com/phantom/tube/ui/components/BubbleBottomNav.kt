package com.phantom.tube.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.BubbleDockAmbientShadow
import com.phantom.tube.core.theme.BubbleDockBg
import com.phantom.tube.core.theme.BubbleDockBgBottom
import com.phantom.tube.core.theme.BubbleDockBottomRim
import com.phantom.tube.core.theme.BubbleDockEdgeHighlight
import com.phantom.tube.core.theme.BubbleDockInnerSheen
import com.phantom.tube.core.theme.BubbleDockTopHighlight
import com.phantom.tube.core.theme.BubblePillGlassBorderBottom
import com.phantom.tube.core.theme.BubblePillGlassBorderTop
import com.phantom.tube.core.theme.BubblePillGlassBottom
import com.phantom.tube.core.theme.BubblePillGlassTop
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NavTab(
    val titleRes: Int,
    val icon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH(R.string.nav_search, Icons.Filled.Search, Icons.Filled.Search),
    HISTORY(R.string.nav_history, PhantomIcons.History, PhantomIcons.History),
    SUBSCRIPTION(R.string.nav_library, PhantomIcons.Subscriptions, PhantomIcons.SubscriptionsOutlined)
}

private val DOCK_MAX_WIDTH = 420.dp
private val DOCK_COMPACT_WIDTH = 236.dp
private val DOCK_EXPANDED_HEIGHT = 60.dp
private val DOCK_EXPANDED_HEIGHT_LARGE_FONT = 66.dp
private val DOCK_COMPACT_HEIGHT = 48.dp
private val DOCK_BOTTOM_MARGIN_EXPANDED = 16.dp
private val DOCK_BOTTOM_MARGIN_COMPACT = 10.dp
private val DOCK_CORNER_RADIUS = 32.dp
private val DOCK_PILL_CORNER_RADIUS = 24.dp

/**
 * Navigasi dock kaca melayang di bagian bawah layar dengan animasi morph responsif terhadap scroll.
 * - Menyusut ke tengah menjadi pil icon-only ramping saat pengguna scroll ke bawah.
 * - Membesar kembali ke ukuran penuh beserta label saat pengguna scroll ke atas atau berpindah tab.
 * - Pil indikator aktif kaca dengan peregangan dinamis (liquid glide) mengikuti perpindahan tab.
 */
@Composable
fun BubbleBottomNav(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onExpandRequest: () -> Unit = {},
    pagePositionProvider: (() -> Float)? = null
) {
    val haptic = LocalHapticFeedback.current
    val tabs = remember { NavTab.values() }
    val activeIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val configuration = LocalConfiguration.current
    val expandedHeight = if (configuration.fontScale > 1.15f) {
        DOCK_EXPANDED_HEIGHT_LARGE_FONT
    } else {
        DOCK_EXPANDED_HEIGHT
    }

    val morphProgress by animateFloatAsState(
        targetValue = if (isCompact) 1f else 0f,
        animationSpec = IosSpringSpecs.DockMorph,
        label = "dock_morph_progress"
    )

    val animatedHeight by animateDpAsState(
        targetValue = if (isCompact) DOCK_COMPACT_HEIGHT else expandedHeight,
        animationSpec = IosSpringSpecs.DockMorphDp,
        label = "dock_height"
    )

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isCompact) DOCK_BOTTOM_MARGIN_COMPACT else DOCK_BOTTOM_MARGIN_EXPANDED,
        animationSpec = IosSpringSpecs.DockMorphDp,
        label = "dock_bottom_padding"
    )

    val dockShape = remember { RoundedCornerShape(DOCK_CORNER_RADIUS) }
    val pillShape = remember { RoundedCornerShape(DOCK_PILL_CORNER_RADIUS) }

    val dockSurfaceBrush = remember {
        Brush.verticalGradient(
            colors = listOf(BubbleDockBg, BubbleDockBgBottom)
        )
    }
    val dockSpecularBorderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                BubbleDockTopHighlight,
                BubbleDockEdgeHighlight,
                BubbleDockBottomRim
            )
        )
    }
    val dockInnerSheenBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to BubbleDockInnerSheen,
                0.45f to Color.Transparent
            )
        )
    }
    val activePillBrush = remember {
        Brush.verticalGradient(
            colors = listOf(BubblePillGlassTop, BubblePillGlassBottom)
        )
    }
    val activePillBorderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(BubblePillGlassBorderTop, BubblePillGlassBorderBottom)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = animatedBottomPadding.coerceAtLeast(0.dp),
                top = 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val expandedWidth = maxWidth.coerceAtMost(DOCK_MAX_WIDTH)
        val compactWidth = DOCK_COMPACT_WIDTH.coerceAtMost(expandedWidth)
        val animatedWidth by animateDpAsState(
            targetValue = if (isCompact) compactWidth else expandedWidth,
            animationSpec = IosSpringSpecs.DockMorphDp,
            label = "dock_width"
        )

        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .shadow(
                    elevation = if (isCompact) 10.dp else 16.dp,
                    shape = dockShape,
                    ambientColor = BubbleDockAmbientShadow,
                    spotColor = BubbleDockAmbientShadow
                )
                .clip(dockShape)
                .background(dockSurfaceBrush)
                .drawBehind {
                    drawRect(brush = dockInnerSheenBrush)
                }
                .border(
                    width = 0.85.dp,
                    brush = dockSpecularBorderBrush,
                    shape = dockShape
                )
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val tabWidthPx = totalWidthPx / tabs.size

                val effectivePosition = pagePositionProvider?.invoke() ?: activeIndex.toFloat()
                val effectiveActiveIndex = (effectivePosition + 0.5f).toInt().coerceIn(0, tabs.size - 1)

                val animatedTabPosition by animateFloatAsState(
                    targetValue = effectivePosition,
                    animationSpec = if (pagePositionProvider != null) {
                        IosSpringSpecs.DockPillFollow
                    } else {
                        IosSpringSpecs.DockPillGlide
                    },
                    label = "dock_sliding_pill_pos"
                )

                val travelStretch = abs(effectivePosition - animatedTabPosition).coerceIn(0f, 1f)
                val pillHorizontalInset = if (isCompact) 2.dp else 3.dp

                // Pil indikator aktif kaca yang meluncur dan merenggang dinamis saat berpindah tab
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset((animatedTabPosition * tabWidthPx).roundToInt(), 0)
                        }
                        .width(maxWidth / tabs.size)
                        .fillMaxHeight()
                        .padding(horizontal = pillHorizontalInset, vertical = 1.dp)
                        .graphicsLayer {
                            scaleX = 1f + (travelStretch * 0.14f)
                            scaleY = 1f - (travelStretch * 0.08f)
                        }
                        .clip(pillShape)
                        .background(activePillBrush)
                        .border(0.75.dp, activePillBorderBrush, pillShape)
                )

                // Item-item tab di atas pil geser
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val labelHeightFraction = (1f - morphProgress).coerceIn(0f, 1f)
                    val labelAlpha = (1f - morphProgress * 1.45f).coerceIn(0f, 1f)
                    val iconSize = (22f - morphProgress * 1.5f).dp

                    tabs.forEachIndexed { index, tab ->
                        val isSelected = (index == effectiveActiveIndex)

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        LaunchedEffect(isPressed) {
                            if (isPressed) {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {}
                            }
                        }

                        val targetScale = when {
                            isPressed -> 0.86f
                            isSelected -> if (isCompact) 1.08f else 1.05f
                            else -> 0.96f
                        }

                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = IosSpringSpecs.Bouncy,
                            label = "tab_bounce_scale"
                        )

                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else TextSecondary,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "tab_icon_color"
                        )

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else TextSecondary,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "tab_text_color"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .testTag("nav_tab_${tab.name.lowercase()}")
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        if (isCompact) {
                                            onExpandRequest()
                                        }
                                        if (!isSelected) {
                                            try {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } catch (_: Exception) {}
                                            onTabSelected(tab)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val title = stringResource(tab.titleRes)
                                Icon(
                                    imageVector = if (isSelected) tab.icon else tab.unselectedIcon,
                                    contentDescription = title,
                                    tint = iconColor,
                                    modifier = Modifier.size(iconSize)
                                )
                                if (labelHeightFraction > 0.01f) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val animatedLabelH = (placeable.height * labelHeightFraction)
                                                    .roundToInt()
                                                    .coerceAtLeast(0)
                                                layout(placeable.width, animatedLabelH) {
                                                    placeable.placeRelativeWithLayer(0, 0) {
                                                        alpha = labelAlpha
                                                        val labelScale = 0.85f + (0.15f * labelAlpha)
                                                        scaleX = labelScale
                                                        scaleY = labelScale
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

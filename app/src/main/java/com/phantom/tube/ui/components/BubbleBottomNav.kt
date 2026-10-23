package com.phantom.tube.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.BubbleDockBg
import com.phantom.tube.core.theme.BubbleDockBorder
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import kotlin.math.roundToInt

enum class NavTab(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Default.Home),
    SEARCH(R.string.nav_search, Icons.Default.Search),
    HISTORY(R.string.nav_history, Icons.Default.History),
    SUBSCRIPTION(R.string.nav_library, Icons.Default.Subscriptions)
}

/**
 * Navigasi dock gelembung melayang di bagian bawah layar dengan animasi fluid ala iOS.
 * - Sliding glass pill indicator dengan fisika pegas (spring physics).
 * - Tactile squeeze & jelly pop saat ditekan.
 * - Micro-haptic tick khas iOS Taptic Engine.
 */
@Composable
fun BubbleBottomNav(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = NavTab.values()
    val activeIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(BubbleDockBg)
                .border(
                    width = 1.dp,
                    color = BubbleDockBorder,
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(4.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val tabWidthPx = totalWidthPx / tabs.size

            // Animasi pergeseran pil aktif (Sliding Liquid Glass Pill)
            val animatedPillOffsetPx by animateFloatAsState(
                targetValue = activeIndex * tabWidthPx,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = 360f
                ),
                label = "dock_sliding_pill_x"
            )

            // Pil latar belakang aktif meluncur fluida
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedPillOffsetPx.roundToInt(), 0) }
                    .width(maxWidth / tabs.size)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF28282C))
                    .border(0.5.dp, Color(0xFF3A3A3F), RoundedCornerShape(26.dp))
            )

            // Item-item tab di atas pil geser
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = (index == activeIndex)

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // Haptic tick saat tab disentuh
                    LaunchedEffect(isPressed) {
                        if (isPressed) {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {}
                        }
                    }

                    // Skala sentuhan dan pop saat aktif
                    val targetScale = when {
                        isPressed -> 0.88f // Tactile compression saat ditekan
                        isSelected -> 1.05f // Sedikit membesar saat terpilih
                        else -> 0.95f
                    }

                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = IosSpringSpecs.Bouncy,
                        label = "tab_bounce_scale"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) TextPrimary else TextSecondary,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "tab_icon_color"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) TextPrimary else TextSecondary,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "tab_text_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
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
                                imageVector = tab.icon,
                                contentDescription = title,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = title,
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

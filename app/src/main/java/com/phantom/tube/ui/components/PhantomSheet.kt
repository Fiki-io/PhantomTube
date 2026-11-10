package com.phantom.tube.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.theme.GlassBorderDim
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Komponen sheet standar yang menempel tepat di bawah viewport video 16:9 saat portrait.
 * Mendukung:
 * - Pelacakan jari 1:1 tanpa jeda (0ms lag) menggunakan Animatable.snapTo
 * - Tarik ke bawah dari handle/header MAUPUN langsung dari dalam LazyColumn saat berada di paling atas (NestedScrollConnection)
 * - Animasi buka/tutup seragam menggunakan PhantomMotion.SheetSlideEnter / SheetSlideExit
 */
@Composable
fun PhantomAnchoredPlayerSheet(
    visible: Boolean,
    isFullscreen: Boolean,
    title: String,
    badgeText: String = "",
    subtitleText: String = "",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { PhantomMotion.SheetDismissThresholdDp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetY.snapTo(0f)
        }
    }

    fun settleOrDismiss(velocityY: Float = 0f) {
        if (dragOffsetY.value > dismissThresholdPx || velocityY > 1100f) {
            onDismiss()
        } else {
            scope.launch {
                dragOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = IosSpringSpecs.Gentle
                )
            }
        }
    }

    val nestedScrollConnection = remember(dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && dragOffsetY.value > 0f) {
                    val consumedY = available.y.coerceAtLeast(-dragOffsetY.value)
                    scope.launch {
                        dragOffsetY.snapTo((dragOffsetY.value + consumedY).coerceAtLeast(0f))
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    scope.launch {
                        dragOffsetY.snapTo((dragOffsetY.value + available.y).coerceAtLeast(0f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetY.value > 0f) {
                    settleOrDismiss(velocityY = available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val headerDragModifier = Modifier.pointerInput(dismissThresholdPx) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                scope.launch {
                    dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                }
            },
            onDragEnd = {
                settleOrDismiss()
            },
            onDragCancel = {
                scope.launch {
                    dragOffsetY.animateTo(0f, IosSpringSpecs.Gentle)
                }
            }
        )
    }

    AnimatedVisibility(
        visible = visible && !isFullscreen,
        enter = PhantomMotion.SheetSlideEnter,
        exit = PhantomMotion.SheetSlideExit,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f)
            )

            val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .clip(sheetShape)
                    .background(YouTubeSurface)
                    .border(0.75.dp, GlassBorderLight, sheetShape)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Consume tap on sheet surface */ }
                    }
                    .nestedScroll(nestedScrollConnection)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Area Handle & Header yang dapat ditarik ke bawah
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(headerDragModifier)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(TextMuted.copy(alpha = 0.45f))
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (badgeText.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = badgeText,
                                            color = TextMuted,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (subtitleText.isNotBlank()) {
                                    Text(
                                        text = subtitleText,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            thickness = 0.5.dp,
                            color = GlassBorderDim
                        )
                    }

                    content()
                }
            }
        }
    }
}

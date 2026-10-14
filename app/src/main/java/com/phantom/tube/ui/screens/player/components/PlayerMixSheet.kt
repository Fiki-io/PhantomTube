package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.VideoItem
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import kotlin.math.roundToInt

@Composable
fun MixPlaylistItemCard(
    index: Int,
    video: VideoItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) Color(0xFF272727) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index number or Playing Indicator
            Box(
                modifier = Modifier.width(26.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Text(
                        text = "▶",
                        color = YouTubeRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "$index",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Thumbnail with duration
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                if (video.durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title and Channel Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = if (isCurrent) YouTubeRed else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = video.channelTitle,
                    color = if (isCurrent) YouTubeRed.copy(alpha = 0.85f) else TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FloatingMixBar(
    nextVideo: VideoItem,
    mixTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(YouTubeRed.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_mix_next, nextVideo.title),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mixTitle.ifBlank { "Mix" },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ExpandLess,
                contentDescription = stringResource(R.string.player_mix_open_queue),
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun PlayerMixSheet(
    visible: Boolean,
    isFullscreen: Boolean,
    mixTitle: String,
    mixPlaylist: List<VideoItem>,
    currentMixIndex: Int,
    onVideoSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mixSheetOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedMixSheetOffsetY by animateFloatAsState(
        targetValue = mixSheetOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "mix_sheet_offset"
    )

    LaunchedEffect(visible) {
        if (visible) {
            mixSheetOffsetY = 0f
        }
    }

    val mixHandleDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                mixSheetOffsetY = (mixSheetOffsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragEnd = {
                if (mixSheetOffsetY > 80f) {
                    onDismiss()
                } else {
                    mixSheetOffsetY = 0f
                }
            },
            onDragCancel = {
                mixSheetOffsetY = 0f
            }
        )
    }

    AnimatedVisibility(
        visible = visible && !isFullscreen,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(animationSpec = tween(100)) + slideOutVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) { it },
        modifier = modifier
    ) {
        // Scrim background (tap empty area to dismiss). Transparent so video on top continues playing clearly
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            // Invisible top spacer reserving the height of status bar + 16:9 Video Player Box
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset { IntOffset(0, animatedMixSheetOffsetY.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF212121))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drag Handle with touch target and vertical drag
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(mixHandleDragModifier)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Header Bar (also supports dragging down to close)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(mixHandleDragModifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mixTitle.ifBlank { "Mix" },
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = if (mixPlaylist.isNotEmpty()) "${currentMixIndex + 1} / ${mixPlaylist.size}" else "${currentMixIndex + 1}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.player_close),
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(Color(0xFF2E2E2E))
                    )

                    // Mix Playlist List
                    val listState = rememberLazyListState()
                    LaunchedEffect(visible) {
                        if (visible && currentMixIndex in mixPlaylist.indices) {
                            listState.animateScrollToItem(currentMixIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 24.dp)
                    ) {
                        itemsIndexed(
                            items = mixPlaylist,
                            key = { index, item -> "mix_${item.id}_$index" },
                            contentType = { _, _ -> "mix_item" }
                        ) { index, item ->
                            val isCurrent = index == currentMixIndex
                            MixPlaylistItemCard(
                                index = index + 1,
                                video = item,
                                isCurrent = isCurrent,
                                onClick = {
                                    onVideoSelect(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

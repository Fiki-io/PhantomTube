package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.R
import com.phantom.tube.ui.components.PhantomLinkText
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun PlayerDescriptionSheet(
    visible: Boolean,
    isFullscreen: Boolean,
    video: VideoItem,
    nextQueueData: NextQueue?,
    activeAvatarUrl: String,
    isSubscribed: Boolean,
    currentTimeSecProvider: () -> Float = { 0f },
    onSubscribeClick: () -> Unit,
    onChannelClick: (String, String) -> Unit,
    onTimestampClick: ((Float) -> Unit)? = null,
    onVideoLinkClick: ((VideoItem) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var descSheetOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDescSheetOffsetY by animateFloatAsState(
        targetValue = descSheetOffsetY,
        animationSpec = tween(durationMillis = 200),
        label = "desc_sheet_offset"
    )

    LaunchedEffect(visible) {
        if (visible) {
            descSheetOffsetY = 0f
        }
    }

    val descHandleDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                descSheetOffsetY = (descSheetOffsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragEnd = {
                if (descSheetOffsetY > 140f) {
                    onDismiss()
                } else {
                    descSheetOffsetY = 0f
                }
            },
            onDragCancel = {
                descSheetOffsetY = 0f
            }
        )
    }

    AnimatedVisibility(
        visible = visible && !isFullscreen,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(animationSpec = tween(100)) + slideOutVertically(animationSpec = tween(150, easing = FastOutLinearInEasing)) { it },
        modifier = modifier
    ) {
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

            // Sheet container filling the space below the video, NEVER overlapping the video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset { IntOffset(0, animatedDescSheetOffsetY.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFF212121))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(descHandleDragModifier)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )
                    }

                    // Header: Deskripsi + Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(descHandleDragModifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_description),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
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

                    val descriptionText = nextQueueData?.description.orEmpty()
                    val chapters = remember(descriptionText) {
                        com.phantom.tube.data.model.VideoChapterParser.parse(descriptionText)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Video Title
                        item {
                            Text(
                                text = nextQueueData?.currentVideo?.title?.ifBlank { video.title } ?: video.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )
                        }

                        // 2. Channel info
                        item {
                            val targetChannelId = nextQueueData?.currentVideo?.channelId?.ifBlank { video.channelId } ?: video.channelId
                            val targetChannelTitle = nextQueueData?.currentVideo?.channelTitle?.ifBlank { video.channelTitle } ?: video.channelTitle
                            val currentAvatar = nextQueueData?.currentVideo?.channelAvatarUrl?.ifBlank { activeAvatarUrl } ?: activeAvatarUrl

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onChannelClick(targetChannelId, targetChannelTitle) }
                                ) {
                                    if (currentAvatar.isNotBlank()) {
                                        AsyncImage(
                                            model = currentAvatar,
                                            contentDescription = targetChannelTitle,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column {
                                        Text(
                                            text = targetChannelTitle,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val subs = nextQueueData?.channelSubscriberCountText ?: ""
                                        if (subs.isNotBlank()) {
                                            Text(
                                                text = subs,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isSubscribed) Color(0xFF272727) else Color.White)
                                        .clickable(onClick = onSubscribeClick)
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (isSubscribed) stringResource(R.string.channel_subscribed) else stringResource(R.string.channel_subscribe),
                                        color = if (isSubscribed) Color.White else Color.Black,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // 3. Stats Card (YouTube Mobile Official Style)
                        item {
                            val rawViews = nextQueueData?.fullViewCountText?.ifBlank { video.viewCountText } ?: "-"
                            val displayViews = if (rawViews.contains("ditonton", ignoreCase = true)) {
                                rawViews.replace("ditonton", "").replace("x", "").trim()
                            } else if (rawViews.contains("views", ignoreCase = true)) {
                                rawViews.replace("views", "", ignoreCase = true).trim()
                            } else rawViews

                            val rawDate = nextQueueData?.dateText?.ifBlank { video.publishedTimeText } ?: "-"
                            val displayDate = rawDate
                                .replace("Telah tayang perdana pada", "", ignoreCase = true)
                                .replace("Tayang perdana pada", "", ignoreCase = true)
                                .replace("Premiered on", "", ignoreCase = true)
                                .replace("Premiered", "", ignoreCase = true)
                                .replace("Streamed", "", ignoreCase = true)
                                .replace("Disiarkan langsung pada", "", ignoreCase = true)
                                .trim()
                                .ifBlank { rawDate }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(YouTubeSurface)
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = nextQueueData?.likeCountText?.ifBlank { "-" } ?: "-",
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(R.string.player_stat_likes), color = TextMuted, fontSize = 11.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(26.dp)
                                            .width(1.dp)
                                            .background(Color(0xFF272727))
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = displayViews.ifBlank { "-" },
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(R.string.player_stat_views), color = TextMuted, fontSize = 11.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(26.dp)
                                            .width(1.dp)
                                            .background(Color(0xFF272727))
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = displayDate.ifBlank { "-" },
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = stringResource(R.string.player_stat_published), color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // 3.5. Video Chapters (if available in description)
                        if (chapters.isNotEmpty()) {
                            item {
                                val currentTime = currentTimeSecProvider()
                                val activeChapterIndex = remember(chapters, currentTime) {
                                    chapters.indexOfLast { it.timeSec <= currentTime }.coerceAtLeast(0)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(YouTubeSurface)
                                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.player_chapters),
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${chapters.size}",
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            chapters.forEachIndexed { index, chapter ->
                                                val isActive = index == activeChapterIndex
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                                        .clickable {
                                                            onTimestampClick?.invoke(chapter.timeSec)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isActive) Color(0xFF0A78D2) else Color(0xFF2E2E2E))
                                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = chapter.timeFormatted,
                                                            color = if (isActive) Color.White else TextPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = chapter.title,
                                                        color = if (isActive) Color.White else TextSecondary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Description Content (Clean Readable Card with Interactive Links & Timestamps)
                        item {
                            val emptyDesc = stringResource(R.string.player_no_description)
                            val desc = nextQueueData?.description?.ifBlank { emptyDesc } ?: emptyDesc
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(YouTubeSurface)
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                PhantomLinkText(
                                    text = desc,
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 22.sp,
                                    onTimestampClick = onTimestampClick,
                                    onChannelClick = onChannelClick,
                                    onVideoLinkClick = onVideoLinkClick,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

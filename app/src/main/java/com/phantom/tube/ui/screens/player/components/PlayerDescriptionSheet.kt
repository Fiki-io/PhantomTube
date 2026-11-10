package com.phantom.tube.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phantom.tube.R
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.components.PhantomAnchoredPlayerSheet
import com.phantom.tube.ui.components.PhantomLinkText

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
    PhantomAnchoredPlayerSheet(
        visible = visible,
        isFullscreen = isFullscreen,
        title = stringResource(R.string.player_description),
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        val descriptionText = nextQueueData?.description.orEmpty()
        val chapters = remember(descriptionText) {
            com.phantom.tube.data.model.VideoChapterParser.parse(descriptionText)
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = navBarBottom + 32.dp)
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


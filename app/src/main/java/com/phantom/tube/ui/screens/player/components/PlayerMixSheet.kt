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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.ui.components.PhantomAnchoredPlayerSheet

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
            Box(
                modifier = Modifier.width(26.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = YouTubeRed,
                        modifier = Modifier.size(16.dp)
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
                    text = mixTitle.ifBlank { stringResource(R.string.player_up_next) },
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
    val defaultQueueTitle = stringResource(R.string.player_up_next)
    val subtitle = if (mixPlaylist.isNotEmpty()) {
        "${currentMixIndex + 1} / ${mixPlaylist.size}"
    } else {
        "${currentMixIndex + 1}"
    }

    PhantomAnchoredPlayerSheet(
        visible = visible,
        isFullscreen = isFullscreen,
        title = mixTitle.ifBlank { defaultQueueTitle },
        subtitleText = subtitle,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(visible) {
            if (visible && currentMixIndex in mixPlaylist.indices) {
                listState.animateScrollToItem(currentMixIndex)
            }
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = navBarBottom + 32.dp)
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


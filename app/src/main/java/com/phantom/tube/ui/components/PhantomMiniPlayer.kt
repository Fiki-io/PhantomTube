package com.phantom.tube.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import coil.compose.AsyncImage
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.VideoItem
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bar miniplayer melayang di atas dock navigasi dengan gesture swipe interaktif.
 */
@Composable
fun PhantomMiniPlayer(
    video: VideoItem,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentTimeSec: Float = 0f,
    durationSec: Float = 0f,
    currentTimeSecProvider: (() -> Float)? = null,
    durationSecProvider: (() -> Float)? = null,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val dismissThresholdX = 160f
    val dismissThresholdY = 80f

    // Reset offset jika video berganti
    LaunchedEffect(video.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    val currentAbsX = abs(offsetX.value)
    val dismissAlpha = (1f - (currentAbsX / 360f) - (offsetY.value / 250f)).coerceIn(0.15f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                alpha = dismissAlpha
            }
            .pointerInput(video.id) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        if (dragAmount.y < -15f && offsetY.value <= 0f && abs(offsetX.value) < 30f) {
                            change.consume()
                            onExpand()
                        } else {
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                if (dragAmount.y > 0 || offsetY.value > 0) {
                                    offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtLeast(0f))
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            when {
                                abs(offsetX.value) > dismissThresholdX -> {
                                    val targetX = if (offsetX.value > 0) 1000f else -1000f
                                    offsetX.animateTo(targetX, tween(200))
                                    onClose()
                                }
                                offsetY.value > dismissThresholdY -> {
                                    offsetY.animateTo(600f, tween(200))
                                    onClose()
                                }
                                else -> {
                                    launch {
                                        offsetX.animateTo(0f, IosSpringSpecs.Bouncy)
                                    }
                                    launch {
                                        offsetY.animateTo(0f, IosSpringSpecs.Bouncy)
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            launch { offsetX.animateTo(0f, IosSpringSpecs.Bouncy) }
                            launch { offsetY.animateTo(0f, IosSpringSpecs.Bouncy) }
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(YouTubeSurface)
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onExpand)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Thumbnail
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141414))
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )

                    if (isBuffering) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = YouTubeRed,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title and Channel
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = video.title.ifBlank { "Sedang Memutar" },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = video.channelTitle.ifBlank { "Channel" },
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .iosBounceClick(scaleDown = 0.88f, onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Close Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .iosBounceClick(scaleDown = 0.85f, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.player_close),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Real-time YouTube Red Scrubber Line
            val current = currentTimeSecProvider?.invoke() ?: currentTimeSec
            val duration = durationSecProvider?.invoke() ?: durationSec
            val safeCurrent = if (current.isNaN() || !current.isFinite()) 0f else current
            val safeDuration = if (duration.isNaN() || !duration.isFinite()) 0f else duration
            val progressFraction = if (safeDuration > 0f) {
                (safeCurrent / safeDuration).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
                    .drawBehind {
                        if (progressFraction > 0.001f) {
                            drawRect(
                                color = YouTubeRed,
                                size = Size(size.width * progressFraction, size.height)
                            )
                        }
                    }
            )
        }
    }
}

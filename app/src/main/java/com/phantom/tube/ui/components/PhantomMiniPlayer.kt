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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.core.theme.ControlBorderSubtle
import com.phantom.tube.core.theme.MiniMediaPlaceholderBg
import com.phantom.tube.core.theme.OverlayButtonTint
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.VideoItem
import kotlinx.coroutines.CoroutineStart
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
    onSkipNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var rawDragX by remember(video.id) { mutableFloatStateOf(0f) }
    var rawDragY by remember(video.id) { mutableFloatStateOf(0f) }

    val dismissThresholdX = 160f
    val dismissThresholdY = 80f

    // Reset offset jika video berganti
    LaunchedEffect(video.id) {
        rawDragX = 0f
        rawDragY = 0f
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer {
                val tx = offsetX.value + rawDragX
                val ty = offsetY.value + rawDragY
                val dismissAlpha = (1f - (abs(tx) / 360f) - (ty / 250f)).coerceIn(0.15f, 1f)
                translationX = tx
                translationY = ty
                alpha = dismissAlpha
            }
            .pointerInput(video.id) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        val curX = offsetX.value + rawDragX
                        val curY = offsetY.value + rawDragY
                        if (dragAmount.y < -15f && curY <= 0f && abs(curX) < 30f) {
                            change.consume()
                            rawDragX = 0f
                            rawDragY = 0f
                            onExpand()
                        } else {
                            change.consume()
                            rawDragX += dragAmount.x
                            val totalY = offsetY.value + rawDragY
                            if (dragAmount.y > 0f || totalY > 0f) {
                                rawDragY = (totalY + dragAmount.y).coerceAtLeast(0f) - offsetY.value
                            }
                        }
                    },
                    onDragEnd = {
                        val curX = offsetX.value + rawDragX
                        val curY = (offsetY.value + rawDragY).coerceAtLeast(0f)
                        rawDragX = 0f
                        rawDragY = 0f
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offsetX.snapTo(curX)
                            offsetY.snapTo(curY)
                            when {
                                abs(curX) > dismissThresholdX -> {
                                    val targetX = if (curX > 0f) 1000f else -1000f
                                    offsetX.animateTo(targetX, tween(200))
                                    onClose()
                                }
                                curY > dismissThresholdY -> {
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
                        val curX = offsetX.value + rawDragX
                        val curY = (offsetY.value + rawDragY).coerceAtLeast(0f)
                        rawDragX = 0f
                        rawDragY = 0f
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offsetX.snapTo(curX)
                            offsetY.snapTo(curY)
                            launch { offsetX.animateTo(0f, IosSpringSpecs.Bouncy) }
                            launch { offsetY.animateTo(0f, IosSpringSpecs.Bouncy) }
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(YouTubeSurface)
            .border(1.dp, ControlBorderSubtle, RoundedCornerShape(12.dp))
            .testTag("miniplayer_root")
            .clickable(onClick = onExpand)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Thumbnail (downsampled to 160x90 to save RAM)
                val context = LocalContext.current
                val miniThumbRequest = remember(video.thumbnailUrl, context) {
                    PhantomImageRequest.Builder(context)
                        .data(video.thumbnailUrl)
                        .size(160, 90)
                        .allowRgb565(true)
                        .build()
                }
                Box(
                    modifier = Modifier
                        .width(66.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiniMediaPlaceholderBg)
                ) {
                    PhantomAsyncImage(
                        model = miniThumbRequest,
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
                        text = video.title.ifBlank { stringResource(R.string.common_loading) },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.channelTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = video.channelTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Play / Pause Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("miniplayer_play_pause")
                        .iosBounceClick(scaleDown = 0.88f, onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(OverlayButtonTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (onSkipNext != null) {
                    // Skip Next Button (48.dp touch target)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("miniplayer_next")
                            .iosBounceClick(scaleDown = 0.88f, onClick = onSkipNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next_video),
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Close Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("miniplayer_close")
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

            // Real-time YouTube Red Scrubber Line (read state inside drawBehind to avoid recomposing miniplayer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
                    .drawBehind {
                        val current = currentTimeSecProvider?.invoke() ?: currentTimeSec
                        val duration = durationSecProvider?.invoke() ?: durationSec
                        val safeCurrent = if (current.isNaN() || !current.isFinite()) 0f else current
                        val safeDuration = if (duration.isNaN() || !duration.isFinite()) 0f else duration
                        val progressFraction = if (safeDuration > 0f) {
                            (safeCurrent / safeDuration).coerceIn(0f, 1f)
                        } else 0f
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

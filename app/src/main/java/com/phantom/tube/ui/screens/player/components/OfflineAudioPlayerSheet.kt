package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.phantom.tube.ui.components.PhantomIcons
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.GlassSheetScrim
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.MediaPlaceholderBg
import com.phantom.tube.core.theme.OverlayTrackSubtle
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.core.theme.phantomGlassSheet
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import java.util.Locale

/**
 * BottomSheet pemutar musik MP3 offline lengkap dengan cover art, slider durasi,
 * tombol kontrol (Shuffle, Prev, Play/Pause, Next, Loop), dan antrean putar (Queue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAudioPlayerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTrack by OfflineAudioPlayerManager.currentTrack.collectAsState()
    val isPlaying by OfflineAudioPlayerManager.isPlaying.collectAsState()
    val currentPositionMs by OfflineAudioPlayerManager.currentPositionMs.collectAsState()
    val durationMs by OfflineAudioPlayerManager.durationMs.collectAsState()
    val queue by OfflineAudioPlayerManager.queue.collectAsState()
    val currentIndex by OfflineAudioPlayerManager.currentIndex.collectAsState()
    val isLooping by OfflineAudioPlayerManager.isLooping.collectAsState()
    val isShuffle by OfflineAudioPlayerManager.isShuffle.collectAsState()
    val repeatMode by OfflineAudioPlayerManager.repeatMode.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val track = currentTrack ?: return

    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = GlassSheetScrim,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = null,
        shape = sheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .phantomGlassSheet(shape = sheetShape)
                .then(if (!showQueue) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("offline_audio_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.player_close),
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.downloads_title).uppercase(),
                        color = YouTubeRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = stringResource(R.string.offline_audio_track_counter, currentIndex + 1, queue.size.coerceAtLeast(1)),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = { showQueue = !showQueue },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("offline_audio_queue_btn")
                ) {
                    Icon(
                        imageVector = PhantomIcons.QueueMusic,
                        contentDescription = stringResource(R.string.player_mix_open_queue),
                        tint = if (showQueue) YouTubeRed else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!showQueue) {
                // Large Album Art Cover
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(YouTubeSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.thumbnailUrl.isNotBlank()) {
                        val coverRequest = remember(track.thumbnailUrl, context) {
                            PhantomImageRequest.Builder(context)
                                .data(track.thumbnailUrl)
                                .size(512, 512)
                                .allowRgb565(true)
                                .build()
                        }
                        PhantomAsyncImage(
                            model = coverRequest,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = PhantomIcons.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title & Channel
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track.channelTitle} • ${track.qualityLabel}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Scrubber Slider
                val maxDur = durationMs.coerceAtLeast(1)
                val displayPos = if (isUserScrubbing) scrubPositionMs.toLong() else currentPositionMs

                Slider(
                    value = displayPos.toFloat().coerceIn(0f, maxDur.toFloat()),
                    onValueChange = { newVal ->
                        isUserScrubbing = true
                        scrubPositionMs = newVal
                    },
                    onValueChangeFinished = {
                        OfflineAudioPlayerManager.seekTo(context, scrubPositionMs.toLong())
                        isUserScrubbing = false
                    },
                    valueRange = 0f..maxDur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = YouTubeRed,
                        activeTrackColor = YouTubeRed,
                        inactiveTrackColor = OverlayTrackSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(displayPos),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Toggle
                    IconButton(onClick = {
                        OfflineAudioPlayerManager.toggleShuffle(context)
                        Toast.makeText(
                            context,
                            if (!isShuffle) context.getString(R.string.downloads_shuffle_on) else context.getString(R.string.downloads_shuffle_off),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = PhantomIcons.Shuffle,
                            contentDescription = stringResource(R.string.downloads_shuffle_button),
                            tint = if (isShuffle) YouTubeRed else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous Button
                    IconButton(
                        onClick = { OfflineAudioPlayerManager.playPrevious(context) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("offline_audio_prev_btn")
                    ) {
                        Icon(
                            imageVector = PhantomIcons.SkipPrevious,
                            contentDescription = stringResource(R.string.player_prev_video),
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play / Pause Main Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("offline_audio_play_pause_btn")
                            .clip(CircleShape)
                            .background(YouTubeRed)
                            .clickable { OfflineAudioPlayerManager.togglePlayPause(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) PhantomIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = { OfflineAudioPlayerManager.playNext(context) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("offline_audio_next_btn")
                    ) {
                        Icon(
                            imageVector = PhantomIcons.SkipNext,
                            contentDescription = stringResource(R.string.player_next_video),
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Repeat Toggle (Cycle OFF -> ALL -> ONE)
                    IconButton(onClick = {
                        OfflineAudioPlayerManager.toggleRepeatMode(context)
                        val msg = when (OfflineAudioPlayerManager.repeatMode.value) {
                            com.phantom.tube.player.offline.OfflineRepeatMode.OFF -> context.getString(R.string.downloads_repeat_off)
                            com.phantom.tube.player.offline.OfflineRepeatMode.ALL -> context.getString(R.string.downloads_repeat_all)
                            com.phantom.tube.player.offline.OfflineRepeatMode.ONE -> context.getString(R.string.downloads_repeat_one)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = if (repeatMode == com.phantom.tube.player.offline.OfflineRepeatMode.ONE) PhantomIcons.RepeatOne else PhantomIcons.Repeat,
                            contentDescription = stringResource(R.string.downloads_repeat_all),
                            tint = if (repeatMode != com.phantom.tube.player.offline.OfflineRepeatMode.OFF) YouTubeRed else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                // Queue List View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_queue_format, queue.size),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.player_auto_play),
                            color = YouTubeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { idx, item ->
                            val isCurrentItem = idx == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrentItem) GlassSurface else Color.Transparent)
                                    .clickable {
                                        OfflineAudioPlayerManager.playQueue(context, queue, idx)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MediaPlaceholderBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.thumbnailUrl.isNotBlank()) {
                                        val itemThumbRequest = remember(item.thumbnailUrl, context) {
                                            PhantomImageRequest.Builder(context)
                                                .data(item.thumbnailUrl)
                                                .size(96, 96)
                                                .allowRgb565(true)
                                                .build()
                                        }
                                        PhantomAsyncImage(
                                            model = itemThumbRequest,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (isCurrentItem && isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = PhantomIcons.GraphicEq,
                                                contentDescription = null,
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isCurrentItem) YouTubeRed else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrentItem) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.channelTitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = item.qualityLabel,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

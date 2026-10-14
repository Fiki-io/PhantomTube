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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import android.widget.Toast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import coil.compose.AsyncImage
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YouTubeDark,
        tonalElevation = 8.dp,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
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

                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
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
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
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
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
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
                            if (!isShuffle) "Acak lagu aktif" else "Acak lagu nonaktif",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Acak",
                            tint = if (isShuffle) YouTubeRed else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous Button
                    IconButton(
                        onClick = { OfflineAudioPlayerManager.playPrevious(context) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Lagu Sebelumnya",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play / Pause Main Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(YouTubeRed)
                            .clickable { OfflineAudioPlayerManager.togglePlayPause(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = { OfflineAudioPlayerManager.playNext(context) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
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
                            imageVector = if (repeatMode == com.phantom.tube.player.offline.OfflineRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
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
                                        .background(Color(0xFF181818)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.thumbnailUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
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
                                                imageVector = Icons.Default.GraphicEq,
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
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

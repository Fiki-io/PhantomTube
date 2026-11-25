package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import com.phantom.tube.core.theme.MediaPlaceholderBg
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import com.phantom.tube.ui.components.PhantomMotion
import com.phantom.tube.ui.components.iosBounceClick

@Composable
fun OfflineMiniPlayer(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by OfflineAudioPlayerManager.currentTrack.collectAsState()
    val isPlaying by OfflineAudioPlayerManager.isPlaying.collectAsState()
    val currentPositionMs by OfflineAudioPlayerManager.currentPositionMs.collectAsState()
    val durationMs by OfflineAudioPlayerManager.durationMs.collectAsState()

    var showFullSheet by remember { mutableStateOf(false) }

    val track = currentTrack ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .phantomGlassSurface(shape = RoundedCornerShape(16.dp))
            .clickable { showFullSheet = true }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Thumbnail
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MediaPlaceholderBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.thumbnailUrl.isNotBlank()) {
                        val thumbRequest = remember(track.thumbnailUrl, context) {
                            PhantomImageRequest.Builder(context)
                                .data(track.thumbnailUrl)
                                .size(128, 128)
                                .allowRgb565(true)
                                .build()
                        }
                        PhantomAsyncImage(
                            model = thumbRequest,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.channelTitle} • ${stringResource(R.string.downloads_saved_offline)}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .iosBounceClick(scaleDown = 0.88f) {
                            OfflineAudioPlayerManager.togglePlayPause(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "offline_play_pause_icon"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Next Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .iosBounceClick(scaleDown = 0.88f) {
                            OfflineAudioPlayerManager.playNext(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_next_video),
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Close Button (48.dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .iosBounceClick(scaleDown = 0.85f) {
                            OfflineAudioPlayerManager.stop(context)
                        },
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

            // Slim Bottom Progress Bar
            val progress = if (durationMs > 0) {
                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = YouTubeRed,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }

    if (showFullSheet) {
        OfflineAudioPlayerSheet(
            onDismiss = { showFullSheet = false }
        )
    }
}

package com.phantom.tube.ui.screens.player.components

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phantom.tube.core.database.DownloadEntity
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.util.findActivity
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import com.phantom.tube.player.service.PhantomMediaService
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Pemutar video MP4 offline bawaan di dalam aplikasi Phantom.
 * Menggunakan VideoView standar bawaan Android OS (0 KB library tambahan).
 * Mendukung rotasi Layar Penuh (Landscape), gestur double-tap seek 10 detik,
 * tombol Replay/Forward 10s, dan sinkronisasi audio terisolasi.
 */
@Composable
fun OfflineVideoPlayerDialog(
    video: DownloadEntity,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isManualLandscape by remember { mutableStateOf(false) }
    val isLandscape = isDeviceLandscape || isManualLandscape

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }
    var seekNoticeText by remember { mutableStateOf<String?>(null) }

    // Isolasi Audio: Pastikan pemutar musik online & offline dihentikan
    LaunchedEffect(Unit) {
        OfflineAudioPlayerManager.stop(context)
        try {
            context.stopService(Intent(context, PhantomMediaService::class.java))
        } catch (_: Exception) {}
    }

    // Auto-dismiss seek notice
    LaunchedEffect(seekNoticeText) {
        if (seekNoticeText != null) {
            delay(1200)
            seekNoticeText = null
        }
    }

    // Auto hide controls after 3.5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Playback time progress loop
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying && !isUserScrubbing) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                }
            }
            delay(300)
        }
    }

    // Immersive Mode saat mode Landscape
    DisposableEffect(isLandscape) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }

    // Reset orientasi & hentikan video saat dialog ditutup
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            videoViewRef?.stopPlayback()
            videoViewRef = null
        }
    }

    val toggleFullscreen = {
        val targetLandscape = !isLandscape
        isManualLandscape = targetLandscape
        activity?.requestedOrientation = if (targetLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val closePlayer = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDismiss()
    }

    BackHandler {
        if (isLandscape) {
            isManualLandscape = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            closePlayer()
        }
    }

    Dialog(
        onDismissRequest = closePlayer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Android VideoView Surface
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this

                        var uriSet = false
                        if (video.fileUri.isNotBlank()) {
                            try {
                                setVideoURI(Uri.parse(video.fileUri))
                                uriSet = true
                            } catch (_: Exception) {}
                        }
                        if (!uriSet && video.filePath.isNotBlank()) {
                            val f = File(video.filePath)
                            if (f.exists()) {
                                setVideoPath(f.absolutePath)
                                uriSet = true
                            }
                        }
                        if (!uriSet) {
                            setVideoURI(Uri.parse(video.fileUri))
                        }

                        setOnPreparedListener { mp ->
                            // Pastikan tidak ada audio lain saat video mulai
                            OfflineAudioPlayerManager.stop(ctx)
                            isBuffering = false
                            durationMs = mp.duration
                            mp.isLooping = false
                            start()
                            isPlaying = true
                        }

                        setOnCompletionListener {
                            isPlaying = false
                            showControls = true
                        }

                        setOnErrorListener { _, _, _ ->
                            isBuffering = false
                            isPlaying = false
                            true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Touch Gestures: Single Tap (Controls) & Double Tap (Seek -10s / +10s)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                videoViewRef?.let { vv ->
                                    val current = vv.currentPosition
                                    val maxDur = durationMs.coerceAtLeast(1)
                                    if (offset.x < size.width / 2) {
                                        val newPos = (current - 10000).coerceAtLeast(0)
                                        vv.seekTo(newPos)
                                        currentPositionMs = newPos
                                        seekNoticeText = context.getString(R.string.player_seek_notice_rewind)
                                    } else {
                                        val newPos = (current + 10000).coerceAtMost(maxDur)
                                        vv.seekTo(newPos)
                                        currentPositionMs = newPos
                                        seekNoticeText = context.getString(R.string.player_seek_notice_forward)
                                    }
                                }
                            }
                        )
                    }
            )

            // Buffering Indicator
            if (isBuffering) {
                CircularProgressIndicator(
                    color = YouTubeRed,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }

            // Floating Seek Notice Pill
            AnimatedVisibility(
                visible = seekNoticeText != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = seekNoticeText ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Bar Gradient
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.88f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = closePlayer) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.player_close),
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.channelTitle} • ${video.qualityLabel}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onOpenExternal) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = stringResource(R.string.downloads_play_external),
                                tint = TextSecondary
                            )
                        }
                    }

                    // Center Control Row: Replay10, Play/Pause, Forward10
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                        vv.seekTo(newPos)
                                        currentPositionMs = newPos
                                        seekNoticeText = context.getString(R.string.player_seek_notice_rewind)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = stringResource(R.string.seek_rewind_10s),
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Center Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(YouTubeRed, CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Forward 10s Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable {
                                    videoViewRef?.let { vv ->
                                        val maxDur = durationMs.coerceAtLeast(1)
                                        val newPos = (vv.currentPosition + 10000).coerceAtMost(maxDur)
                                        vv.seekTo(newPos)
                                        currentPositionMs = newPos
                                        seekNoticeText = context.getString(R.string.player_seek_notice_forward)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = stringResource(R.string.seek_forward_10s),
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Bottom Bar Gradient & Scrubber + Fullscreen Toggle
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        val displayPos = if (isUserScrubbing) scrubPositionMs.toInt() else currentPositionMs
                        val maxDur = durationMs.coerceAtLeast(1)

                        Slider(
                            value = displayPos.toFloat().coerceIn(0f, maxDur.toFloat()),
                            onValueChange = { newVal ->
                                isUserScrubbing = true
                                scrubPositionMs = newVal
                            },
                            onValueChangeFinished = {
                                videoViewRef?.seekTo(scrubPositionMs.toInt())
                                currentPositionMs = scrubPositionMs.toInt()
                                isUserScrubbing = false
                            },
                            valueRange = 0f..maxDur.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = YouTubeRed,
                                activeTrackColor = YouTubeRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatDuration(displayPos.toLong()),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " / ${formatDuration(durationMs.toLong())}",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            // Fullscreen / Landscape Toggle Button
                            IconButton(
                                onClick = toggleFullscreen,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = stringResource(if (isLandscape) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

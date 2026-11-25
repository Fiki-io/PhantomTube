package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phantom.tube.R
import com.phantom.tube.core.theme.OverlayControlScrim
import com.phantom.tube.core.theme.OverlayTrackSubtle
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.ui.components.PhantomMotion
import com.phantom.tube.ui.components.iosBounceClick

/**
 * Overlay interaktif di atas floating live miniplayer kotak 16:9 ala YouTube.
 * Menyediakan tombol Expand (Kembali ke Layar Penuh), Close (X), Play/Pause, Next,
 * garis progress tipis (dibaca saat draw phase untuk cegah recomposition), serta gesture drag 2D.
 */
@Composable
fun BoxScope.FloatingMiniPlayerOverlay(
    isPlaying: Boolean,
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onSkipNext: (() -> Unit)? = null
) {
    // 1. Lapisan Penangkap Sentuh Tap & Drag 2D (Di belakang tombol kontrol)
    Box(
        modifier = Modifier
            .matchParentSize()
            .testTag("floating_miniplayer_surface")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (kotlin.math.abs(dragAmount.x) > 0.2f || kotlin.math.abs(dragAmount.y) > 0.2f) {
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand
            )
    )

    // 2. Tombol Expand (Kembali ke Layar Penuh) di Pojok Kiri Atas
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(48.dp)
            .testTag("floating_miniplayer_expand")
            .iosBounceClick(scaleDown = 0.88f, onClick = onExpand),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(OverlayControlScrim),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = stringResource(R.string.player_expand),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // 3. Tombol Close (X) di Pojok Kanan Atas
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(48.dp)
            .testTag("floating_miniplayer_close")
            .iosBounceClick(scaleDown = 0.88f, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(OverlayControlScrim),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // 4. Tombol Play/Pause & Skip Next di Pojok Kanan Bawah
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag("floating_miniplayer_play_pause")
                .iosBounceClick(scaleDown = 0.88f, onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OverlayControlScrim),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = { PhantomMotion.IconSwapTransition },
                    label = "floating_play_pause_icon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onSkipNext != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("floating_miniplayer_next")
                    .iosBounceClick(scaleDown = 0.88f, onClick = onSkipNext),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(OverlayControlScrim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_next_video),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // 5. Garis Progress Tipis di Bagian Paling Bawah (drawBehind avoids periodic recompositions)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .align(Alignment.BottomCenter)
            .background(OverlayTrackSubtle)
            .drawBehind {
                val current = currentTimeSecProvider()
                val duration = durationSecProvider()
                val safeCurrent = if (current.isNaN() || !current.isFinite()) 0f else current
                val safeDuration = if (duration.isNaN() || !duration.isFinite()) 0f else duration
                val progressFrac = if (safeDuration > 0f) {
                    (safeCurrent / safeDuration).coerceIn(0f, 1f)
                } else 0f
                if (progressFrac > 0.001f) {
                    drawRect(
                        color = YouTubeRed,
                        size = Size(size.width * progressFrac, size.height)
                    )
                }
            }
    )
}


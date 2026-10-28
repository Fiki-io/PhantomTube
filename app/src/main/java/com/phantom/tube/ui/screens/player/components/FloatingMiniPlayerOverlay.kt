package com.phantom.tube.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phantom.tube.R
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.ui.components.iosBounceClick

/**
 * Overlay interaktif di atas floating live miniplayer kotak 16:9 ala YouTube.
 * Menyediakan tombol Close (X), Play/Pause, indikator progress tipis,
 * tap untuk memperbesar kembali ke layar penuh, dan gesture drag horizontal untuk dismiss.
 */
@Composable
fun BoxScope.FloatingMiniPlayerOverlay(
    isPlaying: Boolean,
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onDragDismiss: (Float) -> Unit,
    onDragDismissEnd: () -> Unit
) {
    val currentSec = currentTimeSecProvider()
    val durSec = durationSecProvider()
    val progressFrac = if (durSec > 0f) (currentSec / durSec).coerceIn(0f, 1f) else 0f

    // 1. Lapisan Utama Penangkap Klik Expand & Drag Dismiss
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDismiss(dragAmount)
                    },
                    onDragEnd = {
                        onDragDismissEnd()
                    },
                    onDragCancel = {
                        onDragDismissEnd()
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand
            )
    )

    // 2. Tombol Close (X) di Pojok Kiri Atas
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .iosBounceClick(scaleDown = 0.88f, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.action_close),
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }

    // 3. Tombol Play / Pause di Pojok Kanan Bawah
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .iosBounceClick(scaleDown = 0.88f, onClick = onTogglePlayPause),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }

    // 4. Garis Progress Tipis di Bagian Paling Bawah
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.BottomCenter)
            .background(Color(0x44FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = progressFrac)
                .height(2.dp)
                .background(YouTubeRed)
        )
    }
}


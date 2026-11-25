package com.phantom.tube.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.phantom.tube.ui.components.PhantomIcons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomScrubber
import java.util.Locale

fun getShortQualityBadge(code: String): String {
    return when (code.lowercase()) {
        "auto" -> "Auto"
        "highres" -> "High"
        "hd2160" -> "4K"
        "hd1440" -> "1440p"
        "hd1080" -> "1080p"
        "hd720" -> "720p"
        "large" -> "480p"
        "medium" -> "360p"
        "small" -> "240p"
        "tiny" -> "144p"
        else -> if (code.endsWith("p")) code else code.take(5).uppercase()
    }
}

fun formatPlayerSeconds(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun PlayerScrubberControls(
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    bufferedFractionProvider: () -> Float,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Boolean, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSec = currentTimeSecProvider()
    val durSec = durationSecProvider()
    val bufFrac = bufferedFractionProvider()
    val progFrac = if (durSec > 0f) (currentSec / durSec).coerceIn(0f, 1f) else 0f
    val formattedCurrent = remember(currentSec.toLong()) { formatPlayerSeconds(currentSec.toLong()) }
    val formattedDuration = remember(durSec.toLong()) { formatPlayerSeconds(durSec.toLong()) }

    Column(
        modifier = modifier.padding(
            start = if (isFullscreen) 20.dp else 0.dp,
            end = if (isFullscreen) 20.dp else 0.dp,
            bottom = if (isFullscreen) 10.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isFullscreen) 4.dp else 12.dp, end = if (isFullscreen) 4.dp else 12.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$formattedCurrent / $formattedDuration",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            PhantomIconButton(
                icon = if (isFullscreen) PhantomIcons.FullscreenExit else PhantomIcons.Fullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                size = 48.dp,
                iconSize = 20.dp,
                onClick = onToggleFullscreen
            )
        }

        PhantomScrubber(
            progress = progFrac,
            bufferedFraction = bufFrac,
            showThumb = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            onSeek = onSeek,
            onScrubbing = onScrubbing
        )
    }
}

@Composable
fun PlayerIdleProgressBar(
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    bufferedFractionProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val currentSec = currentTimeSecProvider()
    val durSec = durationSecProvider()
    val bufFrac = bufferedFractionProvider()
    val progFrac = if (durSec > 0f) (currentSec / durSec).coerceIn(0f, 1f) else 0f

    PhantomScrubber(
        progress = progFrac,
        bufferedFraction = bufFrac,
        showThumb = false,
        modifier = modifier
    )
}

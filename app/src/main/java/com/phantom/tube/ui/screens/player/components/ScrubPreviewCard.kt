package com.phantom.tube.ui.screens.player.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.player.StoryboardData
import com.phantom.tube.player.StoryboardHelper

@Composable
fun ScrubPreviewCard(
    visible: Boolean,
    targetSeconds: Float,
    durationSeconds: Float,
    touchX: Float,
    parentWidthPx: Float,
    storyboardData: StoryboardData?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(visible, targetSeconds, storyboardData) {
        if (!visible) {
            frameBitmap = null
            return@LaunchedEffect
        }
        if (storyboardData != null && durationSeconds > 0f) {
            val frame = storyboardData.getFrame(targetSeconds, durationSeconds)
            if (frame != null) {
                val bmp = StoryboardHelper.loadFrameBitmap(context, frame)
                if (bmp != null) {
                    frameBitmap = bmp
                }
            }
        }
    }

    val currentBmp = frameBitmap
    val hasFrame = currentBmp != null && !currentBmp.isRecycled

    val cardWidth = if (hasFrame) 132.dp else 116.dp
    val cardHeight = if (hasFrame) 78.dp else 34.dp

    val touchXDp = with(density) { touchX.toDp() }
    val parentWidthDp = with(density) { parentWidthPx.toDp() }

    val offsetX = if (parentWidthDp > cardWidth + 16.dp) {
        (touchXDp - (cardWidth / 2)).coerceIn(8.dp, parentWidthDp - cardWidth - 8.dp)
    } else {
        8.dp
    }

    // Time formatting helper
    val totalSec = targetSeconds.toLong().coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val timeText = if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }

    val totalDurSec = durationSeconds.toLong().coerceAtLeast(0)
    val durH = totalDurSec / 3600
    val durM = (totalDurSec % 3600) / 60
    val durS = totalDurSec % 60
    val durText = if (durH > 0) {
        String.format("%d:%02d:%02d", durH, durM, durS)
    } else {
        String.format("%02d:%02d", durM, durS)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        if (hasFrame && currentBmp != null) {
            // Storyboard Scene Frame Card
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(cardWidth)
                    .height(cardHeight)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = Color(0x66000000),
                        spotColor = Color(0xDD000000)
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(YouTubeDark)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
            ) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = "Preview scene",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = timeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Sleek Floating Live Scrubber Time Pill (Never blocks video player)
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(cardWidth)
                    .height(cardHeight)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(17.dp),
                        ambientColor = Color(0x66000000),
                        spotColor = Color(0xDD000000)
                    )
                    .clip(RoundedCornerShape(17.dp))
                    .background(YouTubeSurface)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(17.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(YouTubeRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " / $durText",
                        color = Color(0xFFBBBBBB),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

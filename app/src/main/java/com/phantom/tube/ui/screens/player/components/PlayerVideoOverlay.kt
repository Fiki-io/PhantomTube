package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.phantom.tube.R
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.core.theme.phantomSurface
import com.phantom.tube.player.PlayerState
import com.phantom.tube.ui.components.ALL_STANDARD_QUALITIES
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.VideoQualityOption
import com.phantom.tube.ui.components.getLocalizedQualityLabel
import com.phantom.tube.ui.components.iosBounceClick
import com.phantom.tube.ui.screens.player.SeekFeedbackDirection

@Composable
fun BoxScope.AudioOnlyViewportOverlay(
    isAudioOnly: Boolean,
    isControlsVisible: Boolean,
    onDisableAudioOnly: () -> Unit
) {
    if (!isAudioOnly) return
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0xFF07070A)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isControlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .phantomSurface(
                            shape = CircleShape,
                            borderWidth = 1.dp,
                            tintColor = YouTubeSurface,
                            surfaceAlpha = 0.95f,
                            accentGlow = Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.settings_audio_only_player_title),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_audio_only_player_desc),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = if (isControlsVisible) 48.dp else 18.dp)
            .zIndex(28f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE61F1F24))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .iosBounceClick(scaleDown = 0.92f) {
                onDisableAudioOnly()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.player_audio_only_show_video),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BoxScope.PlayerLockedOverlay(
    visible: Boolean,
    onUnlock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = IosSpringSpecs.Bouncy),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.88f, animationSpec = tween(150)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .zIndex(65f)
    ) {
        Box(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xE61A1A1E))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                .iosBounceClick(scaleDown = 0.92f) {
                    onUnlock()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.player_unlock_screen),
                    tint = YouTubeRed,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.player_screen_locked_tap_to_unlock),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BoxScope.PlayerHold2xSpeedOverlay(
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f, animationSpec = IosSpringSpecs.Bouncy),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 14.dp)
            .zIndex(55f)
    ) {
        Box(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xDD141418))
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "2x Speed",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BoxScope.PlayerVerticalGestureHudOverlay(
    gestureType: String?,
    gestureValue: Float
) {
    AnimatedVisibility(
        visible = gestureType != null,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.88f, animationSpec = IosSpringSpecs.Bouncy),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 18.dp)
            .zIndex(56f)
    ) {
        val isVol = gestureType == "volume"
        val pct = (gestureValue * 100).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .shadow(14.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE616161A))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isVol) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(18.dp)
                )
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(gestureValue.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(YouTubeRed)
                    )
                }
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.PlayerLiveCaptionsOverlay(
    captionText: String,
    isControlsVisible: Boolean
) {
    if (captionText.isBlank()) return
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = if (isControlsVisible) 44.dp else 14.dp,
                start = 24.dp,
                end = 24.dp
            )
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = captionText,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BoxScope.DoubleTapSeekFeedbackOverlay(
    seekAnimationSide: SeekFeedbackDirection?,
    seekAccumulatedSeconds: Int
) {
    AnimatedVisibility(
        visible = seekAnimationSide == SeekFeedbackDirection.REWIND,
        enter = fadeIn(tween(100)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = IosSpringSpecs.Bouncy
        ),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .align(Alignment.CenterStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                .background(Color(0x66000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "-${stringResource(R.string.settings_seek_seconds, seekAccumulatedSeconds.toString())}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    AnimatedVisibility(
        visible = seekAnimationSide == SeekFeedbackDirection.FORWARD,
        enter = fadeIn(tween(100)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = IosSpringSpecs.Bouncy
        ),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200)),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .align(Alignment.CenterEnd)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100))
                .background(Color(0x66000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${stringResource(R.string.settings_seek_seconds, seekAccumulatedSeconds.toString())}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BoxScope.PlayerBufferingErrorOverlay(
    isBuffering: Boolean,
    errorCode: String?
) {
    if (isBuffering) {
        CircularProgressIndicator(
            color = YouTubeRed,
            strokeWidth = 3.dp,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center)
        )
    } else if (errorCode != null) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .phantomSurface(
                    shape = RoundedCornerShape(16.dp),
                    borderWidth = 1.dp,
                    tintColor = Color(0xFF261010),
                    surfaceAlpha = 0.85f
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = errorCode,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BoxScope.PlayerToastNoticeOverlay(
    noticeText: String?,
    isFullscreen: Boolean
) {
    AnimatedVisibility(
        visible = noticeText != null,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.88f, animationSpec = IosSpringSpecs.Bouncy),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = if (isFullscreen) 60.dp else 44.dp)
            .zIndex(50f)
    ) {
        Box(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x99000000))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xEE1A1A1D))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = noticeText ?: "",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BoxScope.PlayerControlsOverlay(
    isControlsVisible: Boolean,
    isFullscreen: Boolean,
    playerState: PlayerState,
    isCaptionsEnabled: Boolean,
    hasCaptions: Boolean,
    isQualityMenuExpanded: Boolean,
    isZoomToFill: Boolean,
    doubleTapSeekSeconds: Int = 10,
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    bufferedFractionProvider: () -> Float,
    onMinimizeOrExitFullscreen: () -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleQualityMenu: (Boolean) -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onToggleZoom: () -> Unit,
    onLockControls: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkipPrevious: () -> Unit,
    onRewind10s: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10s: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Boolean, Float, Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val centerControlsScale by animateFloatAsState(
        targetValue = if (isControlsVisible) 1.0f else 0.90f,
        animationSpec = IosSpringSpecs.Gentle,
        label = "centerControlsScale"
    )

    AnimatedVisibility(
        visible = isControlsVisible,
        enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
        exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        ) {
            // Top Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        start = if (isFullscreen) 24.dp else 12.dp,
                        end = if (isFullscreen) 24.dp else 12.dp,
                        top = if (isFullscreen) 16.dp else 10.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhantomIconButton(
                    icon = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.player_minimize),
                    size = 38.dp,
                    iconSize = 24.dp,
                    onClick = onMinimizeOrExitFullscreen
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Closed Captions Toggle
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCaptionsEnabled && hasCaptions) Color(0x33FF0033) else Color(0x44272727))
                            .border(
                                width = if (isCaptionsEnabled && hasCaptions) 1.2.dp else 0.5.dp,
                                color = if (isCaptionsEnabled && hasCaptions) YouTubeRed else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .iosBounceClick(scaleDown = 0.90f, onClick = onToggleCaptions),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = stringResource(if (isCaptionsEnabled) R.string.player_captions_turn_off else R.string.player_captions_turn_on),
                            tint = if (isCaptionsEnabled && hasCaptions) YouTubeRed
                            else if (!hasCaptions) Color.White.copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Quality Dropdown Button
                    Box {
                        val currentQualityBadge = remember(playerState.currentQuality) {
                            getShortQualityBadge(playerState.currentQuality)
                        }
                        val isHighQuality = playerState.currentQuality in listOf("highres", "hd2160", "hd1440", "hd1080", "hd720")

                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isQualityMenuExpanded) Color(0x33FF0033)
                                    else if (isHighQuality) Color(0x33FFFFFF)
                                    else Color(0x44272727)
                                )
                                .border(
                                    width = if (isQualityMenuExpanded) 1.2.dp else 0.5.dp,
                                    color = if (isQualityMenuExpanded) YouTubeRed else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onToggleQualityMenu(!isQualityMenuExpanded)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = stringResource(R.string.player_quality_format, currentQualityBadge),
                                    tint = if (isQualityMenuExpanded) YouTubeRed else if (isHighQuality) Color.White else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = currentQualityBadge,
                                    color = if (isQualityMenuExpanded) YouTubeRed else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isQualityMenuExpanded,
                            onDismissRequest = { onToggleQualityMenu(false) },
                            modifier = Modifier
                                .background(Color(0xFF212121), RoundedCornerShape(12.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        ) {
                            val autoQualityLabel = stringResource(R.string.settings_quality_auto)
                            val qualityOptions = remember(playerState.availableQualities, autoQualityLabel) {
                                if (playerState.availableQualities.isNotEmpty()) {
                                    val list = mutableListOf(VideoQualityOption("auto", autoQualityLabel))
                                    val availableSet = playerState.availableQualities.toSet()
                                    ALL_STANDARD_QUALITIES.filter { it.code != "auto" && availableSet.contains(it.code) }.forEach {
                                        list.add(it)
                                    }
                                    if (list.size == 1) ALL_STANDARD_QUALITIES else list
                                } else {
                                    ALL_STANDARD_QUALITIES
                                }
                            }

                            qualityOptions.forEach { opt ->
                                val isSelected = (opt.code == playerState.currentQuality)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getLocalizedQualityLabel(opt.code),
                                            color = if (isSelected) YouTubeRed else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.player_selected),
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = { onSelectQuality(opt) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Zoom to Fill Toggle
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isZoomToFill) Color.White.copy(alpha = 0.22f) else Color(0x44272727))
                            .border(
                                width = if (isZoomToFill) 1.2.dp else 0.5.dp,
                                color = if (isZoomToFill) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .iosBounceClick(scaleDown = 0.90f, onClick = onToggleZoom),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isZoomToFill) Icons.Default.FitScreen else Icons.Default.CropFree,
                            contentDescription = stringResource(if (isZoomToFill) R.string.player_zoom_original else R.string.player_zoom_fill),
                            tint = if (isZoomToFill) Color.White else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Screen Lock Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x44272727))
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .iosBounceClick(scaleDown = 0.90f, onClick = onLockControls),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.player_lock_screen),
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Playback Speed Quick Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF272727))
                            .iosBounceClick(scaleDown = 0.90f, onClick = onCycleSpeed)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${playerState.playbackSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Settings Button
                    PhantomIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        size = 36.dp,
                        iconSize = 20.dp,
                        onClick = onOpenSettings
                    )
                }
            }

            // Center Transport Controls
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = centerControlsScale
                        scaleY = centerControlsScale
                    },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhantomIconButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_prev_video),
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onSkipPrevious
                )

                val rewindIcon = when (doubleTapSeekSeconds) {
                    5 -> Icons.Default.Replay5
                    10 -> Icons.Default.Replay10
                    30 -> Icons.Default.Replay30
                    else -> Icons.Default.FastRewind
                }
                val forwardIcon = when (doubleTapSeekSeconds) {
                    5 -> Icons.Default.Forward5
                    10 -> Icons.Default.Forward10
                    30 -> Icons.Default.Forward30
                    else -> Icons.Default.FastForward
                }

                PhantomIconButton(
                    icon = rewindIcon,
                    contentDescription = "-${stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString())}",
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onRewind10s
                )

                PhantomIconButton(
                    icon = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (playerState.isPlaying) R.string.player_pause else R.string.player_play),
                    size = 64.dp,
                    iconSize = 36.dp,
                    onClick = onTogglePlayPause
                )

                PhantomIconButton(
                    icon = forwardIcon,
                    contentDescription = "+${stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString())}",
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onForward10s
                )

                PhantomIconButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next_video),
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onSkipNext
                )
            }

            // Bottom Scrubber & Fullscreen
            PlayerScrubberControls(
                currentTimeSecProvider = currentTimeSecProvider,
                durationSecProvider = durationSecProvider,
                bufferedFractionProvider = bufferedFractionProvider,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onSeek = onSeek,
                onScrubbing = onScrubbing,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

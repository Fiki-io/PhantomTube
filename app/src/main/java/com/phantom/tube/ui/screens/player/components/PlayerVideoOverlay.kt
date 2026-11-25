package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import java.util.Locale
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.phantom.tube.ui.components.PhantomIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.phantom.tube.R
import com.phantom.tube.core.theme.AudioOnlyBackdrop
import com.phantom.tube.core.theme.MenuBorderSubtle
import com.phantom.tube.core.theme.MenuSurfaceDark
import com.phantom.tube.core.theme.PlayerErrorSurface
import com.phantom.tube.core.theme.PlayerOverlayControlActiveBg
import com.phantom.tube.core.theme.PlayerOverlayControlBg
import com.phantom.tube.core.theme.PlayerOverlayControlBorder
import com.phantom.tube.core.theme.PlayerOverlayZoomActiveBg
import com.phantom.tube.core.theme.PlayerSeekRippleScrim
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.core.theme.phantomSurface
import com.phantom.tube.player.PlayerState
import com.phantom.tube.ui.components.ALL_STANDARD_QUALITIES
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomMotion
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

    val pillBottomPadding by animateDpAsState(
        targetValue = if (isControlsVisible) 48.dp else 18.dp,
        animationSpec = IosSpringSpecs.TopBarDp,
        label = "audio_only_pill_bottom"
    )
    val pillShape = remember { RoundedCornerShape(20.dp) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(AudioOnlyBackdrop),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isControlsVisible,
            enter = PhantomMotion.PlayerControlsEnter,
            exit = PhantomMotion.PlayerControlsExit
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .phantomGlassSurface(shape = CircleShape, shadowElevation = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PhantomIcons.Headphones,
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
            .padding(bottom = pillBottomPadding.coerceAtLeast(0.dp))
            .zIndex(28f)
            .phantomGlassSurface(shape = pillShape, shadowElevation = 10.dp)
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
                imageVector = PhantomIcons.Headphones,
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
    val unlockPillShape = remember { RoundedCornerShape(24.dp) }
    AnimatedVisibility(
        visible = visible,
        enter = PhantomMotion.OverlayPillEnter,
        exit = PhantomMotion.OverlayPillExit,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .zIndex(65f)
    ) {
        Box(
            modifier = Modifier
                .phantomGlassSurface(shape = unlockPillShape, shadowElevation = 14.dp)
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
                    imageVector = PhantomIcons.LockOpen,
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
fun BoxScope.DoubleTapSeekFeedbackOverlay(
    seekAnimationSide: SeekFeedbackDirection?,
    seekAccumulatedSeconds: Int
) {
    val rewindShape = remember { RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100) }
    val forwardShape = remember { RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100) }

    AnimatedVisibility(
        visible = seekAnimationSide == SeekFeedbackDirection.REWIND,
        enter = PhantomMotion.SeekFeedbackEnter,
        exit = PhantomMotion.SeekFeedbackExit,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .align(Alignment.CenterStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(rewindShape)
                .background(PlayerSeekRippleScrim)
                .border(1.dp, PlayerOverlayControlBorder, rewindShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = PhantomIcons.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = seekAccumulatedSeconds,
                    transitionSpec = { PhantomMotion.IconSwapTransition },
                    label = "seek_rewind_seconds"
                ) { seconds ->
                    Text(
                        text = "-${stringResource(R.string.settings_seek_seconds, seconds.toString())}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = seekAnimationSide == SeekFeedbackDirection.FORWARD,
        enter = PhantomMotion.SeekFeedbackEnter,
        exit = PhantomMotion.SeekFeedbackExit,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .align(Alignment.CenterEnd)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(forwardShape)
                .background(PlayerSeekRippleScrim)
                .border(1.dp, PlayerOverlayControlBorder, forwardShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = PhantomIcons.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = seekAccumulatedSeconds,
                    transitionSpec = { PhantomMotion.IconSwapTransition },
                    label = "seek_forward_seconds"
                ) { seconds ->
                    Text(
                        text = "+${stringResource(R.string.settings_seek_seconds, seconds.toString())}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.PlayerBufferingErrorOverlay(
    isBuffering: Boolean,
    errorCode: String?
) {
    AnimatedVisibility(
        visible = isBuffering,
        enter = PhantomMotion.PopScaleEnter,
        exit = PhantomMotion.PopScaleExit,
        modifier = Modifier.align(Alignment.Center)
    ) {
        CircularProgressIndicator(
            color = YouTubeRed,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
    }

    AnimatedVisibility(
        visible = !isBuffering && errorCode != null,
        enter = PhantomMotion.OverlayPillEnter,
        exit = PhantomMotion.OverlayPillExit,
        modifier = Modifier.align(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .phantomSurface(
                    shape = RoundedCornerShape(16.dp),
                    borderWidth = 1.dp,
                    tintColor = PlayerErrorSurface,
                    surfaceAlpha = 0.90f
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = errorCode.orEmpty(),
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
    val toastTopPadding by animateDpAsState(
        targetValue = if (isFullscreen) 60.dp else 44.dp,
        animationSpec = IosSpringSpecs.TopBarDp,
        label = "player_toast_top"
    )
    val toastShape = remember { RoundedCornerShape(20.dp) }
    AnimatedVisibility(
        visible = noticeText != null,
        enter = PhantomMotion.OverlayPillEnter,
        exit = PhantomMotion.OverlayPillExit,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = toastTopPadding.coerceAtLeast(0.dp))
            .zIndex(50f)
    ) {
        Box(
            modifier = Modifier
                .phantomGlassSurface(shape = toastShape, shadowElevation = 12.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = noticeText.orEmpty(),
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
    canSkipPrevious: Boolean = true,
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
        enter = PhantomMotion.PlayerControlsEnter,
        exit = PhantomMotion.PlayerControlsExit,
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
            val controlBtnSize = if (isFullscreen) 36.dp else 32.dp
            val controlIconSize = if (isFullscreen) 19.dp else 17.dp
            val controlShape = remember { RoundedCornerShape(10.dp) }
            val menuShape = remember { RoundedCornerShape(14.dp) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        start = if (isFullscreen) 24.dp else 10.dp,
                        end = if (isFullscreen) 24.dp else 10.dp,
                        top = if (isFullscreen) 16.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhantomIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_minimize),
                    size = if (isFullscreen) 38.dp else 34.dp,
                    iconSize = if (isFullscreen) 24.dp else 22.dp,
                    onClick = onMinimizeOrExitFullscreen
                )

                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 8.dp else 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Closed Captions Toggle
                    val ccActive = isCaptionsEnabled && hasCaptions
                    val ccBg by animateColorAsState(
                        targetValue = if (ccActive) PlayerOverlayControlActiveBg else PlayerOverlayControlBg,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "cc_bg"
                    )
                    val ccBorder by animateColorAsState(
                        targetValue = if (ccActive) YouTubeRed else PlayerOverlayControlBorder,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "cc_border"
                    )
                    val ccTint by animateColorAsState(
                        targetValue = when {
                            ccActive -> YouTubeRed
                            !hasCaptions -> Color.White.copy(alpha = 0.35f)
                            else -> Color.White.copy(alpha = 0.80f)
                        },
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "cc_tint"
                    )
                    Box(
                        modifier = Modifier
                            .size(controlBtnSize)
                            .clip(controlShape)
                            .background(ccBg)
                            .border(
                                width = if (ccActive) 1.2.dp else 0.75.dp,
                                color = ccBorder,
                                shape = controlShape
                            )
                            .iosBounceClick(scaleDown = 0.90f, onClick = onToggleCaptions),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PhantomIcons.ClosedCaption,
                            contentDescription = stringResource(if (isCaptionsEnabled) R.string.player_captions_turn_off else R.string.player_captions_turn_on),
                            tint = ccTint,
                            modifier = Modifier.size(controlIconSize)
                        )
                    }

                    // Quality Dropdown Button
                    Box {
                        val currentQualityBadge = remember(playerState.currentQuality) {
                            getShortQualityBadge(playerState.currentQuality)
                        }
                        val isHighQuality = playerState.currentQuality in listOf("highres", "hd2160", "hd1440", "hd1080", "hd720")
                        val qualityBg by animateColorAsState(
                            targetValue = when {
                                isQualityMenuExpanded -> PlayerOverlayControlActiveBg
                                isHighQuality -> PlayerOverlayZoomActiveBg
                                else -> PlayerOverlayControlBg
                            },
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "quality_bg"
                        )
                        val qualityBorder by animateColorAsState(
                            targetValue = if (isQualityMenuExpanded) YouTubeRed else PlayerOverlayControlBorder,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "quality_border"
                        )
                        val qualityContentColor by animateColorAsState(
                            targetValue = if (isQualityMenuExpanded) YouTubeRed else Color.White,
                            animationSpec = IosSpringSpecs.ColorFade,
                            label = "quality_color"
                        )

                        Box(
                            modifier = Modifier
                                .height(controlBtnSize)
                                .clip(controlShape)
                                .background(qualityBg)
                                .border(
                                    width = if (isQualityMenuExpanded) 1.2.dp else 0.75.dp,
                                    color = qualityBorder,
                                    shape = controlShape
                                )
                                .iosBounceClick(scaleDown = 0.90f) {
                                    onToggleQualityMenu(!isQualityMenuExpanded)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = if (isFullscreen) 8.dp else 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = PhantomIcons.HighQuality,
                                    contentDescription = stringResource(R.string.player_quality_format, currentQualityBadge),
                                    tint = if (isQualityMenuExpanded || isHighQuality) qualityContentColor else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(if (isFullscreen) 17.dp else 15.dp)
                                )
                                Text(
                                    text = currentQualityBadge,
                                    color = qualityContentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isQualityMenuExpanded,
                            onDismissRequest = { onToggleQualityMenu(false) },
                            modifier = Modifier
                                .background(MenuSurfaceDark, menuShape)
                                .border(0.75.dp, MenuBorderSubtle, menuShape)
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
                    val zoomBg by animateColorAsState(
                        targetValue = if (isZoomToFill) PlayerOverlayZoomActiveBg else PlayerOverlayControlBg,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "zoom_bg"
                    )
                    val zoomBorder by animateColorAsState(
                        targetValue = if (isZoomToFill) Color.White else PlayerOverlayControlBorder,
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "zoom_border"
                    )
                    val zoomTint by animateColorAsState(
                        targetValue = if (isZoomToFill) Color.White else Color.White.copy(alpha = 0.70f),
                        animationSpec = IosSpringSpecs.ColorFade,
                        label = "zoom_tint"
                    )
                    Box(
                        modifier = Modifier
                            .size(controlBtnSize)
                            .clip(controlShape)
                            .background(zoomBg)
                            .border(
                                width = if (isZoomToFill) 1.2.dp else 0.75.dp,
                                color = zoomBorder,
                                shape = controlShape
                            )
                            .iosBounceClick(scaleDown = 0.90f, onClick = onToggleZoom),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isZoomToFill,
                            transitionSpec = { PhantomMotion.IconSwapTransition },
                            label = "zoom_icon"
                        ) { zoomed ->
                            Icon(
                                imageVector = if (zoomed) PhantomIcons.FitScreen else PhantomIcons.CropFree,
                                contentDescription = stringResource(if (zoomed) R.string.player_zoom_original else R.string.player_zoom_fill),
                                tint = zoomTint,
                                modifier = Modifier.size(controlIconSize)
                            )
                        }
                    }

                    // Screen Lock Button
                    Box(
                        modifier = Modifier
                            .size(controlBtnSize)
                            .clip(controlShape)
                            .background(PlayerOverlayControlBg)
                            .border(0.75.dp, PlayerOverlayControlBorder, controlShape)
                            .iosBounceClick(scaleDown = 0.90f, onClick = onLockControls),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.player_lock_screen),
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(if (isFullscreen) 17.dp else 15.dp)
                        )
                    }

                    // Playback Speed Quick Button
                    val formattedSpeed = remember(playerState.playbackSpeed) {
                        String.format(Locale.getDefault(), "%.2f", playerState.playbackSpeed)
                            .trimEnd('0')
                            .trimEnd('.', ',') + "x"
                    }
                    Box(
                        modifier = Modifier
                            .height(controlBtnSize)
                            .clip(controlShape)
                            .background(PlayerOverlayControlBg)
                            .border(0.75.dp, PlayerOverlayControlBorder, controlShape)
                            .iosBounceClick(scaleDown = 0.90f, onClick = onCycleSpeed)
                            .padding(horizontal = if (isFullscreen) 8.dp else 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = formattedSpeed,
                            transitionSpec = { PhantomMotion.IconSwapTransition },
                            label = "speed_badge"
                        ) { speedLabel ->
                            Text(
                                text = speedLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Settings Button
                    PhantomIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        size = controlBtnSize,
                        iconSize = if (isFullscreen) 20.dp else 18.dp,
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
                    icon = PhantomIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.player_prev_video),
                    size = 42.dp,
                    iconSize = 22.dp,
                    tint = if (canSkipPrevious) TextPrimary else TextPrimary.copy(alpha = 0.35f),
                    onClick = onSkipPrevious
                )

                val rewindIcon = when (doubleTapSeekSeconds) {
                    5 -> PhantomIcons.Replay5
                    10 -> PhantomIcons.Replay10
                    30 -> PhantomIcons.Replay30
                    else -> PhantomIcons.FastRewind
                }
                val forwardIcon = when (doubleTapSeekSeconds) {
                    5 -> PhantomIcons.Forward5
                    10 -> PhantomIcons.Forward10
                    30 -> PhantomIcons.Forward30
                    else -> PhantomIcons.FastForward
                }

                PhantomIconButton(
                    icon = rewindIcon,
                    contentDescription = "-${stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString())}",
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onRewind10s
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .phantomGlassSurface(shape = CircleShape, shadowElevation = 10.dp)
                        .iosBounceClick(scaleDown = 0.88f) {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {}
                            onTogglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = playerState.isPlaying,
                        transitionSpec = { PhantomMotion.IconSwapTransition },
                        label = "player_main_play_pause"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) PhantomIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                            tint = TextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                PhantomIconButton(
                    icon = forwardIcon,
                    contentDescription = "+${stringResource(R.string.settings_seek_seconds, doubleTapSeekSeconds.toString())}",
                    size = 42.dp,
                    iconSize = 22.dp,
                    onClick = onForward10s
                )

                PhantomIconButton(
                    icon = PhantomIcons.SkipNext,
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

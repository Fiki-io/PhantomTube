package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.phantom.tube.ui.components.IosSpringSpecs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.player.PhantomGhostSurface
import com.phantom.tube.player.PhantomPlayerBridge
import com.phantom.tube.player.PhantomPlayerController
import com.phantom.tube.player.PlayerState
import com.phantom.tube.player.StoryboardData
import com.phantom.tube.ui.components.SponsorSkipPill
import com.phantom.tube.ui.components.VideoQualityOption
import com.phantom.tube.ui.screens.player.SeekFeedbackDirection

@Composable
fun BoxScope.PlayerAmbientBackdrop(
    isSheetRendered: Boolean,
    isFullscreen: Boolean,
    thumbnailUrl: String,
    sheetProgressProvider: () -> Float,
    dragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    isInPipMode: Boolean = false
) {
    if (!isSheetRendered || isFullscreen || isInPipMode) return
    val context = LocalContext.current

    // Stationary Status Bar Protection
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                alpha = ((progress - 0.92f) / 0.08f).coerceIn(0f, 1f)
            }
            .background(SystemStatusBarColor)
    )

    // Solid Sheet Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                val sheetOff = (1f - progress) * screenHeightPx
                translationY = sheetOff
                alpha = (progress / 0.25f).coerceIn(0f, 1f)
            }
            .background(ObsidianDark)
    )

    // Ambient Mode Cinematic Glow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .statusBarsPadding()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                val sheetAlpha = (progress / 0.25f).coerceIn(0f, 1f)
                translationY = (1f - progress) * screenHeightPx
                alpha = (sheetAlpha * 0.90f).coerceIn(0f, 1f)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .align(Alignment.TopCenter)
    ) {
        if (thumbnailUrl.isNotBlank()) {
            val ambientImageRequest = remember(thumbnailUrl, context) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(96, 54)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .allowHardware(false)
                    .build()
            }
            AsyncImage(
                model = ambientImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.22f
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            ObsidianDark.copy(alpha = 0.4f),
                            ObsidianDark
                        )
                    )
                )
        )
    }
}

@Composable
fun BoxScope.PlayerVideoViewportBox(
    video: VideoItem,
    isSheetRendered: Boolean,
    isFullscreen: Boolean,
    sheetProgressProvider: () -> Float,
    dragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    dragModifier: Modifier,
    animatedZoomScale: Float,
    controller: PhantomPlayerController,
    bridge: PhantomPlayerBridge,
    playerState: PlayerState,
    isAudioOnly: Boolean,
    isControlsVisible: Boolean,
    isCaptionsEnabled: Boolean,
    hasCaptions: Boolean,
    isQualityMenuExpanded: Boolean,
    isZoomToFill: Boolean,
    doubleTapSeekSeconds: Int,
    seekAnimationSide: SeekFeedbackDirection?,
    seekAccumulatedSeconds: Int,
    showSponsorPill: Boolean,
    lastSkippedSeconds: Int,
    lastSkippedCategory: String,
    isManualSkipMode: Boolean,
    liveEndscreenVideos: List<VideoItem>,
    nextQueueData: NextQueue?,
    recommendedVideos: List<VideoItem>,
    isEndscreenDismissed: Boolean,
    isScrubbing: Boolean,
    scrubFraction: Float,
    scrubTouchX: Float,
    videoBoxWidthPx: Float,
    storyboardData: StoryboardData?,
    playerNoticeText: String?,
    currentTimeSecProvider: () -> Float,
    durationSecProvider: () -> Float,
    bufferedFractionProvider: () -> Float,
    onViewportMeasured: (Float, Float) -> Unit,
    onDisableAudioOnly: () -> Unit,
    onSingleTapViewport: () -> Unit,
    onDoubleTapSeek: (Boolean) -> Unit,
    onManualSponsorSkip: () -> Unit,
    onUndoSponsorSkip: () -> Unit,
    onDismissEndscreen: () -> Unit,
    onEndscreenVideoClick: (VideoItem) -> Unit,
    onMinimizeOrExitFullscreen: () -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleQualityMenu: (Boolean) -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onToggleZoom: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkipPrevious: () -> Unit,
    onRewind10s: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10s: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbing: (Boolean, Float, Float) -> Unit,
    isInPipMode: Boolean = false,
    isMinimized: Boolean = false,
    isFloatingMiniPlayer: Boolean = false,
    isBottomNavVisible: Boolean = true,
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var isControlsLocked by remember(video.id) { mutableStateOf(false) }

    val floatingDismissOffsetX = remember { Animatable(0f) }
    val floatingScope = rememberCoroutineScope()

    LaunchedEffect(isMinimized) {
        if (!isMinimized) {
            floatingDismissOffsetX.snapTo(0f)
        }
    }

    val isFloatingState = isMinimized && isFloatingMiniPlayer && !isInPipMode

    val videoBoxModifier = when {
        isInPipMode -> Modifier.fillMaxSize()
        isFullscreen && isSheetRendered -> Modifier
            .fillMaxSize()
            .graphicsLayer {
                val drag = dragOffsetProvider()
                val landscapeDragProg = (drag / 100f).coerceIn(0f, 1f)
                val landscapeScale = (1f - (drag / 1000f)).coerceIn(0.88f, 1f)
                scaleX = landscapeScale
                scaleY = landscapeScale
                translationY = drag
                if (landscapeDragProg > 0f) {
                    shape = RoundedCornerShape((landscapeDragProg * 20f).dp)
                    clip = true
                } else {
                    clip = false
                }
            }
        isFloatingState -> Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(
                end = 14.dp,
                bottom = if (isBottomNavVisible) 92.dp else 16.dp
            )
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                translationX = floatingDismissOffsetX.value
                val absX = kotlin.math.abs(floatingDismissOffsetX.value)
                alpha = (1f - (absX / 260f)).coerceIn(0.1f, 1f)
                shadowElevation = 16f
                shape = RoundedCornerShape(14.dp)
                clip = true
            }
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
        else -> Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .offset {
                IntOffset(0, if (isSheetRendered) 0 else (screenHeightPx.toInt() * 2))
            }
            .graphicsLayer {
                val progress = sheetProgressProvider()
                translationY = if (isSheetRendered) {
                    (1f - progress) * screenHeightPx
                } else {
                    screenHeightPx * 2f
                }
                alpha = if (isSheetRendered) {
                    (progress * 1.25f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            .aspectRatio(16f / 9f)
            .align(Alignment.TopCenter)
    }

    Box(
        modifier = videoBoxModifier
            .then(if (isControlsLocked || !isSheetRendered || isFloatingState) Modifier else dragModifier)
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                if (isSheetRendered) {
                    onViewportMeasured(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
            }
            .zIndex(if (isFloatingState) 25f else if (isSheetRendered) 10f else 0f)
    ) {
        val isSurfaceCollapsed = isAudioOnly
        Box(
            modifier = if (isSurfaceCollapsed) {
                Modifier
                    .size(1.dp)
                    .alpha(0.01f)
                    .align(Alignment.TopStart)
            } else {
                Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = animatedZoomScale
                        scaleY = animatedZoomScale
                        transformOrigin = TransformOrigin.Center
                    }
            }
        ) {
            PhantomGhostSurface(
                videoId = video.id,
                modifier = if (isSurfaceCollapsed) Modifier.size(1.dp) else Modifier.matchParentSize(),
                controller = controller,
                bridge = bridge
            )
        }

        if (isFloatingState) {
            FloatingMiniPlayerOverlay(
                isPlaying = playerState.isPlaying,
                currentTimeSecProvider = currentTimeSecProvider,
                durationSecProvider = durationSecProvider,
                onExpand = onExpand,
                onTogglePlayPause = onTogglePlayPause,
                onClose = onClose,
                onDragDismiss = { deltaX ->
                    floatingScope.launch {
                        floatingDismissOffsetX.snapTo(floatingDismissOffsetX.value + deltaX)
                    }
                },
                onDragDismissEnd = {
                    floatingScope.launch {
                        if (kotlin.math.abs(floatingDismissOffsetX.value) > 90f) {
                            val target = if (floatingDismissOffsetX.value > 0) 500f else -500f
                            floatingDismissOffsetX.animateTo(target, tween(180))
                            onClose()
                        } else {
                            floatingDismissOffsetX.animateTo(0f, IosSpringSpecs.Bouncy)
                        }
                    }
                }
            )
        } else if (isSheetRendered && !isInPipMode) {
            AudioOnlyViewportOverlay(
                isAudioOnly = isAudioOnly,
                isControlsVisible = isControlsVisible && !isControlsLocked,
                onDisableAudioOnly = onDisableAudioOnly
            )

            // Gesture Touch Handler
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(doubleTapSeekSeconds, isControlsLocked) {
                        detectTapGestures(
                            onTap = {
                                onSingleTapViewport()
                            },
                            onDoubleTap = { offset ->
                                if (isControlsLocked) return@detectTapGestures
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Exception) {}
                                val isRewind = offset.x < size.width / 2
                                onDoubleTapSeek(isRewind)
                            }
                        )
                    }
            )

            PlayerLockedOverlay(
                visible = isControlsLocked && isControlsVisible,
                onUnlock = {
                    isControlsLocked = false
                }
            )

            DoubleTapSeekFeedbackOverlay(
                seekAnimationSide = seekAnimationSide,
                seekAccumulatedSeconds = seekAccumulatedSeconds
            )

            PlayerBufferingErrorOverlay(
                isBuffering = playerState.isBuffering,
                errorCode = playerState.errorCode
            )

            SponsorSkipPill(
                visible = showSponsorPill,
                skippedSeconds = lastSkippedSeconds,
                category = lastSkippedCategory,
                isManualMode = isManualSkipMode,
                onSkip = onManualSponsorSkip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                onUndo = onUndoSponsorSkip
            )

            val currentSec = currentTimeSecProvider()
            val durSec = durationSecProvider()
            val isNearEnd by remember(currentSec, durSec) {
                derivedStateOf {
                    durSec > 25f && (durSec - currentSec) in 0.5f..20f
                }
            }
            val endscreenCards = remember(liveEndscreenVideos, nextQueueData, recommendedVideos, video.id) {
                if (liveEndscreenVideos.isNotEmpty()) {
                    liveEndscreenVideos
                } else {
                    nextQueueData?.effectiveEndscreens?.ifEmpty {
                        recommendedVideos.filter { it.id != video.id }.take(2)
                    } ?: recommendedVideos.filter { it.id != video.id }.take(2)
                }
            }
            PlayerEndscreenOverlay(
                visible = isNearEnd && !isEndscreenDismissed && !isControlsVisible && !isControlsLocked && endscreenCards.isNotEmpty(),
                items = endscreenCards,
                isFullscreen = isFullscreen,
                onDismiss = onDismissEndscreen,
                onVideoClick = onEndscreenVideoClick,
                modifier = Modifier.matchParentSize()
            )

            PlayerControlsOverlay(
                isControlsVisible = isControlsVisible && !isControlsLocked,
                isFullscreen = isFullscreen,
                playerState = playerState,
                isCaptionsEnabled = isCaptionsEnabled,
                hasCaptions = hasCaptions,
                isQualityMenuExpanded = isQualityMenuExpanded,
                isZoomToFill = isZoomToFill,
                doubleTapSeekSeconds = doubleTapSeekSeconds,
                currentTimeSecProvider = currentTimeSecProvider,
                durationSecProvider = durationSecProvider,
                bufferedFractionProvider = bufferedFractionProvider,
                onMinimizeOrExitFullscreen = onMinimizeOrExitFullscreen,
                onToggleCaptions = onToggleCaptions,
                onToggleQualityMenu = onToggleQualityMenu,
                onSelectQuality = onSelectQuality,
                onToggleZoom = onToggleZoom,
                onLockControls = {
                    isControlsLocked = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onCycleSpeed = onCycleSpeed,
                onOpenSettings = onOpenSettings,
                onSkipPrevious = onSkipPrevious,
                onRewind10s = onRewind10s,
                onTogglePlayPause = onTogglePlayPause,
                onForward10s = onForward10s,
                onSkipNext = onSkipNext,
                onToggleFullscreen = onToggleFullscreen,
                onSeek = onSeek,
                onScrubbing = onScrubbing
            )

            AnimatedVisibility(
                visible = !isControlsVisible || isControlsLocked,
                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                PlayerIdleProgressBar(
                    currentTimeSecProvider = currentTimeSecProvider,
                    durationSecProvider = durationSecProvider,
                    bufferedFractionProvider = bufferedFractionProvider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            }

            ScrubPreviewCard(
                visible = isScrubbing,
                targetSeconds = scrubFraction * durSec,
                durationSeconds = durSec,
                touchX = scrubTouchX,
                parentWidthPx = videoBoxWidthPx,
                storyboardData = storyboardData,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (isFullscreen) 56.dp else 42.dp)
                    .zIndex(30f)
            )

            PlayerToastNoticeOverlay(
                noticeText = playerNoticeText,
                isFullscreen = isFullscreen
            )
        }
    }
}

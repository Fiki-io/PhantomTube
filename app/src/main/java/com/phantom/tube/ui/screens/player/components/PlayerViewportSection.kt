package com.phantom.tube.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    screenHeightPx: Float
) {
    if (!isSheetRendered || isFullscreen) return
    val context = LocalContext.current

    // Stationary Status Bar Protection
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                val dragProg = (dragOffsetProvider() / 220f).coerceIn(0f, 1f)
                alpha = (progress * (1f - dragProg * 2f)).coerceIn(0f, 1f)
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
                translationY = sheetOff + dragOffsetProvider()
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                clip = true
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
                val drag = dragOffsetProvider()
                val dragProg = (drag / 220f).coerceIn(0f, 1f)
                translationY = (1f - progress) * screenHeightPx + drag
                alpha = (progress * (1f - dragProg) * 0.90f).coerceIn(0f, 1f)
            }
            .align(Alignment.TopCenter)
    ) {
        if (thumbnailUrl.isNotBlank()) {
            val ambientImageRequest = remember(thumbnailUrl, context) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(160, 90)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .build()
            }
            AsyncImage(
                model = ambientImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.22f }
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
    onScrubbing: (Boolean, Float, Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isControlsLocked by remember(video.id) { mutableStateOf(false) }

    val videoBoxModifier = when {
        !isSheetRendered -> Modifier
            .size(1.dp)
            .alpha(0.01f)
            .align(Alignment.TopStart)
        isFullscreen -> Modifier
            .fillMaxSize()
            .graphicsLayer {
                val drag = dragOffsetProvider()
                val landscapeDragProg = (drag / 100f).coerceIn(0f, 1f)
                val landscapeScale = (1f - (drag / 1000f)).coerceIn(0.88f, 1f)
                scaleX = landscapeScale
                scaleY = landscapeScale
                translationY = drag
                shape = RoundedCornerShape((landscapeDragProg * 20f).dp)
                clip = landscapeDragProg > 0f
            }
        else -> Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                val drag = dragOffsetProvider()
                val dragProg = (drag / 220f).coerceIn(0f, 1f)
                val dragScale = 1f - (dragProg * 0.04f)
                scaleX = dragScale
                scaleY = dragScale
                translationY = (1f - progress) * screenHeightPx + drag
                alpha = (progress * 1.15f).coerceIn(0f, 1f)
                shape = RoundedCornerShape(14.dp)
                clip = true
            }
            .aspectRatio(16f / 9f)
            .align(Alignment.TopCenter)
    }

    Box(
        modifier = videoBoxModifier
            .then(if (isControlsLocked) Modifier else dragModifier)
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                onViewportMeasured(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            .zIndex(if (isSheetRendered) 10f else 0f)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = animatedZoomScale
                    scaleY = animatedZoomScale
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            PhantomGhostSurface(
                videoId = video.id,
                modifier = Modifier.matchParentSize(),
                controller = controller,
                bridge = bridge
            )
        }

        if (isSheetRendered) {
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

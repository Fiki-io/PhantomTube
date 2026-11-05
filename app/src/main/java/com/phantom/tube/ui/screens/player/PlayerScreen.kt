package com.phantom.tube.ui.screens.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.phantom.tube.R
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.util.findActivity
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.player.PlayerState
import com.phantom.tube.player.StoryboardHelper
import com.phantom.tube.player.offline.OfflineAudioPlayerManager
import com.phantom.tube.player.service.PhantomMediaService
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.components.PhantomMiniPlayer
import com.phantom.tube.ui.components.SleepTimerOption
import com.phantom.tube.ui.screens.player.components.PlayerAmbientBackdrop
import com.phantom.tube.ui.screens.player.components.PlayerPortraitSheetContent
import com.phantom.tube.ui.screens.player.components.PlayerSheetsHost
import com.phantom.tube.ui.screens.player.components.PlayerVideoViewportBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SeekFeedbackDirection { FORWARD, REWIND }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: VideoItem,
    repository: PhantomRepository,
    isMinimized: Boolean = false,
    isInPipMode: Boolean = false,
    isBottomNavVisible: Boolean = true,
    onMinimize: () -> Unit = {},
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {},
    onBackClick: () -> Unit = onMinimize,
    onPlayNextVideo: (VideoItem) -> Unit,
    onPlayPreviousVideo: () -> Boolean = { false },
    onChannelClick: ((channelId: String, channelTitle: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().coerceAtLeast(1f) }

    val session = rememberPlayerSessionState(
        context = context,
        repository = repository,
        haptic = haptic,
        video = video,
        isMinimized = isMinimized,
        onPlayNextVideo = onPlayNextVideo,
        onPlayPreviousVideo = onPlayPreviousVideo
    )

    val doubleTapSeekSeconds by remember(repository) {
        repository.preferences?.doubleTapSeekSeconds ?: MutableStateFlow(10)
    }.collectAsState()

    val miniPlayerStyle by remember(repository) {
        repository.preferences?.miniPlayerStyle ?: MutableStateFlow(PhantomPreferences.MINIPLAYER_STYLE_FLOATING)
    }.collectAsState()
    val isFloatingMiniPlayer = miniPlayerStyle == PhantomPreferences.MINIPLAYER_STYLE_FLOATING

    PlayerLifecycleEffects(
        session = session,
        video = video,
        isMinimized = isMinimized,
        isFloatingMiniPlayer = isFloatingMiniPlayer
    )

    PlayerMediaServiceEffect(session = session)

    BackHandler(enabled = !isMinimized) {
        when {
            session.showMixSheet -> session.showMixSheet = false
            session.showDescriptionSheet -> session.showDescriptionSheet = false
            session.showCommentsSheet -> session.showCommentsSheet = false
            session.showDownloadSheet -> session.showDownloadSheet = false
            session.showSettingsSheet -> session.showSettingsSheet = false
            session.isFullscreen -> session.exitFullscreenToPortrait()
            else -> onBackClick()
        }
    }

    val animatedDragOffset by animateFloatAsState(
        targetValue = session.dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drag_minimize_offset"
    )

    val isSheetRendered by remember(isMinimized, isFloatingMiniPlayer) {
        derivedStateOf {
            if (isMinimized) {
                if (isFloatingMiniPlayer) false else session.fullSheetProgress.value > 0.005f
            } else {
                true
            }
        }
    }

    val fillScale = remember(session.videoBoxWidthPx, session.videoBoxHeightPx) {
        if (session.videoBoxHeightPx > 0f && session.videoBoxWidthPx > 0f) {
            val containerRatio = session.videoBoxWidthPx / session.videoBoxHeightPx
            val videoRatio = 16f / 9f
            if (containerRatio > videoRatio) {
                (containerRatio / videoRatio).coerceIn(1.0f, 2.5f)
            } else {
                (videoRatio / containerRatio).coerceIn(1.0f, 2.5f)
            }
        } else {
            1.28f
        }
    }

    val animatedZoomScale by animateFloatAsState(
        targetValue = if (session.isZoomToFill) fillScale else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "zoomScale"
    )

    val dragModifier = rememberPlayerDragModifier(
        session = session,
        isMinimized = isMinimized,
        screenHeightPx = screenHeightPx,
        onMinimize = onMinimize
    )

    val effectiveDragOffsetProvider = { if (session.isFullscreen && !isMinimized) animatedDragOffset else 0f }

    Box(
        modifier = if (!isSheetRendered || isInPipMode) {
            modifier
        } else {
            modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = session.fullSheetProgress.value
                    val drag = effectiveDragOffsetProvider()
                    val scrimAlpha = if (session.isFullscreen) {
                        (1f - (drag / 120f)).coerceIn(0f, 0.95f)
                    } else {
                        (progress * 0.78f).coerceIn(0f, 0.78f)
                    }
                    if (scrimAlpha > 0f) {
                        drawRect(ObsidianDark.copy(alpha = scrimAlpha))
                    }
                }
        }
    ) {
        PlayerAmbientBackdrop(
            isSheetRendered = isSheetRendered,
            isFullscreen = session.isFullscreen,
            thumbnailUrl = video.thumbnailUrl,
            sheetProgressProvider = { session.fullSheetProgress.value },
            dragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            isInPipMode = isInPipMode
        )

        PlayerViewportBridgeSection(
            session = session,
            video = video,
            isSheetRendered = isSheetRendered,
            effectiveDragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            dragModifier = dragModifier,
            animatedZoomScale = animatedZoomScale,
            doubleTapSeekSeconds = doubleTapSeekSeconds,
            isInPipMode = isInPipMode,
            isMinimized = isMinimized,
            isFloatingMiniPlayer = isFloatingMiniPlayer,
            isBottomNavVisible = isBottomNavVisible,
            onMinimize = onMinimize,
            onExpand = onExpand,
            onClose = onClose
        )

        if (!isInPipMode) {
            PlayerPortraitSheetContent(
                isSheetRendered = isSheetRendered,
                isFullscreen = session.isFullscreen,
                sheetProgressProvider = { session.fullSheetProgress.value },
                dragOffsetProvider = effectiveDragOffsetProvider,
                screenHeightPx = screenHeightPx,
                dragModifier = dragModifier,
                lazyListState = session.lazyListState,
                video = video,
                nextQueueData = session.nextQueueData,
                activeAvatarUrl = session.activeAvatarUrl,
                commentsList = session.commentsList,
                commentsTotalCountText = session.commentsTotalCountText,
                repository = repository,
                isLoadingQueue = session.isLoadingQueue,
                recommendedVideos = session.recommendedVideos,
                isLoadingMoreRecs = session.isLoadingMoreRecs,
                mixPlaylist = session.mixPlaylist,
                currentMixIndex = session.currentMixIndex,
                mixTitle = session.mixTitle,
                showMixSheet = session.showMixSheet,
                showCommentsSheet = session.showCommentsSheet,
                showDescriptionSheet = session.showDescriptionSheet,
                showSettingsSheet = session.showSettingsSheet,
                onOpenDescription = {
                    session.showCommentsSheet = false
                    session.showMixSheet = false
                    session.showSettingsSheet = false
                    session.showDescriptionSheet = true
                },
                onOpenComments = {
                    session.showDescriptionSheet = false
                    session.showMixSheet = false
                    session.showSettingsSheet = false
                    session.showCommentsSheet = true
                },
                onOpenDownload = {
                    session.showDownloadSheet = true
                },
                onOpenMixSheet = {
                    session.showCommentsSheet = false
                    session.showDescriptionSheet = false
                    session.showSettingsSheet = false
                    session.showMixSheet = true
                },
                onChannelSelected = { chId, chTitle ->
                    onMinimize()
                    onChannelClick?.invoke(chId, chTitle)
                },
                onRecommendationClick = { item ->
                    if (!session.networkObserver.isConnected()) {
                        session.showNotice(context.getString(R.string.player_no_internet_notice))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        session.isInternalNavigation = false
                        session.onPlayNextVideoCallback(item)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = isMinimized && !isInPipMode && !isFloatingMiniPlayer,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = IosSpringSpecs.GentleOffset
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = IosSpringSpecs.GentleOffset
            ) + fadeOut(animationSpec = tween(160)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isBottomNavVisible) 76.dp else 12.dp)
                .zIndex(20f)
        ) {
            PhantomMiniPlayer(
                video = video,
                isPlaying = session.playerState.isPlaying,
                isBuffering = session.playerState.isBuffering,
                currentTimeSecProvider = { session.currentPositionSec },
                durationSecProvider = { session.currentDurationSec },
                onExpand = onExpand,
                onTogglePlayPause = {
                    if (session.playerState.isPlaying) {
                        session.controller.pause()
                    } else {
                        if (!session.networkObserver.isConnected()) {
                            session.showNotice(context.getString(R.string.player_no_internet_notice))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            session.controller.play()
                        }
                    }
                },
                onClose = onClose
            )
        }

        PlayerSheetsBridgeSection(
            session = session,
            video = video,
            isMinimized = isMinimized,
            effectiveDragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            doubleTapSeekSeconds = doubleTapSeekSeconds,
            onMinimize = onMinimize,
            onChannelClick = onChannelClick
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerViewportBridgeSection(
    session: PlayerSessionState,
    video: VideoItem,
    isSheetRendered: Boolean,
    effectiveDragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    dragModifier: Modifier,
    animatedZoomScale: Float,
    doubleTapSeekSeconds: Int,
    isInPipMode: Boolean,
    isMinimized: Boolean,
    isFloatingMiniPlayer: Boolean,
    isBottomNavVisible: Boolean,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val context = session.context
    val haptic = session.haptic
    val scope = session.scope
    val repository = session.repository

    PlayerVideoViewportBox(
        video = video,
        isSheetRendered = isSheetRendered,
        isFullscreen = session.isFullscreen,
        sheetProgressProvider = { session.fullSheetProgress.value },
        dragOffsetProvider = effectiveDragOffsetProvider,
        screenHeightPx = screenHeightPx,
        dragModifier = dragModifier,
        animatedZoomScale = animatedZoomScale,
        controller = session.controller,
        bridge = session.bridge,
        playerState = session.playerState,
        isAudioOnly = session.isAudioOnly,
        isControlsVisible = session.isControlsVisible,
        isCaptionsEnabled = session.isCaptionsEnabled,
        hasCaptions = session.hasCaptions,
        isQualityMenuExpanded = session.isQualityMenuExpanded,
        isZoomToFill = session.isZoomToFill,
        doubleTapSeekSeconds = doubleTapSeekSeconds,
        seekAnimationSide = session.seekAnimationSide,
        seekAccumulatedSeconds = session.seekAccumulatedSeconds,
        showSponsorPill = session.showSponsorPill,
        lastSkippedSeconds = session.lastSkippedSeconds,
        lastSkippedCategory = session.lastSkippedCategory,
        isManualSkipMode = !(repository.preferences?.sponsorBlockAutoSkip?.value ?: true),
        liveEndscreenVideos = session.liveEndscreenVideos,
        nextQueueData = session.nextQueueData,
        recommendedVideos = session.recommendedVideos,
        isEndscreenDismissed = session.isEndscreenDismissed,
        isScrubbing = session.isScrubbing,
        scrubFraction = session.scrubFraction,
        scrubTouchX = session.scrubTouchX,
        videoBoxWidthPx = session.videoBoxWidthPx,
        storyboardData = session.storyboardData,
        playerNoticeText = session.playerNoticeText,
        currentTimeSecProvider = { session.currentPositionSec },
        durationSecProvider = { session.currentDurationSec },
        bufferedFractionProvider = { session.currentBufferedFraction },
        isOfflineWaiting = session.isOfflineWaiting,
        onViewportMeasured = { w, h ->
            session.videoBoxWidthPx = w
            session.videoBoxHeightPx = h
        },
        onDisableAudioOnly = { session.applyAudioOnlyState(false) },
        onSingleTapViewport = {
            when {
                session.showMixSheet -> session.showMixSheet = false
                session.showDescriptionSheet -> session.showDescriptionSheet = false
                session.showCommentsSheet -> session.showCommentsSheet = false
                session.showSettingsSheet -> session.showSettingsSheet = false
                session.isQualityMenuExpanded -> session.isQualityMenuExpanded = false
                else -> session.isControlsVisible = !session.isControlsVisible
            }
        },
        onDoubleTapSeek = { isRewind ->
            val delta = doubleTapSeekSeconds
            if (isRewind) {
                session.seekAccumulatedSeconds = if (session.seekAnimationSide == SeekFeedbackDirection.REWIND) {
                    session.seekAccumulatedSeconds + delta
                } else {
                    delta
                }
                session.seekAnimationSide = SeekFeedbackDirection.REWIND
                val newTime = (session.currentPositionSec - delta.toFloat()).coerceAtLeast(0f)
                session.controller.seekTo(newTime)
                session.currentPositionSec = newTime
                session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (newTime * 1000).toLong())
            } else {
                session.seekAccumulatedSeconds = if (session.seekAnimationSide == SeekFeedbackDirection.FORWARD) {
                    session.seekAccumulatedSeconds + delta
                } else {
                    delta
                }
                session.seekAnimationSide = SeekFeedbackDirection.FORWARD
                val newTime = (session.currentPositionSec + delta.toFloat()).coerceAtMost(session.currentDurationSec)
                session.controller.seekTo(newTime)
                session.currentPositionSec = newTime
                session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (newTime * 1000).toLong())
            }
        },
        onManualSponsorSkip = {
            session.controller.seekTo(session.lastTargetSkipEndSec)
            session.currentPositionSec = session.lastTargetSkipEndSec
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (session.lastTargetSkipEndSec * 1000).toLong())
            if (session.lastSkippedCategory == "outro") {
                val activeVid = session.currentVideo
                scope.launch {
                    repository.resetWatchPosition(activeVid.id)
                }
            }
            session.showSponsorPill = false
        },
        onUndoSponsorSkip = {
            session.undoneSegmentUuids.addAll(session.skippedSegmentUuids)
            session.controller.seekTo(session.lastSkippedFromSec)
            session.currentPositionSec = session.lastSkippedFromSec
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (session.lastSkippedFromSec * 1000).toLong())
            session.showSponsorPill = false
        },
        onDismissEndscreen = { session.isEndscreenDismissed = true },
        onEndscreenVideoClick = { targetVid ->
            if (!session.networkObserver.isConnected()) {
                session.showNotice(context.getString(R.string.player_no_internet_notice))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                session.isInternalNavigation = false
                session.onPlayNextVideoCallback(targetVid)
            }
        },
        onMinimizeOrExitFullscreen = {
            if (session.isFullscreen) session.exitFullscreenToPortrait() else onMinimize()
        },
        onToggleCaptions = {
            if (!session.hasCaptions) {
                session.showNotice(context.getString(R.string.player_no_captions))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                session.isCaptionsEnabled = !session.isCaptionsEnabled
                session.controller.setCaptionsEnabled(session.isCaptionsEnabled)
                session.showNotice(
                    if (session.isCaptionsEnabled) context.getString(R.string.player_captions_on)
                    else context.getString(R.string.player_captions_off)
                )
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        onToggleQualityMenu = { expanded -> session.isQualityMenuExpanded = expanded },
        onSelectQuality = { opt ->
            if (session.isAudioOnly && opt.code != "tiny") {
                session.isAudioOnly = false
                session.controller.setAudioOnly(false)
            }
            session.previousQualityBeforeAudioOnly = opt.code
            session.playerState = session.playerState.copy(currentQuality = opt.code)
            session.controller.setPlaybackQuality(opt.code)
            session.isQualityMenuExpanded = false
            session.showNotice(context.getString(R.string.player_quality_format, opt.label))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        onToggleZoom = {
            session.isZoomToFill = !session.isZoomToFill
            session.showNotice(
                if (session.isZoomToFill) context.getString(R.string.player_zoom_fill)
                else context.getString(R.string.player_zoom_original)
            )
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        onCycleSpeed = {
            val nextSpeed = when (session.playerState.playbackSpeed) {
                1.0f -> 1.5f
                1.5f -> 2.0f
                2.0f -> 0.5f
                else -> 1.0f
            }
            session.playerState = session.playerState.copy(playbackSpeed = nextSpeed)
            session.controller.setPlaybackRate(nextSpeed)
        },
        onSetPlaybackSpeed = { speed ->
            session.playerState = session.playerState.copy(playbackSpeed = speed)
            session.controller.setPlaybackRate(speed)
        },
        onOpenSettings = {
            session.isControlsVisible = false
            session.isQualityMenuExpanded = false
            session.showCommentsSheet = false
            session.showDescriptionSheet = false
            session.showMixSheet = false
            session.showSettingsSheet = true
        },
        onSkipPrevious = { session.playPrevious() },
        onRewind10s = {
            val delta = doubleTapSeekSeconds
            session.seekAccumulatedSeconds = if (session.seekAnimationSide == SeekFeedbackDirection.REWIND) {
                session.seekAccumulatedSeconds + delta
            } else {
                delta
            }
            session.seekAnimationSide = SeekFeedbackDirection.REWIND
            val newTime = (session.currentPositionSec - delta.toFloat()).coerceAtLeast(0f)
            session.controller.seekTo(newTime)
            session.currentPositionSec = newTime
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (newTime * 1000).toLong())
        },
        onTogglePlayPause = {
            if (session.playerState.isPlaying) {
                session.controller.pause()
            } else {
                if (!session.networkObserver.isConnected()) {
                    session.showNotice(context.getString(R.string.player_no_internet_notice))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    session.controller.play()
                }
            }
        },
        onForward10s = {
            val delta = doubleTapSeekSeconds
            session.seekAccumulatedSeconds = if (session.seekAnimationSide == SeekFeedbackDirection.FORWARD) {
                session.seekAccumulatedSeconds + delta
            } else {
                delta
            }
            session.seekAnimationSide = SeekFeedbackDirection.FORWARD
            val newTime = (session.currentPositionSec + delta.toFloat()).coerceAtMost(session.currentDurationSec)
            session.controller.seekTo(newTime)
            session.currentPositionSec = newTime
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (newTime * 1000).toLong())
        },
        onSkipNext = { session.playNext() },
        onToggleFullscreen = {
            if (session.isFullscreen) {
                session.exitFullscreenToPortrait()
            } else {
                val activity = context.findActivity()
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                session.isFullscreen = true
            }
        },
        onSeek = { fraction ->
            val targetSec = fraction * session.currentDurationSec
            session.controller.seekTo(targetSec)
            session.currentPositionSec = targetSec
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (targetSec * 1000).toLong())
            if (session.wasPlayingBeforeScrub) {
                session.controller.play()
            }
        },
        onScrubbing = { scrubbing, fraction, touchX ->
            if (scrubbing && !session.isScrubbing) {
                session.wasPlayingBeforeScrub = session.playerState.isPlaying
                if (session.playerState.isPlaying) {
                    session.controller.pause()
                }
            }
            session.isScrubbing = scrubbing
            session.scrubFraction = fraction
            session.scrubTouchX = touchX
            if (scrubbing) {
                session.isControlsVisible = true
                val now = System.currentTimeMillis()
                if (now - session.lastScrubSeekTimeMs >= 50L) {
                    session.lastScrubSeekTimeMs = now
                    val targetSec = fraction * session.currentDurationSec
                    session.controller.seekTo(targetSec)
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        isInPipMode = isInPipMode,
        isMinimized = isMinimized,
        isFloatingMiniPlayer = isFloatingMiniPlayer,
        isBottomNavVisible = isBottomNavVisible,
        onExpand = onExpand,
        onClose = onClose
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlayerSheetsBridgeSection(
    session: PlayerSessionState,
    video: VideoItem,
    isMinimized: Boolean,
    effectiveDragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    doubleTapSeekSeconds: Int,
    onMinimize: () -> Unit,
    onChannelClick: ((channelId: String, channelTitle: String) -> Unit)?
) {
    val context = session.context
    val haptic = session.haptic
    val scope = session.scope
    val repository = session.repository

    PlayerSheetsHost(
        isMinimized = isMinimized,
        isFullscreen = session.isFullscreen,
        dragOffsetY = session.dragOffsetY,
        sheetProgressProvider = { session.fullSheetProgress.value },
        animatedDragOffsetProvider = effectiveDragOffsetProvider,
        screenHeightPx = screenHeightPx,
        video = session.currentVideo,
        repository = repository,
        nextQueueData = session.nextQueueData,
        activeAvatarUrl = session.activeAvatarUrl,
        playerState = session.playerState,
        doubleTapSeekSeconds = doubleTapSeekSeconds,
        isLoopEnabled = session.isLoopEnabled,
        isAutoplayNext = session.isAutoplayNext,
        isAudioOnly = session.isAudioOnly,
        sleepTimerRemainingSec = session.sleepTimerRemainingSec,
        sleepTimerOption = session.sleepTimerOption,
        mixTitle = session.mixTitle,
        mixPlaylist = session.mixPlaylist,
        currentMixIndex = session.currentMixIndex,
        commentsList = session.commentsList,
        commentsTotalCountText = session.commentsTotalCountText,
        isLoadingComments = session.isLoadingComments,
        hasMoreComments = !session.commentsContinuationToken.isNullOrBlank(),
        showMixSheet = session.showMixSheet,
        showSettingsSheet = session.showSettingsSheet,
        showDescriptionSheet = session.showDescriptionSheet,
        showCommentsSheet = session.showCommentsSheet,
        showDownloadSheet = session.showDownloadSheet,
        currentTimeSecProvider = { session.currentPositionSec },
        onDismissMixSheet = { session.showMixSheet = false },
        onSelectMixIndex = { index -> session.playFromMix(index) },
        onDismissSettingsSheet = { session.showSettingsSheet = false },
        onSpeedSelected = { speed ->
            session.playerState = session.playerState.copy(playbackSpeed = speed)
            session.controller.setPlaybackRate(speed)
        },
        onQualitySelected = { quality ->
            if (session.isAudioOnly && quality != "tiny") {
                session.isAudioOnly = false
                session.controller.setAudioOnly(false)
            }
            session.previousQualityBeforeAudioOnly = quality
            session.playerState = session.playerState.copy(currentQuality = quality)
            session.controller.setPlaybackQuality(quality)
        },
        onDoubleTapSeekSelected = { seconds ->
            repository.preferences?.setDoubleTapSeekSeconds(seconds)
        },
        onSleepTimerSelected = { option ->
            session.sleepTimerOption = option
            session.sleepTimerRemainingSec = if (option.seconds != null && option.seconds > 0) option.seconds else null
        },
        onLoopToggle = { loop ->
            session.isLoopEnabled = loop
            session.controller.setLoop(loop)
        },
        onAutoplayToggle = { session.isAutoplayNext = it },
        onAudioOnlyToggle = { enabled -> session.applyAudioOnlyState(enabled) },
        onDismissDescriptionSheet = { session.showDescriptionSheet = false },
        onChannelSelected = { chId, chTitle ->
            session.showDescriptionSheet = false
            session.showCommentsSheet = false
            onMinimize()
            onChannelClick?.invoke(chId, chTitle)
        },
        onTimestampClick = { targetSec ->
            val clamped = if (session.currentDurationSec > 0f) {
                targetSec.coerceIn(0f, session.currentDurationSec)
            } else {
                targetSec.coerceAtLeast(0f)
            }
            session.controller.seekTo(clamped)
            session.currentPositionSec = clamped
            session.mediaService?.updatePlaybackState(session.playerState.isPlaying, (clamped * 1000).toLong())
            if (!session.playerState.isPlaying) {
                session.controller.play()
            }
        },
        onVideoLinkClick = { targetVideo ->
            session.showDescriptionSheet = false
            session.showCommentsSheet = false
            if (targetVideo.id != video.id) {
                if (!session.networkObserver.isConnected()) {
                    session.showNotice(context.getString(R.string.player_no_internet_notice))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    val matchedRec = (session.recommendedVideos + session.mixPlaylist).firstOrNull { it.id == targetVideo.id }
                    val targetVideoItem = matchedRec ?: targetVideo
                    session.isInternalNavigation = false
                    session.onPlayNextVideoCallback(targetVideoItem)
                }
            }
        },
        onLoadMoreComments = {
            val token = session.commentsContinuationToken
            if (token != null) {
                session.isLoadingComments = true
                scope.launch {
                    try {
                        val res = repository.getComments(token)
                        if (res.comments.isNotEmpty()) {
                            val existingIds = session.commentsList.map { it.id }.toSet()
                            val fresh = res.comments.filterNot { it.id in existingIds }
                            session.commentsList = session.commentsList + fresh
                        }
                        session.commentsContinuationToken = res.continuationToken
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        session.isLoadingComments = false
                    }
                }
            }
        },
        onDismissCommentsSheet = { session.showCommentsSheet = false },
        onDismissDownloadSheet = { session.showDownloadSheet = false }
    )
}

@Composable
private fun rememberPlayerDragModifier(
    session: PlayerSessionState,
    isMinimized: Boolean,
    screenHeightPx: Float,
    onMinimize: () -> Unit
): Modifier {
    val scope = session.scope
    val haptic = session.haptic
    return if (!isMinimized) {
        Modifier.pointerInput(session.isFullscreen, screenHeightPx) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (session.isFullscreen) {
                        if (dragAmount > 0f || session.dragOffsetY > 0f) {
                            change.consume()
                            session.dragOffsetY = (session.dragOffsetY + dragAmount).coerceAtLeast(0f)
                            if (session.dragOffsetY > 80f && !session.hasTriggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                session.hasTriggeredHaptic = true
                            }
                        }
                    } else {
                        if (dragAmount > 0f || session.portraitDragPx > 0f) {
                            change.consume()
                            session.portraitDragPx = (session.portraitDragPx + dragAmount).coerceIn(0f, screenHeightPx)
                            val targetProgress = (1f - (session.portraitDragPx / screenHeightPx)).coerceIn(0f, 1f)
                            scope.launch {
                                session.fullSheetProgress.snapTo(targetProgress)
                            }
                            if (session.portraitDragPx > 15f) {
                                session.showMixSheet = false
                                session.showDescriptionSheet = false
                                session.showCommentsSheet = false
                                session.showSettingsSheet = false
                            }
                            if (session.portraitDragPx > 180f && !session.hasTriggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                session.hasTriggeredHaptic = true
                            }
                        }
                    }
                },
                onDragEnd = {
                    val triggeredFullscreenDrag = session.dragOffsetY
                    val triggeredPortraitDrag = session.portraitDragPx
                    session.dragOffsetY = 0f
                    session.portraitDragPx = 0f
                    session.hasTriggeredHaptic = false
                    if (session.isFullscreen) {
                        if (triggeredFullscreenDrag > 80f) {
                            session.exitFullscreenToPortrait()
                        }
                    } else {
                        if (triggeredPortraitDrag > 180f) {
                            session.showMixSheet = false
                            session.showDescriptionSheet = false
                            session.showCommentsSheet = false
                            session.showSettingsSheet = false
                            onMinimize()
                        } else {
                            scope.launch {
                                session.fullSheetProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = IosSpringSpecs.Gentle
                                )
                            }
                        }
                    }
                },
                onDragCancel = {
                    session.dragOffsetY = 0f
                    session.portraitDragPx = 0f
                    session.hasTriggeredHaptic = false
                    if (!session.isFullscreen && !isMinimized) {
                        scope.launch {
                            session.fullSheetProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = IosSpringSpecs.Gentle
                            )
                        }
                    }
                }
            )
        }
    } else Modifier
}

@Composable
private fun PlayerLifecycleEffects(
    session: PlayerSessionState,
    video: VideoItem,
    isMinimized: Boolean,
    isFloatingMiniPlayer: Boolean
) {
    val context = session.context
    val repository = session.repository
    val scope = session.scope
    val haptic = session.haptic
    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isDeviceLandscape) {
        if (isDeviceLandscape && !session.isFullscreen) {
            session.isFullscreen = true
        } else if (!isDeviceLandscape && session.isFullscreen) {
            val activity = context.findActivity()
            if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            session.isFullscreen = false
        }
    }

    DisposableEffect(session.isFullscreen, isMinimized) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (session.isFullscreen && !isMinimized) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(session.seekAnimationSide, session.seekAccumulatedSeconds) {
        if (session.seekAnimationSide != null) {
            delay(750L)
            session.seekAnimationSide = null
            session.seekAccumulatedSeconds = 0
        }
    }

    LaunchedEffect(video.id, video.playlistId) {
        if (!video.playlistId.isNullOrBlank()) {
            session.activePlaylistId = video.playlistId
        }
        if (video.channelAvatarUrl.isNotBlank()) {
            session.activeAvatarUrl = video.channelAvatarUrl
        }
        session.skippedSegmentUuids = ConcurrentHashMap.newKeySet()
        session.undoneSegmentUuids = ConcurrentHashMap.newKeySet()
        session.hasSyncedMediaSessionInitialPosition = false
        session.lastSyncedMediaSessionSec = 0f
        session.recContinuationToken = null
        session.canLoadMoreRecs = true
        session.nextQueueData = null
        session.commentsList = emptyList()
        session.commentsTotalCountText = ""
        session.commentsContinuationToken = null
        session.isLoadingComments = false
        session.showDescriptionSheet = false
        session.isEndscreenDismissed = false
        session.liveEndscreenVideos = emptyList()
        session.showCommentsSheet = false
        session.showDownloadSheet = false
    }

    val isNetworkConnected by session.networkObserver.isConnectedFlow.collectAsState(initial = session.networkObserver.isConnected())
    val isOfflinePlaying by OfflineAudioPlayerManager.isPlaying.collectAsState()

    LaunchedEffect(isOfflinePlaying) {
        if (isOfflinePlaying) {
            session.controller.pause()
        }
    }

    LaunchedEffect(isNetworkConnected) {
        if (!isNetworkConnected) {
            session.isOfflineWaiting = true
        } else {
            if (session.isOfflineWaiting) {
                session.isOfflineWaiting = false
                session.showNotice(context.getString(R.string.player_online_restored))
                if (session.wasPlayingBeforeOffline || session.isPlaybackStalled || session.playerState.isBuffering) {
                    session.wasPlayingBeforeOffline = false
                    session.isPlaybackStalled = false
                    session.controller.play()
                    delay(2500)
                    if (!session.playerState.isPlaying && session.networkObserver.isConnected()) {
                        session.controller.loadVideo(session.currentVideo.id, session.currentPositionSec)
                    }
                }
            }
        }
    }

    LaunchedEffect(session.playerState.isPlaying) {
        if (session.playerState.isPlaying) {
            OfflineAudioPlayerManager.stop(context)
            session.lastProgressElapsedRealtime = SystemClock.elapsedRealtime()
            session.lastObservedPlaybackSec = session.currentPositionSec
            while (session.playerState.isPlaying) {
                delay(1500L)
                val now = SystemClock.elapsedRealtime()
                if (session.playerState.isPlaying && (now - session.lastProgressElapsedRealtime > 2500L)) {
                    if (!session.isPlaybackStalled) {
                        session.isPlaybackStalled = true
                        if (!session.networkObserver.isConnected()) {
                            session.wasPlayingBeforeOffline = true
                        }
                        session.playerState = session.playerState.copy(isBuffering = true)
                        session.mediaService?.updatePlaybackState(
                            playing = false,
                            currentPositionMs = (session.currentPositionSec * 1000).toLong(),
                            forceNotify = true,
                            isBuffering = true
                        )
                    }
                }
            }
        } else {
            session.isPlaybackStalled = false
        }
    }

    LaunchedEffect(session.sleepTimerRemainingSec, session.playerState.isPlaying) {
        if (session.sleepTimerRemainingSec != null && (session.sleepTimerRemainingSec ?: 0) > 0 && session.playerState.isPlaying) {
            delay(1000L)
            if (session.playerState.isPlaying) {
                val current = (session.sleepTimerRemainingSec ?: 0) - 1
                if (current <= 0) {
                    session.controller.pause()
                    session.sleepTimerRemainingSec = null
                    session.sleepTimerOption = SleepTimerOption.OFF
                } else {
                    session.sleepTimerRemainingSec = current
                }
            }
        }
    }

    LaunchedEffect(session.isAudioOnly, isMinimized) {
        val activity = context.findActivity()
        if (session.isAudioOnly || isMinimized) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(session.isControlsVisible, session.playerState.isPlaying, session.isQualityMenuExpanded) {
        if (session.isControlsVisible && session.playerState.isPlaying && !session.isQualityMenuExpanded) {
            delay(4000)
            session.isControlsVisible = false
        }
    }

    LaunchedEffect(session.mediaService) {
        val service = session.mediaService ?: return@LaunchedEffect
        val lastPos = (session.currentPositionSec * 1000).toLong()
        service.updateMediaInfo(
            title = session.currentVideo.title,
            channel = session.currentVideo.channelTitle,
            durationMs = (session.currentDurationSec * 1000).toLong(),
            playing = session.playerState.isPlaying,
            thumbnailUrl = session.currentVideo.thumbnailUrl,
            currentPositionMs = lastPos
        )
    }

    LaunchedEffect(video.id) {
        OfflineAudioPlayerManager.stop(context)
        session.playerState = PlayerState(
            videoId = video.id,
            playbackSpeed = session.playerState.playbackSpeed
        )
        session.appliedDefaultsVideoId = null
        session.sponsorSegments = emptyList()
        session.lastRecordedPositionSec = 0f
        session.isPlaybackStalled = false
        session.lastProgressElapsedRealtime = SystemClock.elapsedRealtime()
        session.lastObservedPlaybackSec = 0f
        session.storyboardData = null
        StoryboardHelper.clearCache()
        repository.clearCommentsCache()
        session.isScrubbing = false
        session.isQualityMenuExpanded = false
        session.controller.requestStoryboard()
        val fromInternal = session.isInternalNavigation
        if (fromInternal) {
            session.isInternalNavigation = false
        }

        val lastPos = repository.getLastPosition(video.id)
        session.currentPositionSec = lastPos / 1000f
        session.currentDurationSec = 0f
        session.currentBufferedFraction = 0f
        session.playerState = session.playerState.copy(currentTimeSec = lastPos / 1000f)
        session.controller.loadVideo(video.id, lastPos / 1000f)

        session.mediaService?.updateMediaInfo(
            title = video.title,
            channel = video.channelTitle,
            durationMs = 0L,
            playing = true,
            thumbnailUrl = video.thumbnailUrl,
            currentPositionMs = lastPos
        )

        launch {
            session.sponsorSegments = repository.getSponsorSegments(video.id)
        }

        launch {
            session.isLoadingQueue = true
            val targetPlaylistId = video.playlistId?.ifBlank { null }
                ?: session.activePlaylistId?.ifBlank { null }
                ?: "RD${video.id}"
            session.activePlaylistId = targetPlaylistId
            val nextData = repository.getWatchNext(video.id, targetPlaylistId)
            if (nextData != null) {
                session.nextQueueData = nextData
                if (nextData.currentVideo.channelAvatarUrl.isNotBlank()) {
                    session.activeAvatarUrl = nextData.currentVideo.channelAvatarUrl
                }
                val enrichedVideo = video.copy(
                    title = nextData.currentVideo.title.ifBlank { video.title },
                    channelTitle = nextData.currentVideo.channelTitle.ifBlank { video.channelTitle },
                    channelId = nextData.currentVideo.channelId.ifBlank { video.channelId },
                    thumbnailUrl = nextData.currentVideo.thumbnailUrl.ifBlank { video.thumbnailUrl },
                    channelAvatarUrl = nextData.currentVideo.channelAvatarUrl.ifBlank { video.channelAvatarUrl },
                    durationText = nextData.currentVideo.durationText.ifBlank { video.durationText }
                )
                if (video.title.isBlank() && enrichedVideo.title.isNotBlank()) {
                    if (session.currentPositionSec > 0.5f || lastPos > 0L) {
                        val posMs = if (session.currentPositionSec > 0.5f) (session.currentPositionSec * 1000).toLong() else lastPos
                        repository.recordWatch(
                            video = enrichedVideo,
                            positionMs = posMs,
                            durationMs = (session.currentDurationSec * 1000).toLong()
                        )
                    }
                }
                session.mediaService?.updateMediaInfo(
                    title = enrichedVideo.title,
                    channel = enrichedVideo.channelTitle,
                    durationMs = (session.currentDurationSec * 1000).toLong(),
                    playing = session.playerState.isPlaying,
                    thumbnailUrl = enrichedVideo.thumbnailUrl,
                    currentPositionMs = (session.currentPositionSec * 1000).toLong()
                )
                session.recommendedVideos = nextData.recommendations
                session.recContinuationToken = nextData.recommendationsContinuationToken
                session.canLoadMoreRecs = true

                if (!fromInternal) {
                    session.showMixSheet = (video.isPlaylist || !video.playlistId.isNullOrBlank()) && nextData.mixPlaylist.isNotEmpty()
                    session.mixPlaylist = nextData.mixPlaylist
                    session.currentMixIndex = if (nextData.mixPlaylist.isNotEmpty()) {
                        val match = nextData.mixPlaylist.indexOfFirst { it.id == video.id }
                        if (match != -1) match else nextData.currentIndex
                    } else 0
                    session.mixTitle = nextData.playlistTitle.ifBlank { if (video.isPlaylist) video.title else "Mix" }
                } else {
                    if (session.currentMixIndex !in session.mixPlaylist.indices || session.mixPlaylist[session.currentMixIndex].id != video.id) {
                        val idx = session.mixPlaylist.indexOfFirst { it.id == video.id }
                        if (idx != -1) {
                            session.currentMixIndex = idx
                        }
                    }
                    if (session.mixPlaylist.isNotEmpty() && session.currentMixIndex >= session.mixPlaylist.size - 5) {
                        val existingIds = session.mixPlaylist.map { it.id }.toSet()
                        val freshItems = nextData.mixPlaylist.filter { it.id !in existingIds }
                        if (freshItems.isNotEmpty()) {
                            session.mixPlaylist = session.mixPlaylist + freshItems
                        }
                    }
                }

                if (!nextData.commentsContinuationToken.isNullOrBlank()) {
                    launch {
                        session.isLoadingComments = true
                        try {
                            val cResult = repository.getComments(nextData.commentsContinuationToken)
                            session.commentsList = cResult.comments
                            session.commentsTotalCountText = cResult.totalCountText.ifBlank { nextData.commentsCountText }
                            session.commentsContinuationToken = cResult.continuationToken
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            session.isLoadingComments = false
                        }
                    }
                }
            } else if (fromInternal) {
                if (session.currentMixIndex !in session.mixPlaylist.indices || session.mixPlaylist[session.currentMixIndex].id != video.id) {
                    val idx = session.mixPlaylist.indexOfFirst { it.id == video.id }
                    if (idx != -1) {
                        session.currentMixIndex = idx
                    }
                }
            }
            session.isLoadingQueue = false
        }
    }

    LaunchedEffect(isMinimized, isFloatingMiniPlayer) {
        if (!isMinimized) {
            session.dragOffsetY = 0f
            session.portraitDragPx = 0f
            if (!session.isAudioOnly) {
                val restoreQ = session.playerState.currentQuality.ifBlank {
                    repository.preferences?.defaultQuality?.value ?: "auto"
                }
                session.controller.setVideoSurfaceActive(true, restoreQ)
            }
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
            session.fullSheetProgress.animateTo(
                targetValue = 1f,
                animationSpec = IosSpringSpecs.Gentle
            )
        } else {
            session.showMixSheet = false
            session.showDescriptionSheet = false
            session.showCommentsSheet = false
            session.showSettingsSheet = false
            session.showDownloadSheet = false
            session.dragOffsetY = 0f
            session.portraitDragPx = 0f
            if (!session.isAudioOnly && !isFloatingMiniPlayer) {
                session.controller.setVideoSurfaceActive(false)
            }
            if (isFloatingMiniPlayer) {
                session.fullSheetProgress.snapTo(0f)
            } else {
                session.fullSheetProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = IosSpringSpecs.Gentle
                )
            }
            if (!isFloatingMiniPlayer) {
                com.phantom.tube.core.util.PhantomMemoryOptimizer.trimNow(
                    context = context,
                    aggressiveGraphicsPurge = true
                )
            }
        }
    }

    LaunchedEffect(session.showCommentsSheet) {
        if (session.showCommentsSheet && session.commentsList.isEmpty() && !session.isLoadingComments) {
            val token = session.commentsContinuationToken ?: session.nextQueueData?.commentsContinuationToken
            if (!token.isNullOrBlank()) {
                scope.launch {
                    session.isLoadingComments = true
                    try {
                        val cResult = repository.getComments(token)
                        session.commentsList = cResult.comments
                        if (cResult.totalCountText.isNotBlank()) {
                            session.commentsTotalCountText = cResult.totalCountText
                        } else if (session.commentsTotalCountText.isBlank()) {
                            session.commentsTotalCountText = session.nextQueueData?.commentsCountText ?: ""
                        }
                        session.commentsContinuationToken = cResult.continuationToken
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        session.isLoadingComments = false
                    }
                }
            }
        }
    }

    LaunchedEffect(session.lazyListState, session.recContinuationToken, session.isLoadingMoreRecs, session.canLoadMoreRecs) {
        snapshotFlow {
            val layoutInfo = session.lazyListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total to last
        }
            .filter { (total, last) -> total > 0 && last >= total - 3 }
            .collect {
                if (!session.isLoadingMoreRecs && !session.isLoadingQueue && session.canLoadMoreRecs && session.recommendedVideos.isNotEmpty()) {
                    session.loadMoreRecommendations()
                }
            }
    }

    LaunchedEffect(session.recContinuationToken, session.isLoadingMoreRecs) {
        if (!session.isLoadingMoreRecs && !session.isLoadingQueue && session.canLoadMoreRecs && session.recContinuationToken != null) {
            val layoutInfo = session.lazyListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && last >= total - 3) {
                session.loadMoreRecommendations()
            }
        }
    }
}

@Composable
private fun PlayerMediaServiceEffect(session: PlayerSessionState) {
    val context = session.context
    val repository = session.repository
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, PhantomMediaService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? PhantomMediaService.LocalBinder)?.getService()
                service?.apply {
                    onPlayAction = {
                        if (!session.networkObserver.isConnected()) {
                            session.showNotice(context.getString(R.string.player_no_internet_notice))
                        } else {
                            session.controller.play()
                        }
                    }
                    onPauseAction = { session.controller.pause() }
                    onNextAction = { session.playNext() }
                    onPreviousAction = { session.playPrevious() }
                    onOfflineTimeoutAction = {
                        session.controller.pause()
                        session.isOfflineWaiting = false
                        session.wasPlayingBeforeOffline = false
                        session.showNotice(context.getString(R.string.player_stopped_offline_timeout))
                    }
                    onSeekAction = { posMs ->
                        val sec = posMs / 1000f
                        session.controller.seekTo(sec)
                        session.currentPositionSec = sec
                        updatePlaybackState(session.playerState.isPlaying, posMs)
                    }
                }
                session.mediaService = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                session.mediaService = null
            }
        }

        try {
            context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (session.isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity?.window
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            session.controller.release()
            StoryboardHelper.clearCache()
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                context.stopService(Intent(context, PhantomMediaService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val isBgAllowed = repository.preferences?.backgroundPlaybackEnabled?.value ?: true
                    val activity = context.findActivity()
                    val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        activity?.isInPictureInPictureMode == true
                    } else false
                    if (!inPip) {
                        if (!isBgAllowed) {
                            session.controller.pause()
                        } else {
                            session.controller.setVideoSurfaceActive(false)
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (!session.isAudioOnly) {
                        val restoreQ = session.playerState.currentQuality.ifBlank {
                            repository.preferences?.defaultQuality?.value ?: "auto"
                        }
                        session.controller.setVideoSurfaceActive(true, restoreQ)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

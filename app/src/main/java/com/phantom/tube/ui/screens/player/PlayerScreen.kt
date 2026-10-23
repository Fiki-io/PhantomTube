package com.phantom.tube.ui.screens.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.SponsorSegment
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.player.PhantomPlayerBridge
import com.phantom.tube.player.PhantomPlayerController
import com.phantom.tube.player.PlayerState
import com.phantom.tube.player.StoryboardData
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
import com.phantom.tube.ui.screens.player.components.SponsorBlockEvaluator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

enum class SeekFeedbackDirection { FORWARD, REWIND }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: VideoItem,
    repository: PhantomRepository,
    isMinimized: Boolean = false,
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
    val scope = rememberCoroutineScope()

    var playerState by remember { mutableStateOf(PlayerState(videoId = video.id)) }
    var currentPositionSec by remember { mutableFloatStateOf(0f) }
    var currentDurationSec by remember { mutableFloatStateOf(0f) }
    var currentBufferedFraction by remember { mutableFloatStateOf(0f) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isDeviceLandscape) {
        if (isDeviceLandscape && !isFullscreen) {
            isFullscreen = true
        } else if (!isDeviceLandscape && isFullscreen) {
            val activity = context.findActivity()
            if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            isFullscreen = false
        }
    }

    DisposableEffect(isFullscreen, isMinimized) {
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
            if (isFullscreen && !isMinimized) {
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

    var seekAnimationSide by remember { mutableStateOf<SeekFeedbackDirection?>(null) }
    var seekAccumulatedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekAnimationSide, seekAccumulatedSeconds) {
        if (seekAnimationSide != null) {
            delay(750L)
            seekAnimationSide = null
            seekAccumulatedSeconds = 0
        }
    }

    var sponsorSegments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var showSponsorPill by remember { mutableStateOf(false) }
    var lastSkippedSeconds by remember { mutableIntStateOf(0) }
    var lastSkippedFromSec by remember { mutableFloatStateOf(0f) }
    var lastSkippedCategory by remember { mutableStateOf("sponsor") }
    var lastTargetSkipEndSec by remember { mutableFloatStateOf(0f) }
    val skippedSegmentUuids = remember(video.id) { mutableSetOf<String>() }
    val undoneSegmentUuids = remember(video.id) { mutableSetOf<String>() }
    var appliedDefaultsVideoId by remember { mutableStateOf<String?>(null) }
    var hasSyncedMediaSessionInitialPosition by remember(video.id) { mutableStateOf(false) }
    var lastSyncedMediaSessionSec by remember(video.id) { mutableFloatStateOf(0f) }

    var mixPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var activePlaylistId by remember(video.id) { mutableStateOf(video.playlistId) }
    var currentMixIndex by remember { mutableIntStateOf(0) }
    var recommendedVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var recContinuationToken by remember { mutableStateOf<String?>(null) }
    var isLoadingMoreRecs by remember { mutableStateOf(false) }
    var canLoadMoreRecs by remember { mutableStateOf(true) }
    var activeAvatarUrl by remember { mutableStateOf(video.channelAvatarUrl) }
    var nextQueueData by remember { mutableStateOf<NextQueue?>(null) }
    var commentsList by remember { mutableStateOf<List<VideoComment>>(emptyList()) }
    var commentsTotalCountText by remember { mutableStateOf("") }
    var commentsContinuationToken by remember { mutableStateOf<String?>(null) }
    var isLoadingComments by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var isEndscreenDismissed by remember(video.id) { mutableStateOf(false) }
    var liveEndscreenVideos by remember(video.id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var mixTitle by remember { mutableStateOf("") }
    var showMixSheet by remember { mutableStateOf(false) }
    var isInternalNavigation by remember { mutableStateOf(false) }
    var isLoadingQueue by remember { mutableStateOf(false) }

    LaunchedEffect(video.id, video.playlistId) {
        if (!video.playlistId.isNullOrBlank()) {
            activePlaylistId = video.playlistId
        }
        if (video.channelAvatarUrl.isNotBlank()) {
            activeAvatarUrl = video.channelAvatarUrl
        }
        recContinuationToken = null
        canLoadMoreRecs = true
        nextQueueData = null
        commentsList = emptyList()
        commentsTotalCountText = ""
        commentsContinuationToken = null
        isLoadingComments = false
        showDescriptionSheet = false
        isEndscreenDismissed = false
        liveEndscreenVideos = emptyList()
        showCommentsSheet = false
        showDownloadSheet = false
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    val prefDoubleTapSeekFlow = remember(repository) { repository.preferences?.doubleTapSeekSeconds }
    val prefDoubleTapSeek by (prefDoubleTapSeekFlow?.collectAsState() ?: remember { mutableIntStateOf(10) })
    var fallbackDoubleTapSeek by remember { mutableIntStateOf(10) }
    val doubleTapSeekSeconds = if (prefDoubleTapSeekFlow != null) prefDoubleTapSeek else fallbackDoubleTapSeek
    var isLoopEnabled by remember { mutableStateOf(false) }
    var isAutoplayNext by remember { mutableStateOf(true) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var previousQualityBeforeAudioOnly by remember { mutableStateOf("auto") }
    var sleepTimerOption by remember { mutableStateOf(SleepTimerOption.OFF) }
    var sleepTimerRemainingSec by remember { mutableStateOf<Int?>(null) }
    var lastRecordedPositionSec by remember { mutableFloatStateOf(0f) }

    var mediaService by remember { mutableStateOf<PhantomMediaService?>(null) }

    val currentVideo by rememberUpdatedState(video)
    val currentOnPlayNextVideo by rememberUpdatedState(onPlayNextVideo)
    val currentOnPlayPreviousVideo by rememberUpdatedState(onPlayPreviousVideo)

    val controller = remember { PhantomPlayerController(context) }

    val isOfflinePlaying by OfflineAudioPlayerManager.isPlaying.collectAsState()
    LaunchedEffect(isOfflinePlaying) {
        if (isOfflinePlaying) {
            controller.pause()
        }
    }

    LaunchedEffect(video.id) {
        OfflineAudioPlayerManager.stop(context)
    }
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            OfflineAudioPlayerManager.stop(context)
        }
    }

    LaunchedEffect(sleepTimerRemainingSec, playerState.isPlaying) {
        if (sleepTimerRemainingSec != null && (sleepTimerRemainingSec ?: 0) > 0 && playerState.isPlaying) {
            delay(1000L)
            if (playerState.isPlaying) {
                val current = (sleepTimerRemainingSec ?: 0) - 1
                if (current <= 0) {
                    controller.pause()
                    sleepTimerRemainingSec = null
                    sleepTimerOption = SleepTimerOption.OFF
                } else {
                    sleepTimerRemainingSec = current
                }
            }
        }
    }

    val playNext: () -> Unit = {
        if (mixPlaylist.isNotEmpty()) {
            if (currentMixIndex < mixPlaylist.lastIndex) {
                val nextIndex = currentMixIndex + 1
                val nextVid = mixPlaylist[nextIndex]
                currentMixIndex = nextIndex
                isInternalNavigation = true
                currentOnPlayNextVideo(nextVid)
            } else {
                scope.launch {
                    isLoadingQueue = true
                    val targetPlId = activePlaylistId?.ifBlank { null } ?: "RD${currentVideo.id}"
                    val nextData = repository.getWatchNext(currentVideo.id, targetPlId)
                    if (nextData != null && nextData.mixPlaylist.isNotEmpty()) {
                        val existingIds = mixPlaylist.map { it.id }.toSet()
                        val freshItems = nextData.mixPlaylist.filter { it.id !in existingIds }
                        if (freshItems.isNotEmpty()) {
                            val nextIndex = mixPlaylist.size
                            mixPlaylist = mixPlaylist + freshItems
                            currentMixIndex = nextIndex
                            isInternalNavigation = true
                            currentOnPlayNextVideo(mixPlaylist[nextIndex])
                        }
                    }
                    isLoadingQueue = false
                }
            }
        } else if (recommendedVideos.isNotEmpty()) {
            val nextVid = recommendedVideos.first()
            isInternalNavigation = false
            currentOnPlayNextVideo(nextVid)
        }
    }

    val playPrevious: () -> Boolean = {
        if (currentPositionSec > 3f) {
            controller.seekTo(0f)
            currentPositionSec = 0f
            true
        } else if (mixPlaylist.isNotEmpty() && currentMixIndex > 0) {
            val prevIndex = currentMixIndex - 1
            val prevVid = mixPlaylist[prevIndex]
            currentMixIndex = prevIndex
            isInternalNavigation = true
            scope.launch {
                repository.resetWatchPosition(prevVid.id)
            }
            currentOnPlayNextVideo(prevVid)
            true
        } else {
            val handled = currentOnPlayPreviousVideo()
            if (!handled) {
                controller.seekTo(0f)
            }
            handled
        }
    }

    val playFromMix: (Int) -> Unit = { targetIndex ->
        if (targetIndex in mixPlaylist.indices) {
            currentMixIndex = targetIndex
            isInternalNavigation = true
            currentOnPlayNextVideo(mixPlaylist[targetIndex])
        }
    }

    var storyboardData by remember { mutableStateOf<StoryboardData?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var scrubTouchX by remember { mutableFloatStateOf(0f) }
    var videoBoxWidthPx by remember { mutableFloatStateOf(0f) }
    var videoBoxHeightPx by remember { mutableFloatStateOf(0f) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var lastScrubSeekTimeMs by remember { mutableLongStateOf(0L) }
    var isZoomToFill by remember { mutableStateOf(false) }
    var isCaptionsEnabled by remember { mutableStateOf(false) }
    var hasCaptions by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    var playerNoticeText by remember { mutableStateOf<String?>(null) }
    var noticeJob by remember { mutableStateOf<Job?>(null) }

    fun showNotice(text: String) {
        noticeJob?.cancel()
        playerNoticeText = text
        noticeJob = scope.launch {
            delay(1800)
            playerNoticeText = null
        }
    }

    fun applyAudioOnlyState(enabled: Boolean) {
        if (enabled == isAudioOnly) return
        if (enabled) {
            val currentQ = playerState.currentQuality
            if (currentQ.isNotBlank() && currentQ != "tiny") {
                previousQualityBeforeAudioOnly = currentQ
            }
            isAudioOnly = true
            playerState = playerState.copy(currentQuality = "tiny")
            controller.setAudioOnly(true)
            showNotice(context.getString(R.string.player_audio_only_active_toast))
        } else {
            isAudioOnly = false
            controller.setAudioOnly(false)
            val restoreQ = previousQualityBeforeAudioOnly.ifBlank {
                repository.preferences?.defaultQuality?.value ?: "auto"
            }
            playerState = playerState.copy(currentQuality = restoreQ)
            controller.setPlaybackQuality(restoreQ)
            showNotice(context.getString(R.string.player_audio_only_restored_toast))
        }
    }

    val fillScale = remember(videoBoxWidthPx, videoBoxHeightPx) {
        if (videoBoxHeightPx > 0f && videoBoxWidthPx > 0f) {
            val containerRatio = videoBoxWidthPx / videoBoxHeightPx
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
        targetValue = if (isZoomToFill) fillScale else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "zoomScale"
    )

    val bridge = remember {
        PhantomPlayerBridge(
            onReadyCallback = {
                controller.onReady()
                if (isCaptionsEnabled) {
                    controller.setCaptionsEnabled(true)
                }
            },
            onStoryboardSpecCallback = { spec ->
                val parsed = StoryboardHelper.parse(spec)
                if (parsed != null) {
                    storyboardData = parsed
                }
            },
            onStateChangeCallback = { state ->
                when (state) {
                    1 -> {
                        playerState = playerState.copy(isPlaying = true, isBuffering = false, isEnded = false, errorCode = null)
                        mediaService?.updatePlaybackState(true, (currentPositionSec * 1000).toLong())
                        if (isCaptionsEnabled) {
                            controller.setCaptionsEnabled(true)
                        }
                        if (isAudioOnly) {
                            controller.setAudioOnly(true)
                        }
                        if (appliedDefaultsVideoId != currentVideo.id) {
                            appliedDefaultsVideoId = currentVideo.id
                            val defSpeed = repository.preferences?.defaultSpeed?.value ?: 1.0f
                            if (defSpeed != 1.0f) {
                                playerState = playerState.copy(playbackSpeed = defSpeed)
                                controller.setPlaybackRate(defSpeed)
                            }
                            if (isAudioOnly) {
                                playerState = playerState.copy(currentQuality = "tiny")
                                controller.setAudioOnly(true)
                            } else {
                                val defQuality = repository.preferences?.defaultQuality?.value ?: "auto"
                                if (defQuality != "auto") {
                                    playerState = playerState.copy(currentQuality = defQuality)
                                    controller.setPlaybackQuality(defQuality)
                                }
                            }
                        }
                    }
                    2 -> {
                        playerState = playerState.copy(isPlaying = false, isBuffering = false)
                        mediaService?.updatePlaybackState(false, (currentPositionSec * 1000).toLong())
                        val activeVid = currentVideo
                        val pos = currentPositionSec
                        val dur = currentDurationSec
                        if (pos > 1f) {
                            scope.launch {
                                repository.recordWatch(
                                    video = activeVid,
                                    positionMs = (pos * 1000).toLong(),
                                    durationMs = (dur * 1000).toLong()
                                )
                            }
                        }
                    }
                    3 -> playerState = playerState.copy(isBuffering = true)
                    0 -> {
                        playerState = playerState.copy(isPlaying = false, isEnded = true)
                        mediaService?.updatePlaybackState(false, 0L)
                        scope.launch {
                            repository.resetWatchPosition(video.id)
                        }
                        if (isLoopEnabled) {
                            controller.seekTo(0f)
                            controller.play()
                        } else if (sleepTimerOption == SleepTimerOption.END_OF_VIDEO) {
                            controller.pause()
                            sleepTimerOption = SleepTimerOption.OFF
                            sleepTimerRemainingSec = null
                        } else if (isAutoplayNext) {
                            playNext()
                        }
                    }
                }
            },
            onTimeUpdateCallback = { reportingVideoId, current, duration, buffered ->
                if (reportingVideoId.isNotBlank() && reportingVideoId != currentVideo.id) {
                    return@PhantomPlayerBridge
                }

                val wasZeroDuration = currentDurationSec <= 0f && duration > 0f
                currentPositionSec = current
                currentDurationSec = duration
                currentBufferedFraction = buffered

                if (wasZeroDuration) {
                    playerState = playerState.copy(durationSec = duration)
                    mediaService?.updateDuration((duration * 1000).toLong(), (current * 1000).toLong())
                }

                if (!hasSyncedMediaSessionInitialPosition && current > 0.5f) {
                    hasSyncedMediaSessionInitialPosition = true
                    lastSyncedMediaSessionSec = current
                    mediaService?.updatePlaybackState(playerState.isPlaying, (current * 1000).toLong())
                } else if (kotlin.math.abs(current - lastSyncedMediaSessionSec) >= 5f) {
                    lastSyncedMediaSessionSec = current
                    mediaService?.updatePlaybackState(playerState.isPlaying, (current * 1000).toLong())
                }

                if (current > 2f && (current - lastRecordedPositionSec >= 5f || current < lastRecordedPositionSec)) {
                    lastRecordedPositionSec = current
                    val activeVid = currentVideo
                    scope.launch {
                        repository.recordWatch(
                            video = activeVid,
                            positionMs = (current * 1000).toLong(),
                            durationMs = (duration * 1000).toLong()
                        )
                    }
                }

                val match = SponsorBlockEvaluator.findMatchingSegment(
                    currentSec = current,
                    durationSec = duration,
                    segments = sponsorSegments,
                    preferences = repository.preferences,
                    skippedUuids = skippedSegmentUuids,
                    undoneUuids = undoneSegmentUuids
                )
                if (match != null) {
                    skippedSegmentUuids.add(match.uuid)
                    lastSkippedFromSec = current
                    lastTargetSkipEndSec = match.endSecond
                    lastSkippedSeconds = match.skippedSeconds
                    lastSkippedCategory = match.category
                    if (match.autoSkip) {
                        controller.seekTo(match.endSecond)
                        currentPositionSec = match.endSecond
                        mediaService?.updatePlaybackState(playerState.isPlaying, (match.endSecond * 1000).toLong())
                    }
                    showSponsorPill = true
                    scope.launch {
                        delay(4000)
                        showSponsorPill = false
                    }
                    if (match.category == "outro") {
                        val activeVid = currentVideo
                        scope.launch {
                            repository.resetWatchPosition(activeVid.id)
                        }
                    }
                }
            },
            onErrorCallback = { errorCode ->
                val msg = when (errorCode) {
                    100 -> context.getString(R.string.player_err_not_available)
                    101, 150 -> context.getString(R.string.player_err_restricted)
                    152 -> context.getString(R.string.player_err_unsupported)
                    2 -> context.getString(R.string.player_err_invalid)
                    5 -> context.getString(R.string.player_error_load)
                    else -> "${context.getString(R.string.player_error_load)} ($errorCode)"
                }
                playerState = playerState.copy(isBuffering = false, errorCode = msg)
            },
            onQualityChangeCallback = { currentQuality, availableQualities ->
                playerState = playerState.copy(
                    currentQuality = currentQuality,
                    availableQualities = availableQualities
                )
            },
            onCaptionsAvailableCallback = { available ->
                hasCaptions = available
            },
            onEndscreenDataCallback = { itemsJson ->
                try {
                    val arr = org.json.JSONArray(itemsJson)
                    val items = mutableListOf<VideoItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id")
                        if (id.isNotBlank() && id != currentVideo.id) {
                            items.add(
                                VideoItem(
                                    id = id,
                                    title = obj.optString("title"),
                                    channelTitle = obj.optString("channel"),
                                    durationText = obj.optString("duration"),
                                    thumbnailUrl = obj.optString("thumbnail").ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
                                )
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        scope.launch {
                            liveEndscreenVideos = items
                        }
                    }
                } catch (_: Exception) {
                }
            }
        )
    }

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
                    onPlayAction = { controller.play() }
                    onPauseAction = { controller.pause() }
                    onNextAction = { playNext() }
                    onPreviousAction = { playPrevious() }
                    onSeekAction = { posMs ->
                        val sec = posMs / 1000f
                        controller.seekTo(sec)
                        currentPositionSec = sec
                        updatePlaybackState(playerState.isPlaying, posMs)
                    }
                }
                mediaService = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mediaService = null
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
            if (isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity?.window
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            controller.release()
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

    LaunchedEffect(isAudioOnly, isMinimized) {
        val activity = context.findActivity()
        if (isAudioOnly || isMinimized) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val isBgAllowed = repository.preferences?.backgroundPlaybackEnabled?.value ?: true
                val activity = context.findActivity()
                val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    activity?.isInPictureInPictureMode == true
                } else false
                if (!isBgAllowed && !inPip) {
                    controller.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isControlsVisible, playerState.isPlaying, isQualityMenuExpanded) {
        if (isControlsVisible && playerState.isPlaying && !isQualityMenuExpanded) {
            delay(4000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(video.id, mediaService) {
        playerState = PlayerState(videoId = video.id)
        appliedDefaultsVideoId = null
        sponsorSegments = emptyList()
        lastRecordedPositionSec = 0f
        storyboardData = null
        StoryboardHelper.clearCache()
        isScrubbing = false
        isQualityMenuExpanded = false
        controller.requestStoryboard()
        val fromInternal = isInternalNavigation
        if (fromInternal) {
            isInternalNavigation = false
        }

        val lastPos = repository.getLastPosition(video.id) ?: 0L
        currentPositionSec = lastPos / 1000f
        currentDurationSec = 0f
        currentBufferedFraction = 0f
        playerState = playerState.copy(currentTimeSec = lastPos / 1000f)
        controller.loadVideo(video.id, lastPos / 1000f)

        mediaService?.updateMediaInfo(
            title = video.title,
            channel = video.channelTitle,
            durationMs = 0L,
            playing = true,
            thumbnailUrl = video.thumbnailUrl,
            currentPositionMs = lastPos
        )

        launch {
            sponsorSegments = repository.getSponsorSegments(video.id)
        }

        launch {
            isLoadingQueue = true
            val targetPlaylistId = video.playlistId?.ifBlank { null }
                ?: activePlaylistId?.ifBlank { null }
                ?: "RD${video.id}"
            activePlaylistId = targetPlaylistId
            val nextData = repository.getWatchNext(video.id, targetPlaylistId)
            if (nextData != null) {
                nextQueueData = nextData
                if (nextData.currentVideo.channelAvatarUrl.isNotBlank()) {
                    activeAvatarUrl = nextData.currentVideo.channelAvatarUrl
                }
                mediaService?.updateMediaInfo(
                    title = nextData.currentVideo.title.ifBlank { video.title },
                    channel = nextData.currentVideo.channelTitle.ifBlank { video.channelTitle },
                    durationMs = (currentDurationSec * 1000).toLong(),
                    playing = playerState.isPlaying,
                    thumbnailUrl = nextData.currentVideo.thumbnailUrl.ifBlank { video.thumbnailUrl },
                    currentPositionMs = (currentPositionSec * 1000).toLong()
                )
                recommendedVideos = nextData.recommendations
                recContinuationToken = nextData.recommendationsContinuationToken
                canLoadMoreRecs = true

                if (!fromInternal) {
                    showMixSheet = (video.isPlaylist || !video.playlistId.isNullOrBlank()) && nextData.mixPlaylist.isNotEmpty()
                    mixPlaylist = nextData.mixPlaylist
                    currentMixIndex = if (nextData.mixPlaylist.isNotEmpty()) {
                        val match = nextData.mixPlaylist.indexOfFirst { it.id == video.id }
                        if (match != -1) match else nextData.currentIndex
                    } else 0
                    mixTitle = nextData.playlistTitle.ifBlank { if (video.isPlaylist) video.title else "Mix" }
                } else {
                    if (currentMixIndex !in mixPlaylist.indices || mixPlaylist[currentMixIndex].id != video.id) {
                        val idx = mixPlaylist.indexOfFirst { it.id == video.id }
                        if (idx != -1) {
                            currentMixIndex = idx
                        }
                    }
                    if (mixPlaylist.isNotEmpty() && currentMixIndex >= mixPlaylist.size - 5) {
                        val existingIds = mixPlaylist.map { it.id }.toSet()
                        val freshItems = nextData.mixPlaylist.filter { it.id !in existingIds }
                        if (freshItems.isNotEmpty()) {
                            mixPlaylist = mixPlaylist + freshItems
                        }
                    }
                }

                if (!nextData.commentsContinuationToken.isNullOrBlank()) {
                    launch {
                        isLoadingComments = true
                        try {
                            val cResult = repository.getComments(nextData.commentsContinuationToken)
                            commentsList = cResult.comments
                            commentsTotalCountText = cResult.totalCountText.ifBlank { nextData.commentsCountText }
                            commentsContinuationToken = cResult.continuationToken
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isLoadingComments = false
                        }
                    }
                }
            } else if (fromInternal) {
                if (currentMixIndex !in mixPlaylist.indices || mixPlaylist[currentMixIndex].id != video.id) {
                    val idx = mixPlaylist.indexOfFirst { it.id == video.id }
                    if (idx != -1) {
                        currentMixIndex = idx
                    }
                }
            }
            isLoadingQueue = false
        }
    }

    val exitFullscreenToPortrait = {
        val activity = context.findActivity()
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity.window.decorView.postDelayed({
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }, 600)
        }
        isFullscreen = false
    }

    BackHandler(enabled = !isMinimized) {
        when {
            showMixSheet -> showMixSheet = false
            showDescriptionSheet -> showDescriptionSheet = false
            showCommentsSheet -> showCommentsSheet = false
            showDownloadSheet -> showDownloadSheet = false
            showSettingsSheet -> showSettingsSheet = false
            isFullscreen -> exitFullscreenToPortrait()
            else -> onBackClick()
        }
    }

    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().coerceAtLeast(1f) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drag_minimize_offset"
    )

    val haptic = LocalHapticFeedback.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    LaunchedEffect(dragOffsetY) {
        val threshold = if (isFullscreen) 80f else 180f
        if (dragOffsetY > threshold && !hasTriggeredHaptic) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasTriggeredHaptic = true
        } else if (dragOffsetY == 0f) {
            hasTriggeredHaptic = false
        }
    }

    val fullSheetProgress = remember { Animatable(if (isMinimized) 0f else 1f) }
    val isSheetRendered by remember {
        derivedStateOf { !isMinimized || fullSheetProgress.value > 0.01f }
    }

    LaunchedEffect(isMinimized) {
        if (!isMinimized) {
            dragOffsetY = 0f
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
            fullSheetProgress.animateTo(
                targetValue = 1f,
                animationSpec = IosSpringSpecs.Gentle
            )
        } else {
            showMixSheet = false
            showDescriptionSheet = false
            showCommentsSheet = false
            showSettingsSheet = false
            showDownloadSheet = false
            // Handoff dragOffsetY directly into fullSheetProgress to prevent double-offset jump
            if (dragOffsetY > 0f && screenHeightPx > 0f) {
                val handoffProgress = (1f - (dragOffsetY / screenHeightPx)).coerceIn(0f, 1f)
                dragOffsetY = 0f
                fullSheetProgress.snapTo(handoffProgress)
            }
            fullSheetProgress.animateTo(
                targetValue = 0f,
                animationSpec = IosSpringSpecs.Gentle
            )
        }
    }

    val lazyListState = rememberLazyListState()

    val dragModifier = if (!isMinimized) {
        Modifier.pointerInput(isFullscreen) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (dragAmount > 0f || dragOffsetY > 0f) {
                        change.consume()
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        if (dragOffsetY > 15f) {
                            showMixSheet = false
                            showDescriptionSheet = false
                            showCommentsSheet = false
                            showSettingsSheet = false
                        }
                    }
                },
                onDragEnd = {
                    if (isFullscreen) {
                        if (dragOffsetY > 80f) {
                            dragOffsetY = 0f
                            exitFullscreenToPortrait()
                        } else {
                            dragOffsetY = 0f
                        }
                    } else {
                        if (dragOffsetY > 180f) {
                            showMixSheet = false
                            showDescriptionSheet = false
                            showCommentsSheet = false
                            showSettingsSheet = false
                            onMinimize()
                        } else {
                            dragOffsetY = 0f
                        }
                    }
                },
                onDragCancel = {
                    dragOffsetY = 0f
                }
            )
        }
    } else Modifier

    LaunchedEffect(showCommentsSheet) {
        if (showCommentsSheet && commentsList.isEmpty() && !isLoadingComments) {
            val token = commentsContinuationToken ?: nextQueueData?.commentsContinuationToken
            if (!token.isNullOrBlank()) {
                scope.launch {
                    isLoadingComments = true
                    try {
                        val cResult = repository.getComments(token)
                        commentsList = cResult.comments
                        if (cResult.totalCountText.isNotBlank()) {
                            commentsTotalCountText = cResult.totalCountText
                        } else if (commentsTotalCountText.isBlank()) {
                            commentsTotalCountText = nextQueueData?.commentsCountText ?: ""
                        }
                        commentsContinuationToken = cResult.continuationToken
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoadingComments = false
                    }
                }
            }
        }
    }

    val loadMoreRecommendations: () -> Unit = {
        if (!isLoadingMoreRecs && !isLoadingQueue && canLoadMoreRecs) {
            scope.launch {
                isLoadingMoreRecs = true
                try {
                    val result = repository.getMoreRecommendations(
                        video = currentVideo,
                        continuation = recContinuationToken
                    )
                    if (result.videos.isNotEmpty()) {
                        val existingIds = (recommendedVideos + mixPlaylist).map { it.id }.toSet()
                        val freshVideos = result.videos.filterNot { it.id in existingIds }
                        if (freshVideos.isNotEmpty()) {
                            recommendedVideos = (recommendedVideos + freshVideos).take(80)
                        }
                    } else if (result.continuationToken == null) {
                        canLoadMoreRecs = false
                    }
                    recContinuationToken = result.continuationToken
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMoreRecs = false
                }
            }
        }
    }

    LaunchedEffect(lazyListState, recContinuationToken, isLoadingMoreRecs, canLoadMoreRecs) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total to last
        }
            .filter { (total, last) -> total > 0 && last >= total - 3 }
            .collect {
                if (!isLoadingMoreRecs && !isLoadingQueue && canLoadMoreRecs && recommendedVideos.isNotEmpty()) {
                    loadMoreRecommendations()
                }
            }
    }

    LaunchedEffect(recContinuationToken, isLoadingMoreRecs) {
        if (!isLoadingMoreRecs && !isLoadingQueue && canLoadMoreRecs && recContinuationToken != null) {
            val layoutInfo = lazyListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && last >= total - 3) {
                loadMoreRecommendations()
            }
        }
    }

    val effectiveDragOffsetProvider = { if (isMinimized) 0f else animatedDragOffset }

    Box(
        modifier = if (!isSheetRendered) {
            modifier
        } else {
            modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = fullSheetProgress.value
                    val drag = effectiveDragOffsetProvider()
                    val dragProg = (drag / 220f).coerceIn(0f, 1f)
                    val scrimAlpha = if (isFullscreen) {
                        (1f - (drag / 120f)).coerceIn(0f, 0.95f)
                    } else {
                        (progress * (1f - dragProg) * 0.78f).coerceIn(0f, 0.78f)
                    }
                    if (scrimAlpha > 0f) {
                        drawRect(ObsidianDark.copy(alpha = scrimAlpha))
                    }
                }
        }
    ) {
        PlayerAmbientBackdrop(
            isSheetRendered = isSheetRendered,
            isFullscreen = isFullscreen,
            thumbnailUrl = video.thumbnailUrl,
            sheetProgressProvider = { fullSheetProgress.value },
            dragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx
        )

        PlayerVideoViewportBox(
            video = video,
            isSheetRendered = isSheetRendered,
            isFullscreen = isFullscreen,
            sheetProgressProvider = { fullSheetProgress.value },
            dragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            dragModifier = dragModifier,
            animatedZoomScale = animatedZoomScale,
            controller = controller,
            bridge = bridge,
            playerState = playerState,
            isAudioOnly = isAudioOnly,
            isControlsVisible = isControlsVisible,
            isCaptionsEnabled = isCaptionsEnabled,
            hasCaptions = hasCaptions,
            isQualityMenuExpanded = isQualityMenuExpanded,
            isZoomToFill = isZoomToFill,
            doubleTapSeekSeconds = doubleTapSeekSeconds,
            seekAnimationSide = seekAnimationSide,
            seekAccumulatedSeconds = seekAccumulatedSeconds,
            showSponsorPill = showSponsorPill,
            lastSkippedSeconds = lastSkippedSeconds,
            lastSkippedCategory = lastSkippedCategory,
            isManualSkipMode = !(repository.preferences?.sponsorBlockAutoSkip?.value ?: true),
            liveEndscreenVideos = liveEndscreenVideos,
            nextQueueData = nextQueueData,
            recommendedVideos = recommendedVideos,
            isEndscreenDismissed = isEndscreenDismissed,
            isScrubbing = isScrubbing,
            scrubFraction = scrubFraction,
            scrubTouchX = scrubTouchX,
            videoBoxWidthPx = videoBoxWidthPx,
            storyboardData = storyboardData,
            playerNoticeText = playerNoticeText,
            currentTimeSecProvider = { currentPositionSec },
            durationSecProvider = { currentDurationSec },
            bufferedFractionProvider = { currentBufferedFraction },
            onViewportMeasured = { w, h ->
                videoBoxWidthPx = w
                videoBoxHeightPx = h
            },
            onDisableAudioOnly = { applyAudioOnlyState(false) },
            onSingleTapViewport = {
                when {
                    showMixSheet -> showMixSheet = false
                    showDescriptionSheet -> showDescriptionSheet = false
                    showCommentsSheet -> showCommentsSheet = false
                    showSettingsSheet -> showSettingsSheet = false
                    isQualityMenuExpanded -> isQualityMenuExpanded = false
                    else -> isControlsVisible = !isControlsVisible
                }
            },
            onDoubleTapSeek = { isRewind ->
                val delta = doubleTapSeekSeconds
                if (isRewind) {
                    seekAccumulatedSeconds = if (seekAnimationSide == SeekFeedbackDirection.REWIND) {
                        seekAccumulatedSeconds + delta
                    } else {
                        delta
                    }
                    seekAnimationSide = SeekFeedbackDirection.REWIND
                    val newTime = (currentPositionSec - delta.toFloat()).coerceAtLeast(0f)
                    controller.seekTo(newTime)
                    currentPositionSec = newTime
                    mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
                } else {
                    seekAccumulatedSeconds = if (seekAnimationSide == SeekFeedbackDirection.FORWARD) {
                        seekAccumulatedSeconds + delta
                    } else {
                        delta
                    }
                    seekAnimationSide = SeekFeedbackDirection.FORWARD
                    val newTime = (currentPositionSec + delta.toFloat()).coerceAtMost(currentDurationSec)
                    controller.seekTo(newTime)
                    currentPositionSec = newTime
                    mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
                }
            },
            onManualSponsorSkip = {
                controller.seekTo(lastTargetSkipEndSec)
                currentPositionSec = lastTargetSkipEndSec
                mediaService?.updatePlaybackState(playerState.isPlaying, (lastTargetSkipEndSec * 1000).toLong())
                if (lastSkippedCategory == "outro") {
                    val activeVid = currentVideo
                    scope.launch {
                        repository.resetWatchPosition(activeVid.id)
                    }
                }
                showSponsorPill = false
            },
            onUndoSponsorSkip = {
                undoneSegmentUuids.addAll(skippedSegmentUuids)
                controller.seekTo(lastSkippedFromSec)
                currentPositionSec = lastSkippedFromSec
                mediaService?.updatePlaybackState(playerState.isPlaying, (lastSkippedFromSec * 1000).toLong())
                showSponsorPill = false
            },
            onDismissEndscreen = { isEndscreenDismissed = true },
            onEndscreenVideoClick = { targetVid ->
                isInternalNavigation = false
                currentOnPlayNextVideo(targetVid)
            },
            onMinimizeOrExitFullscreen = {
                if (isFullscreen) exitFullscreenToPortrait() else onMinimize()
            },
            onToggleCaptions = {
                if (!hasCaptions) {
                    showNotice(context.getString(R.string.player_no_captions))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    isCaptionsEnabled = !isCaptionsEnabled
                    controller.setCaptionsEnabled(isCaptionsEnabled)
                    showNotice(
                        if (isCaptionsEnabled) context.getString(R.string.player_captions_on)
                        else context.getString(R.string.player_captions_off)
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onToggleQualityMenu = { expanded -> isQualityMenuExpanded = expanded },
            onSelectQuality = { opt ->
                if (isAudioOnly && opt.code != "tiny") {
                    isAudioOnly = false
                    controller.setAudioOnly(false)
                }
                previousQualityBeforeAudioOnly = opt.code
                playerState = playerState.copy(currentQuality = opt.code)
                controller.setPlaybackQuality(opt.code)
                isQualityMenuExpanded = false
                showNotice(context.getString(R.string.player_quality_format, opt.label))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onToggleZoom = {
                isZoomToFill = !isZoomToFill
                showNotice(if (isZoomToFill) "Di-zoom untuk memenuhi" else "Asli")
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onCycleSpeed = {
                val nextSpeed = when (playerState.playbackSpeed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    2.0f -> 0.5f
                    else -> 1.0f
                }
                playerState = playerState.copy(playbackSpeed = nextSpeed)
                controller.setPlaybackRate(nextSpeed)
            },
            onOpenSettings = {
                isControlsVisible = false
                isQualityMenuExpanded = false
                showCommentsSheet = false
                showDescriptionSheet = false
                showMixSheet = false
                showSettingsSheet = true
            },
            onSkipPrevious = { playPrevious() },
            onRewind10s = {
                val delta = doubleTapSeekSeconds
                seekAccumulatedSeconds = if (seekAnimationSide == SeekFeedbackDirection.REWIND) {
                    seekAccumulatedSeconds + delta
                } else {
                    delta
                }
                seekAnimationSide = SeekFeedbackDirection.REWIND
                val newTime = (currentPositionSec - delta.toFloat()).coerceAtLeast(0f)
                controller.seekTo(newTime)
                currentPositionSec = newTime
                mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
            },
            onTogglePlayPause = {
                if (playerState.isPlaying) controller.pause() else controller.play()
            },
            onForward10s = {
                val delta = doubleTapSeekSeconds
                seekAccumulatedSeconds = if (seekAnimationSide == SeekFeedbackDirection.FORWARD) {
                    seekAccumulatedSeconds + delta
                } else {
                    delta
                }
                seekAnimationSide = SeekFeedbackDirection.FORWARD
                val newTime = (currentPositionSec + delta.toFloat()).coerceAtMost(currentDurationSec)
                controller.seekTo(newTime)
                currentPositionSec = newTime
                mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
            },
            onSkipNext = { playNext() },
            onToggleFullscreen = {
                if (isFullscreen) {
                    exitFullscreenToPortrait()
                } else {
                    val activity = context.findActivity()
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    isFullscreen = true
                }
            },
            onSeek = { fraction ->
                val targetSec = fraction * currentDurationSec
                controller.seekTo(targetSec)
                currentPositionSec = targetSec
                mediaService?.updatePlaybackState(playerState.isPlaying, (targetSec * 1000).toLong())
                if (wasPlayingBeforeScrub) {
                    controller.play()
                }
            },
            onScrubbing = { scrubbing, fraction, touchX ->
                if (scrubbing && !isScrubbing) {
                    wasPlayingBeforeScrub = playerState.isPlaying
                    if (playerState.isPlaying) {
                        controller.pause()
                    }
                }
                isScrubbing = scrubbing
                scrubFraction = fraction
                scrubTouchX = touchX
                if (scrubbing) {
                    isControlsVisible = true
                    val now = System.currentTimeMillis()
                    if (now - lastScrubSeekTimeMs >= 50L) {
                        lastScrubSeekTimeMs = now
                        val targetSec = fraction * currentDurationSec
                        controller.seekTo(targetSec)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        )

        PlayerPortraitSheetContent(
            isSheetRendered = isSheetRendered,
            isFullscreen = isFullscreen,
            sheetProgressProvider = { fullSheetProgress.value },
            dragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            dragModifier = dragModifier,
            lazyListState = lazyListState,
            video = video,
            nextQueueData = nextQueueData,
            activeAvatarUrl = activeAvatarUrl,
            commentsList = commentsList,
            commentsTotalCountText = commentsTotalCountText,
            repository = repository,
            isLoadingQueue = isLoadingQueue,
            recommendedVideos = recommendedVideos,
            isLoadingMoreRecs = isLoadingMoreRecs,
            mixPlaylist = mixPlaylist,
            currentMixIndex = currentMixIndex,
            mixTitle = mixTitle,
            showMixSheet = showMixSheet,
            showCommentsSheet = showCommentsSheet,
            showDescriptionSheet = showDescriptionSheet,
            showSettingsSheet = showSettingsSheet,
            onOpenDescription = {
                showCommentsSheet = false
                showMixSheet = false
                showSettingsSheet = false
                showDescriptionSheet = true
            },
            onOpenComments = {
                showDescriptionSheet = false
                showMixSheet = false
                showSettingsSheet = false
                showCommentsSheet = true
            },
            onOpenDownload = {
                showDownloadSheet = true
            },
            onOpenMixSheet = {
                showCommentsSheet = false
                showDescriptionSheet = false
                showSettingsSheet = false
                showMixSheet = true
            },
            onChannelSelected = { chId, chTitle ->
                onMinimize()
                onChannelClick?.invoke(chId, chTitle)
            },
            onRecommendationClick = { item ->
                isInternalNavigation = false
                currentOnPlayNextVideo(item)
            }
        )

        AnimatedVisibility(
            visible = isMinimized,
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
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                currentTimeSecProvider = { currentPositionSec },
                durationSecProvider = { currentDurationSec },
                onExpand = onExpand,
                onTogglePlayPause = {
                    if (playerState.isPlaying) controller.pause() else controller.play()
                },
                onClose = onClose
            )
        }

        PlayerSheetsHost(
            isMinimized = isMinimized,
            isFullscreen = isFullscreen,
            dragOffsetY = dragOffsetY,
            sheetProgressProvider = { fullSheetProgress.value },
            animatedDragOffsetProvider = effectiveDragOffsetProvider,
            screenHeightPx = screenHeightPx,
            video = video,
            repository = repository,
            nextQueueData = nextQueueData,
            activeAvatarUrl = activeAvatarUrl,
            playerState = playerState,
            doubleTapSeekSeconds = doubleTapSeekSeconds,
            isLoopEnabled = isLoopEnabled,
            isAutoplayNext = isAutoplayNext,
            isAudioOnly = isAudioOnly,
            sleepTimerRemainingSec = sleepTimerRemainingSec,
            sleepTimerOption = sleepTimerOption,
            mixTitle = mixTitle,
            mixPlaylist = mixPlaylist,
            currentMixIndex = currentMixIndex,
            commentsList = commentsList,
            commentsTotalCountText = commentsTotalCountText,
            isLoadingComments = isLoadingComments,
            hasMoreComments = !commentsContinuationToken.isNullOrBlank(),
            showMixSheet = showMixSheet,
            showSettingsSheet = showSettingsSheet,
            showDescriptionSheet = showDescriptionSheet,
            showCommentsSheet = showCommentsSheet,
            showDownloadSheet = showDownloadSheet,
            onDismissMixSheet = { showMixSheet = false },
            onSelectMixIndex = { index -> playFromMix(index) },
            onDismissSettingsSheet = { showSettingsSheet = false },
            onSpeedSelected = { speed ->
                playerState = playerState.copy(playbackSpeed = speed)
                controller.setPlaybackRate(speed)
            },
            onQualitySelected = { quality ->
                if (isAudioOnly && quality != "tiny") {
                    isAudioOnly = false
                    controller.setAudioOnly(false)
                }
                previousQualityBeforeAudioOnly = quality
                playerState = playerState.copy(currentQuality = quality)
                controller.setPlaybackQuality(quality)
            },
            onDoubleTapSeekSelected = { seconds ->
                fallbackDoubleTapSeek = seconds
                repository.preferences?.setDoubleTapSeekSeconds(seconds)
            },
            onSleepTimerSelected = { option ->
                sleepTimerOption = option
                sleepTimerRemainingSec = if (option.seconds != null && option.seconds > 0) option.seconds else null
            },
            onLoopToggle = { loop ->
                isLoopEnabled = loop
                controller.setLoop(loop)
            },
            onAutoplayToggle = { isAutoplayNext = it },
            onAudioOnlyToggle = { enabled -> applyAudioOnlyState(enabled) },
            onDismissDescriptionSheet = { showDescriptionSheet = false },
            onChannelSelected = { chId, chTitle ->
                showDescriptionSheet = false
                showCommentsSheet = false
                onMinimize()
                onChannelClick?.invoke(chId, chTitle)
            },
            onTimestampClick = { targetSec ->
                val clamped = if (currentDurationSec > 0f) {
                    targetSec.coerceIn(0f, currentDurationSec)
                } else {
                    targetSec.coerceAtLeast(0f)
                }
                controller.seekTo(clamped)
                currentPositionSec = clamped
                mediaService?.updatePlaybackState(playerState.isPlaying, (clamped * 1000).toLong())
                if (!playerState.isPlaying) {
                    controller.play()
                }
            },
            onVideoLinkClick = { targetVideo ->
                showDescriptionSheet = false
                showCommentsSheet = false
                if (targetVideo.id != video.id) {
                    val matchedRec = (recommendedVideos + mixPlaylist).firstOrNull { it.id == targetVideo.id }
                    val targetVideoItem = matchedRec ?: targetVideo
                    isInternalNavigation = false
                    currentOnPlayNextVideo(targetVideoItem)
                }
            },
            onLoadMoreComments = {
                val token = commentsContinuationToken
                if (token != null) {
                    isLoadingComments = true
                    scope.launch {
                        try {
                            val res = repository.getComments(token)
                            if (res.comments.isNotEmpty()) {
                                val existingIds = commentsList.map { it.id }.toSet()
                                val fresh = res.comments.filterNot { it.id in existingIds }
                                commentsList = commentsList + fresh
                            }
                            commentsContinuationToken = res.continuationToken
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isLoadingComments = false
                        }
                    }
                }
            },
            onDismissCommentsSheet = { showCommentsSheet = false },
            onDismissDownloadSheet = { showDownloadSheet = false }
        )
    }
}

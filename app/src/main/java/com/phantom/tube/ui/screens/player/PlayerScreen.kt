package com.phantom.tube.ui.screens.player

import android.app.Activity
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.ui.components.getLocalizedQualityLabel
import com.phantom.tube.core.util.findActivity
import com.phantom.tube.player.service.PhantomMediaService
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.ui.screens.player.components.FloatingMixBar
import com.phantom.tube.ui.screens.player.components.PlayerCommentsSheet
import com.phantom.tube.ui.screens.player.components.PlayerDescriptionSheet
import com.phantom.tube.ui.screens.player.components.PlayerMixSheet
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.phantom.tube.player.PlayerState
import com.phantom.tube.player.StoryboardData
import com.phantom.tube.player.StoryboardHelper
import com.phantom.tube.ui.components.PhantomMiniPlayer
import com.phantom.tube.ui.components.iosBounceClick
import com.phantom.tube.ui.components.IosSpringSpecs
import com.phantom.tube.ui.screens.player.components.ScrubPreviewCard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import com.phantom.tube.core.theme.phantomSurface
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.SponsorSegment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import com.phantom.tube.player.PhantomGhostSurface
import com.phantom.tube.ui.screens.player.components.PlayerEndscreenOverlay
import com.phantom.tube.player.PhantomPlayerBridge
import com.phantom.tube.player.PhantomPlayerController
import com.phantom.tube.ui.components.ALL_STANDARD_QUALITIES
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomScrubber
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.PlayerSettingsSheet
import com.phantom.tube.ui.components.SleepTimerOption
import com.phantom.tube.ui.components.SponsorSkipPill
import com.phantom.tube.ui.components.VideoQualityOption
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.data.model.VideoComment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SeekFeedbackDirection { FORWARD, REWIND }

private fun getShortQualityBadge(code: String): String {
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

    val centerControlsScale by animateFloatAsState(
        targetValue = if (isControlsVisible) 1.0f else 0.88f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "centerControlsScale"
    )

    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Sync fullscreen state with physical device orientation
    LaunchedEffect(isDeviceLandscape) {
        if (isDeviceLandscape && !isFullscreen) {
            isFullscreen = true
        } else if (!isDeviceLandscape && isFullscreen) {
            val activity = context.findActivity()
            if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            isFullscreen = false
        }
    }

    // Hide status bar & navigation bar in landscape / fullscreen (Immersive Sticky Mode)
    DisposableEffect(isFullscreen, isMinimized) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
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

    // Double-Tap Seek Visual Feedback State
    var seekAnimationSide by remember { mutableStateOf<SeekFeedbackDirection?>(null) }
    var seekAccumulatedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekAnimationSide, seekAccumulatedSeconds) {
        if (seekAnimationSide != null) {
            delay(750L)
            seekAnimationSide = null
            seekAccumulatedSeconds = 0
        }
    }

    // SponsorBlock state
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

    // YouTube Mix Playlist & Session (Preserving all songs in the Mix)
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
    var mixTitle by remember { mutableStateOf("") }
    var showMixSheet by remember { mutableStateOf(false) }
    var isInternalNavigation by remember { mutableStateOf(false) }
    var isLoadingQueue by remember { mutableStateOf(false) }

    // Settings States (Speed, Double Tap Seek, Sleep Timer, Loop, Autoplay, Audio-Only)
    var showSettingsSheet by remember { mutableStateOf(false) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(10) }
    var isLoopEnabled by remember { mutableStateOf(false) }
    var isAutoplayNext by remember { mutableStateOf(true) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var sleepTimerOption by remember { mutableStateOf(SleepTimerOption.OFF) }
    var sleepTimerRemainingSec by remember { mutableStateOf<Int?>(null) }
    var lastRecordedPositionSec by remember { mutableFloatStateOf(0f) }

    var mediaService by remember { mutableStateOf<PhantomMediaService?>(null) }

    val currentVideo by rememberUpdatedState(video)
    val currentOnPlayNextVideo by rememberUpdatedState(onPlayNextVideo)
    val currentOnPlayPreviousVideo by rememberUpdatedState(onPlayPreviousVideo)

    val controller = remember { PhantomPlayerController(context) }

    // Cegah audio dobel: Pause pemutar online jika lagu offline mulai diputar
    val isOfflinePlaying by com.phantom.tube.player.offline.OfflineAudioPlayerManager.isPlaying.collectAsState()
    LaunchedEffect(isOfflinePlaying) {
        if (isOfflinePlaying) {
            controller.pause()
        }
    }

    // Hentikan musik offline saat video online mulai dimuat/dimainkan atau di-resume
    LaunchedEffect(video.id) {
        com.phantom.tube.player.offline.OfflineAudioPlayerManager.stop(context)
    }
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            com.phantom.tube.player.offline.OfflineAudioPlayerManager.stop(context)
        }
    }

    // Sleep Timer countdown effect
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
                // At the end of playlist, ask YouTube for continuation of the Mix!
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
        }
    }

    val playPrevious: () -> Boolean = {
        if (currentPositionSec > 3f) {
            // Standard media playback: rewinds current track if played past 3 seconds
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
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var lastScrubSeekTimeMs by remember { mutableLongStateOf(0L) }
    var isZoomToFill by remember { mutableStateOf(false) }
    var isCaptionsEnabled by remember { mutableStateOf(false) }
    var hasCaptions by remember { mutableStateOf(false) }
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    var playerNoticeText by remember { mutableStateOf<String?>(null) }
    var noticeJob by remember { mutableStateOf<Job?>(null) }
    var videoBoxHeightPx by remember { mutableFloatStateOf(0f) }

    fun showNotice(text: String) {
        noticeJob?.cancel()
        playerNoticeText = text
        noticeJob = scope.launch {
            delay(1800)
            playerNoticeText = null
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
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
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
                // 1 = PLAYING, 2 = PAUSED, 3 = BUFFERING, 0 = ENDED
                when (state) {
                    1 -> {
                        playerState = playerState.copy(isPlaying = true, isBuffering = false, isEnded = false, errorCode = null)
                        mediaService?.updatePlaybackState(true, (currentPositionSec * 1000).toLong())
                        if (isCaptionsEnabled) {
                            controller.setCaptionsEnabled(true)
                        }
                        if (appliedDefaultsVideoId != currentVideo.id) {
                            appliedDefaultsVideoId = currentVideo.id
                            val defSpeed = repository.preferences?.defaultSpeed?.value ?: 1.0f
                            if (defSpeed != 1.0f) {
                                playerState = playerState.copy(playbackSpeed = defSpeed)
                                controller.setPlaybackRate(defSpeed)
                            }
                            val defQuality = repository.preferences?.defaultQuality?.value ?: "auto"
                            if (defQuality != "auto") {
                                playerState = playerState.copy(currentQuality = defQuality)
                                controller.setPlaybackQuality(defQuality)
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
                // Guard against stale time ticks from previous video during transition
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

                // Synchronize Media Notification position immediately on start and periodically
                if (!hasSyncedMediaSessionInitialPosition && current > 0.5f) {
                    hasSyncedMediaSessionInitialPosition = true
                    lastSyncedMediaSessionSec = current
                    mediaService?.updatePlaybackState(playerState.isPlaying, (current * 1000).toLong())
                } else if (kotlin.math.abs(current - lastSyncedMediaSessionSec) >= 5f) {
                    lastSyncedMediaSessionSec = current
                    mediaService?.updatePlaybackState(playerState.isPlaying, (current * 1000).toLong())
                }

                // Throttled watch history recording (every 5 seconds instead of 4 times/sec)
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

                // Check SponsorBlock segments (UUID tracking, strict intro/outro validation, and outro watch reset)
                val prefs = repository.preferences
                val sbEnabled = prefs?.sponsorBlockEnabled?.value ?: true
                if (sbEnabled && duration > 0f && current < duration) {
                    val skipSponsor = prefs?.skipSponsor?.value ?: true
                    val skipSelfPromo = prefs?.skipSelfPromo?.value ?: true
                    val skipInteraction = prefs?.skipInteraction?.value ?: true
                    val skipIntro = prefs?.skipIntro?.value ?: true
                    val skipOutro = prefs?.skipOutro?.value ?: true
                    val autoSkip = prefs?.sponsorBlockAutoSkip?.value ?: true

                    sponsorSegments.forEach { seg ->
                        val isCatAllowed = when (seg.category) {
                            "sponsor" -> skipSponsor
                            "selfpromo" -> skipSelfPromo
                            "interaction" -> skipInteraction
                            "intro" -> skipIntro
                            "outro" -> skipOutro
                            else -> true
                        }
                        if (isCatAllowed) {
                            val uuid = if (seg.uuid.isNotBlank()) seg.uuid else "${seg.category}_${seg.startSecond}_${seg.endSecond}"
                            if (uuid !in skippedSegmentUuids && uuid !in undoneSegmentUuids) {
                                // 1. Strict segment length & bounds verification
                                val isValidLength = seg.endSecond > (seg.startSecond + 0.5f) && seg.startSecond >= 0f
                                
                                // 2. Outro safety check:
                                // Outro cannot start in the beginning or middle of the video.
                                // Must be in the last 35% of video, video >= 20s, start >= 15s, and current playback already >= 60% of video
                                val isOutroValid = if (seg.category == "outro") {
                                    duration >= 20f &&
                                    seg.startSecond >= (duration * 0.65f) &&
                                    seg.startSecond >= 15f &&
                                    current >= (duration * 0.60f)
                                } else true

                                // 3. Intro safety check:
                                // Intro cannot start late in the video (must be within first 40% of duration and first 5 minutes)
                                val isIntroValid = if (seg.category == "intro") {
                                    seg.startSecond < (duration * 0.40f) &&
                                    seg.startSecond < 300f &&
                                    seg.endSecond <= (duration * 0.55f)
                                } else true

                                // 4. Prevent skipping excessive proportion of video
                                val isNotEntireVideo = (seg.endSecond - seg.startSecond) < (duration * 0.85f)

                                if (isValidLength && isOutroValid && isIntroValid && isNotEntireVideo &&
                                    current >= seg.startSecond && current < (seg.endSecond - 0.5f)) {
                                    skippedSegmentUuids.add(uuid)
                                    lastSkippedFromSec = current
                                    lastTargetSkipEndSec = seg.endSecond
                                    lastSkippedSeconds = (seg.endSecond - seg.startSecond).toInt().coerceAtLeast(1)
                                    lastSkippedCategory = seg.category
                                    if (autoSkip) {
                                        controller.seekTo(seg.endSecond)
                                        currentPositionSec = seg.endSecond
                                        mediaService?.updatePlaybackState(playerState.isPlaying, (seg.endSecond * 1000).toLong())
                                    }
                                    showSponsorPill = true
                                    scope.launch {
                                        delay(4000)
                                        showSponsorPill = false
                                    }

                                    // If OUTRO is skipped, reset watch history position so reopening video starts at 0:00!
                                    if (seg.category == "outro") {
                                        val activeVid = currentVideo
                                        scope.launch {
                                            repository.resetWatchPosition(activeVid.id)
                                        }
                                    }
                                }
                            }
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

    // Maintain persistent PhantomMediaService connection for the entire playback session
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

    // Enforce Background Playback preference when screen turns off or app leaves foreground
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

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible, playerState.isPlaying, isQualityMenuExpanded) {
        if (isControlsVisible && playerState.isPlaying && !isQualityMenuExpanded) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Load new video into engine & immediately sync notification
    LaunchedEffect(video.id, mediaService) {
        playerState = PlayerState(videoId = video.id)
        appliedDefaultsVideoId = null
        sponsorSegments = emptyList()
        lastRecordedPositionSec = 0f
        storyboardData = null
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
                    // Internal navigation: preserve currentMixIndex and prevent duplicate ID jumps
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
            } else {
                if (fromInternal) {
                    if (currentMixIndex !in mixPlaylist.indices || mixPlaylist[currentMixIndex].id != video.id) {
                        val idx = mixPlaylist.indexOfFirst { it.id == video.id }
                        if (idx != -1) {
                            currentMixIndex = idx
                        }
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
        if (showMixSheet) {
            showMixSheet = false
        } else if (showDescriptionSheet) {
            showDescriptionSheet = false
        } else if (showCommentsSheet) {
            showCommentsSheet = false
        } else if (showDownloadSheet) {
            showDownloadSheet = false
        } else if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (isFullscreen) {
            exitFullscreenToPortrait()
        } else {
            onBackClick()
        }
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
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

    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val fullSheetProgress = remember { Animatable(0f) }

    LaunchedEffect(isMinimized) {
        if (!isMinimized) {
            fullSheetProgress.snapTo(0f)
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
            fullSheetProgress.animateTo(
                targetValue = 1f,
                animationSpec = IosSpringSpecs.Gentle
            )
        } else {
            fullSheetProgress.snapTo(0f)
            dragOffsetY = 0f
            showMixSheet = false
            showDescriptionSheet = false
            showCommentsSheet = false
            showSettingsSheet = false
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
                        // Tarik dari atas ke bawah di mode landscape / fullscreen untuk kembali ke portrait (seperti YouTube)
                        if (dragOffsetY > 80f) {
                            dragOffsetY = 0f
                            exitFullscreenToPortrait()
                        } else {
                            dragOffsetY = 0f
                        }
                    } else {
                        // Tarik ke bawah di mode portrait untuk memperkecil ke miniplayer
                        if (dragOffsetY > 200f) {
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
        if (showCommentsSheet) {
            if (commentsList.isEmpty() && !isLoadingComments) {
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
                            recommendedVideos = recommendedVideos + freshVideos
                        }
                    } else {
                        if (result.continuationToken == null) {
                            canLoadMoreRecs = false
                        }
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

    // Infinite scroll listener for recommendations list below the video
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

    val sheetOffsetY = if (isFullscreen) 0f else (1f - fullSheetProgress.value) * screenHeightPx
    val totalOffsetY = sheetOffsetY + animatedDragOffset
    val dragProgress = (animatedDragOffset / 220f).coerceIn(0f, 1f)
    val openCornerRadius = ((1f - fullSheetProgress.value) * 24f + dragProgress * 20f).dp.coerceAtLeast(0.dp)

    val backdropScrimAlpha = if (isFullscreen) {
        (1f - (animatedDragOffset / 120f)).coerceIn(0f, 0.95f)
    } else {
        (fullSheetProgress.value * (1f - dragProgress) * 0.75f).coerceIn(0f, 0.75f)
    }

    Box(
        modifier = if (isMinimized) {
            modifier
        } else {
            modifier
                .fillMaxSize()
                .drawBehind {
                    if (backdropScrimAlpha > 0f) {
                        drawRect(ObsidianDark.copy(alpha = backdropScrimAlpha))
                    }
                }
        }
    ) {
        // -1.5 STATIONARY STATUS BAR PROTECTION (Stays at phone top, fades during drag)
        if (!isMinimized && !isFullscreen) {
            val statusBarAlpha = (fullSheetProgress.value * (1f - dragProgress * 2f)).coerceIn(0f, 1f)
            if (statusBarAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .alpha(statusBarAlpha)
                        .background(SystemStatusBarColor)
                )
            }
        }

        // -1. SOLID SHEET BACKGROUND (Surfaces smoothly upwards under the player and content)
        if (!isMinimized && !isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        translationY = totalOffsetY
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        clip = true
                    }
                    .background(ObsidianDark)
            )
        }

        // 0. AMBIENT MODE CINEMATIC GLOW (Eye Comfort & Atmosphere)
        if (!isMinimized && !isFullscreen) {
            val ambientAlpha = (fullSheetProgress.value * (1f - dragProgress) * 0.90f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
                    .statusBarsPadding()
                    .graphicsLayer {
                        translationY = totalOffsetY
                        alpha = ambientAlpha
                    }
                    .align(Alignment.TopCenter)
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.22f
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

        // 1. THE SINGLE PERSISTENT VIDEO PLAYER BOX (Always at exact same tree slot)
        val videoBoxModifier = when {
            isMinimized -> Modifier
                .size(1.dp)
                .alpha(0.01f)
                .align(Alignment.TopStart)
            isFullscreen -> {
                val landscapeDragProgress = (animatedDragOffset / 100f).coerceIn(0f, 1f)
                val landscapeScale = (1f - (animatedDragOffset / 1000f)).coerceIn(0.88f, 1f)
                val landscapeCorners = (landscapeDragProgress * 20f).dp
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = landscapeScale
                        scaleY = landscapeScale
                        translationY = animatedDragOffset
                        shape = RoundedCornerShape(landscapeCorners)
                        clip = landscapeCorners > 0.dp
                        shadowElevation = landscapeDragProgress * 24f
                    }
            }
            else -> Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = (dragProgress * 10f).dp)
                .graphicsLayer {
                    translationY = totalOffsetY
                    shape = RoundedCornerShape(14.dp)
                    clip = true
                    shadowElevation = if (dragProgress > 0f) 16f else 4f
                }
                .aspectRatio(16f / 9f)
                .align(Alignment.TopCenter)
        }

        Box(
            modifier = videoBoxModifier
                .then(dragModifier)
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    videoBoxWidthPx = coords.size.width.toFloat()
                    videoBoxHeightPx = coords.size.height.toFloat()
                }
                .zIndex(if (!isMinimized) 10f else 0f)
        ) {
            // Layer 0: The Ghost Surface (backed by WebViewAssetLoader with Zoom to Fill)
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

            // Audio-Only Mode AMOLED Overlay
            if (isAudioOnly) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF07070A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
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
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Mode Audio Saja",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Layar dinonaktifkan untuk menghemat daya",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Layer 1: Transparent Gesture Touch Handler
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(doubleTapSeekSeconds) {
                        detectTapGestures(
                            onTap = {
                                if (showMixSheet) {
                                    showMixSheet = false
                                } else if (showDescriptionSheet) {
                                    showDescriptionSheet = false
                                } else if (showCommentsSheet) {
                                    showCommentsSheet = false
                                } else if (showSettingsSheet) {
                                    showSettingsSheet = false
                                } else if (isQualityMenuExpanded) {
                                    isQualityMenuExpanded = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                val delta = doubleTapSeekSeconds
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Exception) {}
                                if (offset.x < size.width / 2) {
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
                            }
                        )
                    }
            )

            // Layer 1.5: Double-Tap Seek Visual Ripple Indicator (iOS Spring Fluidity)
            AnimatedVisibility(
                visible = seekAnimationSide == SeekFeedbackDirection.REWIND,
                enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.80f, animationSpec = spring(dampingRatio = 0.68f, stiffness = 400f)),
                exit = fadeOut(tween(260)) + scaleOut(targetScale = 0.95f, animationSpec = tween(260)),
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
                            text = "-${seekAccumulatedSeconds} dtk",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = seekAnimationSide == SeekFeedbackDirection.FORWARD,
                enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.80f, animationSpec = spring(dampingRatio = 0.68f, stiffness = 400f)),
                exit = fadeOut(tween(260)) + scaleOut(targetScale = 0.95f, animationSpec = tween(260)),
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
                            text = "+${seekAccumulatedSeconds} dtk",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Layer 2: Buffering & Error Indicator
            if (playerState.isBuffering) {
                CircularProgressIndicator(
                    color = YouTubeRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            } else if (playerState.errorCode != null) {
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
                        text = playerState.errorCode ?: "Terjadi kesalahan pemutaran",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Layer 3: Floating Sponsor Skip Pill
            val isManualSkipMode = !(repository.preferences?.sponsorBlockAutoSkip?.value ?: true)
            SponsorSkipPill(
                visible = showSponsorPill,
                skippedSeconds = lastSkippedSeconds,
                category = lastSkippedCategory,
                isManualMode = isManualSkipMode,
                onSkip = {
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                onUndo = {
                    undoneSegmentUuids.addAll(skippedSegmentUuids)
                    controller.seekTo(lastSkippedFromSec)
                    currentPositionSec = lastSkippedFromSec
                    mediaService?.updatePlaybackState(playerState.isPlaying, (lastSkippedFromSec * 1000).toLong())
                    showSponsorPill = false
                }
            )

            // Layer 3.5: Native Endscreen Recommendations Overlay
            val isNearEnd = remember(currentPositionSec, currentDurationSec) {
                currentDurationSec > 25f && (currentDurationSec - currentPositionSec) in 0.5f..20f
            }
            val endscreenCards = remember(liveEndscreenVideos, nextQueueData, recommendedVideos) {
                if (liveEndscreenVideos.isNotEmpty()) {
                    liveEndscreenVideos
                } else {
                    nextQueueData?.effectiveEndscreens?.ifEmpty {
                        recommendedVideos.filter { it.id != video.id }.take(2)
                    } ?: recommendedVideos.filter { it.id != video.id }.take(2)
                }
            }
            PlayerEndscreenOverlay(
                visible = isNearEnd && !isEndscreenDismissed && !isControlsVisible && endscreenCards.isNotEmpty(),
                items = endscreenCards,
                isFullscreen = isFullscreen,
                onDismiss = { isEndscreenDismissed = true },
                onVideoClick = { targetVid ->
                    isInternalNavigation = false
                    currentOnPlayNextVideo(targetVid)
                },
                modifier = Modifier.matchParentSize()
            )

            // Layer 4: 100% Native Liquid Glass Controls Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(200, easing = FastOutLinearInEasing)),
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
                    // Top Bar Controls: Back/Minimize & Speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhantomIconButton(
                            icon = Icons.Default.ExpandMore,
                            contentDescription = "Perkecil Player",
                            size = 38.dp,
                            iconSize = 24.dp,
                            onClick = {
                                if (isFullscreen) {
                                    exitFullscreenToPortrait()
                                } else {
                                    onMinimize()
                                }
                            }
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Closed Captions / Subtitle Toggle Button
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
                                    .iosBounceClick(scaleDown = 0.90f) {
                                        if (!hasCaptions) {
                                            showNotice(context.getString(R.string.player_no_captions))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else {
                                            isCaptionsEnabled = !isCaptionsEnabled
                                            controller.setCaptionsEnabled(isCaptionsEnabled)
                                            showNotice(if (isCaptionsEnabled) context.getString(R.string.player_captions_on) else context.getString(R.string.player_captions_off))
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = if (isCaptionsEnabled) "Matikan Teks" else "Hidupkan Teks",
                                    tint = if (isCaptionsEnabled && hasCaptions) YouTubeRed else if (!hasCaptions) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Quick Video Quality Dropdown Button (pinggir tombol CC)
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
                                            isQualityMenuExpanded = !isQualityMenuExpanded
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
                                    onDismissRequest = { isQualityMenuExpanded = false },
                                    modifier = Modifier
                                        .background(Color(0xFF212121), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                ) {
                                    val qualityOptions = remember(playerState.availableQualities) {
                                        if (playerState.availableQualities.isNotEmpty()) {
                                            val list = mutableListOf(VideoQualityOption("auto", "Otomatis (Disarankan)"))
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
                                            onClick = {
                                                playerState = playerState.copy(currentQuality = opt.code)
                                                controller.setPlaybackQuality(opt.code)
                                                isQualityMenuExpanded = false
                                                showNotice(context.getString(R.string.player_quality_format, opt.label))
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Zoom to Fill / Aspect Ratio Toggle Button
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
                                    .iosBounceClick(scaleDown = 0.90f) {
                                        isZoomToFill = !isZoomToFill
                                        showNotice(if (isZoomToFill) "Di-zoom untuk memenuhi" else "Asli")
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isZoomToFill) Icons.Default.FitScreen else Icons.Default.CropFree,
                                    contentDescription = if (isZoomToFill) "Rasio Asli" else "Zoom Penuhi Layar",
                                    tint = if (isZoomToFill) Color.White else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Playback Speed Quick Button (cycles 1.0x -> 1.5x -> 2.0x -> 0.5x)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF272727))
                                    .iosBounceClick(scaleDown = 0.90f) {
                                        val nextSpeed = when (playerState.playbackSpeed) {
                                            1.0f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.5f
                                            else -> 1.0f
                                        }
                                        playerState = playerState.copy(playbackSpeed = nextSpeed)
                                        controller.setPlaybackRate(nextSpeed)
                                    }
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
                                onClick = {
                                    showCommentsSheet = false
                                    showDescriptionSheet = false
                                    showMixSheet = false
                                    showSettingsSheet = true
                                }
                            )
                        }
                    }

                    // Center Row: SkipPrevious, Replay10, Play/Pause, Forward10, SkipNext
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
                            onClick = { playPrevious() }
                        )

                        PhantomIconButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = stringResource(R.string.seek_rewind_10s),
                            size = 42.dp,
                            iconSize = 22.dp,
                            onClick = {
                                val newTime = (currentPositionSec - 10f).coerceAtLeast(0f)
                                controller.seekTo(newTime)
                                currentPositionSec = newTime
                                mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
                            }
                        )

                        PhantomIconButton(
                            icon = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (playerState.isPlaying) R.string.player_pause else R.string.player_play),
                            size = 64.dp,
                            iconSize = 36.dp,
                            onClick = {
                                if (playerState.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.play()
                                }
                            }
                        )

                        PhantomIconButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = stringResource(R.string.seek_forward_10s),
                            size = 42.dp,
                            iconSize = 22.dp,
                            onClick = {
                                val newTime = (currentPositionSec + 10f).coerceAtMost(currentDurationSec)
                                controller.seekTo(newTime)
                                currentPositionSec = newTime
                                mediaService?.updatePlaybackState(playerState.isPlaying, (newTime * 1000).toLong())
                            }
                        )

                        PhantomIconButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next_video),
                            size = 42.dp,
                            iconSize = 22.dp,
                            onClick = { playNext() }
                        )
                    }

                    // Bottom Row: Timestamps + Fullscreen above Scrubber, Scrubber at bottom edge
                    PlayerScrubberControls(
                        currentTimeSecProvider = { currentPositionSec },
                        durationSecProvider = { currentDurationSec },
                        bufferedFractionProvider = { currentBufferedFraction },
                        isFullscreen = isFullscreen,
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
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            // Layer 5: Persistent Idle Progress Line (Smooth crossfade when controls are hidden)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isControlsVisible,
                enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(180, easing = FastOutLinearInEasing)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                PlayerIdleProgressBar(
                    currentTimeSecProvider = { currentPositionSec },
                    durationSecProvider = { currentDurationSec },
                    bufferedFractionProvider = { currentBufferedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            }

            // Floating Video Scrubbing Storyboard Preview Card
            ScrubPreviewCard(
                visible = isScrubbing,
                targetSeconds = scrubFraction * currentDurationSec,
                durationSeconds = currentDurationSec,
                touchX = scrubTouchX,
                parentWidthPx = videoBoxWidthPx,
                storyboardData = storyboardData,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (isFullscreen) 56.dp else 42.dp)
                    .zIndex(30f)
            )

            // Floating YouTube-style Toast Notification ("Di-zoom untuk memenuhi", "Asli", "Teks diaktifkan", dsb.)
            AnimatedVisibility(
                visible = playerNoticeText != null,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.85f),
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
                        text = playerNoticeText ?: "",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. BELOW PLAYER CONTENT (Only shown in portrait full-player mode)
        if (!isMinimized && !isFullscreen) {
            val contentAlpha = (1f - (animatedDragOffset / 90f) * 1.5f).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = totalOffsetY
                        alpha = contentAlpha
                    }
                    .statusBarsPadding()
                    .zIndex(1f)
            ) {
                // Spacer reserving the height of the top 16:9 Video Player Box
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                // Dedicated subtle drag handle directly under video player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier)
                        .padding(top = 6.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 4.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.15f)
                                    )
                                ),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                // Video Details Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Video Title
                        val activeTitle = nextQueueData?.currentVideo?.title?.ifBlank { video.title } ?: video.title
                        Text(
                            text = activeTitle,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Sub-info row (Handle • Likes • Views • Date • ...selengkapnya)
                        val activeHandle = nextQueueData?.channelHandle?.let { if (it.startsWith("@")) it else "@$it" }
                            ?: ""
                        val activeViews = InnerTubeHelpers.normalizeViewCount(nextQueueData?.fullViewCountText?.ifBlank { video.viewCountText } ?: video.viewCountText)
                        val activeDate = InnerTubeHelpers.normalizePublishedTime(nextQueueData?.dateText?.ifBlank { video.publishedTimeText } ?: video.publishedTimeText)
                        val activeLikeCount = nextQueueData?.likeCountText ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCommentsSheet = false
                                    showMixSheet = false
                                    showSettingsSheet = false
                                    showDescriptionSheet = true
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                buildAnnotatedString {
                                    if (activeHandle.isNotBlank()) {
                                        append(activeHandle)
                                        append(" • ")
                                    }
                                    if (activeLikeCount.isNotBlank()) {
                                        append(activeLikeCount)
                                        append(" ")
                                        append(stringResource(R.string.player_stat_likes).lowercase())
                                        append(" • ")
                                    }
                                    if (activeViews.isNotBlank()) {
                                        append("$activeViews • ")
                                    }
                                    if (activeDate.isNotBlank()) {
                                        append("$activeDate • ")
                                    }
                                    withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                                        append(stringResource(R.string.player_more_details))
                                    }
                                },
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Channel Row (Avatar, Name, Subscriber count, Subscribe button)
                        val targetChannelId = nextQueueData?.currentVideo?.channelId?.ifBlank { video.channelId } ?: video.channelId
                        val targetChannelTitle = nextQueueData?.currentVideo?.channelTitle?.ifBlank { video.channelTitle } ?: video.channelTitle
                        val isSubscribed by repository.isSubscribed(targetChannelId).collectAsState(initial = false)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .iosBounceClick(scaleDown = 0.96f) {
                                        val tId = targetChannelId.ifBlank { targetChannelTitle }
                                        if (tId.isNotBlank()) {
                                            onMinimize()
                                            onChannelClick?.invoke(tId, targetChannelTitle)
                                        }
                                    }
                            ) {
                                // Channel Avatar
                                val currentAvatar = nextQueueData?.currentVideo?.channelAvatarUrl?.ifBlank { activeAvatarUrl } ?: activeAvatarUrl
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(YouTubeSurfaceLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentAvatar.isNotBlank()) {
                                        AsyncImage(
                                            model = currentAvatar,
                                            contentDescription = targetChannelTitle,
                                            modifier = Modifier.matchParentSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = targetChannelTitle.ifBlank { "Channel" },
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subsText = nextQueueData?.channelSubscriberCountText ?: ""
                                    if (subsText.isNotBlank()) {
                                        Text(
                                            text = subsText,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Subscribe Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSubscribed) Color(0xFF272727) else Color.White)
                                    .iosBounceClick(scaleDown = 0.90f) {
                                        val currentAvatar = nextQueueData?.currentVideo?.channelAvatarUrl?.ifBlank { activeAvatarUrl } ?: activeAvatarUrl
                                        scope.launch {
                                            if (isSubscribed) {
                                                repository.unsubscribe(targetChannelId)
                                            } else {
                                                repository.subscribe(
                                                    SubscriptionEntity(
                                                        channelId = targetChannelId,
                                                        channelTitle = targetChannelTitle,
                                                        channelHandle = nextQueueData?.channelHandle ?: "",
                                                        channelAvatarUrl = currentAvatar,
                                                        subscriberCountText = nextQueueData?.channelSubscriberCountText ?: ""
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = if (isSubscribed) stringResource(R.string.channel_subscribed) else stringResource(R.string.channel_subscribe),
                                    color = if (isSubscribed) Color.White else Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Action Buttons Row (Like/Dislike total display pill, Bagikan intent pill, Unduh pill)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Like & Dislike Pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF272727))
                                    .iosBounceClick(scaleDown = 0.94f) {}
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = stringResource(R.string.player_like),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeLikeCount.ifBlank { stringResource(R.string.player_like) },
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(Color(0x33FFFFFF))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.ThumbDown,
                                    contentDescription = stringResource(R.string.player_dislike),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Bagikan (Share) Pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF272727))
                                    .iosBounceClick(scaleDown = 0.92f) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, activeTitle)
                                            putExtra(Intent.EXTRA_TEXT, "$activeTitle\nhttps://youtu.be/${video.id}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, null).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(shareIntent)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.player_share),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.player_share),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Unduh (Download) Pill
                            val isDownloaded by repository.isDownloaded(video.id).collectAsState(initial = false)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF272727))
                                    .iosBounceClick(scaleDown = 0.92f) {
                                        showDownloadSheet = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                                    contentDescription = stringResource(if (isDownloaded) R.string.player_downloaded else R.string.player_download),
                                    tint = if (isDownloaded) Color(0xFF4CAF50) else TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(if (isDownloaded) R.string.player_downloaded else R.string.player_download),
                                    color = if (isDownloaded) Color(0xFF4CAF50) else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 5. Comments Preview Card (Screenshot 5)
                        val totalCommentsDisplay = commentsTotalCountText.ifBlank {
                            nextQueueData?.commentsCountText ?: ""
                        }
                        val topCommentSnippet = commentsList.firstOrNull()?.contentText
                            ?: nextQueueData?.topComment?.contentText
                            ?: stringResource(R.string.player_comments_hint)
                        val topCommentAvatar = commentsList.firstOrNull()?.authorAvatarUrl
                            ?: nextQueueData?.topComment?.authorAvatarUrl
                            ?: ""

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardBackground)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .iosBounceClick(scaleDown = 0.97f) {
                                    showDescriptionSheet = false
                                    showMixSheet = false
                                    showSettingsSheet = false
                                    showCommentsSheet = true
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_comments),
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (totalCommentsDisplay.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = totalCommentsDisplay,
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (topCommentAvatar.isNotBlank()) {
                                        AsyncImage(
                                            model = topCommentAvatar,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = topCommentSnippet,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Section Video Berikutnya
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_up_next_short),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoadingQueue && recommendedVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YouTubeRed,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = recommendedVideos,
                        key = { index, item -> "rec_${item.id}_$index" },
                        contentType = { _, _ -> "video_card" }
                    ) { _, item ->
                        PhantomVideoCard(
                            video = item,
                            onChannelClick = { chId ->
                                onMinimize()
                                onChannelClick?.invoke(chId, item.channelTitle)
                            },
                            onClick = {
                                isInternalNavigation = false
                                currentOnPlayNextVideo(item)
                            }
                        )
                    }

                    if (isLoadingMoreRecs) {
                        item(key = "loading_more_recs", contentType = "loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(YouTubeSurface)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = YouTubeRed,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.common_loading),
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // 3. Floating YouTube Mix Bar (Official YouTube mobile bottom dock)
        if (!isMinimized && !isFullscreen && mixPlaylist.isNotEmpty() && !showMixSheet && !showCommentsSheet && !showDescriptionSheet && !showSettingsSheet) {
            val nextVid = if (currentMixIndex < mixPlaylist.lastIndex) mixPlaylist[currentMixIndex + 1] else mixPlaylist.first()
            FloatingMixBar(
                nextVideo = nextVid,
                mixTitle = mixTitle,
                onClick = {
                    showCommentsSheet = false
                    showDescriptionSheet = false
                    showSettingsSheet = false
                    showMixSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = totalOffsetY
                        alpha = (1f - (animatedDragOffset / 90f) * 1.5f).coerceIn(0f, 1f)
                    }
                    .zIndex(4f)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            )
        }

        // 4. FLOATING MIX QUEUE SHEET (Only in full player mode)
        if (!isMinimized) {
            PlayerMixSheet(
                visible = showMixSheet && !isMinimized && dragOffsetY < 20f,
                isFullscreen = isFullscreen,
                mixTitle = mixTitle,
                mixPlaylist = mixPlaylist,
                currentMixIndex = currentMixIndex,
                onVideoSelect = { index -> playFromMix(index) },
                onDismiss = { showMixSheet = false },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = totalOffsetY
                    }
                    .zIndex(5f)
            )
        }

        // 5. PERSISTENT LIQUID GLASS MINIPLAYER (Animated enter & exit)
        AnimatedVisibility(
            visible = isMinimized,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = IosSpringSpecs.GentleOffset
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = IosSpringSpecs.GentleOffset
            ) + fadeOut(animationSpec = tween(180)),
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

        // 6. Liquid Glass Player Settings Sheet (Speed, Double Tap Seek, Sleep Timer, Repeat, Autoplay, Audio-Only)
        if (!isMinimized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(25f)
            ) {
                PlayerSettingsSheet(
                    visible = showSettingsSheet,
                    isFullscreen = isFullscreen,
                    currentSpeed = playerState.playbackSpeed,
                    currentQuality = playerState.currentQuality,
                    availableQualities = playerState.availableQualities,
                    doubleTapSeekSeconds = doubleTapSeekSeconds,
                    isLoopEnabled = isLoopEnabled,
                    isAutoplayNext = isAutoplayNext,
                    isAudioOnly = isAudioOnly,
                    sleepTimerRemainingSec = sleepTimerRemainingSec,
                    sleepTimerOption = sleepTimerOption,
                    onDismiss = { showSettingsSheet = false },
                    onSpeedSelected = { speed ->
                        playerState = playerState.copy(playbackSpeed = speed)
                        controller.setPlaybackRate(speed)
                    },
                    onQualitySelected = { quality ->
                        playerState = playerState.copy(currentQuality = quality)
                        controller.setPlaybackQuality(quality)
                    },
                    onDoubleTapSeekSelected = { seconds ->
                        doubleTapSeekSeconds = seconds
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
                    onAudioOnlyToggle = { isAudioOnly = it }
                )
            }
        }

        // 7. Deskripsi Bottom Sheet (YouTube standard, non-screen-covering)
        val descTargetChannelId = nextQueueData?.currentVideo?.channelId?.ifBlank { video.channelId } ?: video.channelId
        val descTargetChannelTitle = nextQueueData?.currentVideo?.channelTitle?.ifBlank { video.channelTitle } ?: video.channelTitle
        val descCurrentAvatar = nextQueueData?.currentVideo?.channelAvatarUrl?.ifBlank { activeAvatarUrl } ?: activeAvatarUrl
        val isDescSubscribed by repository.isSubscribed(descTargetChannelId).collectAsState(initial = false)

        PlayerDescriptionSheet(
            visible = showDescriptionSheet && !isMinimized,
            isFullscreen = isFullscreen,
            video = video,
            nextQueueData = nextQueueData,
            activeAvatarUrl = descCurrentAvatar,
            isSubscribed = isDescSubscribed,
            onSubscribeClick = {
                scope.launch {
                    if (isDescSubscribed) {
                        repository.unsubscribe(descTargetChannelId)
                    } else {
                        repository.subscribe(
                            SubscriptionEntity(
                                channelId = descTargetChannelId,
                                channelTitle = descTargetChannelTitle,
                                channelHandle = nextQueueData?.channelHandle ?: "",
                                channelAvatarUrl = descCurrentAvatar,
                                subscriberCountText = nextQueueData?.channelSubscriberCountText ?: ""
                            )
                        )
                    }
                }
            },
            onChannelClick = { chId, chTitle ->
                onMinimize()
                onChannelClick?.invoke(chId, chTitle)
            },
            onDismiss = { showDescriptionSheet = false },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(22f)
        )

        // 8. Komentar Bottom Sheet (YouTube standard, non-screen-covering)
        PlayerCommentsSheet(
            visible = showCommentsSheet && !isMinimized,
            isFullscreen = isFullscreen,
            commentsList = commentsList,
            commentsTotalCountText = commentsTotalCountText,
            isLoadingComments = isLoadingComments,
            hasMoreComments = !commentsContinuationToken.isNullOrBlank(),
            onLoadMore = {
                val token = commentsContinuationToken ?: return@PlayerCommentsSheet
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
            },
            onDismiss = { showCommentsSheet = false },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(22f)
        )

        // 9. Download Format Picker Sheet
        if (showDownloadSheet && !isMinimized) {
            DownloadFormatSheet(
                video = video,
                repository = repository,
                onDismiss = { showDownloadSheet = false }
            )
        }
    }
}

@Composable
private fun PlayerScrubberControls(
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
    val formattedCurrent = remember(currentSec.toLong()) { formatSeconds(currentSec.toLong()) }
    val formattedDuration = remember(durSec.toLong()) { formatSeconds(durSec.toLong()) }

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
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
                icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                size = 36.dp,
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
private fun PlayerIdleProgressBar(
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

private fun formatSeconds(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

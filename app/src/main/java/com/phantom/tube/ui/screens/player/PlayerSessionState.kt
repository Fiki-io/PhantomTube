package com.phantom.tube.ui.screens.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.phantom.tube.R
import com.phantom.tube.core.network.NetworkConnectivityObserver
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
import com.phantom.tube.player.service.PhantomMediaService
import com.phantom.tube.ui.components.SleepTimerOption
import com.phantom.tube.ui.screens.player.components.SponsorBlockEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Stable
class PlayerSessionState(
    val context: Context,
    val scope: CoroutineScope,
    val repository: PhantomRepository,
    val haptic: HapticFeedback,
    initialVideo: VideoItem
) {
    val controller = PhantomPlayerController(context)
    val networkObserver = NetworkConnectivityObserver(context)
    val lazyListState = LazyListState()
    val fullSheetProgress = Animatable(1f)

    var activeVideo by mutableStateOf(initialVideo)
    var onPlayNextVideoCallback: (VideoItem) -> Unit = {}
    var onSelectQueueVideoCallback: (VideoItem, Boolean) -> Unit = { vid, _ -> onPlayNextVideoCallback(vid) }
    var onPlayPreviousVideoCallback: () -> Boolean = { false }
    var onPlayForwardVideoCallback: () -> Boolean = { false }
    var isVideoInBackStackCallback: (String) -> Boolean = { false }

    var playerNoticeText by mutableStateOf<String?>(null)
    private var noticeJob: Job? = null
    private var sponsorPillJob: Job? = null
    private var orientationUnlockRunnable: Runnable? = null

    var playerState by mutableStateOf(
        PlayerState(
            videoId = initialVideo.id,
            playbackSpeed = repository.preferences?.defaultSpeed?.value ?: 1.0f
        )
    )
    var currentPositionSec by mutableFloatStateOf(0f)
    var currentDurationSec by mutableFloatStateOf(0f)
    var currentBufferedFraction by mutableFloatStateOf(0f)
    var isControlsVisible by mutableStateOf(true)
    var isFullscreen by mutableStateOf(false)

    var seekAnimationSide by mutableStateOf<SeekFeedbackDirection?>(null)
    var seekAccumulatedSeconds by mutableIntStateOf(0)

    var sponsorSegments by mutableStateOf<List<SponsorSegment>>(emptyList())
    var showSponsorPill by mutableStateOf(false)
    var lastSkippedSeconds by mutableIntStateOf(0)
    var lastSkippedFromSec by mutableFloatStateOf(0f)
    var lastSkippedCategory by mutableStateOf("sponsor")
    var lastTargetSkipEndSec by mutableFloatStateOf(0f)
    var skippedSegmentUuids: MutableSet<String> = ConcurrentHashMap.newKeySet()
    var undoneSegmentUuids: MutableSet<String> = ConcurrentHashMap.newKeySet()
    var appliedDefaultsVideoId by mutableStateOf<String?>(null)
    var hasSyncedMediaSessionInitialPosition by mutableStateOf(false)
    var lastSyncedMediaSessionSec by mutableFloatStateOf(0f)

    var mixPlaylist by mutableStateOf<List<VideoItem>>(emptyList())
    var activePlaylistId by mutableStateOf(initialVideo.playlistId)
    var isExplicitPlaylistSession by mutableStateOf(initialVideo.isPlaylist || !initialVideo.playlistId.isNullOrBlank())
    var currentMixIndex by mutableIntStateOf(0)
    var recommendedVideos by mutableStateOf<List<VideoItem>>(emptyList())
    var recContinuationToken by mutableStateOf<String?>(null)
    var isLoadingMoreRecs by mutableStateOf(false)
    var canLoadMoreRecs by mutableStateOf(true)
    var activeAvatarUrl by mutableStateOf(initialVideo.channelAvatarUrl)
    var nextQueueData by mutableStateOf<NextQueue?>(null)
    var commentsList by mutableStateOf<List<VideoComment>>(emptyList())
    var commentsTotalCountText by mutableStateOf("")
    var commentsContinuationToken by mutableStateOf<String?>(null)
    var isLoadingComments by mutableStateOf(false)
    var showDescriptionSheet by mutableStateOf(false)
    var showCommentsSheet by mutableStateOf(false)
    var showDownloadSheet by mutableStateOf(false)
    var isEndscreenDismissed by mutableStateOf(false)
    var liveEndscreenVideos by mutableStateOf<List<VideoItem>>(emptyList())
    var mixTitle by mutableStateOf("")
    var showMixSheet by mutableStateOf(false)
    var isInternalNavigation by mutableStateOf(false)
    var isLoadingQueue by mutableStateOf(false)

    var showSettingsSheet by mutableStateOf(false)
    var isLoopEnabled by mutableStateOf(false)
    var isAutoplayNext by mutableStateOf(true)
    var isAudioOnly by mutableStateOf(false)
    var previousQualityBeforeAudioOnly by mutableStateOf("auto")
    var sleepTimerOption by mutableStateOf(SleepTimerOption.OFF)
    var sleepTimerRemainingSec by mutableStateOf<Int?>(null)
    var lastRecordedPositionSec by mutableFloatStateOf(0f)

    var mediaService by mutableStateOf<PhantomMediaService?>(null)

    var isOfflineWaiting by mutableStateOf(false)
    var wasPlayingBeforeOffline by mutableStateOf(false)

    var isUserPaused by mutableStateOf(false)
    var isPendingFadePause by mutableStateOf(false)
    var pausedByExternalAudio by mutableStateOf(false)
    var unexpectedPauseElapsedMs by mutableLongStateOf(0L)
    var lastLoadVideoElapsedMs by mutableLongStateOf(0L)
    private var pendingFadePauseJob: Job? = null
    private var duckRecoveryCooldownMs: Long = 0L

    var lastProgressElapsedRealtime by mutableLongStateOf(0L)
    var lastObservedPlaybackSec by mutableFloatStateOf(0f)
    var isPlaybackStalled by mutableStateOf(false)

    var storyboardData by mutableStateOf<StoryboardData?>(null)
    var isScrubbing by mutableStateOf(false)
    var scrubFraction by mutableFloatStateOf(0f)
    var scrubTouchX by mutableFloatStateOf(0f)
    var videoBoxWidthPx by mutableFloatStateOf(0f)
    var videoBoxHeightPx by mutableFloatStateOf(0f)
    var wasPlayingBeforeScrub by mutableStateOf(false)
    var lastScrubSeekTimeMs by mutableLongStateOf(0L)
    var isZoomToFill by mutableStateOf(false)
    var isCaptionsEnabled by mutableStateOf(false)
    var hasCaptions by mutableStateOf(false)
    var isQualityMenuExpanded by mutableStateOf(false)

    var dragOffsetY by mutableFloatStateOf(0f)
    var portraitDragPx by mutableFloatStateOf(0f)
    var hasTriggeredHaptic by mutableStateOf(false)

    val currentVideo: VideoItem by derivedStateOf {
        val v = activeVideo
        nextQueueData?.currentVideo?.let { fetched ->
            v.copy(
                title = fetched.title.ifBlank { v.title },
                channelTitle = fetched.channelTitle.ifBlank { v.channelTitle },
                channelId = fetched.channelId.ifBlank { v.channelId },
                thumbnailUrl = fetched.thumbnailUrl.ifBlank { v.thumbnailUrl },
                channelAvatarUrl = fetched.channelAvatarUrl.ifBlank { activeAvatarUrl.ifBlank { v.channelAvatarUrl } },
                durationText = fetched.durationText.ifBlank { v.durationText }
            )
        } ?: v
    }

    fun showNotice(text: String) {
        noticeJob?.cancel()
        playerNoticeText = text
        noticeJob = scope.launch {
            delay(1800)
            playerNoticeText = null
        }
    }

    fun markUserPlay(fadeIn: Boolean = true) {
        pendingFadePauseJob?.cancel()
        pendingFadePauseJob = null
        isPendingFadePause = false
        isUserPaused = false
        pausedByExternalAudio = false
        unexpectedPauseElapsedMs = 0L
        mediaService?.clearExternalInterruptionState()
        controller.play(fadeIn = fadeIn)
    }

    fun markUserPause(fadeOut: Boolean = true) {
        isUserPaused = true
        pausedByExternalAudio = false
        unexpectedPauseElapsedMs = 0L
        mediaService?.clearExternalInterruptionState()
        if (fadeOut) {
            isPendingFadePause = true
            pendingFadePauseJob?.cancel()
            pendingFadePauseJob = scope.launch {
                delay(350L)
                isPendingFadePause = false
                pendingFadePauseJob = null
            }
            controller.pause(fadeOut = true)
        } else {
            pendingFadePauseJob?.cancel()
            pendingFadePauseJob = null
            isPendingFadePause = false
            controller.pause(fadeOut = false)
        }
    }

    fun togglePlayPauseFromUser() {
        if (isPendingFadePause) {
            markUserPlay(fadeIn = true)
        } else if (playerState.isPlaying) {
            markUserPause(fadeOut = true)
        } else {
            if (!networkObserver.isConnected()) {
                showNotice(context.getString(R.string.player_no_internet_notice))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                markUserPlay(fadeIn = true)
            }
        }
    }

    fun prepareForNewVideoLoad() {
        pendingFadePauseJob?.cancel()
        pendingFadePauseJob = null
        isPendingFadePause = false
        isUserPaused = false
        pausedByExternalAudio = false
        unexpectedPauseElapsedMs = 0L
        lastLoadVideoElapsedMs = SystemClock.elapsedRealtime()
        mediaService?.clearExternalInterruptionState()
    }

    fun handleExternalAudioInterruptionDetected() {
        if (isUserPaused || isScrubbing || isOfflineWaiting || playerState.isPlaying || playerState.isEnded) {
            return
        }
        if (!networkObserver.isConnected()) return
        val now = SystemClock.elapsedRealtime()
        if (pausedByExternalAudio || (unexpectedPauseElapsedMs > 0L && (now - unexpectedPauseElapsedMs) <= 4500L)) {
            pausedByExternalAudio = true
        }
    }

    fun handleExternalAudioInterruptionEnded() {
        if (pausedByExternalAudio && !isUserPaused && !isScrubbing && !isOfflineWaiting &&
            !playerState.isPlaying && !playerState.isEnded && networkObserver.isConnected()
        ) {
            pausedByExternalAudio = false
            unexpectedPauseElapsedMs = 0L
            controller.play(fadeIn = true)
        }
    }

    fun handleExternalAudioDuckingEnded() {
        val now = SystemClock.elapsedRealtime()
        if (playerState.isPlaying && !isPendingFadePause && !isScrubbing &&
            (now - lastLoadVideoElapsedMs > 1500L) &&
            (now - duckRecoveryCooldownMs > 800L)
        ) {
            duckRecoveryCooldownMs = now
            controller.recoverAudioDucking()
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

    fun playNext() {
        if (!networkObserver.isConnected()) {
            showNotice(context.getString(R.string.player_no_internet_notice))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }
        if (mixPlaylist.isNotEmpty()) {
            val activeIdx = if (currentMixIndex in mixPlaylist.indices && mixPlaylist[currentMixIndex].id == currentVideo.id) {
                currentMixIndex
            } else {
                val found = mixPlaylist.indexOfFirst { it.id == currentVideo.id }
                if (found != -1) found else currentMixIndex.coerceIn(0, mixPlaylist.lastIndex)
            }
            if (activeIdx < mixPlaylist.lastIndex) {
                val nextIndex = activeIdx + 1
                val nextVid = mixPlaylist[nextIndex]
                currentMixIndex = nextIndex
                isInternalNavigation = true
                scope.launch {
                    repository.resetWatchPosition(nextVid.id)
                }
                onSelectQueueVideoCallback(nextVid, true)
            } else {
                scope.launch {
                    isLoadingQueue = true
                    val fetchPlId = if (isExplicitPlaylistSession) {
                        activePlaylistId?.ifBlank { null }
                    } else {
                        null
                    }
                    val nextData = repository.getWatchNext(currentVideo.id, fetchPlId)
                    val existingIds = mixPlaylist.map { it.id }.toSet()
                    val rawFresh = nextData?.mixPlaylist?.filter { it.id !in existingIds }.orEmpty()
                    val freshItems = if (isExplicitPlaylistSession) {
                        rawFresh
                    } else {
                        rawFresh.shuffled(kotlin.random.Random(System.nanoTime()))
                    }
                    if (freshItems.isNotEmpty()) {
                        val nextIndex = mixPlaylist.size
                        mixPlaylist = mixPlaylist + freshItems
                        val nextVid = mixPlaylist[nextIndex]
                        currentMixIndex = nextIndex
                        isInternalNavigation = true
                        repository.resetWatchPosition(nextVid.id)
                        onSelectQueueVideoCallback(nextVid, true)
                    } else {
                        val fallbackRec = recommendedVideos.firstOrNull {
                            it.id != currentVideo.id && !it.isPlaylist && it.playlistId.isNullOrBlank()
                        }
                        if (fallbackRec != null) {
                            isInternalNavigation = false
                            onPlayNextVideoCallback(fallbackRec.copy(playlistId = null, isPlaylist = false))
                        }
                    }
                    isLoadingQueue = false
                }
            }
        } else {
            scope.launch {
                isLoadingQueue = true
                val fetchPlId = if (isExplicitPlaylistSession) {
                    activePlaylistId?.ifBlank { null }
                } else {
                    null
                }
                val nextData = repository.getWatchNext(currentVideo.id, fetchPlId)
                val queueList = nextData?.mixPlaylist.orEmpty()
                if (queueList.isNotEmpty()) {
                    val preparedQueue = if (isExplicitPlaylistSession) {
                        if (queueList.any { it.id == currentVideo.id }) {
                            queueList
                        } else {
                            listOf(currentVideo.copy(playlistId = null, isPlaylist = false)) + queueList
                        }
                    } else {
                        val rng = kotlin.random.Random(System.nanoTime())
                        val otherItems = queueList.filter { it.id != currentVideo.id }
                        val unplayedShuffled = otherItems
                            .filterNot { isVideoInBackStackCallback(it.id) }
                            .shuffled(rng)
                        val playedShuffled = otherItems
                            .filter { isVideoInBackStackCallback(it.id) }
                            .shuffled(rng)
                        val currentEntry = queueList.firstOrNull { it.id == currentVideo.id }
                            ?: currentVideo.copy(playlistId = null, isPlaylist = false)
                        listOf(currentEntry) + unplayedShuffled + playedShuffled
                    }
                    mixPlaylist = preparedQueue
                    val curIdx = preparedQueue.indexOfFirst { it.id == currentVideo.id }.coerceAtLeast(0)
                    val nextIndex = (curIdx + 1).coerceAtMost(preparedQueue.lastIndex)
                    if (nextIndex > curIdx) {
                        val nextVid = preparedQueue[nextIndex]
                        currentMixIndex = nextIndex
                        isInternalNavigation = true
                        repository.resetWatchPosition(nextVid.id)
                        onSelectQueueVideoCallback(nextVid, true)
                        isLoadingQueue = false
                        return@launch
                    }
                }
                isLoadingQueue = false
                if (onPlayForwardVideoCallback()) {
                    return@launch
                }
                val regularRecs = recommendedVideos.filter {
                    it.id != currentVideo.id && !it.isPlaylist && it.playlistId.isNullOrBlank()
                }
                val nextVid = regularRecs.firstOrNull { !isVideoInBackStackCallback(it.id) }
                    ?: regularRecs.firstOrNull()
                if (nextVid != null) {
                    isInternalNavigation = false
                    onPlayNextVideoCallback(nextVid.copy(playlistId = null, isPlaylist = false))
                }
            }
        }
    }

    val hasPreviousInQueue: Boolean by derivedStateOf {
        if (mixPlaylist.isEmpty()) {
            false
        } else {
            val activeIdx = if (currentMixIndex in mixPlaylist.indices && mixPlaylist[currentMixIndex].id == currentVideo.id) {
                currentMixIndex
            } else {
                val found = mixPlaylist.indexOfFirst { it.id == currentVideo.id }
                if (found != -1) found else currentMixIndex
            }
            activeIdx > 0
        }
    }

    fun playPrevious(): Boolean {
        if (currentPositionSec > 3f) {
            controller.seekTo(0f)
            currentPositionSec = 0f
            return true
        }
        if (!networkObserver.isConnected()) {
            showNotice(context.getString(R.string.player_no_internet_notice))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return false
        }
        if (mixPlaylist.isNotEmpty()) {
            val activeIdx = if (currentMixIndex in mixPlaylist.indices && mixPlaylist[currentMixIndex].id == currentVideo.id) {
                currentMixIndex
            } else {
                val found = mixPlaylist.indexOfFirst { it.id == currentVideo.id }
                if (found != -1) found else currentMixIndex
            }
            if (activeIdx > 0) {
                val prevIndex = activeIdx - 1
                val prevVid = mixPlaylist[prevIndex]
                currentMixIndex = prevIndex
                isInternalNavigation = true
                scope.launch {
                    repository.resetWatchPosition(prevVid.id)
                }
                onSelectQueueVideoCallback(prevVid, false)
                return true
            }
        }
        controller.seekTo(0f)
        currentPositionSec = 0f
        return false
    }

    fun playFromMix(targetIndex: Int) {
        if (!networkObserver.isConnected()) {
            showNotice(context.getString(R.string.player_no_internet_notice))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (targetIndex in mixPlaylist.indices) {
            val targetVid = mixPlaylist[targetIndex]
            currentMixIndex = targetIndex
            isInternalNavigation = true
            scope.launch {
                repository.resetWatchPosition(targetVid.id)
            }
            onSelectQueueVideoCallback(targetVid, true)
        }
    }

    fun enterFullscreen() {
        val activity = context.findActivity()
        if (activity != null) {
            orientationUnlockRunnable?.let { activity.window.decorView.removeCallbacks(it) }
            orientationUnlockRunnable = null
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        isFullscreen = true
    }

    fun exitFullscreenToPortrait() {
        val activity = context.findActivity()
        if (activity != null) {
            orientationUnlockRunnable?.let { activity.window.decorView.removeCallbacks(it) }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val runnable = Runnable {
                if (!isFullscreen) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                orientationUnlockRunnable = null
            }
            orientationUnlockRunnable = runnable
            activity.window.decorView.postDelayed(runnable, 600)
        }
        isFullscreen = false
    }

    fun cancelOrientationUnlock() {
        val activity = context.findActivity() ?: return
        orientationUnlockRunnable?.let { activity.window.decorView.removeCallbacks(it) }
        orientationUnlockRunnable = null
    }

    fun dismissSponsorPill() {
        sponsorPillJob?.cancel()
        sponsorPillJob = null
        showSponsorPill = false
    }

    fun loadMoreRecommendations() {
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
                            recommendedVideos = (recommendedVideos + freshVideos).take(40)
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

    val bridge = PhantomPlayerBridge(
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
                    pendingFadePauseJob?.cancel()
                    pendingFadePauseJob = null
                    isPendingFadePause = false
                    isUserPaused = false
                    pausedByExternalAudio = false
                    unexpectedPauseElapsedMs = 0L
                    isPlaybackStalled = false
                    lastProgressElapsedRealtime = SystemClock.elapsedRealtime()
                    lastObservedPlaybackSec = currentPositionSec
                    playerState = playerState.copy(isPlaying = true, isBuffering = false, isEnded = false, errorCode = null)
                    mediaService?.updatePlaybackState(
                        playing = true,
                        currentPositionMs = (currentPositionSec * 1000).toLong(),
                        forceNotify = true,
                        isBuffering = false
                    )
                    if (isCaptionsEnabled) {
                        controller.setCaptionsEnabled(true)
                    }
                    if (isAudioOnly) {
                        controller.setAudioOnly(true)
                    }
                    if (appliedDefaultsVideoId != currentVideo.id) {
                        appliedDefaultsVideoId = currentVideo.id
                        val targetSpeed = playerState.playbackSpeed
                        if (targetSpeed != 1.0f) {
                            controller.setPlaybackRate(targetSpeed)
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
                    val wasPlaying = playerState.isPlaying
                    pendingFadePauseJob?.cancel()
                    pendingFadePauseJob = null
                    isPendingFadePause = false
                    isPlaybackStalled = false
                    wasPlayingBeforeOffline = false
                    playerState = playerState.copy(isPlaying = false, isBuffering = false)
                    mediaService?.updatePlaybackState(
                        playing = false,
                        currentPositionMs = (currentPositionSec * 1000).toLong(),
                        forceNotify = true,
                        isBuffering = false
                    )
                    if (wasPlaying && !isUserPaused && !isScrubbing && !isOfflineWaiting && networkObserver.isConnected()) {
                        unexpectedPauseElapsedMs = SystemClock.elapsedRealtime()
                        if (mediaService?.isExternalAudioCurrentlyActive() == true) {
                            pausedByExternalAudio = true
                            mediaService?.ensurePausedInterruptionMonitoring()
                        } else {
                            mediaService?.onUnexpectedOnlinePause()
                        }
                    } else {
                        unexpectedPauseElapsedMs = 0L
                        pausedByExternalAudio = false
                    }
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
                3 -> {
                    val wasPlaying = playerState.isPlaying
                    playerState = playerState.copy(isBuffering = true)
                    if (wasPlaying && !networkObserver.isConnected()) {
                        wasPlayingBeforeOffline = true
                    }
                    mediaService?.updatePlaybackState(
                        playing = false,
                        currentPositionMs = (currentPositionSec * 1000).toLong(),
                        forceNotify = true,
                        isBuffering = true
                    )
                }
                0 -> {
                    pendingFadePauseJob?.cancel()
                    pendingFadePauseJob = null
                    isPendingFadePause = false
                    pausedByExternalAudio = false
                    unexpectedPauseElapsedMs = 0L
                    isPlaybackStalled = false
                    wasPlayingBeforeOffline = false
                    playerState = playerState.copy(isPlaying = false, isEnded = true)
                    mediaService?.updatePlaybackState(false, 0L, forceNotify = true, isBuffering = false)
                    val endedVidId = activeVideo.id
                    scope.launch {
                        repository.resetWatchPosition(endedVidId)
                    }
                    if (isLoopEnabled) {
                        controller.seekTo(0f)
                        controller.play(fadeIn = false)
                    } else if (sleepTimerOption == SleepTimerOption.END_OF_VIDEO) {
                        markUserPause(fadeOut = true)
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

            if (kotlin.math.abs(current - lastObservedPlaybackSec) >= 0.1f) {
                lastObservedPlaybackSec = current
                lastProgressElapsedRealtime = SystemClock.elapsedRealtime()
                if (isPlaybackStalled) {
                    isPlaybackStalled = false
                    if (playerState.isPlaying) {
                        playerState = playerState.copy(isBuffering = false)
                        mediaService?.updatePlaybackState(
                            playing = true,
                            currentPositionMs = (current * 1000).toLong(),
                            forceNotify = true,
                            isBuffering = false
                        )
                    }
                }
            }

            val wasZeroDuration = currentDurationSec <= 0f && duration > 0f
            currentPositionSec = current
            currentDurationSec = duration
            currentBufferedFraction = buffered

            if (wasZeroDuration) {
                playerState = playerState.copy(durationSec = duration)
                mediaService?.updateDuration((duration * 1000).toLong(), (current * 1000).toLong())
            }

            val isEffectivelyPlaying = playerState.isPlaying && !isPlaybackStalled && !playerState.isBuffering
            if (!hasSyncedMediaSessionInitialPosition && current > 0.5f) {
                hasSyncedMediaSessionInitialPosition = true
                lastSyncedMediaSessionSec = current
                mediaService?.updatePlaybackState(isEffectivelyPlaying, (current * 1000).toLong())
            } else if (kotlin.math.abs(current - lastSyncedMediaSessionSec) >= 5f) {
                lastSyncedMediaSessionSec = current
                mediaService?.updatePlaybackState(isEffectivelyPlaying, (current * 1000).toLong())
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
                sponsorPillJob?.cancel()
                sponsorPillJob = scope.launch {
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

@Composable
fun rememberPlayerSessionState(
    context: Context,
    repository: PhantomRepository,
    haptic: HapticFeedback,
    video: VideoItem,
    isMinimized: Boolean,
    onPlayNextVideo: (VideoItem) -> Unit,
    onSelectQueueVideo: (VideoItem, Boolean) -> Unit = { vid, _ -> onPlayNextVideo(vid) },
    onPlayPreviousVideo: () -> Boolean,
    onPlayForwardVideo: () -> Boolean = { false },
    isVideoInBackStack: (String) -> Boolean = { false }
): PlayerSessionState {
    val scope = rememberCoroutineScope()
    val session = remember {
        PlayerSessionState(
            context = context,
            scope = scope,
            repository = repository,
            haptic = haptic,
            initialVideo = video
        ).apply {
            if (isMinimized) {
                scope.launch { fullSheetProgress.snapTo(0f) }
            }
        }
    }
    session.activeVideo = video
    session.onPlayNextVideoCallback = onPlayNextVideo
    session.onSelectQueueVideoCallback = onSelectQueueVideo
    session.onPlayPreviousVideoCallback = onPlayPreviousVideo
    session.onPlayForwardVideoCallback = onPlayForwardVideo
    session.isVideoInBackStackCallback = isVideoInBackStack
    return session
}

package com.phantom.tube.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.phantom.tube.R
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.VideoComment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.player.PlayerState
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.PlayerSettingsSheet
import com.phantom.tube.ui.components.SleepTimerOption
import kotlinx.coroutines.launch

@Composable
fun BoxScope.PlayerPortraitSheetContent(
    isSheetRendered: Boolean,
    isFullscreen: Boolean,
    sheetProgressProvider: () -> Float,
    dragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    dragModifier: Modifier,
    lazyListState: LazyListState,
    video: VideoItem,
    nextQueueData: NextQueue?,
    activeAvatarUrl: String,
    commentsList: List<VideoComment>,
    commentsTotalCountText: String,
    repository: PhantomRepository,
    isLoadingQueue: Boolean,
    recommendedVideos: List<VideoItem>,
    isLoadingMoreRecs: Boolean,
    mixPlaylist: List<VideoItem>,
    currentMixIndex: Int,
    mixTitle: String,
    showMixSheet: Boolean,
    showCommentsSheet: Boolean,
    showDescriptionSheet: Boolean,
    showSettingsSheet: Boolean,
    onOpenDescription: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenMixSheet: () -> Unit,
    onChannelSelected: (String, String) -> Unit,
    onRecommendationClick: (VideoItem) -> Unit
) {
    if (!isSheetRendered || isFullscreen) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = sheetProgressProvider()
                val drag = dragOffsetProvider()
                val contentAlpha = (progress * (1f - (drag / 90f) * 1.5f)).coerceIn(0f, 1f)
                translationY = (1f - progress) * screenHeightPx + drag
                alpha = contentAlpha
            }
            .statusBarsPadding()
            .zIndex(1f)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Drag handle
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
            item(key = "player_metadata_header") {
                PlayerMetadataSection(
                    video = video,
                    nextQueueData = nextQueueData,
                    activeAvatarUrl = activeAvatarUrl,
                    commentsList = commentsList,
                    commentsTotalCountText = commentsTotalCountText,
                    repository = repository,
                    onOpenDescription = onOpenDescription,
                    onOpenComments = onOpenComments,
                    onOpenDownload = onOpenDownload,
                    onChannelSelected = onChannelSelected
                )
            }

            item(key = "up_next_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.player_up_next_short),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoadingQueue && recommendedVideos.isEmpty()) {
                item(key = "queue_initial_loader") {
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
                            onChannelSelected(chId, item.channelTitle)
                        },
                        onClick = {
                            onRecommendationClick(item)
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

    // Floating YouTube Mix Bar
    if (mixPlaylist.isNotEmpty() && !showMixSheet && !showCommentsSheet && !showDescriptionSheet && !showSettingsSheet) {
        val nextVid = if (currentMixIndex < mixPlaylist.lastIndex) mixPlaylist[currentMixIndex + 1] else mixPlaylist.first()
        FloatingMixBar(
            nextVideo = nextVid,
            mixTitle = mixTitle,
            onClick = onOpenMixSheet,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val progress = sheetProgressProvider()
                    val drag = dragOffsetProvider()
                    translationY = (1f - progress) * screenHeightPx + drag
                    alpha = (progress * (1f - (drag / 90f) * 1.5f)).coerceIn(0f, 1f)
                }
                .zIndex(4f)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.PlayerSheetsHost(
    isMinimized: Boolean,
    isFullscreen: Boolean,
    dragOffsetY: Float,
    sheetProgressProvider: () -> Float,
    animatedDragOffsetProvider: () -> Float,
    screenHeightPx: Float,
    video: VideoItem,
    repository: PhantomRepository,
    nextQueueData: NextQueue?,
    activeAvatarUrl: String,
    playerState: PlayerState,
    doubleTapSeekSeconds: Int,
    isLoopEnabled: Boolean,
    isAutoplayNext: Boolean,
    isAudioOnly: Boolean,
    sleepTimerRemainingSec: Int?,
    sleepTimerOption: SleepTimerOption,
    mixTitle: String,
    mixPlaylist: List<VideoItem>,
    currentMixIndex: Int,
    commentsList: List<VideoComment>,
    commentsTotalCountText: String,
    isLoadingComments: Boolean,
    hasMoreComments: Boolean,
    showMixSheet: Boolean,
    showSettingsSheet: Boolean,
    showDescriptionSheet: Boolean,
    showCommentsSheet: Boolean,
    showDownloadSheet: Boolean,
    onDismissMixSheet: () -> Unit,
    onSelectMixIndex: (Int) -> Unit,
    onDismissSettingsSheet: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDoubleTapSeekSelected: (Int) -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onAudioOnlyToggle: (Boolean) -> Unit,
    onDismissDescriptionSheet: () -> Unit,
    onChannelSelected: (String, String) -> Unit,
    onLoadMoreComments: () -> Unit,
    onDismissCommentsSheet: () -> Unit,
    onDismissDownloadSheet: () -> Unit
) {
    val scope = rememberCoroutineScope()

    if (!isMinimized) {
        PlayerMixSheet(
            visible = showMixSheet && dragOffsetY < 20f,
            isFullscreen = isFullscreen,
            mixTitle = mixTitle,
            mixPlaylist = mixPlaylist,
            currentMixIndex = currentMixIndex,
            onVideoSelect = onSelectMixIndex,
            onDismiss = onDismissMixSheet,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - sheetProgressProvider()) * screenHeightPx + animatedDragOffsetProvider()
                }
                .zIndex(5f)
        )

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
                onDismiss = onDismissSettingsSheet,
                onSpeedSelected = onSpeedSelected,
                onQualitySelected = onQualitySelected,
                onDoubleTapSeekSelected = onDoubleTapSeekSelected,
                onSleepTimerSelected = onSleepTimerSelected,
                onLoopToggle = onLoopToggle,
                onAutoplayToggle = onAutoplayToggle,
                onAudioOnlyToggle = onAudioOnlyToggle
            )
        }
    }

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
        onChannelClick = onChannelSelected,
        onDismiss = onDismissDescriptionSheet,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(22f)
    )

    PlayerCommentsSheet(
        visible = showCommentsSheet && !isMinimized,
        isFullscreen = isFullscreen,
        commentsList = commentsList,
        commentsTotalCountText = commentsTotalCountText,
        isLoadingComments = isLoadingComments,
        hasMoreComments = hasMoreComments,
        onLoadMore = onLoadMoreComments,
        onDismiss = onDismissCommentsSheet,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(22f)
    )

    if (showDownloadSheet && !isMinimized) {
        DownloadFormatSheet(
            video = video,
            repository = repository,
            onDismiss = onDismissDownloadSheet
        )
    }
}

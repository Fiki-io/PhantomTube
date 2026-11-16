package com.phantom.tube.ui.screens.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.phantom.tube.R
import com.phantom.tube.core.theme.CardBackground
import com.phantom.tube.core.theme.CardBorder
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.VideoCardSkeleton
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, channelTitle: String) -> Unit,
    onExploreClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subscriptions by repository.getSubscriptions().collectAsState(initial = emptyList())
    val favorites by repository.getFavorites().collectAsState(initial = emptyList())
    val downloadsList by repository.getDownloads().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var feedJob by remember { mutableStateOf<Job?>(null) }
    var feedVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var videoToDownload by remember { mutableStateOf<VideoItem?>(null) }

    val pullRefreshState = rememberPullToRefreshState()

    fun refreshFeed(forceRefresh: Boolean = false) {
        if (subscriptions.isEmpty()) {
            feedJob?.cancel()
            feedVideos = emptyList()
            isLoadingFeed = false
            isRefreshing = false
            return
        }
        feedJob?.cancel()
        feedJob = scope.launch {
            isRefreshing = forceRefresh
            isLoadingFeed = true
            try {
                feedVideos = repository.getSubscriptionFeed(
                    subscriptions = subscriptions,
                    forceRefresh = forceRefresh
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
                isLoadingFeed = false
            }
        }
    }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            refreshFeed(forceRefresh = true)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
        }
    }

    val totalMb = remember(downloadsList) {
        val totalBytes = downloadsList.sumOf { it.fileSize }
        String.format(java.util.Locale.getDefault(), "%.1f MB", totalBytes / (1024f * 1024f))
    }

    // Fetch latest videos from subscribed channels (uses repository 5-minute cache when available)
    LaunchedEffect(subscriptions) {
        if (subscriptions.isNotEmpty()) {
            refreshFeed(forceRefresh = false)
        } else {
            feedJob?.cancel()
            feedVideos = emptyList()
            isLoadingFeed = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        // Header Koleksi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.subscriptions_title),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // DOWNLOADS SHORTCUT CARD (Opsi 3: Unduhan di Halaman Koleksi)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onDownloadsClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(YouTubeRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (downloadsList.isEmpty()) {
                        stringResource(R.string.downloads_empty_subtitle)
                    } else {
                        stringResource(R.string.downloads_count_format, downloadsList.size, totalMb)
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))


        if (subscriptions.isEmpty() && favorites.isEmpty()) {
            // EMPTY STATE
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Subscriptions,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.empty_subscriptions_title),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.empty_subscriptions_desc),
                        color = TextSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YouTubeRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.explore_home),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomNavInset + 140.dp)
                ) {
                    // 1. Horizontal Avatars Row of Subscribed Channels
                    if (subscriptions.isNotEmpty()) {
                        item(key = "subscribed_channels_row") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(subscriptions, key = { it.channelId }) { sub ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(64.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onChannelClick(sub.channelId, sub.channelTitle) }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF222222))
                                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                        ) {
                                            val avatarRequest = remember(sub.channelAvatarUrl, context) {
                                                ImageRequest.Builder(context)
                                                    .data(sub.channelAvatarUrl)
                                                    .size(128, 128)
                                                    .precision(Precision.INEXACT)
                                                    .allowRgb565(true)
                                                    .allowHardware(false)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = avatarRequest,
                                                contentDescription = sub.channelTitle,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.matchParentSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = sub.channelTitle,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (favorites.isNotEmpty()) {
                        item(key = "favorites_section_header") {
                            Text(
                                text = stringResource(R.string.library_favorites),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        itemsIndexed(favorites, key = { index, fav -> "fav_vid_${fav.videoId}_$index" }) { _, fav ->
                            val favVideo = VideoItem(
                                id = fav.videoId,
                                title = fav.title,
                                channelTitle = fav.channelTitle,
                                channelId = fav.channelId,
                                thumbnailUrl = fav.thumbnailUrl,
                                durationText = fav.durationText
                            )
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                PhantomVideoCard(
                                    video = favVideo,
                                    onChannelClick = { chId ->
                                        val target = chId.ifBlank { fav.channelTitle }
                                        if (target.isNotBlank()) {
                                            onChannelClick(target, fav.channelTitle)
                                        }
                                    },
                                    onDownloadClick = { videoToDownload = it },
                                    onClick = { onVideoClick(favVideo) }
                                )
                            }
                        }
                    }

                    // 2. Section divider / header
                    if (subscriptions.isNotEmpty()) {
                        item(key = "feed_section_header") {
                            Text(
                                text = stringResource(R.string.channel_recent_videos),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // 3. Loading indicator, Empty message, or Feed items
                    if (isLoadingFeed && feedVideos.isEmpty()) {
                        repeat(3) { index ->
                            item(key = "sub_skeleton_$index") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    VideoCardSkeleton()
                                }
                            }
                        }
                    } else if (subscriptions.isNotEmpty() && feedVideos.isEmpty()) {
                        item(key = "sub_empty_videos") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.channel_no_videos),
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { refreshFeed(forceRefresh = true) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF272727),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text(text = stringResource(R.string.common_retry), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else if (feedVideos.isNotEmpty()) {
                        itemsIndexed(feedVideos, key = { index, v -> "sub_vid_${v.id}_$index" }) { _, video ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                PhantomVideoCard(
                                    video = video,
                                    onChannelClick = { chId ->
                                        onChannelClick(chId.ifBlank { video.channelTitle }, video.channelTitle)
                                    },
                                    onDownloadClick = { videoToDownload = it },
                                    onClick = { onVideoClick(video) }
                                )
                            }
                        }
                    }
                }

                if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                    PullToRefreshContainer(
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = CardBackground,
                        contentColor = YouTubeRed
                    )
                }
            }
        }
    }

    videoToDownload?.let { targetVideo ->
        DownloadFormatSheet(
            video = targetVideo,
            repository = repository,
            onDismiss = { videoToDownload = null }
        )
    }
}

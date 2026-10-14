package com.phantom.tube.ui.screens.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import android.content.Intent
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.phantom.tube.R
import com.phantom.tube.PhantomApp
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import com.phantom.tube.data.model.ChannelSortChip
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.ChannelScreenSkeleton
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    initialChannelTitle: String = "",
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<ChannelProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.channel_tab_home),
        stringResource(R.string.channel_tab_videos)
    )

    var videoTabVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var videoContinuationToken by remember { mutableStateOf<String?>(null) }
    var isLoadingMoreVideos by remember { mutableStateOf(false) }

    var sortChips by remember { mutableStateOf<List<ChannelSortChip>>(emptyList()) }
    var selectedSortChip by remember { mutableStateOf("Terbaru") }

    var showDescriptionSheet by remember { mutableStateOf(false) }

    // Check if channel is subscribed
    val isSubscribed by repository.isSubscribed(channelId).collectAsState(initial = false)

    // Load channel data
    fun loadChannelData(sortToken: String? = null, isRefresh: Boolean = false) {
        scope.launch {
            if (sortToken != null) {
                isLoadingMoreVideos = true
                try {
                    val contResult = repository.getChannelVideosContinuation(sortToken)
                    val chTitle = profile?.title ?: initialChannelTitle
                    val chAvatar = profile?.avatarUrl ?: ""
                    videoTabVideos = contResult.videos.map {
                        it.copy(
                            channelTitle = if (it.channelTitle.isNotBlank()) it.channelTitle else chTitle,
                            channelAvatarUrl = if (it.channelAvatarUrl.isNotBlank()) it.channelAvatarUrl else chAvatar,
                            channelId = if (it.channelId.isNotBlank()) it.channelId else channelId
                        )
                    }
                    videoContinuationToken = contResult.continuationToken
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMoreVideos = false
                }
            } else {
                if (isRefresh) {
                    isRefreshing = true
                } else if (profile == null) {
                    isLoading = true
                }
                hasError = false
                try {
                    val res = repository.getChannel(channelId)
                    if (res != null) {
                        profile = res
                        sortChips = if (res.sortChips.isNotEmpty()) {
                            res.sortChips
                        } else {
                            listOf(
                                ChannelSortChip("Terbaru", "", isSelected = true),
                                ChannelSortChip("Populer", ""),
                                ChannelSortChip("Terlama", "")
                            )
                        }

                        // If videos were in main profile response, populate them
                        if (res.videos.isNotEmpty()) {
                            videoTabVideos = res.videos
                            videoContinuationToken = res.continuationToken
                        }
                        if (!res.videoTabParams.isNullOrBlank()) {
                            // Load video tab specifically so full catalog is always available for Beranda and Video tabs
                            val vTabRes = repository.getChannel(channelId, params = res.videoTabParams)
                            if (vTabRes != null && vTabRes.videos.isNotEmpty()) {
                                val chTitle = res.title.ifBlank { initialChannelTitle }
                                val chAvatar = res.avatarUrl
                                val mapped = vTabRes.videos.map {
                                    it.copy(
                                        channelTitle = if (it.channelTitle.isNotBlank()) it.channelTitle else chTitle,
                                        channelAvatarUrl = if (it.channelAvatarUrl.isNotBlank()) it.channelAvatarUrl else chAvatar,
                                        channelId = if (it.channelId.isNotBlank()) it.channelId else channelId
                                    )
                                }
                                if (videoTabVideos.isEmpty()) {
                                    videoTabVideos = mapped
                                    videoContinuationToken = vTabRes.continuationToken
                                } else {
                                    val currentIds = videoTabVideos.map { it.id }.toSet()
                                    videoTabVideos = videoTabVideos + mapped.filter { it.id !in currentIds }
                                }
                                if (vTabRes.sortChips.isNotEmpty()) {
                                    sortChips = vTabRes.sortChips
                                }
                            }
                        }
                    } else if (profile == null) {
                        hasError = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (profile == null) {
                        hasError = true
                    }
                } finally {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    fun loadMoreVideos() {
        val token = videoContinuationToken
        if (token.isNullOrBlank() || isLoadingMoreVideos) return
        isLoadingMoreVideos = true
        scope.launch {
            try {
                val nextResult = repository.getChannelVideosContinuation(token)
                if (nextResult.videos.isNotEmpty()) {
                    val currentIds = videoTabVideos.map { it.id }.toSet()
                    val chTitle = profile?.title ?: initialChannelTitle
                    val chAvatar = profile?.avatarUrl ?: ""
                    val uniqueNew = nextResult.videos
                        .filter { it.id !in currentIds }
                        .map {
                            it.copy(
                                channelTitle = if (it.channelTitle.isNotBlank()) it.channelTitle else chTitle,
                                channelAvatarUrl = if (it.channelAvatarUrl.isNotBlank()) it.channelAvatarUrl else chAvatar,
                                channelId = if (it.channelId.isNotBlank()) it.channelId else channelId
                            )
                        }
                    videoTabVideos = videoTabVideos + uniqueNew
                    videoContinuationToken = nextResult.continuationToken
                } else {
                    videoContinuationToken = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMoreVideos = false
            }
        }
    }

    LaunchedEffect(channelId) {
        loadChannelData()
    }

    val listState = rememberLazyListState()

    // Pagination for channel videos on scroll
    LaunchedEffect(listState, selectedTabIndex, videoContinuationToken, isLoadingMoreVideos) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (!videoContinuationToken.isNullOrBlank() && !isLoadingMoreVideos) {
                    loadMoreVideos()
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = profile?.title?.ifBlank { initialChannelTitle } ?: initialChannelTitle,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { loadChannelData(isRefresh = true) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        tint = TextPrimary
                    )
                }

                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.nav_search),
                        tint = TextPrimary
                    )
                }

                IconButton(onClick = { /* Opsi lainnya */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = TextPrimary
                    )
                }
            }

            when {
                isLoading && profile == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        ChannelScreenSkeleton()
                    }
                }
                hasError && profile == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.channel_failed_load),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.channel_network_retry),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .clickable { loadChannelData() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.common_retry),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                else -> {
                    val chan = profile
                    val featured = chan?.featuredVideo ?: chan?.homeVideos?.firstOrNull() ?: videoTabVideos.firstOrNull()
                    val homeList = remember(chan?.homeVideos, videoTabVideos, featured?.id) {
                        val combined = mutableListOf<VideoItem>()
                        if (chan?.homeVideos?.isNotEmpty() == true) {
                            combined.addAll(chan.homeVideos)
                        }
                        if (videoTabVideos.isNotEmpty()) {
                            val existingIds = combined.map { it.id }.toSet()
                            combined.addAll(videoTabVideos.filter { it.id !in existingIds })
                        }
                        combined.filter { it.id != featured?.id }.distinctBy { it.id }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            // 1. BANNER
                            item(key = "channel_banner") {
                                if (chan?.bannerUrl?.isNotBlank() == true) {
                                    AsyncImage(
                                        model = chan.bannerUrl,
                                        contentDescription = "Banner ${chan.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 5.5f)
                                            .background(Color(0xFF1E1E1E))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF282828), Color(0xFF141414))
                                                )
                                            )
                                    )
                                }
                            }

                            // 2. CHANNEL PROFILE INFO & HEADER (Exact match to Photo 3)
                            item(key = "channel_profile_info") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Large Circular Avatar
                                        AsyncImage(
                                            model = chan?.avatarUrl,
                                            contentDescription = chan?.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF222222))
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            // Title + Official Artist / Verified Badge
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = chan?.title ?: initialChannelTitle,
                                                    color = TextPrimary,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.width(6.dp))

                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = "Official Artist Channel",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))

                                            // Handle
                                            if (chan?.handle?.isNotBlank() == true) {
                                                Text(
                                                    text = chan.handle,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            // Subscribers & Videos count
                                            val statsText = buildString {
                                                if (chan?.subscriberCountText?.isNotBlank() == true) {
                                                    append(chan.subscriberCountText)
                                                }
                                                if (chan?.videoCountText?.isNotBlank() == true) {
                                                    if (isNotEmpty()) append(" • ")
                                                    append(chan.videoCountText)
                                                }
                                            }

                                            if (statsText.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = statsText,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    // Description Preview (Cp official ...selengkapnya)
                                    if (chan?.description?.isNotBlank() == true) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { showDescriptionSheet = true }
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = chan.description.trim().take(70),
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = " ...selengkapnya",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // External Links
                                    if (chan?.externalLinksText?.isNotBlank() == true) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = chan.externalLinksText,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Big SUBSCRIBE Button (Exact Match to Photo 3)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSubscribed) Color(0xFF272727) else Color.White
                                            )
                                            .clickable {
                                                scope.launch {
                                                    if (isSubscribed) {
                                                        repository.unsubscribe(channelId)
                                                    } else {
                                                        repository.subscribe(
                                                            SubscriptionEntity(
                                                                channelId = channelId,
                                                                channelTitle = chan?.title ?: initialChannelTitle,
                                                                channelHandle = chan?.handle ?: "",
                                                                channelAvatarUrl = chan?.avatarUrl ?: "",
                                                                subscriberCountText = chan?.subscriberCountText ?: ""
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSubscribed) {
                                                Icon(
                                                    imageVector = Icons.Default.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.channel_subscribed),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.channel_subscribe),
                                                    color = Color.Black,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. TAB ROW (Beranda, Video)
                            item(key = "channel_tab_row") {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedTabIndex,
                                    containerColor = ObsidianDark,
                                    contentColor = TextPrimary,
                                    edgePadding = 16.dp,
                                    indicator = { tabPositions ->
                                        if (selectedTabIndex < tabPositions.size) {
                                            Box(
                                                modifier = Modifier
                                                    .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                                    .fillMaxWidth()
                                                    .height(2.5.dp)
                                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                    .background(Color.White)
                                            )
                                        }
                                    },
                                    divider = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(Color(0x22FFFFFF))
                                        )
                                    }
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTabIndex == index,
                                            onClick = { selectedTabIndex = index },
                                            text = {
                                                Text(
                                                    text = title,
                                                    color = if (selectedTabIndex == index) TextPrimary else TextSecondary,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // 4. TAB CONTENTS
                            if (selectedTabIndex == 1) {
                                // TAB "VIDEO" (Photo 2)
                                // Sort Chips: Terbaru, Populer, Terlama
                                item(key = "video_sort_chips") {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(sortChips) { chip ->
                                            val isSelected = selectedSortChip == chip.title
                                            val chipDisplayName = when (chip.title.trim().lowercase()) {
                                                "terbaru", "latest" -> stringResource(R.string.channel_sort_latest)
                                                "populer", "popular" -> stringResource(R.string.channel_sort_popular)
                                                "terlama", "oldest" -> stringResource(R.string.channel_sort_oldest)
                                                else -> chip.title
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) Color.White else Color(0xFF272727)
                                                    )
                                                    .clickable {
                                                        selectedSortChip = chip.title
                                                        if (chip.continuationToken.isNotBlank()) {
                                                            loadChannelData(sortToken = chip.continuationToken)
                                                            scope.launch {
                                                                listState.animateScrollToItem(2)
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = chipDisplayName,
                                                    color = if (isSelected) Color.Black else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                // Video List (Horizontal Items exactly matching Photo 2)
                                itemsIndexed(videoTabVideos, key = { index, video -> "channel_vid_${video.id}_$index" }) { _, video ->
                                    ChannelVideoHorizontalItem(
                                        video = video,
                                        onClick = { onVideoClick(video) }
                                    )
                                }

                                if (isLoadingMoreVideos) {
                                    item(key = "loading_more_videos") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = YouTubeRed,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                            } else {
                                // TAB "BERANDA" (Photo 1 & Photo 3)
                                if (featured != null) {
                                    // "Untuk Anda" Section
                                    item(key = "for_you_header") {
                                        Text(
                                            text = "Untuk Anda",
                                            color = TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                        )
                                    }

                                    // Featured video (Full width card matching official YouTube UI)
                                    item(key = "featured_for_you_video_${featured.id}") {
                                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            PhantomVideoCard(
                                                video = featured,
                                                onClick = { onVideoClick(featured) }
                                            )
                                        }
                                    }
                                }

                                if (homeList.isNotEmpty()) {
                                    item(key = "recent_releases_header") {
                                        Text(
                                            text = stringResource(R.string.channel_recent_videos),
                                            color = TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }

                                    itemsIndexed(homeList, key = { index, video -> "home_vid_${video.id}_$index" }) { _, video ->
                                        ChannelVideoHorizontalItem(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    }
                                }

                                if (isLoadingMoreVideos) {
                                    item(key = "home_loading_more_videos") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = YouTubeRed,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Floating animated refresh status pill
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isRefreshing,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .zIndex(10f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xE61E1E1E))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = YouTubeRed,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Memperbarui channel...",
                                        color = TextPrimary,
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

        // MODAL BOTTOM SHEET FOR CHANNEL DESCRIPTION (when clicking ...selengkapnya)
        if (showDescriptionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDescriptionSheet = false },
                containerColor = Color(0xFF1E1E1E),
                contentColor = TextPrimary,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.channel_about_title),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showDescriptionSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.player_close),
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = profile?.description ?: stringResource(R.string.channel_no_description),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (profile?.externalLinksText?.isNotBlank() == true) {
                        Text(
                            text = stringResource(R.string.channel_links),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = profile?.externalLinksText ?: "",
                            color = YouTubeRed,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Item video horizontal khusus Channel Page (Persis seperti pada Screenshot 2):
 * - Thumbnail di sisi kiri dengan badge durasi
 * - Judul video, views & waktu rilis di sisi kanan
 * - Tombol opsi ⋮ di ujung kanan
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelVideoHorizontalItem(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val repository = remember(context) {
        (context.applicationContext as? PhantomApp)?.repository
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail 16:9 on Left (Width ~136dp)
        Box(
            modifier = Modifier
                .width(136.dp)
                .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            // Duration badge in bottom right corner (e.g. 4.20)
            if (video.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Meta on Right
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
        ) {
            Text(
                text = video.title,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val metaText = InnerTubeHelpers.formatVideoMeta(
                channelTitle = "",
                viewCountText = video.viewCountText,
                publishedTimeText = video.publishedTimeText
            )

            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // More options icon
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(Color(0xFF212121))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Download",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        showDownloadSheet = true
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.player_share),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.player_share),
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, video.title)
                            putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtu.be/${video.id}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(shareIntent)
                    }
                )
            }
        }
    }

    if (showDownloadSheet && repository != null) {
        DownloadFormatSheet(
            video = video,
            repository = repository,
            onDismiss = { showDownloadSheet = false }
        )
    }
}

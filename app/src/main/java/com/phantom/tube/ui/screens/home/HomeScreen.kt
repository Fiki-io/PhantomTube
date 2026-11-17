package com.phantom.tube.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomErrorStateView
import com.phantom.tube.ui.components.PhantomTopBar
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.VideoFeedSkeleton
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, channelTitle: String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categories = listOf("Semua", "Trending", "Musik", "Gaming", "Berita", "Podcast", "Teknologi", "Animasi")
    var selectedCategory by remember { mutableStateOf("Semua") }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var continuationToken by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var videoToDownload by remember { mutableStateOf<VideoItem?>(null) }
    var homeRefreshNonce by remember { mutableIntStateOf(0) }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var feedJob by remember { mutableStateOf<Job?>(null) }
    var loadMoreJob by remember { mutableStateOf<Job?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    val homeScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isLoading && videos.isNotEmpty()) {
                    val deltaY = available.y
                    if (deltaY < -10f) {
                        if (isTopBarVisible) {
                            isTopBarVisible = false
                        }
                    } else if (deltaY > 10f) {
                        if (!isTopBarVisible) {
                            isTopBarVisible = true
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { isAtTop ->
                if (isAtTop) {
                    isTopBarVisible = true
                }
            }
    }
    val historyMap by remember(repository) {
        repository.getWatchHistory()
            .map { list ->
                list.associate { it.videoId to ((it.progressFraction * 50).toInt() / 50f) }
            }
            .distinctUntilChanged()
    }.collectAsState(initial = emptyMap())
    val defaultCountry = remember { com.phantom.tube.core.util.RegionHelper.resolveDefaultCountry() }
    val defaultLanguage = remember { com.phantom.tube.core.util.LocaleHelper.resolveDefaultLanguage() }
    val contentCountry by (repository.preferences?.contentCountry ?: kotlinx.coroutines.flow.MutableStateFlow(defaultCountry)).collectAsState()
    val appLanguage by (repository.preferences?.appLanguage ?: kotlinx.coroutines.flow.MutableStateFlow(defaultLanguage)).collectAsState()

    fun categoryToQuery(category: String, country: String): String {
        val suffix = when (country) {
            "GLOBAL" -> "global"
            "US" -> "usa"
            "JP" -> "japan"
            "KR" -> "korea"
            "GB" -> "uk"
            "ID" -> "indonesia"
            "ES" -> "spain"
            "RU" -> "russia"
            else -> country.lowercase()
        }
        val lang = appLanguage
        return when (category) {
            "Semua" -> if (country == "GLOBAL") "trending" else "trending $suffix"
            "Trending" -> when (lang) {
                "ja" -> "急上昇 $suffix"
                "es" -> "tendencias $suffix"
                "ru" -> "в тренде $suffix"
                "id" -> "trending viral $suffix"
                else -> "trending $suffix"
            }
            "Musik" -> when (lang) {
                "ja" -> "音楽 $suffix"
                "es" -> "música $suffix"
                "ru" -> "музыка $suffix"
                "id" -> "lagu hits $suffix"
                else -> "music $suffix"
            }
            "Gaming" -> when (lang) {
                "ja" -> "ゲーム $suffix"
                "es" -> "videojuegos $suffix"
                "ru" -> "игры $suffix"
                "id" -> "gaming $suffix"
                else -> "gaming $suffix"
            }
            "Berita" -> when (lang) {
                "ja" -> "ニュース $suffix"
                "es" -> "noticias $suffix"
                "ru" -> "новости $suffix"
                "id" -> "berita terkini $suffix"
                else -> "news $suffix"
            }
            "Podcast" -> "podcast $suffix"
            "Teknologi" -> when (lang) {
                "ja" -> "テクノロジー $suffix"
                "es" -> "tecnología $suffix"
                "ru" -> "технологии $suffix"
                "id" -> "teknologi gadget $suffix"
                else -> "technology $suffix"
            }
            "Animasi" -> when (lang) {
                "ja" -> "アニメ"
                "es" -> "animación"
                "ru" -> "аниме"
                "id" -> "animasi"
                else -> "animation"
            }
            else -> "$category $suffix"
        }
    }

    fun loadFeed(category: String, isRefresh: Boolean = false) {
        feedJob?.cancel()
        loadMoreJob?.cancel()
        isLoadingMore = false
        val prevLeadIds = if (isRefresh) {
            videos.take(4).map { it.id }.toSet()
        } else {
            emptySet()
        }
        val activeNonce = if (isRefresh) {
            homeRefreshNonce += 1
            homeRefreshNonce
        } else {
            homeRefreshNonce = 0
            0
        }
        feedJob = scope.launch {
            if (isRefresh) {
                isRefreshing = true
                isTopBarVisible = true
            } else {
                isLoading = true
            }
            hasError = false
            errorDetail = null
            try {
                val result = if (category == "Semua") {
                    repository.getHomeRecommendations(
                        historyIndex = 0,
                        refreshNonce = activeNonce,
                        excludeLeadIds = prevLeadIds
                    )
                } else {
                    repository.getFeedPage(
                        query = categoryToQuery(category, contentCountry),
                        refreshNonce = activeNonce,
                        excludeLeadIds = prevLeadIds
                    )
                }
                videos = result.videos
                continuationToken = if (category == "Semua") {
                    result.continuationToken ?: if (result.videos.isNotEmpty()) "feed_start" else null
                } else {
                    result.continuationToken
                }
                hasError = result.videos.isEmpty() || result.errorMessage != null
                errorDetail = result.errorMessage
                if (isRefresh) {
                    gridState.scrollToItem(0)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isRefresh) {
                    hasError = true
                    errorDetail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        val token = continuationToken ?: return
        if (isLoadingMore || isLoading || isRefreshing) return
        loadMoreJob?.cancel()
        val activeNonce = homeRefreshNonce
        loadMoreJob = scope.launch {
            isLoadingMore = true
            try {
                val result = if (selectedCategory == "Semua") {
                    if (token.startsWith("history_") || token.startsWith("smart_")) {
                        val nextIdx = token.substringAfter("_").toIntOrNull() ?: 1
                        repository.getHomeRecommendations(
                            historyIndex = nextIdx,
                            refreshNonce = activeNonce
                        )
                    } else if (token == "feed_start") {
                        repository.getFeedPage(
                            query = categoryToQuery("Semua", contentCountry),
                            refreshNonce = activeNonce
                        )
                    } else {
                        repository.getFeedPage(continuation = token)
                    }
                } else {
                    repository.getFeedPage(continuation = token)
                }

                if (result.videos.isNotEmpty()) {
                    val existingIds = videos.map { v -> v.id }.toSet()
                    val newVideos = result.videos.filterNot { v -> v.id in existingIds }
                    videos = videos + newVideos
                }
                continuationToken = if (result.videos.isEmpty()) null else result.continuationToken
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(selectedCategory, contentCountry, appLanguage) {
        loadFeed(selectedCategory)
    }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            loadFeed(selectedCategory, isRefresh = true)
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
        }
    }

    // Automatic infinite scroll engine: monitors scroll state and triggers seamlessly
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 0 && last >= total - 4) {
                    if (continuationToken != null && !isLoadingMore && !isLoading && !isRefreshing) {
                        loadMore()
                    }
                }
            }
    }

    LaunchedEffect(continuationToken, isLoadingMore) {
        if (!isLoadingMore && !isLoading && !isRefreshing && continuationToken != null) {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && last >= total - 4) {
                loadMore()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .nestedScroll(homeScrollConnection)
    ) {
        PhantomTopBar(
            selectedCategory = selectedCategory,
            categories = categories,
            onCategorySelected = {
                selectedCategory = it
                isTopBarVisible = true
            },
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            isVisible = isTopBarVisible
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            when {
                isLoading -> {
                    VideoFeedSkeleton()
                }
                hasError && videos.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PhantomErrorStateView(
                            title = stringResource(R.string.home_failed_load_video),
                            detail = errorDetail,
                            retryLabel = stringResource(R.string.common_retry),
                            onRetry = { loadFeed(selectedCategory) },
                            retryTestTag = "home_error_retry"
                        )
                    }
                }
                else -> {
                    val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = when {
                            maxWidth >= 840.dp -> 3
                            maxWidth >= 600.dp -> 2
                            else -> 1
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = bottomNavInset + 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = videos,
                                key = { index, video -> "home_${video.id}_$index" },
                                contentType = { _, _ -> "video_card" }
                            ) { _, video ->
                                PhantomVideoCard(
                                    video = video,
                                    progressFraction = historyMap[video.id] ?: 0f,
                                    onChannelClick = { chId ->
                                        onChannelClick(chId.ifBlank { video.channelTitle }, video.channelTitle)
                                    },
                                    onDownloadClick = { videoToDownload = it },
                                    onClick = { onVideoClick(video) }
                                )
                            }

                            if (isLoadingMore) {
                                item(
                                    key = "loading_more_indicator",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "loader"
                                ) {
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
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (continuationToken == null && videos.isNotEmpty()) {
                                item(
                                    key = "end_of_feed_spacer",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = YouTubeSurface,
                    contentColor = YouTubeRed
                )
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


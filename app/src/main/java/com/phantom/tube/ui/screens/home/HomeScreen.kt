package com.phantom.tube.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomTopBar
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.VideoFeedSkeleton
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
    var isTopBarVisible by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
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
    val contentCountry by (repository.preferences?.contentCountry ?: kotlinx.coroutines.flow.MutableStateFlow("ID")).collectAsState()

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
        val lang = repository.preferences?.appLanguage?.value ?: "en"
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
        scope.launch {
            if (isRefresh) {
                isRefreshing = true
                isTopBarVisible = true
            } else {
                isLoading = true
            }
            hasError = false
            try {
                val result = if (category == "Semua") {
                    repository.getHomeRecommendations(historyIndex = 0)
                } else {
                    repository.getFeedPage(query = categoryToQuery(category, contentCountry))
                }
                videos = result.videos
                continuationToken = result.continuationToken ?: if (result.videos.isNotEmpty()) "feed_start" else null
                hasError = result.videos.isEmpty()
                if (isRefresh) {
                    listState.scrollToItem(0)
                }
            } catch (e: Exception) {
                if (!isRefresh) {
                    hasError = true
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
        scope.launch {
            isLoadingMore = true
            try {
                val result = if (selectedCategory == "Semua") {
                    if (token.startsWith("history_") || token.startsWith("smart_")) {
                        val nextIdx = token.substringAfter("_").toIntOrNull() ?: 1
                        repository.getHomeRecommendations(historyIndex = nextIdx)
                    } else if (token == "feed_start") {
                        repository.getFeedPage(query = categoryToQuery("Semua", contentCountry))
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
                continuationToken = result.continuationToken ?: if (result.videos.isNotEmpty()) "feed_start" else null
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(selectedCategory, contentCountry) {
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
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
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
            val layoutInfo = listState.layoutInfo
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
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.home_failed_load_video),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PhantomIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.common_retry),
                            onClick = { loadFeed(selectedCategory) }
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
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
                                onClick = { onVideoClick(video) }
                            )
                        }

                        // Infinite scroll loader at bottom
                        if (isLoadingMore) {
                            item(key = "loading_more_indicator", contentType = "loader") {
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
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (continuationToken == null && videos.isNotEmpty()) {
                            item(key = "end_of_feed_spacer") {
                                Spacer(modifier = Modifier.height(16.dp))
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
}


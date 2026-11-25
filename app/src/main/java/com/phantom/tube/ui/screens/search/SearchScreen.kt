package com.phantom.tube.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.phantom.tube.ui.components.PhantomMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.core.image.PhantomAsyncImage
import com.phantom.tube.core.image.PhantomImageRequest
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurfaceLight
import com.phantom.tube.core.theme.phantomGlassSurface
import com.phantom.tube.core.util.openExternalUrl
import com.phantom.tube.data.model.SearchChannelItem
import com.phantom.tube.data.model.SuggestionItem
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomChip
import com.phantom.tube.ui.components.PhantomEmptyStateView
import com.phantom.tube.ui.components.PhantomErrorStateView
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.SearchChannelCard
import com.phantom.tube.ui.components.VideoFeedSkeleton
import com.phantom.tube.ui.components.iosBounceClick
import com.phantom.tube.ui.screens.player.components.DownloadFormatSheet
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SearchSuggestionItem(
    val query: String,
    val isHistory: Boolean,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val channelHandle: String? = null,
    val channelAvatarUrl: String? = null
) {
    val isChannel: Boolean get() = !channelAvatarUrl.isNullOrBlank() || !channelId.isNullOrBlank()
}

enum class SearchFilter(
    val param: String?,
    @StringRes val labelRes: Int
) {
    ALL(null, R.string.search_filter_all),
    VIDEO("EgIQAQ%3D%3D", R.string.search_filter_video),
    CHANNEL("EgIQAg%3D%3D", R.string.search_filter_channel),
    PLAYLIST("EgIQAz%3D%3D", R.string.search_filter_playlist),
    UPLOAD_DATE("CAISAhAB", R.string.search_filter_upload_date),
    VIEW_COUNT("CAMSAhAB", R.string.search_filter_view_count)
}

private enum class SearchContentState {
    LOADING,
    SUGGESTIONS,
    RESULTS,
    ERROR,
    EMPTY_RESULTS,
    HISTORY,
    IDLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, channelTitle: String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    isBackInterceptEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var isFilterBarVisible by remember { mutableStateOf(true) }
    var searchTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val searchQuery = searchTextFieldValue.text
    var lastSearchQuery by remember { mutableStateOf("") }
    var detailedSuggestions by remember { mutableStateOf<List<SuggestionItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var searchChannels by remember { mutableStateOf<List<SearchChannelItem>>(emptyList()) }
    var searchContinuationToken by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isFetchingSuggestions by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var videoToDownload by remember { mutableStateOf<VideoItem?>(null) }
    val searchListState = remember(lastSearchQuery) { LazyGridState() }
    val uniqueSearchChannels = remember(searchChannels) { searchChannels.distinctBy { it.id } }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (hasSearched && (searchResults.isNotEmpty() || searchChannels.isNotEmpty())) {
                    val deltaY = available.y
                    if (deltaY < -10f) {
                        if (isFilterBarVisible) {
                            isFilterBarVisible = false
                        }
                    } else if (deltaY > 10f) {
                        if (!isFilterBarVisible) {
                            isFilterBarVisible = true
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(searchListState) {
        snapshotFlow {
            searchListState.firstVisibleItemIndex == 0 && searchListState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { isAtTop ->
                if (isAtTop) {
                    isFilterBarVisible = true
                }
            }
    }

    val searchHistory by repository.getSearchHistory().collectAsState(initial = emptyList())
    val historyMap by remember(repository) {
        repository.getWatchHistory()
            .map { list ->
                list.associate { it.videoId to ((it.progressFraction * 50).toInt() / 50f) }
            }
            .distinctUntilChanged()
    }.collectAsState(initial = emptyMap())

    // Instant local history matching (0ms latency, always reactive while typing)
    val matchingHistory = remember(searchQuery, searchHistory) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) emptyList()
        else searchHistory.filter { it.query.contains(trimmed, ignoreCase = true) }
    }

    // Unified hybrid suggestions: matching channels first, then history, then YouTube suggestions
    val combinedSuggestions = remember(matchingHistory, detailedSuggestions, searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            val list = mutableListOf<SearchSuggestionItem>()
            val seen = mutableSetOf<String>()

            // 1. Channel suggestion first if returned by YouTube suggest
            detailedSuggestions.filter { it.isChannel }.forEach { ch ->
                val key = "channel_${ch.channelId ?: ch.channelTitle ?: ch.query}"
                if (seen.add(key)) {
                    seen.add(ch.query.trim().lowercase())
                    ch.channelTitle?.trim()?.lowercase()?.let { seen.add(it) }

                    list.add(
                        SearchSuggestionItem(
                            query = ch.query,
                            isHistory = false,
                            channelId = ch.channelId,
                            channelTitle = ch.channelTitle,
                            channelHandle = ch.channelHandle,
                            channelAvatarUrl = ch.channelAvatarUrl
                        )
                    )
                }
            }

            // 2. Matching history queries
            matchingHistory.forEach { item ->
                val q = item.query.trim()
                if (q.isNotEmpty() && seen.add(q.lowercase())) {
                    list.add(SearchSuggestionItem(query = q, isHistory = true))
                }
            }

            // 3. YouTube live suggestions
            detailedSuggestions.filter { !it.isChannel }.forEach { sugg ->
                val q = sugg.query.trim()
                if (q.isNotEmpty() && seen.add(q.lowercase())) {
                    list.add(SearchSuggestionItem(query = q, isHistory = false))
                }
            }

            list
        }
    }

    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var loadMoreJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    var wasImeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            wasImeVisible = true
        } else if (wasImeVisible) {
            wasImeVisible = false
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(searchListState.isScrollInProgress) {
        if (searchListState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun updateSearchInput(newText: String, requestKeyboardFocus: Boolean = false) {
        searchTextFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length)
        )
        if (requestKeyboardFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun executeSearch(query: String, filter: SearchFilter = selectedFilter) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        focusManager.clearFocus()
        keyboardController?.hide()

        val parsedLink = com.phantom.tube.core.util.extractYouTubeLinkFromText(trimmed)
            ?: if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true)
            ) {
                com.phantom.tube.core.util.parseAppLink(trimmed)
            } else null

        if (parsedLink != null) {
            when (parsedLink) {
                is com.phantom.tube.core.util.ParsedAppLink.YouTubeVideo -> {
                    val targetVideo = VideoItem(
                        id = parsedLink.videoId,
                        title = "",
                        channelTitle = "",
                        thumbnailUrl = "https://i.ytimg.com/vi/${parsedLink.videoId}/hqdefault.jpg",
                        playlistId = parsedLink.playlistId
                    )
                    if (parsedLink.startSeconds > 0) {
                        scope.launch {
                            repository.recordWatch(
                                video = targetVideo,
                                positionMs = parsedLink.startSeconds * 1000L,
                                durationMs = 0L
                            )
                            onVideoClick(targetVideo)
                        }
                    } else {
                        onVideoClick(targetVideo)
                    }
                    return
                }
                is com.phantom.tube.core.util.ParsedAppLink.YouTubeChannel -> {
                    onChannelClick(parsedLink.channelIdOrHandle, parsedLink.channelIdOrHandle)
                    return
                }
                is com.phantom.tube.core.util.ParsedAppLink.ExternalWebUrl -> {
                    context.openExternalUrl(parsedLink.url)
                    return
                }
            }
        }

        searchJob?.cancel()
        loadMoreJob?.cancel()
        isLoadingMore = false
        searchJob = scope.launch {
            repository.saveSearchQuery(trimmed)
            isSearching = true
            hasSearched = true
            searchError = null
            isFilterBarVisible = true
            detailedSuggestions = emptyList()
            searchContinuationToken = null
            lastSearchQuery = trimmed
            selectedFilter = filter
            try {
                val pageResult = repository.searchPage(query = trimmed, params = filter.param)
                searchResults = pageResult.videos
                searchChannels = pageResult.channels
                searchContinuationToken = pageResult.continuationToken
                searchError = if (pageResult.videos.isEmpty() && pageResult.channels.isEmpty()) {
                    pageResult.errorMessage
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchResults = emptyList()
                searchChannels = emptyList()
                searchContinuationToken = null
                searchError = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                isSearching = false
            }
        }
    }

    fun loadMoreSearch() {
        val token = searchContinuationToken ?: return
        if (isLoadingMore || isSearching) return
        loadMoreJob?.cancel()
        loadMoreJob = scope.launch {
            isLoadingMore = true
            try {
                val pageResult = repository.searchPage(continuation = token)
                if (pageResult.videos.isNotEmpty()) {
                    val existingIds = searchResults.map { it.id }.toSet()
                    val newVideos = pageResult.videos.filterNot { it.id in existingIds }
                    searchResults = searchResults + newVideos
                }
                searchContinuationToken = pageResult.continuationToken
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Infinite scroll listener: detects when user scrolls near the end of search results
    LaunchedEffect(searchListState, searchContinuationToken, isLoadingMore, isSearching) {
        snapshotFlow {
            val layoutInfo = searchListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (searchContinuationToken != null && !isLoadingMore && !isSearching) {
                    loadMoreSearch()
                }
            }
    }

    LaunchedEffect(searchQuery, hasSearched) {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotBlank() && !hasSearched) {
            isFetchingSuggestions = true
            delay(120) // Fast 120ms debounce for responsive typing
            try {
                val fetched = repository.getDetailedSuggestions(trimmed)
                detailedSuggestions = fetched
            } catch (e: Exception) {
                // Keep suggestions if network glitches
            } finally {
                isFetchingSuggestions = false
            }
        } else if (trimmed.isBlank()) {
            detailedSuggestions = emptyList()
            isFetchingSuggestions = false
        }
    }

    BackHandler(enabled = isBackInterceptEnabled) {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (hasSearched || searchQuery.isNotEmpty()) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            isSearching = false
            isLoadingMore = false
            searchTextFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
            hasSearched = false
            searchError = null
            searchResults = emptyList()
            searchChannels = emptyList()
            detailedSuggestions = emptyList()
            searchContinuationToken = null
            lastSearchQuery = ""
            selectedFilter = SearchFilter.ALL
            isFilterBarVisible = true
        } else {
            onBackClick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .nestedScroll(nestedScrollConnection)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        // YouTube Search Input Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhantomIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                modifier = Modifier.testTag("search_back_btn"),
                size = 48.dp,
                iconSize = 20.dp,
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onBackClick()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Translucent Glass Search Bar
            val searchBarShape = remember { RoundedCornerShape(24.dp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .phantomGlassSurface(shape = searchBarShape, shadowElevation = 6.dp)
                    .padding(start = 14.dp, end = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchTextFieldValue,
                        onValueChange = {
                            searchTextFieldValue = it
                            hasSearched = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                            .focusRequester(focusRequester)
                            .testTag("search_input"),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(YouTubeRed),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = PhantomMotion.PopScaleEnter,
                        exit = PhantomMotion.PopScaleExit
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("search_clear_btn")
                                .iosBounceClick(scaleDown = 0.86f) {
                                    searchJob?.cancel()
                                    loadMoreJob?.cancel()
                                    isSearching = false
                                    isLoadingMore = false
                                    updateSearchInput("")
                                    detailedSuggestions = emptyList()
                                    hasSearched = false
                                    searchError = null
                                    searchResults = emptyList()
                                    searchChannels = emptyList()
                                    searchContinuationToken = null
                                    lastSearchQuery = ""
                                    selectedFilter = SearchFilter.ALL
                                    isFilterBarVisible = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.search_clear_query),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasSearched && isFilterBarVisible,
            enter = PhantomMotion.TopBarEnter,
            exit = PhantomMotion.TopBarExit
        ) {
            val filterScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(filterScrollState)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchFilter.entries.forEach { filter ->
                    PhantomChip(
                        text = stringResource(filter.labelRes),
                        isSelected = filter == selectedFilter,
                        onClick = {
                            if (selectedFilter != filter) {
                                executeSearch(lastSearchQuery, filter)
                            }
                        }
                    )
                }
            }
        }

        if (!hasSearched) {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Content: Loading, History, Suggestions, or Results
        val searchContentState = when {
            isSearching -> SearchContentState.LOADING
            !hasSearched && searchQuery.isNotBlank() -> SearchContentState.SUGGESTIONS
            hasSearched && (searchResults.isNotEmpty() || searchChannels.isNotEmpty()) -> SearchContentState.RESULTS
            hasSearched && searchError != null -> SearchContentState.ERROR
            hasSearched -> SearchContentState.EMPTY_RESULTS
            searchQuery.isBlank() && searchHistory.isNotEmpty() -> SearchContentState.HISTORY
            else -> SearchContentState.IDLE
        }

        AnimatedContent(
            targetState = searchContentState,
            transitionSpec = { PhantomMotion.ContentStateTransition },
            modifier = Modifier.fillMaxSize(),
            label = "search_content_state"
        ) { state ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    SearchContentState.LOADING -> {
                        VideoFeedSkeleton()
                    }
                    SearchContentState.SUGGESTIONS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            if (isFetchingSuggestions && combinedSuggestions.isEmpty()) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = YouTubeRed,
                                    trackColor = Color.Transparent
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomNavInset + 140.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (combinedSuggestions.isEmpty()) {
                                    item(key = "direct_search") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .iosBounceClick(scaleDown = 0.985f) {
                                                    executeSearch(searchQuery)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Text(
                                                text = "${stringResource(R.string.search_for_prefix)} \"${searchQuery.trim()}\"",
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    itemsIndexed(
                                        items = combinedSuggestions,
                                        key = { index, item -> "${item.isHistory}_${item.channelId}_${item.query}_$index" },
                                        contentType = { _, item -> if (item.isChannel) "channel_suggestion" else "query_suggestion" }
                                    ) { _, item ->
                                        if (item.isChannel) {
                                            // Channel suggestion row with circular avatar
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .iosBounceClick(scaleDown = 0.985f) {
                                                        focusManager.clearFocus()
                                                        keyboardController?.hide()
                                                        if (!item.channelId.isNullOrBlank()) {
                                                            onChannelClick(item.channelId, item.channelTitle ?: item.query)
                                                        } else {
                                                            val target = item.channelTitle ?: item.query
                                                            updateSearchInput(target)
                                                            executeSearch(target)
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val suggAvatar = remember(item.channelAvatarUrl, item.channelId, item.channelTitle) {
                                                    val clean = InnerTubeHelpers.normalizeUrl(item.channelAvatarUrl)
                                                    clean.ifBlank { ChannelAvatarCache.get(item.channelId, item.channelTitle) }
                                                }
                                                val suggAvatarRequest = remember(suggAvatar, context) {
                                                    PhantomImageRequest.Builder(context)
                                                        .data(suggAvatar)
                                                        .size(96, 96)
                                                        .allowRgb565(true)
                                                        .build()
                                                }
                                                PhantomAsyncImage(
                                                    model = suggAvatarRequest,
                                                    contentDescription = item.channelTitle ?: item.query,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(YouTubeSurfaceLight)
                                                )

                                                Spacer(modifier = Modifier.width(14.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.channelTitle ?: item.query,
                                                        color = TextPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val channelBadge = stringResource(R.string.search_channel_badge)
                                                    val subline = if (!item.channelHandle.isNullOrBlank()) {
                                                        "${item.channelHandle} • $channelBadge"
                                                    } else {
                                                        channelBadge
                                                    }
                                                    Text(
                                                        text = subline,
                                                        color = TextSecondary,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .iosBounceClick(scaleDown = 0.86f) {
                                                            updateSearchInput(item.channelTitle ?: item.query, requestKeyboardFocus = true)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.NorthWest,
                                                        contentDescription = stringResource(R.string.search_open_channel),
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .iosBounceClick(scaleDown = 0.985f) {
                                                        updateSearchInput(item.query)
                                                        executeSearch(item.query)
                                                    }
                                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isHistory) Icons.Default.History else Icons.Default.Search,
                                                    contentDescription = stringResource(if (item.isHistory) R.string.search_history_tag else R.string.search_suggestion_tag),
                                                    tint = if (item.isHistory) TextSecondary else TextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    text = item.query,
                                                    color = if (item.isHistory) TextPrimary else TextPrimary.copy(alpha = 0.95f),
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .iosBounceClick(scaleDown = 0.86f) {
                                                            updateSearchInput(item.query, requestKeyboardFocus = true)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.NorthWest,
                                                        contentDescription = stringResource(R.string.search_insert_query),
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                if (item.isHistory) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(CircleShape)
                                                            .iosBounceClick(scaleDown = 0.86f) {
                                                                scope.launch {
                                                                    repository.deleteSearchQuery(item.query)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = stringResource(R.string.search_clear_query),
                                                            tint = TextMuted,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchContentState.RESULTS -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columns = when {
                                maxWidth >= 840.dp -> 3
                                maxWidth >= 600.dp -> 2
                                else -> 1
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = searchListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = bottomNavInset + 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. YouTube-style Channel Card at top of search results
                                if (uniqueSearchChannels.isNotEmpty()) {
                                    val channelSpan = if (uniqueSearchChannels.size >= 2 && columns >= 2) 1 else columns
                                    gridItemsIndexed(
                                        items = uniqueSearchChannels,
                                        key = { index, ch -> "search_ch_${ch.id}_$index" },
                                        span = { _, _ -> GridItemSpan(channelSpan) },
                                        contentType = { _, _ -> "channel_card" }
                                    ) { _, channel ->
                                        SearchChannelCard(
                                            channel = channel,
                                            repository = repository,
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                onChannelClick(channel.id, channel.title)
                                            }
                                        )
                                    }
                                }

                                // 2. Video items
                                gridItemsIndexed(
                                    items = searchResults,
                                    key = { index, video -> "search_${video.id}_$index" },
                                    contentType = { _, _ -> "video_card" }
                                ) { _, video ->
                                    PhantomVideoCard(
                                        video = video,
                                        progressFraction = historyMap[video.id] ?: 0f,
                                        onChannelClick = { chId ->
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onChannelClick(chId.ifBlank { video.channelTitle }, video.channelTitle)
                                        },
                                        onDownloadClick = { videoToDownload = it },
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onVideoClick(video)
                                        }
                                    )
                                }

                                if (isLoadingMore) {
                                    item(
                                        key = "loading_more_search",
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
                                                    .phantomGlassSurface(shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp)
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
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchContentState.ERROR -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PhantomErrorStateView(
                                title = stringResource(R.string.home_failed_load_video),
                                detail = searchError,
                                retryLabel = stringResource(R.string.common_retry),
                                onRetry = {
                                    executeSearch(
                                        query = lastSearchQuery.ifBlank { searchQuery },
                                        filter = selectedFilter
                                    )
                                },
                                retryTestTag = "search_error_retry"
                            )
                        }
                    }
                    SearchContentState.EMPTY_RESULTS -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PhantomEmptyStateView(
                                icon = Icons.Rounded.SearchOff,
                                title = stringResource(R.string.search_no_results_for, searchQuery.trim())
                            )
                        }
                    }
                    SearchContentState.HISTORY -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.search_history_title),
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .iosBounceClick(scaleDown = 0.92f) {
                                            scope.launch {
                                                repository.clearSearchHistory()
                                            }
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_history_clear),
                                        color = YouTubeRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = bottomNavInset + 140.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(
                                    items = searchHistory,
                                    key = { it.query },
                                    contentType = { "history_item" }
                                ) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .iosBounceClick(scaleDown = 0.985f) {
                                                updateSearchInput(item.query)
                                                executeSearch(item.query)
                                            }
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = item.query,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .iosBounceClick(scaleDown = 0.86f) {
                                                    updateSearchInput(item.query, requestKeyboardFocus = true)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NorthWest,
                                                contentDescription = stringResource(R.string.search_insert_query),
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .iosBounceClick(scaleDown = 0.86f) {
                                                    scope.launch {
                                                        repository.deleteSearchQuery(item.query)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_delete),
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchContentState.IDLE -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PhantomEmptyStateView(
                                icon = Icons.Default.Search,
                                title = stringResource(R.string.empty_search)
                            )
                        }
                    }
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


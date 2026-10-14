package com.phantom.tube.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.phantom.tube.R
import com.phantom.tube.core.theme.ObsidianDark
import com.phantom.tube.core.theme.SystemStatusBarColor
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.theme.YouTubeSurface
import com.phantom.tube.data.model.SearchChannelItem
import com.phantom.tube.data.model.SuggestionItem
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.PhantomIconButton
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.SearchChannelCard
import com.phantom.tube.ui.components.VideoFeedSkeleton
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
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

@Composable
fun SearchScreen(
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, channelTitle: String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val searchListState = remember(lastSearchQuery) { LazyListState() }

    val searchHistory by repository.getSearchHistory().collectAsState(initial = emptyList())
    val watchHistory by repository.getWatchHistory().collectAsState(initial = emptyList())
    val historyMap = remember(watchHistory) {
        watchHistory.associate { it.videoId to it.progressFraction }
    }

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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        keyboardController?.hide()
        scope.launch {
            repository.saveSearchQuery(trimmed)
            isSearching = true
            hasSearched = true
            detailedSuggestions = emptyList()
            searchContinuationToken = null
            lastSearchQuery = trimmed
            try {
                val pageResult = repository.searchPage(query = trimmed)
                searchResults = pageResult.videos
                searchChannels = pageResult.channels
                searchContinuationToken = pageResult.continuationToken
            } catch (e: Exception) {
                searchResults = emptyList()
                searchChannels = emptyList()
                searchContinuationToken = null
            } finally {
                isSearching = false
            }
        }
    }

    fun loadMoreSearch() {
        val token = searchContinuationToken ?: return
        if (isLoadingMore || isSearching) return
        scope.launch {
            isLoadingMore = true
            try {
                val pageResult = repository.searchPage(continuation = token)
                if (pageResult.videos.isNotEmpty()) {
                    val existingIds = searchResults.map { it.id }.toSet()
                    val newVideos = pageResult.videos.filterNot { it.id in existingIds }
                    searchResults = searchResults + newVideos
                }
                searchContinuationToken = pageResult.continuationToken
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

    BackHandler {
        if (hasSearched || searchQuery.isNotEmpty()) {
            searchTextFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
            hasSearched = false
            searchResults = emptyList()
            searchChannels = emptyList()
            detailedSuggestions = emptyList()
            keyboardController?.hide()
        } else {
            onBackClick()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
    ) {
        // Strip bilah status hitam (di atas input pencarian, bawah baterai)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(SystemStatusBarColor)
        )

        // YouTube Search Input Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhantomIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                size = 38.dp,
                iconSize = 20.dp,
                onClick = onBackClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Standard YouTube Search Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(YouTubeSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
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
                            .focusRequester(focusRequester),
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

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.search_clear_query),
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    updateSearchInput("")
                                    detailedSuggestions = emptyList()
                                    hasSearched = false
                                    searchResults = emptyList()
                                    searchChannels = emptyList()
                                    searchContinuationToken = null
                                    lastSearchQuery = ""
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content: Loading, History, Suggestions, or Results
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching -> {
                    VideoFeedSkeleton()
                }
                // 2. Actively typing (searchQuery is not blank) -> Show Hybrid Suggestions (Instant local history + Live YouTube suggestions)
                !hasSearched && searchQuery.isNotBlank() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (combinedSuggestions.isEmpty()) {
                                item(key = "direct_search") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
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
                                            text = "Telusuri \"${searchQuery.trim()}\"",
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
                                                .clickable {
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
                                            AsyncImage(
                                                model = suggAvatar,
                                                contentDescription = item.channelTitle ?: item.query,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF262626))
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
                                                val subline = if (!item.channelHandle.isNullOrBlank()) {
                                                    "${item.channelHandle} • Saluran"
                                                } else {
                                                    "Channel • Saluran"
                                                }
                                                Text(
                                                    text = subline,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Icon(
                                                imageVector = Icons.Default.NorthWest,
                                                contentDescription = "Buka channel",
                                                tint = TextMuted,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        updateSearchInput(item.channelTitle ?: item.query, requestKeyboardFocus = true)
                                                    }
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    updateSearchInput(item.query)
                                                    executeSearch(item.query)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.NorthWest,
                                                contentDescription = stringResource(R.string.search_insert_query),
                                                tint = TextMuted,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        updateSearchInput(item.query, requestKeyboardFocus = true)
                                                    }
                                            )
                                            if (item.isHistory) {
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.search_clear_query),
                                                    tint = TextMuted,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            scope.launch {
                                                                repository.deleteSearchQuery(item.query)
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                hasSearched && (searchResults.isNotEmpty() || searchChannels.isNotEmpty()) -> {
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. YouTube-style Channel Card at top of search results
                        if (searchChannels.isNotEmpty()) {
                            itemsIndexed(
                                items = searchChannels.distinctBy { it.id },
                                key = { index, ch -> "search_ch_${ch.id}_$index" },
                                contentType = { _, _ -> "channel_card" }
                            ) { _, channel ->
                                SearchChannelCard(
                                    channel = channel,
                                    repository = repository,
                                    onClick = {
                                        onChannelClick(channel.id, channel.title)
                                    }
                                )
                            }
                        }

                        // 2. Video items
                        itemsIndexed(
                            items = searchResults,
                            key = { index, video -> "search_${video.id}_$index" },
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

                        if (isLoadingMore) {
                            item(key = "loading_more_search", contentType = "loader") {
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
                hasSearched && searchResults.isEmpty() && searchChannels.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.search_no_results_for, searchQuery.trim()),
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
                searchQuery.isBlank() && searchHistory.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                            Text(
                                text = stringResource(R.string.search_history_clear),
                                color = YouTubeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        scope.launch {
                                            repository.clearSearchHistory()
                                        }
                                    }
                                    .padding(4.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            updateSearchInput(item.query)
                                            executeSearch(item.query)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = stringResource(R.string.search_insert_query),
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                updateSearchInput(item.query, requestKeyboardFocus = true)
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_delete),
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                scope.launch {
                                                    repository.deleteSearchQuery(item.query)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.empty_search),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


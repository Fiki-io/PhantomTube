package com.phantom.tube.ui.screens.channel

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.tube.R
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.theme.GlassBorderLight
import com.phantom.tube.core.theme.GlassSurface
import com.phantom.tube.core.theme.TextMuted
import com.phantom.tube.core.theme.TextPrimary
import com.phantom.tube.core.theme.TextSecondary
import com.phantom.tube.core.theme.YouTubeDark
import com.phantom.tube.core.theme.YouTubeRed
import com.phantom.tube.core.util.safeStartActivity
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.repository.PhantomRepository
import com.phantom.tube.ui.components.ChannelScreenSkeleton
import com.phantom.tube.ui.components.PhantomVideoCard
import com.phantom.tube.ui.components.animateItemEntrance
import com.phantom.tube.ui.screens.channel.components.ChannelAboutBottomSheet
import com.phantom.tube.ui.screens.channel.components.ChannelAboutCard
import com.phantom.tube.ui.screens.channel.components.ChannelCompactVideoRow
import com.phantom.tube.ui.screens.channel.components.ChannelEmptyStateBox
import com.phantom.tube.ui.screens.channel.components.ChannelHeaderSection
import com.phantom.tube.ui.screens.channel.components.ChannelSortFilterBar
import com.phantom.tube.ui.screens.channel.components.ChannelSortOrder
import com.phantom.tube.ui.screens.channel.components.ChannelTabBar
import com.phantom.tube.ui.screens.channel.components.ChannelTabType
import com.phantom.tube.ui.screens.channel.components.ChannelTopBar
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(
    channelId: String,
    initialChannelTitle: String = "",
    repository: PhantomRepository,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (channelId: String, channelTitle: String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember(channelId) { mutableStateOf<ChannelProfile?>(null) }
    var isLoading by remember(channelId) { mutableStateOf(true) }
    var errorMessage by remember(channelId) { mutableStateOf<String?>(null) }

    var selectedTab by remember(channelId) { mutableStateOf(ChannelTabType.HOME) }
    var selectedSort by remember(channelId) { mutableStateOf(ChannelSortOrder.LATEST) }
    var isSearchActive by remember(channelId) { mutableStateOf(false) }
    var searchQuery by remember(channelId) { mutableStateOf("") }
    var showAboutSheet by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }

    val resolvedChannelId = profile?.id?.ifBlank { channelId } ?: channelId
    val isSubscribed by repository.isSubscribed(resolvedChannelId).collectAsState(initial = false)

    fun loadChannel() {
        if (channelId.isBlank()) return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val loaded = repository.getChannel(channelId = channelId)
                if (loaded != null) {
                    profile = loaded
                } else {
                    errorMessage = context.getString(R.string.channel_load_failed)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.channel_load_failed)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(channelId) {
        loadChannel()
    }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else if (selectedTab != ChannelTabType.HOME) {
            selectedTab = ChannelTabType.HOME
        } else {
            onBackClick()
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val currentProfile = profile
    val availableTabs = remember {
        listOf(ChannelTabType.HOME, ChannelTabType.VIDEOS, ChannelTabType.ABOUT)
    }

    val allChannelVideos = remember(currentProfile) {
        val base = currentProfile?.videos.orEmpty()
        if (base.isNotEmpty()) base else currentProfile?.homeVideos.orEmpty()
    }

    val filteredVideos = remember(allChannelVideos, selectedSort, searchQuery) {
        val queried = if (searchQuery.isBlank()) {
            allChannelVideos
        } else {
            allChannelVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        when (selectedSort) {
            ChannelSortOrder.LATEST -> queried
            ChannelSortOrder.POPULAR -> queried.sortedByDescending { parseViewsEstimate(it.viewCountText) }
            ChannelSortOrder.OLDEST -> queried.asReversed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YouTubeDark)
    ) {
        com.phantom.tube.ui.components.PhantomStatusBarStrip()

        ChannelTopBar(
            title = currentProfile?.title?.ifBlank { initialChannelTitle } ?: initialChannelTitle,
            isSearchActive = isSearchActive,
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            },
            onShareChannel = {
                val id = currentProfile?.id?.ifBlank { channelId } ?: channelId
                val shareUrl = if (id.startsWith("UC")) {
                    "https://www.youtube.com/channel/$id"
                } else {
                    "https://www.youtube.com/$id"
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                }
                context.safeStartActivity(Intent.createChooser(shareIntent, null))
            },
            onBack = onBackClick
        )

        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorderLight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(YouTubeRed),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.search_clear_query),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        when {
            isLoading && currentProfile == null -> {
                ChannelScreenSkeleton()
            }

            errorMessage != null && currentProfile == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = errorMessage ?: stringResource(R.string.channel_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.channel_load_failed_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(YouTubeRed)
                                .clickable { loadChannel() }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_retry),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            currentProfile != null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = navBarPadding + 32.dp)
                ) {
                    item(key = "channel_header") {
                        ChannelHeaderSection(
                            profile = currentProfile,
                            isSubscribed = isSubscribed,
                            onToggleSubscribe = {
                                scope.launch {
                                    if (isSubscribed) {
                                        repository.unsubscribe(resolvedChannelId)
                                    } else {
                                        repository.subscribe(
                                            SubscriptionEntity(
                                                channelId = resolvedChannelId,
                                                channelTitle = currentProfile.title.ifBlank { initialChannelTitle },
                                                channelHandle = currentProfile.handle,
                                                channelAvatarUrl = currentProfile.avatarUrl,
                                                subscriberCountText = currentProfile.subscriberCountText
                                            )
                                        )
                                    }
                                }
                            },
                            onOpenAboutSheet = { showAboutSheet = true }
                        )
                    }

                    item(key = "channel_tabs") {
                        ChannelTabBar(
                            tabs = availableTabs,
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it }
                        )
                    }

                    when (selectedTab) {
                        ChannelTabType.HOME -> {
                            val featured = currentProfile.featuredVideo ?: filteredVideos.firstOrNull()
                            if (featured != null) {
                                item(key = "home_featured_${featured.id}") {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        Text(
                                            text = stringResource(R.string.channel_for_you),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                        PhantomVideoCard(
                                            video = featured,
                                            onClick = { onVideoClick(featured) }
                                        )
                                    }
                                }
                            }

                            val recentVideos = if (featured != null) {
                                filteredVideos.filter { it.id != featured.id }
                            } else {
                                filteredVideos
                            }

                            if (recentVideos.isNotEmpty()) {
                                item(key = "home_recent_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.channel_recent_videos),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = stringResource(R.string.channel_tab_videos),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = YouTubeRed,
                                            modifier = Modifier.clickable {
                                                selectedTab = ChannelTabType.VIDEOS
                                            }
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = recentVideos.take(15),
                                    key = { idx: Int, v: VideoItem -> "home_v_${v.id}_$idx" }
                                ) { idx: Int, video: VideoItem ->
                                    Box(modifier = Modifier.animateItemEntrance(idx)) {
                                        ChannelCompactVideoRow(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    }
                                }
                            } else if (featured == null) {
                                item {
                                    ChannelEmptyStateBox(stringResource(R.string.channel_no_videos))
                                }
                            }
                        }

                        ChannelTabType.VIDEOS -> {
                            item(key = "videos_sort_bar") {
                                ChannelSortFilterBar(
                                    selectedSort = selectedSort,
                                    onSelectSort = { selectedSort = it }
                                )
                            }
                            if (filteredVideos.isEmpty()) {
                                item {
                                    ChannelEmptyStateBox(stringResource(R.string.channel_no_videos))
                                }
                            } else {
                                itemsIndexed(
                                    items = filteredVideos,
                                    key = { idx: Int, v: VideoItem -> "tab_v_${v.id}_$idx" }
                                ) { idx: Int, video: VideoItem ->
                                    Box(modifier = Modifier.animateItemEntrance(idx)) {
                                        ChannelCompactVideoRow(
                                            video = video,
                                            onClick = { onVideoClick(video) }
                                        )
                                    }

                                    if (idx >= filteredVideos.size - 3 &&
                                        !isLoadingMore &&
                                        !currentProfile.continuationToken.isNullOrBlank() &&
                                        searchQuery.isBlank()
                                    ) {
                                        LaunchedEffect(currentProfile.continuationToken) {
                                            val token = currentProfile.continuationToken ?: return@LaunchedEffect
                                            isLoadingMore = true
                                            try {
                                                val page = repository.getChannelVideosContinuation(token)
                                                val existingIds = currentProfile.videos.map { it.id }.toSet()
                                                val newVideos = page.videos.filter { it.id !in existingIds }
                                                profile = currentProfile.copy(
                                                    videos = currentProfile.videos + newVideos,
                                                    continuationToken = if (newVideos.isEmpty()) null else page.continuationToken
                                                )
                                            } catch (_: Exception) {
                                            } finally {
                                                isLoadingMore = false
                                            }
                                        }
                                    }
                                }
                                if (isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = YouTubeRed,
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ChannelTabType.ABOUT -> {
                            item(key = "about_card") {
                                ChannelAboutCard(
                                    profile = currentProfile,
                                    onChannelClick = onChannelClick,
                                    onVideoLinkClick = onVideoClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutSheet && currentProfile != null) {
        ChannelAboutBottomSheet(
            profile = currentProfile,
            onChannelClick = { chId, chTitle ->
                showAboutSheet = false
                onChannelClick(chId, chTitle)
            },
            onVideoLinkClick = { video ->
                showAboutSheet = false
                onVideoClick(video)
            },
            onDismiss = { showAboutSheet = false }
        )
    }
}

private fun parseViewsEstimate(viewsText: String): Long {
    if (viewsText.isBlank()) return 0L
    val normalized = viewsText.uppercase().replace(",", ".").trim()
    val numberRegex = Regex("""(\d+(?:\.\d+)?)\s*([KMBJT]?)""")
    val match = numberRegex.find(normalized) ?: return 0L
    val base = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val suffix = match.groupValues[2]
    val multiplier = when (suffix) {
        "K", "RB" -> 1_000.0
        "M", "JT" -> 1_000_000.0
        "B", "MILYAR" -> 1_000_000_000.0
        else -> 1.0
    }
    return (base * multiplier).toLong()
}

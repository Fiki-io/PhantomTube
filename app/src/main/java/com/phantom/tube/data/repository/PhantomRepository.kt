package com.phantom.tube.data.repository

import com.phantom.tube.core.database.DownloadDao
import com.phantom.tube.core.database.DownloadEntity
import com.phantom.tube.core.database.FavoriteDao
import com.phantom.tube.core.database.FavoriteEntity
import com.phantom.tube.core.database.SearchHistoryDao
import com.phantom.tube.core.database.SearchHistoryEntity
import com.phantom.tube.core.database.SubscriptionDao
import com.phantom.tube.core.database.SubscriptionEntity
import com.phantom.tube.core.database.WatchHistoryDao
import com.phantom.tube.core.database.WatchHistoryEntity
import com.phantom.tube.data.innertube.InnerTubeClient
import com.phantom.tube.data.model.ChannelProfile
import com.phantom.tube.data.model.CommentsResult
import com.phantom.tube.data.model.FeedResult
import com.phantom.tube.data.model.NextQueue
import com.phantom.tube.data.model.SponsorSegment
import com.phantom.tube.data.model.VideoItem
import com.phantom.tube.data.settings.PhantomPreferences
import com.phantom.tube.data.sponsorblock.SponsorBlockClient
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache

class PhantomRepository(
    private val innerTubeClient: InnerTubeClient = InnerTubeClient(),
    private val sponsorBlockClient: SponsorBlockClient = SponsorBlockClient(),
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadDao: DownloadDao? = null,
    val preferences: PhantomPreferences? = null
) {
    init {
        innerTubeClient.regionCodeProvider = {
            preferences?.contentCountry?.value ?: "GLOBAL"
        }
        innerTubeClient.languageCodeProvider = {
            preferences?.appLanguage?.value ?: "en"
        }
    }
    suspend fun getHomeRecommendations(
        historyIndex: Int = 0,
        continuation: String? = null
    ): FeedResult {
        // If we have a direct continuation token from YouTube, load next page
        if (!continuation.isNullOrBlank() && !continuation.startsWith("history_") && !continuation.startsWith("smart_")) {
            val pageResult = innerTubeClient.fetchFeedPage(continuation = continuation)
            if (pageResult.videos.isNotEmpty()) {
                return pageResult
            }
        }

        val country = preferences?.contentCountry?.value ?: "ID"
        val defaultQuery = when (country) {
            "GLOBAL" -> "trending global"
            "US" -> "trending us"
            "JP" -> "trending japan"
            "KR" -> "trending korea"
            "GB" -> "trending uk"
            else -> "trending indonesia"
        }

        // 1. Check local watch history for smart weighted recommendation
        val recentWatched = watchHistoryDao.getRecentWatched(limit = 30)
        if (recentWatched.isEmpty()) {
            return innerTubeClient.fetchFeedPage(query = defaultQuery, continuation = continuation)
        }

        // 2. Compute affinity clusters based on channel frequency, recency decay, and watch duration
        data class AffinityCluster(
            val key: String,
            val channelTitle: String,
            val channelId: String,
            var score: Double,
            var watchCount: Int,
            var bestVideo: WatchHistoryEntity,
            var latestWatchedAt: Long
        )

        val clusters = mutableMapOf<String, AffinityCluster>()
        val totalHistorySize = recentWatched.size.toDouble().coerceAtLeast(1.0)

        recentWatched.forEachIndexed { index, item ->
            val key = if (item.channelId.isNotBlank()) item.channelId else item.channelTitle.trim().lowercase()
            // Recency factor: newer items have higher weight (1.0 down to 0.4)
            val recencyWeight = 1.0 - (index.toDouble() / totalHistorySize) * 0.6

            // Completion weight: if watched >= 50% duration, factor 1.25; if < 10% (quick click), factor 0.35
            val progressWeight = when {
                item.durationMs > 0 && item.progressFraction >= 0.5f -> 1.25
                item.durationMs > 0 && item.progressFraction < 0.10f -> 0.35
                else -> 1.0
            }

            val itemScore = recencyWeight * progressWeight

            val existing = clusters[key]
            if (existing != null) {
                existing.score += itemScore
                existing.watchCount += 1
                if (item.watchedAt > existing.latestWatchedAt) {
                    existing.latestWatchedAt = item.watchedAt
                    existing.bestVideo = item
                }
            } else {
                clusters[key] = AffinityCluster(
                    key = key,
                    channelTitle = item.channelTitle,
                    channelId = item.channelId,
                    score = itemScore,
                    watchCount = 1,
                    bestVideo = item,
                    latestWatchedAt = item.watchedAt
                )
            }
        }

        val sortedClusters = clusters.values.sortedByDescending { it.score }
        if (sortedClusters.isEmpty()) {
            return innerTubeClient.fetchFeedPage(query = defaultQuery, continuation = continuation)
        }

        // If the user scrolled past all available history clusters, fallback to trending
        if (historyIndex >= sortedClusters.size) {
            return innerTubeClient.fetchFeedPage(query = defaultQuery, continuation = continuation)
        }

        // 3. Determine Seed Targets for this page:
        // Target A: The primary affinity cluster for this page slot
        val primaryCluster = sortedClusters[historyIndex]
        val targetA = primaryCluster.bestVideo

        // Target B: Exploration or Secondary cluster
        val latestVideo = recentWatched.first()
        val targetB: WatchHistoryEntity? = if (historyIndex == 0) {
            // On page 0: if latest watched video is from a different channel/topic, blend it as exploration!
            if (!latestVideo.channelTitle.equals(targetA.channelTitle, ignoreCase = true) &&
                latestVideo.videoId != targetA.videoId
            ) {
                latestVideo
            } else {
                // Otherwise pick the #2 affinity cluster if exists
                sortedClusters.getOrNull(1)?.bestVideo
            }
        } else {
            // On subsequent scroll pages: pick next cluster
            sortedClusters.getOrNull(historyIndex + 1)?.bestVideo
        }

        // 4. Fetch recommendations in parallel
        val (nextQueueA, nextQueueB) = coroutineScope {
            val jobA = async { innerTubeClient.fetchWatchNext(targetA.videoId) }
            val jobB = if (targetB != null && targetB.videoId != targetA.videoId) {
                async { innerTubeClient.fetchWatchNext(targetB.videoId) }
            } else null

            Pair(jobA.await(), jobB?.await())
        }

        val listA = nextQueueA?.recommendations ?: emptyList()
        val listB = nextQueueB?.recommendations ?: emptyList()

        if (listA.isEmpty() && listB.isEmpty()) {
            return innerTubeClient.fetchFeedPage(query = defaultQuery, continuation = continuation)
        }

        // Cache extracted avatars
        listA.forEach { v ->
            if (v.channelAvatarUrl.isNotBlank()) ChannelAvatarCache.put(v.channelId, v.channelTitle, v.channelAvatarUrl)
        }
        listB.forEach { v ->
            if (v.channelAvatarUrl.isNotBlank()) ChannelAvatarCache.put(v.channelId, v.channelTitle, v.channelAvatarUrl)
        }

        val blendedVideos = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()

        // 5. If this is page 0 and Target A has an official YouTube Mix, feature the Mix Card!
        if (historyIndex == 0 && nextQueueA != null && nextQueueA.mixPlaylist.isNotEmpty()) {
            val mixPlaylistId = nextQueueA.playlistId ?: "RD${targetA.videoId}"
            val mixTitle = nextQueueA.playlistTitle.ifBlank { "Mix - ${targetA.title}" }
            val otherArtists = nextQueueA.mixPlaylist
                .map { it.channelTitle }
                .filter { it.isNotBlank() && !it.equals(targetA.channelTitle, ignoreCase = true) }
                .distinct()
                .take(2)
            val mixSubtitle = if (otherArtists.isNotEmpty()) {
                "${targetA.channelTitle}, ${otherArtists.joinToString(", ")}, dan lainnya"
            } else {
                targetA.channelTitle
            }
            val resolvedAvatar = nextQueueA.currentVideo.channelAvatarUrl.ifBlank {
                ChannelAvatarCache.get(targetA.channelId, targetA.channelTitle) ?: ""
            }
            val mixCard = VideoItem(
                id = targetA.videoId,
                title = mixTitle,
                channelTitle = mixSubtitle,
                channelId = targetA.channelId,
                thumbnailUrl = targetA.thumbnailUrl,
                channelAvatarUrl = resolvedAvatar,
                durationText = "Playlist",
                viewCountText = "${nextQueueA.mixPlaylist.size} video",
                publishedTimeText = "YouTube Mix",
                playlistId = mixPlaylistId,
                isPlaylist = true
            )
            blendedVideos.add(mixCard)
            seenIds.add(mixCard.id)
        }

        // 6. Smart Interleaving (Weighted Blending: 65% Core Interest, 35% Exploration)
        var idxA = 0
        var idxB = 0
        while (idxA < listA.size || idxB < listB.size) {
            // Push 2 items from Core Interest (listA)
            repeat(2) {
                if (idxA < listA.size) {
                    val item = listA[idxA++]
                    if (seenIds.add(item.id)) {
                        blendedVideos.add(item)
                    }
                }
            }
            // Push 1 item from Exploration / Secondary (listB)
            if (idxB < listB.size) {
                val item = listB[idxB++]
                if (seenIds.add(item.id)) {
                    blendedVideos.add(item)
                }
            }
        }

        val nextToken = if (historyIndex + 1 < sortedClusters.size) {
            "history_${historyIndex + 1}"
        } else {
            "feed_start"
        }

        return FeedResult(videos = blendedVideos, continuationToken = nextToken)
    }

    suspend fun getFeedPage(query: String? = null, continuation: String? = null): FeedResult {
        return innerTubeClient.fetchFeedPage(query, continuation)
    }

    suspend fun getFeed(query: String = "trending"): List<VideoItem> {
        return innerTubeClient.fetchFeed(query)
    }

    suspend fun search(query: String): List<VideoItem> {
        return innerTubeClient.search(query)
    }

    suspend fun searchPage(query: String? = null, continuation: String? = null): FeedResult {
        return innerTubeClient.searchPage(query, continuation)
    }

    suspend fun getSuggestions(query: String): List<String> {
        return innerTubeClient.fetchSuggestions(query)
    }

    suspend fun getDetailedSuggestions(query: String): List<com.phantom.tube.data.model.SuggestionItem> {
        return innerTubeClient.fetchDetailedSuggestions(query)
    }

    suspend fun getWatchNext(videoId: String, playlistId: String? = null): NextQueue? {
        return innerTubeClient.fetchWatchNext(videoId, playlistId)
    }

    private val commentsCache = java.util.concurrent.ConcurrentHashMap<String, CommentsResult>()

    fun clearCommentsCache() {
        commentsCache.clear()
    }

    suspend fun getComments(continuationToken: String): CommentsResult {
        commentsCache[continuationToken]?.let { return it }
        val result = innerTubeClient.fetchComments(continuationToken)
        if (result.comments.isNotEmpty()) {
            if (commentsCache.size >= 4) {
                commentsCache.clear()
            }
            commentsCache[continuationToken] = result
        }
        return result
    }

    suspend fun getMoreRecommendations(
        video: VideoItem,
        continuation: String? = null
    ): FeedResult {
        // 1. Try YouTube official watch next continuation if available
        if (!continuation.isNullOrBlank() && !continuation.startsWith("search_fallback_")) {
            val result = innerTubeClient.fetchWatchNextContinuation(continuation, currentVideoId = video.id)
            if (result.videos.isNotEmpty()) {
                return result
            }
        }

        // 2. Intelligent related content fallback
        val searchQuery = "${video.title} ${video.channelTitle}".trim().ifBlank { video.title }
        val searchContinuation = if (continuation?.startsWith("search_fallback_") == true) {
            continuation.removePrefix("search_fallback_")
        } else null

        val feedResult = innerTubeClient.fetchFeedPage(query = searchQuery, continuation = searchContinuation)
        val filtered = feedResult.videos.filter { it.id != video.id }
        val nextToken = feedResult.continuationToken?.let { "search_fallback_$it" }
        return FeedResult(videos = filtered, continuationToken = nextToken)
    }

    suspend fun getSponsorSegments(videoId: String): List<SponsorSegment> {
        if (preferences?.sponsorBlockEnabled?.value == false) {
            return emptyList()
        }
        return sponsorBlockClient.getSkipSegments(videoId)
    }

    // Search History
    fun getSearchHistory(limit: Int = 25): Flow<List<SearchHistoryEntity>> {
        return searchHistoryDao.getRecentHistory(limit)
    }

    suspend fun saveSearchQuery(query: String) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertOrUpdate(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.delete(query)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearAll()
    }

    // Watch History
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao.getAllHistory()
    }

    suspend fun recordWatch(
        video: VideoItem,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ) {
        if (preferences?.pauseWatchHistory?.value == true) {
            return
        }
        watchHistoryDao.insertOrUpdate(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelTitle = video.channelTitle,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                durationText = video.durationText,
                lastPositionMs = positionMs,
                durationMs = durationMs,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getLastPosition(videoId: String): Long {
        val entry = watchHistoryDao.getEntry(videoId) ?: return 0L
        val duration = entry.durationMs
        val lastPos = entry.lastPositionMs
        // If video was finished, or within 10s of end, or >= 90% watched, or < 5s: start fresh at 0
        if (duration > 0L) {
            if (lastPos >= duration - 10000L || (lastPos.toFloat() / duration.toFloat()) >= 0.90f) {
                return 0L
            }
        }
        if (lastPos < 5000L) {
            return 0L
        }
        return lastPos
    }

    suspend fun resetWatchPosition(videoId: String) {
        watchHistoryDao.getEntry(videoId)?.let { entry ->
            watchHistoryDao.insertOrUpdate(entry.copy(lastPositionMs = 0L))
        }
    }

    suspend fun deleteHistoryItem(videoId: String) {
        watchHistoryDao.delete(videoId)
    }

    suspend fun clearHistory() {
        watchHistoryDao.clearAll()
    }

    suspend fun clearWatchHistory() {
        clearHistory()
    }

    // Favorites
    fun getFavorites(): Flow<List<FavoriteEntity>> {
        return favoriteDao.getAllFavorites()
    }

    fun isFavorite(videoId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(videoId)
    }

    suspend fun toggleFavorite(video: VideoItem, isFav: Boolean) {
        if (isFav) {
            favoriteDao.delete(video.id)
        } else {
            favoriteDao.insert(
                FavoriteEntity(
                    videoId = video.id,
                    title = video.title,
                    channelTitle = video.channelTitle,
                    thumbnailUrl = video.thumbnailUrl,
                    durationText = video.durationText
                )
            )
        }
    }

    // Subscriptions
    fun getSubscriptions(): Flow<List<SubscriptionEntity>> {
        return subscriptionDao.getAllSubscriptions()
    }

    fun isSubscribed(channelId: String): Flow<Boolean> {
        return subscriptionDao.isSubscribed(channelId)
    }

    suspend fun subscribe(subscription: SubscriptionEntity) {
        subscriptionDao.insert(subscription)
    }

    suspend fun unsubscribe(channelId: String) {
        subscriptionDao.delete(channelId)
    }

    // Channel profile & videos
    suspend fun getChannel(
        channelId: String,
        params: String? = null,
        continuation: String? = null
    ): ChannelProfile? {
        return innerTubeClient.fetchChannel(channelId = channelId, params = params, continuation = continuation)
    }

    suspend fun getChannelVideosContinuation(continuation: String): FeedResult {
        return innerTubeClient.fetchChannelVideosContinuation(continuation)
    }

    // Downloads
    fun getDownloads(): Flow<List<DownloadEntity>> {
        return downloadDao?.getAllDownloads() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    fun isDownloaded(videoId: String): Flow<Boolean> {
        return downloadDao?.isDownloaded(videoId) ?: kotlinx.coroutines.flow.flowOf(false)
    }

    suspend fun saveDownload(entity: DownloadEntity) {
        downloadDao?.insert(entity)
    }

    suspend fun deleteDownload(id: String) {
        downloadDao?.delete(id)
    }
}

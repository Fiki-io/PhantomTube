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

import com.phantom.tube.core.util.LocaleHelper
import com.phantom.tube.core.util.RegionHelper
import com.phantom.tube.data.innertube.cache.ChannelAvatarCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
            preferences?.contentCountry?.value ?: RegionHelper.resolveDefaultCountry()
        }
        innerTubeClient.languageCodeProvider = {
            preferences?.appLanguage?.value ?: LocaleHelper.resolveDefaultLanguage()
        }
    }
    private fun reorderForRefresh(
        videos: List<VideoItem>,
        refreshNonce: Int,
        excludeLeadIds: Set<String>
    ): List<VideoItem> {
        if (videos.size <= 1 || (refreshNonce <= 0 && excludeLeadIds.isEmpty())) {
            return videos
        }
        val fresh = if (excludeLeadIds.isNotEmpty()) {
            videos.filterNot { it.id in excludeLeadIds }
        } else {
            videos
        }
        val repeated = if (excludeLeadIds.isNotEmpty()) {
            videos.filter { it.id in excludeLeadIds }
        } else {
            emptyList()
        }
        if (refreshNonce <= 0) {
            return fresh + repeated
        }
        val rng = kotlin.random.Random(refreshNonce.toLong() * 7919L + videos.size)
        return if (fresh.isNotEmpty()) {
            val orderedFresh = if (fresh.size > 1) fresh.shuffled(rng) else fresh
            orderedFresh + repeated
        } else {
            val shift = (refreshNonce % videos.size).let { if (it == 0) 1 else it }
            videos.drop(shift) + videos.take(shift)
        }
    }

    suspend fun getHomeRecommendations(
        historyIndex: Int = 0,
        continuation: String? = null,
        refreshNonce: Int = 0,
        excludeLeadIds: Set<String> = emptySet()
    ): FeedResult {
        // If we have a direct continuation token from YouTube, load next page
        if (!continuation.isNullOrBlank() && !continuation.startsWith("history_") && !continuation.startsWith("smart_")) {
            val pageResult = innerTubeClient.fetchFeedPage(continuation = continuation)
            if (pageResult.videos.isNotEmpty()) {
                return pageResult
            }
        }

        val country = preferences?.contentCountry?.value ?: RegionHelper.resolveDefaultCountry()
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
            return getFeedPage(
                query = defaultQuery,
                continuation = continuation,
                refreshNonce = refreshNonce,
                excludeLeadIds = excludeLeadIds
            )
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
            return getFeedPage(
                query = defaultQuery,
                continuation = continuation,
                refreshNonce = refreshNonce,
                excludeLeadIds = excludeLeadIds
            )
        }

        val seedPool = buildList {
            val seenSeedIds = mutableSetOf<String>()
            sortedClusters.forEach { cluster ->
                if (seenSeedIds.add(cluster.bestVideo.videoId)) {
                    add(cluster.bestVideo)
                }
            }
            recentWatched.forEach { item ->
                if (seenSeedIds.add(item.videoId)) {
                    add(item)
                }
            }
        }
        val maxHistoryPages = seedPool.size.coerceAtMost(10)

        // If the user scrolled past all available history seeds, fallback to trending
        if (historyIndex >= maxHistoryPages) {
            return getFeedPage(
                query = defaultQuery,
                continuation = continuation,
                refreshNonce = refreshNonce,
                excludeLeadIds = excludeLeadIds
            )
        }

        // 3. Determine Seed Targets for this page, rotating on pull-to-refresh
        val effectiveIndex = if (refreshNonce > 0) {
            (historyIndex + refreshNonce) % seedPool.size
        } else {
            historyIndex
        }
        val targetA = seedPool[effectiveIndex]

        // Target B: Exploration or Secondary cluster
        val latestVideo = recentWatched.first()
        val targetB: WatchHistoryEntity? = if (historyIndex == 0 && refreshNonce == 0) {
            if (!latestVideo.channelTitle.equals(targetA.channelTitle, ignoreCase = true) &&
                latestVideo.videoId != targetA.videoId
            ) {
                latestVideo
            } else {
                seedPool.getOrNull(1)
            }
        } else {
            val otherSeeds = (1 until seedPool.size).map { offset ->
                seedPool[(effectiveIndex + offset) % seedPool.size]
            }.filter { it.videoId != targetA.videoId }
            otherSeeds.firstOrNull { !it.channelTitle.equals(targetA.channelTitle, ignoreCase = true) }
                ?: otherSeeds.firstOrNull()
        }

        // 4. Fetch recommendations in parallel
        val (nextQueueA, nextQueueB) = coroutineScope {
            val jobA = async { innerTubeClient.fetchWatchNext(targetA.videoId) }
            val jobB = if (targetB != null && targetB.videoId != targetA.videoId) {
                async { innerTubeClient.fetchWatchNext(targetB.videoId) }
            } else null

            Pair(jobA.await(), jobB?.await())
        }

        val rawListA = nextQueueA?.recommendations ?: emptyList()
        val rawListB = nextQueueB?.recommendations ?: emptyList()

        if (rawListA.isEmpty() && rawListB.isEmpty()) {
            return getFeedPage(
                query = defaultQuery,
                continuation = continuation,
                refreshNonce = refreshNonce,
                excludeLeadIds = excludeLeadIds
            )
        }

        // Cache extracted avatars
        rawListA.forEach { v ->
            if (v.channelAvatarUrl.isNotBlank()) ChannelAvatarCache.put(v.channelId, v.channelTitle, v.channelAvatarUrl)
        }
        rawListB.forEach { v ->
            if (v.channelAvatarUrl.isNotBlank()) ChannelAvatarCache.put(v.channelId, v.channelTitle, v.channelAvatarUrl)
        }

        val listA = if (refreshNonce > 0 && rawListA.size > 1) {
            rawListA.shuffled(kotlin.random.Random(refreshNonce.toLong() * 131L + historyIndex))
        } else {
            rawListA
        }
        val listB = if (refreshNonce > 0 && rawListB.size > 1) {
            rawListB.shuffled(kotlin.random.Random(refreshNonce.toLong() * 257L + historyIndex))
        } else {
            rawListB
        }

        val regularVideos = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()

        // 5. Prepare optional Mix Card (pinned at #1 only on initial load, placed below top items on refresh)
        var mixCardToInsert: VideoItem? = null
        if (historyIndex == 0 && nextQueueA != null && nextQueueA.mixPlaylist.isNotEmpty()) {
            val mixPlaylistId = nextQueueA.playlistId ?: "RD${targetA.videoId}"
            val allowMixOnRefresh = refreshNonce == 0 ||
                (targetA.videoId !in excludeLeadIds && mixPlaylistId !in excludeLeadIds)
            if (allowMixOnRefresh) {
                val mixTitle = nextQueueA.playlistTitle.ifBlank { "Mix - ${targetA.title}" }
                val otherArtists = nextQueueA.mixPlaylist
                    .map { it.channelTitle }
                    .filter { it.isNotBlank() && !it.equals(targetA.channelTitle, ignoreCase = true) }
                    .distinct()
                    .take(2)
                val lang = preferences?.appLanguage?.value ?: LocaleHelper.resolveDefaultLanguage()
                val andMoreText = when (lang) {
                    "en" -> "and more"
                    "es" -> "y más"
                    "ja" -> "ほか"
                    "ru" -> "и др."
                    else -> "dan lainnya"
                }
                val playlistBadgeText = when (lang) {
                    "es" -> "Lista"
                    "ja" -> "再生リスト"
                    "ru" -> "Плейлист"
                    else -> "Playlist"
                }
                val videosCountText = when (lang) {
                    "en" -> "${nextQueueA.mixPlaylist.size} videos"
                    "es" -> "${nextQueueA.mixPlaylist.size} videos"
                    "ja" -> "${nextQueueA.mixPlaylist.size} 本の動画"
                    "ru" -> "${nextQueueA.mixPlaylist.size} видео"
                    else -> "${nextQueueA.mixPlaylist.size} video"
                }
                val mixSubtitle = if (otherArtists.isNotEmpty()) {
                    "${targetA.channelTitle}, ${otherArtists.joinToString(", ")}, $andMoreText"
                } else {
                    targetA.channelTitle
                }
                val resolvedAvatar = nextQueueA.currentVideo.channelAvatarUrl.ifBlank {
                    ChannelAvatarCache.get(targetA.channelId, targetA.channelTitle) ?: ""
                }
                mixCardToInsert = VideoItem(
                    id = targetA.videoId,
                    title = mixTitle,
                    channelTitle = mixSubtitle,
                    channelId = targetA.channelId,
                    thumbnailUrl = targetA.thumbnailUrl,
                    channelAvatarUrl = resolvedAvatar,
                    durationText = playlistBadgeText,
                    viewCountText = videosCountText,
                    publishedTimeText = "YouTube Mix",
                    playlistId = mixPlaylistId,
                    isPlaylist = true
                )
                seenIds.add("playlist_$mixPlaylistId")
            }
        }

        // 6. Smart Interleaving (Weighted Blending: 65% Core Interest, 35% Exploration)
        var idxA = 0
        var idxB = 0
        while (idxA < listA.size || idxB < listB.size) {
            repeat(2) {
                if (idxA < listA.size) {
                    val item = listA[idxA++]
                    if (seenIds.add(item.id)) {
                        regularVideos.add(item)
                    }
                }
            }
            if (idxB < listB.size) {
                val item = listB[idxB++]
                if (seenIds.add(item.id)) {
                    regularVideos.add(item)
                }
            }
        }

        val prioritizedVideos = if (excludeLeadIds.isNotEmpty()) {
            regularVideos.filterNot { it.id in excludeLeadIds } +
                regularVideos.filter { it.id in excludeLeadIds }
        } else {
            regularVideos
        }

        val blendedVideos = prioritizedVideos.toMutableList()
        if (mixCardToInsert != null) {
            val insertIndex = if (refreshNonce == 0) 0 else minOf(2, blendedVideos.size)
            blendedVideos.add(insertIndex, mixCardToInsert)
        }

        val nextToken = if (historyIndex + 1 < maxHistoryPages) {
            "history_${historyIndex + 1}"
        } else {
            "feed_start"
        }

        return FeedResult(videos = blendedVideos, continuationToken = nextToken)
    }

    suspend fun getFeedPage(
        query: String? = null,
        continuation: String? = null,
        refreshNonce: Int = 0,
        excludeLeadIds: Set<String> = emptySet()
    ): FeedResult {
        val result = innerTubeClient.fetchFeedPage(query = query, continuation = continuation)
        if (refreshNonce > 0 && continuation.isNullOrBlank() && result.videos.size > 1) {
            return result.copy(
                videos = reorderForRefresh(
                    videos = result.videos,
                    refreshNonce = refreshNonce,
                    excludeLeadIds = excludeLeadIds
                )
            )
        }
        return result
    }

    suspend fun getFeed(query: String = "trending"): List<VideoItem> {
        return innerTubeClient.fetchFeed(query)
    }

    suspend fun search(query: String): List<VideoItem> {
        return innerTubeClient.search(query)
    }

    suspend fun searchPage(
        query: String? = null,
        params: String? = null,
        continuation: String? = null
    ): FeedResult {
        return innerTubeClient.searchPage(query, params, continuation)
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
        val existing = watchHistoryDao.getEntry(video.id)
        val resolvedTitle = video.title.ifBlank { existing?.title.orEmpty() }
        val resolvedChannelTitle = video.channelTitle.ifBlank { existing?.channelTitle.orEmpty() }
        val resolvedChannelId = video.channelId.ifBlank { existing?.channelId.orEmpty() }
        val resolvedThumb = video.thumbnailUrl.ifBlank { existing?.thumbnailUrl.orEmpty() }
        val resolvedDurationText = video.durationText.ifBlank { existing?.durationText.orEmpty() }
        val resolvedDurationMs = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
        if (resolvedTitle.isBlank() && positionMs <= 0L) {
            return
        }
        watchHistoryDao.insertOrUpdate(
            WatchHistoryEntity(
                videoId = video.id,
                title = resolvedTitle,
                channelTitle = resolvedChannelTitle,
                channelId = resolvedChannelId,
                thumbnailUrl = resolvedThumb,
                durationText = resolvedDurationText,
                lastPositionMs = positionMs,
                durationMs = resolvedDurationMs,
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

    suspend fun clearWatchHistory() {
        watchHistoryDao.clearAll()
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
                    channelId = video.channelId,
                    thumbnailUrl = video.thumbnailUrl,
                    durationText = video.durationText
                )
            )
        }
    }

    // Subscriptions
    @Volatile
    private var subscriptionFeedCacheKey: String = ""
    @Volatile
    private var subscriptionFeedCacheTimestampMs: Long = 0L
    @Volatile
    private var subscriptionFeedCacheVideos: List<VideoItem> = emptyList()

    private fun invalidateSubscriptionFeedCache() {
        subscriptionFeedCacheKey = ""
        subscriptionFeedCacheTimestampMs = 0L
        subscriptionFeedCacheVideos = emptyList()
    }

    fun getSubscriptions(): Flow<List<SubscriptionEntity>> {
        return subscriptionDao.getAllSubscriptions()
    }

    fun isSubscribed(channelId: String): Flow<Boolean> {
        return subscriptionDao.isSubscribed(channelId)
    }

    suspend fun subscribe(subscription: SubscriptionEntity) {
        subscriptionDao.insert(subscription)
        invalidateSubscriptionFeedCache()
    }

    suspend fun unsubscribe(channelId: String) {
        subscriptionDao.delete(channelId)
        invalidateSubscriptionFeedCache()
    }

    suspend fun getSubscriptionFeed(
        subscriptions: List<SubscriptionEntity>,
        forceRefresh: Boolean = false
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (subscriptions.isEmpty()) {
            invalidateSubscriptionFeedCache()
            return@withContext emptyList()
        }

        val targetChannels = subscriptions.take(15)
        val country = preferences?.contentCountry?.value ?: RegionHelper.resolveDefaultCountry()
        val lang = preferences?.appLanguage?.value ?: LocaleHelper.resolveDefaultLanguage()
        val cacheKey = buildString {
            append(targetChannels.map { it.channelId.ifBlank { it.channelTitle } }.sorted().joinToString("|"))
            append("::$country::$lang")
        }

        val now = System.currentTimeMillis()
        if (!forceRefresh &&
            subscriptionFeedCacheKey == cacheKey &&
            subscriptionFeedCacheVideos.isNotEmpty() &&
            now - subscriptionFeedCacheTimestampMs < SUBSCRIPTION_FEED_TTL_MS
        ) {
            return@withContext subscriptionFeedCacheVideos
        }

        val results = coroutineScope {
            targetChannels.map { sub ->
                async {
                    val channelVideos = mutableListOf<VideoItem>()
                    try {
                        val targetId = sub.channelId.ifBlank { sub.channelTitle }
                        val chanProfile = getChannel(targetId)
                        if (chanProfile != null) {
                            if (chanProfile.videos.isNotEmpty()) {
                                channelVideos.addAll(chanProfile.videos)
                            }
                            if (chanProfile.homeVideos.isNotEmpty()) {
                                channelVideos.addAll(chanProfile.homeVideos)
                            }
                            if (chanProfile.featuredVideo != null) {
                                channelVideos.add(chanProfile.featuredVideo)
                            }
                            if (channelVideos.isEmpty() && !chanProfile.videoTabParams.isNullOrBlank()) {
                                val vTabRes = getChannel(targetId, params = chanProfile.videoTabParams)
                                if (vTabRes != null && vTabRes.videos.isNotEmpty()) {
                                    channelVideos.addAll(vTabRes.videos)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                    channelVideos.map { v ->
                        v.copy(
                            channelTitle = v.channelTitle.ifBlank { sub.channelTitle },
                            channelAvatarUrl = v.channelAvatarUrl.ifBlank { sub.channelAvatarUrl },
                            channelId = v.channelId.ifBlank { sub.channelId }
                        )
                    }.take(6)
                }
            }.awaitAll()
        }

        val maxPerChan = results.maxOfOrNull { it.size } ?: 0
        val interleaved = ArrayList<VideoItem>()
        for (i in 0 until maxPerChan) {
            for (chanList in results) {
                if (i < chanList.size) {
                    interleaved.add(chanList[i])
                }
            }
        }
        val deduplicated = interleaved.distinctBy { it.id }
        if (deduplicated.isNotEmpty()) {
            subscriptionFeedCacheKey = cacheKey
            subscriptionFeedCacheTimestampMs = System.currentTimeMillis()
            subscriptionFeedCacheVideos = deduplicated
        }
        deduplicated
    }

    companion object {
        private const val SUBSCRIPTION_FEED_TTL_MS = 5 * 60 * 1000L
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

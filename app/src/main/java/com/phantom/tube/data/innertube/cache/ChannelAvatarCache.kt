package com.phantom.tube.data.innertube.cache

import com.phantom.tube.data.innertube.InnerTubeClient
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance bounded in-memory cache and throttled background resolver for YouTube channel avatars.
 */
object ChannelAvatarCache {

    private const val MAX_CACHE_ENTRIES = 200
    private val cacheById = ConcurrentHashMap<String, String>()
    private val cacheByTitle = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failedIds = ConcurrentHashMap.newKeySet<String>()
    private val networkSemaphore = Semaphore(2)

    private var client: InnerTubeClient? = null

    fun init(innerTubeClient: InnerTubeClient) {
        client = innerTubeClient
    }

    fun trim() {
        if (cacheById.size > MAX_CACHE_ENTRIES / 2) {
            cacheById.clear()
        }
        if (cacheByTitle.size > MAX_CACHE_ENTRIES / 2) {
            cacheByTitle.clear()
        }
        failedIds.clear()
    }

    fun clear() {
        cacheById.clear()
        cacheByTitle.clear()
        failedIds.clear()
    }

    fun put(channelId: String?, channelTitle: String?, avatarUrl: String?) {
        val clean = InnerTubeHelpers.normalizeUrl(avatarUrl)
        if (clean.isBlank()) return

        if (!channelId.isNullOrBlank()) {
            if (cacheById.size >= MAX_CACHE_ENTRIES) {
                cacheById.keys.firstOrNull()?.let { cacheById.remove(it) }
            }
            cacheById[channelId] = clean
        }
        if (!channelTitle.isNullOrBlank()) {
            val key = channelTitle.trim().lowercase()
            if (key.isNotBlank()) {
                if (cacheByTitle.size >= MAX_CACHE_ENTRIES) {
                    cacheByTitle.keys.firstOrNull()?.let { cacheByTitle.remove(it) }
                }
                cacheByTitle[key] = clean
            }
        }
    }

    fun get(channelId: String?, channelTitle: String?): String {
        if (!channelId.isNullOrBlank()) {
            cacheById[channelId]?.let { if (it.isNotBlank()) return it }
        }
        if (!channelTitle.isNullOrBlank()) {
            val key = channelTitle.trim().lowercase()
            if (key.isNotBlank()) {
                cacheByTitle[key]?.let { if (it.isNotBlank()) return it }
            }
        }
        return ""
    }

    suspend fun resolveAvatar(channelId: String, channelTitle: String): String? = withContext(Dispatchers.IO) {
        val cached = get(channelId, channelTitle)
        if (cached.isNotBlank()) return@withContext cached

        if (channelId.isBlank() || !channelId.startsWith("UC") || channelId in failedIds) {
            return@withContext null
        }

        val c = client ?: return@withContext null
        if (!inFlight.add(channelId)) {
            return@withContext get(channelId, channelTitle).ifBlank { null }
        }

        try {
            networkSemaphore.withPermit {
                val recheck = get(channelId, channelTitle)
                if (recheck.isNotBlank()) return@withPermit recheck

                val profile = c.fetchChannel(channelId = channelId)
                val avatar = InnerTubeHelpers.normalizeUrl(profile?.avatarUrl)
                if (avatar.isNotBlank()) {
                    put(channelId, channelTitle, avatar)
                    val profTitle = profile?.title
                    if (!profTitle.isNullOrBlank()) {
                        put(channelId, profTitle, avatar)
                    }
                    return@withContext avatar
                } else {
                    if (failedIds.size > MAX_CACHE_ENTRIES) failedIds.clear()
                    failedIds.add(channelId)
                }
            }
        } catch (e: Exception) {
            // Silently ignore network hiccups; avoid crashing UI
        } finally {
            inFlight.remove(channelId)
        }

        return@withContext get(channelId, channelTitle).ifBlank { null }
    }
}

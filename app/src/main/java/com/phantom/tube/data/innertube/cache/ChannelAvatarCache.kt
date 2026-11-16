package com.phantom.tube.data.innertube.cache

import com.phantom.tube.data.innertube.InnerTubeClient
import com.phantom.tube.data.innertube.parser.InnerTubeHelpers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded LRU in-memory cache and deduplicated background resolver for YouTube channel avatars.
 */
object ChannelAvatarCache {

    private const val MAX_CACHE_ENTRIES = 200
    private val cacheById: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )
    private val cacheByTitle: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val failedIds = ConcurrentHashMap.newKeySet<String>()
    private val networkSemaphore = Semaphore(2)

    @Volatile
    private var client: InnerTubeClient? = null

    fun init(innerTubeClient: InnerTubeClient) {
        client = innerTubeClient
    }

    private fun trimLruHalf(map: MutableMap<String, String>) {
        synchronized(map) {
            val targetSize = MAX_CACHE_ENTRIES / 2
            val iterator = map.entries.iterator()
            while (map.size > targetSize && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun trim() {
        trimLruHalf(cacheById)
        trimLruHalf(cacheByTitle)
        failedIds.clear()
    }

    fun clear() {
        cacheById.clear()
        cacheByTitle.clear()
        failedIds.clear()
    }

    fun put(channelId: String?, channelTitle: String?, avatarUrl: String?) {
        val clean = InnerTubeHelpers.optimizeAvatarUrl(avatarUrl)
        if (clean.isBlank()) return

        if (!channelId.isNullOrBlank()) {
            cacheById[channelId] = clean
        }
        if (!channelTitle.isNullOrBlank()) {
            val key = channelTitle.trim().lowercase()
            if (key.isNotBlank()) {
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
        val newDeferred = CompletableDeferred<String?>()
        val existingDeferred = inFlight.putIfAbsent(channelId, newDeferred)
        if (existingDeferred != null) {
            return@withContext existingDeferred.await()
        }

        var resolvedResult: String? = null
        try {
            resolvedResult = networkSemaphore.withPermit {
                val recheck = get(channelId, channelTitle)
                if (recheck.isNotBlank()) return@withPermit recheck

                val avatar = c.fetchChannelAvatarFast(channelId = channelId).orEmpty()
                if (avatar.isNotBlank()) {
                    put(channelId, channelTitle, avatar)
                    avatar
                } else {
                    if (failedIds.size > MAX_CACHE_ENTRIES) failedIds.clear()
                    failedIds.add(channelId)
                    null
                }
            }
            newDeferred.complete(resolvedResult)
        } catch (e: Exception) {
            newDeferred.complete(null)
        } finally {
            inFlight.remove(channelId, newDeferred)
            if (!newDeferred.isCompleted) {
                newDeferred.complete(resolvedResult)
            }
        }

        return@withContext resolvedResult ?: get(channelId, channelTitle).ifBlank { null }
    }
}

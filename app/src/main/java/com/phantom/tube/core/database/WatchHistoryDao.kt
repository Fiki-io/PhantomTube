package com.phantom.tube.core.database

import kotlinx.coroutines.flow.Flow

interface WatchHistoryDao {
    suspend fun insertOrUpdate(entry: WatchHistoryEntity)
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>
    suspend fun getRecentWatched(limit: Int = 10): List<WatchHistoryEntity>
    suspend fun getEntry(videoId: String): WatchHistoryEntity?
    suspend fun getLastPosition(videoId: String): Long?
    suspend fun delete(videoId: String)
    suspend fun clearAll()
}

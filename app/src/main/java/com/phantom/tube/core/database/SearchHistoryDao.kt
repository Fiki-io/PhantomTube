package com.phantom.tube.core.database

import kotlinx.coroutines.flow.Flow

interface SearchHistoryDao {
    suspend fun insertOrUpdate(entry: SearchHistoryEntity)
    fun getRecentHistory(limit: Int = 25): Flow<List<SearchHistoryEntity>>
    suspend fun delete(query: String)
    suspend fun clearAll()
}

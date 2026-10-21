package com.phantom.tube.core.database

import kotlinx.coroutines.flow.Flow

data class FavoriteEntity(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationText: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

interface FavoriteDao {
    suspend fun insert(favorite: FavoriteEntity)
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
    fun isFavorite(videoId: String): Flow<Boolean>
    suspend fun delete(videoId: String)
}

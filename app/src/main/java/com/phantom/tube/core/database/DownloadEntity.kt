package com.phantom.tube.core.database

import kotlinx.coroutines.flow.Flow

data class DownloadEntity(
    val id: String,
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val format: String,
    val qualityLabel: String,
    val fileName: String,
    val fileUri: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

interface DownloadDao {
    suspend fun insert(download: DownloadEntity)
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    fun isDownloaded(videoId: String): Flow<Boolean>
    suspend fun delete(id: String)
    suspend fun deleteByVideoId(videoId: String)
    suspend fun clearAll()
}

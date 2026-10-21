package com.phantom.tube.core.database

data class WatchHistoryEntity(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelId: String = "",
    val thumbnailUrl: String,
    val durationText: String = "",
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val watchedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

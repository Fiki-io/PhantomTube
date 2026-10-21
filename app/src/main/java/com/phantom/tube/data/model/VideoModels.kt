package com.phantom.tube.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val channelTitle: String = "",
    val channelId: String = "",
    val thumbnailUrl: String,
    val channelAvatarUrl: String = "",
    val viewCountText: String = "",
    val publishedTimeText: String = "",
    val durationText: String = "",
    val durationSeconds: Long = 0L,
    val playlistId: String? = null,
    val isPlaylist: Boolean = false
)

@Immutable
data class VideoComment(
    val id: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorChannelId: String = "",
    val authorAvatarUrl: String = "",
    val publishedTimeText: String = "",
    val contentText: String = "",
    val likeCountText: String = "",
    val replyCountText: String = ""
)

@Immutable
data class CommentsResult(
    val comments: List<VideoComment> = emptyList(),
    val totalCountText: String = "",
    val continuationToken: String? = null
)

@Immutable
data class NextQueue(
    val currentVideo: VideoItem,
    val mixPlaylist: List<VideoItem> = emptyList(),
    val recommendations: List<VideoItem> = emptyList(),
    val recommendationsContinuationToken: String? = null,
    val endscreenVideos: List<VideoItem> = emptyList(),
    val playlistTitle: String = "",
    val playlistId: String? = null,
    val currentIndex: Int = 0,
    val likeCountText: String = "",
    val fullViewCountText: String = "",
    val dateText: String = "",
    val description: String = "",
    val channelSubscriberCountText: String = "",
    val channelHandle: String = "",
    val commentsCountText: String = "",
    val commentsContinuationToken: String? = null,
    val topComment: VideoComment? = null
) {
    val upNext: List<VideoItem> get() = if (mixPlaylist.isNotEmpty() && currentIndex < mixPlaylist.lastIndex) {
        mixPlaylist.subList(currentIndex + 1, mixPlaylist.size)
    } else {
        recommendations
    }
    val mixQueue: List<VideoItem> get() = upNext

    val effectiveEndscreens: List<VideoItem> get() = if (endscreenVideos.isNotEmpty()) {
        endscreenVideos
    } else {
        recommendations.filter { it.id != currentVideo.id }.take(2)
    }
}

@Immutable
data class SponsorSegment(
    val category: String,
    val startSecond: Float,
    val endSecond: Float,
    val uuid: String = ""
) {
    val durationSeconds: Float get() = (endSecond - startSecond).coerceAtLeast(0f)
}


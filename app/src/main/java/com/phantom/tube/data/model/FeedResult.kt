package com.phantom.tube.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class FeedResult(
    val videos: List<VideoItem> = emptyList(),
    val channels: List<SearchChannelItem> = emptyList(),
    val continuationToken: String? = null,
    val errorMessage: String? = null
)

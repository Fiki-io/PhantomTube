package com.phantom.tube.core.database

data class SearchHistoryEntity(
    val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

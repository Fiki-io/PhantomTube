package com.phantom.tube.core.database

import kotlinx.coroutines.flow.Flow

data class SubscriptionEntity(
    val channelId: String,
    val channelTitle: String,
    val channelHandle: String = "",
    val channelAvatarUrl: String = "",
    val subscriberCountText: String = "",
    val subscribedAt: Long = System.currentTimeMillis()
)

interface SubscriptionDao {
    suspend fun insert(subscription: SubscriptionEntity)
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>
    suspend fun getRecentSubscriptions(limit: Int = 10): List<SubscriptionEntity>
    fun isSubscribed(channelId: String): Flow<Boolean>
    suspend fun delete(channelId: String)
    fun getSubscriptionCount(): Flow<Int>
}

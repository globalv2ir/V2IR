package com.v2ir.data.repository

import com.v2ir.data.local.database.SubscriptionDao
import com.v2ir.data.local.entity.SubscriptionEntity
import com.v2ir.data.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao
) {
    fun getPublicSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getPublicSubscriptions().map { list -> list.map { it.toDomain() } }

    fun getPrivateSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getPrivateSubscriptions().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Subscription? =
        subscriptionDao.getById(id)?.toDomain()

    suspend fun insert(subscription: Subscription): Long =
        subscriptionDao.insert(SubscriptionEntity.fromDomain(subscription))

    suspend fun insertAll(subscriptions: List<Subscription>) =
        subscriptionDao.insertAll(subscriptions.map { SubscriptionEntity.fromDomain(it) })

    suspend fun update(subscription: Subscription) =
        subscriptionDao.update(SubscriptionEntity.fromDomain(subscription))

    suspend fun deleteById(id: Long) =
        subscriptionDao.deleteById(id)

    suspend fun getCount(): Int = subscriptionDao.getCount()

    suspend fun updateExpanded(id: Long, expanded: Boolean) =
        subscriptionDao.updateExpanded(id, expanded)

    suspend fun updateEnabled(id: Long, enabled: Boolean) =
        subscriptionDao.updateEnabled(id, enabled)

    suspend fun updateScanMetadata(id: Long, healthyCount: Int, lastScanTime: Long, bestLatency: Long) =
        subscriptionDao.updateScanMetadata(id, healthyCount, lastScanTime, bestLatency)
}





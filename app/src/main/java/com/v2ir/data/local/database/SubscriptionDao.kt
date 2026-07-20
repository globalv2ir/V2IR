package com.v2ir.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.v2ir.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions WHERE isPublic = 1 ORDER BY addedAt ASC")
    fun getPublicSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE isPublic = 0 ORDER BY addedAt DESC")
    fun getPrivateSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM subscriptions")
    suspend fun getCount(): Int

    @Query("UPDATE subscriptions SET isExpanded = :expanded WHERE id = :id")
    suspend fun updateExpanded(id: Long, expanded: Boolean)

    @Query("UPDATE subscriptions SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE subscriptions SET healthyCount = :healthyCount, lastScanTime = :lastScanTime, bestLatency = :bestLatency WHERE id = :id")
    suspend fun updateScanMetadata(id: Long, healthyCount: Int, lastScanTime: Long, bestLatency: Long)
}





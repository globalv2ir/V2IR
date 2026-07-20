package com.v2ir.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.v2ir.data.local.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Query("SELECT * FROM configs ORDER BY addedAt DESC")
    fun getAllConfigs(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs ORDER BY addedAt DESC")
    suspend fun getAllConfigsOnce(): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedConfig(): ConfigEntity?

    @Query("SELECT * FROM configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<ConfigEntity>)

    @Update
    suspend fun updateConfig(config: ConfigEntity)

    @Delete
    suspend fun deleteConfig(config: ConfigEntity)

    @Query("DELETE FROM configs WHERE id = :id")
    suspend fun deleteConfigById(id: Long)

    @Query("UPDATE configs SET isSelected = 0")
    suspend fun clearAllSelections()

    @Query("UPDATE configs SET isSelected = 1 WHERE id = :id")
    suspend fun selectConfig(id: Long)

    // Transactional select: clears all then selects target atomically.
    // Prevents multiple-selected state if the app crashes between the two operations.
    @Transaction
    suspend fun selectConfigTransaction(id: Long) {
        clearAllSelections()
        selectConfig(id)
    }

    @Query("UPDATE configs SET latency = :latency WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Long)

    @Query("SELECT COUNT(*) FROM configs")
    suspend fun getConfigCount(): Int

    @Query("SELECT * FROM configs WHERE subscriptionId IS NULL OR subscriptionId = 0 ORDER BY addedAt DESC")
    fun getPrivateConfigs(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE subscriptionId = :subscriptionId")
    suspend fun getConfigsBySubscription(subscriptionId: Long): List<ConfigEntity>

    @Query("UPDATE configs SET fragmentIp = :ip WHERE id = :id")
    suspend fun updateFragmentIp(id: Long, ip: String)

    @Query("UPDATE configs SET tcpLatency = :tcp, realLatency = :real, latency = :real, lastChecked = :timestamp, lastSuccess = CASE WHEN :real >= 0 THEN :timestamp ELSE lastSuccess END, failCount = CASE WHEN :real >= 0 THEN 0 ELSE failCount + 1 END WHERE id = :id")
    suspend fun updateLatencies(id: Long, tcp: Long, real: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE configs SET tcpLatency = :tcp, lastChecked = :timestamp WHERE id = :id")
    suspend fun updateTcpLatency(id: Long, tcp: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE configs SET realLatency = :real, latency = :real, lastChecked = :timestamp, lastSuccess = CASE WHEN :real >= 0 THEN :timestamp ELSE lastSuccess END, failCount = CASE WHEN :real >= 0 THEN 0 ELSE failCount + 1 END WHERE id = :id")
    suspend fun updateRealLatency(id: Long, real: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE configs SET failCount = failCount + 1 WHERE id = :id")
    suspend fun incrementFailCount(id: Long)

    @Query("UPDATE configs SET failCount = 0 WHERE id = :id")
    suspend fun resetFailCount(id: Long)

    @Query("UPDATE configs SET isCloudflare = :isCf WHERE id = :id")
    suspend fun updateCloudflareFlag(id: Long, isCf: Boolean)

    @Query("DELETE FROM configs WHERE subscriptionId = :subId")
    suspend fun deleteConfigsBySubscriptionId(subId: Long)

    @Transaction
    suspend fun replaceSubscriptionConfigs(subId: Long, configs: List<ConfigEntity>) {
        deleteConfigsBySubscriptionId(subId)
        insertConfigs(configs)
    }
}





package com.v2ir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.v2ir.data.model.Subscription

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val remark: String = "",
    val isPublic: Boolean = false,
    val isEnabled: Boolean = true,
    val serverCount: Int = 0,
    val lastUpdated: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val isExpanded: Boolean = false,
    val healthyCount: Int = 0,
    val lastScanTime: Long = 0L,
    val bestLatency: Long = -1L
) {
    fun toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        url = url,
        remark = remark,
        isPublic = isPublic,
        isEnabled = isEnabled,
        serverCount = serverCount,
        lastUpdated = lastUpdated,
        addedAt = addedAt,
        isExpanded = isExpanded,
        healthyCount = healthyCount,
        lastScanTime = lastScanTime,
        bestLatency = bestLatency
    )

    companion object {
        fun fromDomain(subscription: Subscription): SubscriptionEntity = SubscriptionEntity(
            id = subscription.id,
            name = subscription.name,
            url = subscription.url,
            remark = subscription.remark,
            isPublic = subscription.isPublic,
            isEnabled = subscription.isEnabled,
            serverCount = subscription.serverCount,
            lastUpdated = subscription.lastUpdated,
            addedAt = subscription.addedAt,
            isExpanded = subscription.isExpanded,
            healthyCount = subscription.healthyCount,
            lastScanTime = subscription.lastScanTime,
            bestLatency = subscription.bestLatency
        )
    }
}





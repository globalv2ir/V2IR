package com.v2ir.data.model

data class Subscription(
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
)





package com.v2ir.data.model

enum class ConfigType {
    VMESS,
    VLESS,
    TROJAN,
    SHADOWSOCKS,
    HYSTERIA2,
    TUIC,
    SUBSCRIPTION
}


data class Config(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int = 443,
    val userId: String = "",
    val remark: String = "",
    val type: ConfigType = ConfigType.VLESS,
    val subscriptionUrl: String = "",
    val subscriptionId: Long? = null,
    val sni: String = "",
    val fragmentIp: String = "",
    val isCloudflare: Boolean = false,
    val isSelected: Boolean = false,
    val latency: Long = -1L,
    val tcpLatency: Long = -1L,
    val realLatency: Long = -1L,
    val rawUri: String = "",
    val countryLabel: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val isFree: Boolean = false,
    val bandwidth: Float = 0f,
    val lastSuccess: Long = 0L,
    val lastChecked: Long = 0L,
    val failCount: Int = 0,
    val extraParams: Map<String, String> = emptyMap()
)

data class TrafficStats(
    val downloadBytes: Long = 0L,
    val uploadBytes: Long = 0L,
    val downloadSpeed: Float = 0f,
    val uploadSpeed: Float = 0f,
    val pingMs: Long = -1L
)

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "XrayCore",
    val message: String,
    val rawMessage: String = ""
)

enum class ConnectionMode {
    SMART_AUTO,
    MANUAL
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}





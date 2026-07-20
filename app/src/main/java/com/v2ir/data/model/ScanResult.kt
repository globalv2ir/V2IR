package com.v2ir.data.model

data class ScanResult(
    val configId: Long,
    val address: String,
    val tcpLatencyMs: Long = -1L,
    val realLatencyMs: Long = -1L,
    val isAlive: Boolean = false
)

enum class ScanPhase {
    IDLE,
    PREPARING,
    DISCOVERY,
    VALIDATION,
    PAUSED,
    RESUMING,
    CANCELLING,
    COMPLETED,
    FAILED
}

enum class ScanStage {
    IDLE,
    PENDING,
    TCP_CONNECT,           // Stage 1
    TLS_HANDSHAKE,         // Stage 2
    HTTP_VALIDATION,       // Stage 3
    CLOUDFLARE_VALIDATION, // Stage 4
    WS_VALIDATION,         // Stage 5
    NEIGHBOR_DISCOVERY,    // Stage 6
    STABILITY_CHECK,       // New Stage
    XRAY_TRANSPORT,        // Stage 7
    LATENCY_MEASURE,       // Stage 8
    SPEED_TEST,            // Stage 9
    COMPLETED,
    FAILED
}

data class CloudflareScanResult(
    val ip: String,
    val port: Int = 443,
    val latencyMs: Long = -1,
    val isAlive: Boolean = false,
    val jitter: Long = 0,
    val packetLoss: Float = 0f,
    val rtt: Long = 0,
    val downloadSpeed: Float = 0f, // KB/s
    val uploadSpeed: Float = 0f,   // KB/s
    val stability: Int = 0,
    val handshakeTime: Long = 0,
    val connectionTime: Long = 0,
    val edgeLocation: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val finalScore: Int = 0,
    val ipVersion: Int = 4, // 4 or 6
    val timestamp: Long = System.currentTimeMillis(),
    val stage: ScanStage = ScanStage.IDLE,
    val phase: ScanPhase = ScanPhase.IDLE,
    val errorMessage: String? = null,
    val tlsVersion: String? = null,
    val cipherSuite: String? = null,
    val certIssuer: String? = null
)

data class ScanStatistics(
    val totalTargets: Int = 0,
    val generated: Int = 0,
    val queued: Int = 0,
    val scanning: Int = 0,
    val processed: Int = 0,
    val tcpSuccess: Int = 0,
    val tcpFail: Int = 0,
    val tlsSuccess: Int = 0,
    val tlsFail: Int = 0,
    val httpSuccess: Int = 0,
    val httpFail: Int = 0,
    val cfSuccess: Int = 0,
    val cfFail: Int = 0,
    val wsSuccess: Int = 0,
    val wsFail: Int = 0,
    val transportSuccess: Int = 0,
    val transportFail: Int = 0,
    val latencyReject: Int = 0,
    val packetLossReject: Int = 0,
    val speedReject: Int = 0,
    val finalAccepted: Int = 0,
    val activeWorkers: Int = 0,
    val currentIp: String = "",
    val currentRange: String = "",
    val bestLatency: Long = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val processingRate: Float = 0f, // IP/s
    val elapsedTimeMs: Long = 0,
    val etaSeconds: Long = 0
)





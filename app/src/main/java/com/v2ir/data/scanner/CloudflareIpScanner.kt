package com.v2ir.data.scanner

import com.v2ir.data.cloudflare.CloudflareIpDatabase
import com.v2ir.data.model.CloudflareScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareIpScanner @Inject constructor(
    private val cloudflareIpDatabase: CloudflareIpDatabase,
    private val networkScanner: NetworkScanner
) {

    fun scanCleanIps(
        candidates: List<String> = CloudflareIpDatabase.CLEAN_IP_CANDIDATES
    ): Flow<CloudflareScanResult> = flow {
        val validCandidates = candidates.filter { cloudflareIpDatabase.isCloudflareIp(it) }
        validCandidates.forEachIndexed { index, ip ->
            val latency = withContext(Dispatchers.IO) {
                measureTcpLatency(ip, 443)
            }
            val result = CloudflareScanResult(
                ip = ip,
                latencyMs = latency,
                isAlive = latency >= 0
            )
            emit(result)
        }
    }

    suspend fun findBestCleanIp(
        candidates: List<String> = CloudflareIpDatabase.CLEAN_IP_CANDIDATES
    ): CloudflareScanResult? {
        var best: CloudflareScanResult? = null
        scanCleanIps(candidates).collect { result ->
            if (result.isAlive && (best == null || result.latencyMs < best!!.latencyMs)) {
                best = result
            }
        }
        return best
    }

    private fun measureTcpLatency(host: String, port: Int, timeoutMs: Int = 3000): Long {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }
}





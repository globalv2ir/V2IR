package com.v2ir.data.scanner

import com.v2ir.data.model.Config
import com.v2ir.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkScanner @Inject constructor(
    // FIX (Bug #11): Inject OkHttpClient instead of constructing a private instance.
    // Previously created a third standalone OkHttpClient, wasting thread pool and connection
    // pool resources. The shared client inherits proxy, timeouts, and interceptors.
    private val sharedHttpClient: OkHttpClient
) {

    // Per-operation clients derived from the shared instance with custom timeouts
    private val httpClient: OkHttpClient by lazy {
        sharedHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 2000): Long =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                System.currentTimeMillis() - start
            } catch (_: Exception) {
                -1L
            }
        }

    /**
     * TLS Handshake test for more accurate "is alive" detection.
     * Uses correct SNI for CDN fronting scenarios.
     */
    suspend fun tlsPing(host: String, port: Int, sni: String? = null, timeoutMs: Int = 3000): Long =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                // For CDN Fronting: we want to connect to 'host' (IP) 
                // but use 'sni' (Domain) in the TLS handshake.
                val sniDomain = if (!sni.isNullOrBlank()) sni else host
                
                // If sniDomain is an IP, we don't need special DNS
                val isSniIp = sniDomain.firstOrNull()?.isDigit() == true
                
                val request = Request.Builder()
                    .url("https://$sniDomain:$port")
                    .build()
                
                val builder = httpClient.newBuilder()
                    .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .hostnameVerifier { _, _ -> true } // Bypass hostname verification for ping

                if (!isSniIp && sniDomain != host) {
                    // Custom DNS to resolve 'sniDomain' to 'host' (the specific IP we want to test)
                    builder.dns(object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> =
                            listOf(InetAddress.getByName(host))
                    })
                }

                val response = builder.build().newCall(request).execute()
                response.use { 
                    System.currentTimeMillis() - start
                }
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                // Protocol errors might indicate the server is alive but rejected our specific request
                if (msg.contains("alpn") || msg.contains("h2") || msg.contains("protocol") || msg.contains("handshake")) {
                    System.currentTimeMillis() - start
                } else {
                    -1L
                }
            }
        }

    fun scanConfigs(configs: List<Config>): Flow<ScanResult> = flow {
        val serverConfigs = configs.filter {
            it.type != com.v2ir.data.model.ConfigType.SUBSCRIPTION &&
                it.address.isNotBlank()
        }
        coroutineScope {
            val results = serverConfigs.map { config ->
                async(Dispatchers.IO) {
                    scanSingleConfig(config)
                }
            }.awaitAll()
            results.forEach { emit(it) }
        }
    }

    suspend fun scanSingleConfig(config: Config): ScanResult {
        val tcp = tcpPing(config.address, config.port)
        val real = if (tcp >= 0) {
            tlsPing(config.address, config.port, config.sni.ifBlank { null })
        } else {
            -1L
        }
        
        return ScanResult(
            configId = config.id,
            address = config.address,
            tcpLatencyMs = tcp,
            realLatencyMs = real,
            isAlive = real >= 0 // Strict: must pass TLS test
        )
    }

    /**
     * Measure download speed in KB/s.
     */
    suspend fun measureSpeed(timeoutMs: Long = 10000L): Float = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var totalBytes = 0L
        try {
            val request = Request.Builder()
                .url("https://cachefly.cachefly.net/1mb.test")
                .build()
            
            withTimeoutOrNull(timeoutMs) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTimeoutOrNull
                    val input = response.body?.byteStream() ?: return@withTimeoutOrNull
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        totalBytes += read
                        // Stop after 2 seconds to get a quick sample
                        if (System.currentTimeMillis() - start > 2000) break
                    }
                }
            }
            val durationSec = (System.currentTimeMillis() - start) / 1000f
            if (durationSec > 0) (totalBytes / 1024f) / durationSec else 0f
        } catch (_: Exception) {
            0f
        }
    }
}





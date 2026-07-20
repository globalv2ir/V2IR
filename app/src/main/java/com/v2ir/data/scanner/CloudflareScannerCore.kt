package com.v2ir.data.scanner

import android.content.Context
import android.util.Log
import com.v2ir.R
import com.v2ir.data.cloudflare.CloudflareIpDatabase
import com.v2ir.data.model.*
import com.v2ir.data.xray.XrayBinaryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

enum class ScannerProfile {
    NORMAL, STREAMING
}

enum class IpVersion {
    IPV4, IPV6, BOTH
}

@Singleton
class CloudflareScannerCore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudflareIpDatabase: CloudflareIpDatabase,
    private val httpClient: OkHttpClient,
    private val binaryManager: XrayBinaryManager,
    private val settingsRepository: com.v2ir.data.repository.SettingsRepository
) {
    // FIX (Bug #22): Changed from Dispatchers.Main to Dispatchers.Default.
    // The scanner does CPU-bound IP generation and launches coroutines for IO-bound network ops.
    // Main dispatcher is inappropriate here and caused unnecessary main thread load.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _currentPhase = MutableStateFlow(ScanPhase.IDLE)
    val currentPhase: StateFlow<ScanPhase> = _currentPhase

    private val _results = MutableStateFlow<List<CloudflareScanResult>>(emptyList())
    val results: StateFlow<List<CloudflareScanResult>> = _results

    private val _activeScans = MutableStateFlow<Map<String, CloudflareScanResult>>(emptyMap())
    val activeScans: StateFlow<Map<String, CloudflareScanResult>> = _activeScans

    private val _statistics = MutableStateFlow(ScanStatistics())
    val statistics: StateFlow<ScanStatistics> = _statistics

    private val seenIps = ConcurrentHashMap.newKeySet<String>()

    private val totalTargetsCounter = AtomicInteger(0)
    private val processedCounter = AtomicInteger(0)
    private val aliveCounter = AtomicInteger(0)
    private val successCounter = AtomicInteger(0)
    private val failureCounter = AtomicInteger(0)
    private val finalAcceptedCounter = AtomicInteger(0)
    private val workerCounter = AtomicInteger(0)
    private var startTimeMs: Long = 0
    private var currentIp: String = ""
    private var currentRange: String = ""
    private val bestLatency = AtomicInteger(Int.MAX_VALUE)

    private val TAG = "CloudflareScannerCore"

    fun startDiscovery(ipVersion: IpVersion, concurrency: Int) {
        if (_isScanning.value) return
        resetCounters(0)
        _isScanning.value = true
        _currentPhase.value = ScanPhase.DISCOVERY
        startTimeMs = System.currentTimeMillis()

        scanJob = scope.launch(Dispatchers.Default) {
            val ranges = mutableListOf<String>()
            if (ipVersion == IpVersion.IPV4 || ipVersion == IpVersion.BOTH) {
                ranges.addAll(cloudflareIpDatabase.getCidrRanges(false))
            }
            if (ipVersion == IpVersion.IPV6 || ipVersion == IpVersion.BOTH) {
                ranges.addAll(cloudflareIpDatabase.getCidrRanges(true))
            }
            
            val allIps = mutableListOf<String>()
            ranges.forEach { range ->
                allIps.addAll(generateIpsFromCidr(range))
            }
            totalTargetsCounter.set(allIps.size)

            allIps.shuffled().chunked(concurrency).forEach { batch ->
                if (!isActive || _isPaused.value) {
                    while (_isPaused.value && isActive) delay(500)
                }
                if (!isActive) return@forEach

                val jobs = batch.map { ip ->
                    launch { discoveryCheck(ip) }
                }
                jobs.joinAll()
                updateStatistics()
            }
            _isScanning.value = false
        }
    }

    private suspend fun discoveryCheck(ip: String) {
        workerCounter.incrementAndGet()
        try {
            val result = measureTlsHandshake(ip, 443, "www.cloudflare.com", 2000)
            processedCounter.incrementAndGet()
            if (result.success) {
                aliveCounter.incrementAndGet()
                val scanResult = CloudflareScanResult(
                    ip = ip,
                    port = 443,
                    isAlive = true,
                    handshakeTime = result.duration,
                    phase = ScanPhase.DISCOVERY,
                    stage = ScanStage.COMPLETED
                )
                updateResult(scanResult)
            }
        } finally {
            workerCounter.decrementAndGet()
        }
    }

    fun startValidation(config: Config, concurrency: Int, speedTestSizeKb: Int, profile: ScannerProfile) {
        if (_isScanning.value) return
        val candidates = _results.value.filter { it.isAlive }
        resetCounters(candidates.size)
        _isScanning.value = true
        _currentPhase.value = ScanPhase.VALIDATION
        startTimeMs = System.currentTimeMillis()

        scanJob = scope.launch(Dispatchers.Default) {
            candidates.chunked(concurrency).forEach { batch ->
                if (!isActive || _isPaused.value) {
                    while (_isPaused.value && isActive) delay(500)
                }
                if (!isActive) return@forEach

                val jobs = batch.map { candidate ->
                    launch { validationCheck(candidate, config, speedTestSizeKb, profile) }
                }
                jobs.joinAll()
                updateStatistics()
            }
            _isScanning.value = false
        }
    }

    private suspend fun validationCheck(
        candidate: CloudflareScanResult,
        config: Config,
        speedTestSizeKb: Int,
        profile: ScannerProfile
    ) {
        currentIp = candidate.ip
        var current = candidate.copy(
            phase = ScanPhase.VALIDATION,
            stage = ScanStage.STABILITY_CHECK
        )
        updateActiveScan(current.ip, current)

        val tests = mutableListOf<Long>()
        var localSuccess = 0
        val sni = config.sni.ifBlank { "www.cloudflare.com" }

        repeat(5) {
            val tls = measureTlsHandshake(current.ip, config.port, sni, timeoutMs = 3000)
            if (tls.success) {
                localSuccess++
                tests.add(tls.duration)
            }
            delay(50)
        }

        val stability = (localSuccess.toFloat() / 5f * 100f).toInt()
        val avgHandshake = if (tests.isNotEmpty()) tests.average().toLong() else 0L
        
        val jitter = if (tests.size > 1) {
            var diffSum = 0L
            for (i in 0 until tests.size - 1) {
                diffSum += abs(tests[i] - tests[i + 1])
            }
            diffSum / (tests.size - 1)
        } else 0L

        if (localSuccess == 0) {
            processedCounter.incrementAndGet()
            failureCounter.incrementAndGet()
            val failed = current.copy(
                stage = ScanStage.FAILED, 
                stability = 0,
                errorMessage = context.getString(R.string.configs_cf_error_tls)
            )
            updateActiveScan(current.ip, failed)
            updateResult(failed)
            return
        }

        current = current.copy(
            stability = stability,
            handshakeTime = avgHandshake,
            jitter = jitter,
            stage = ScanStage.XRAY_TRANSPORT
        )
        updateActiveScan(current.ip, current)

        val xrayResult = runXrayTransportValidation(current, config, speedTestSizeKb, profile)
        
        processedCounter.incrementAndGet()
        if (xrayResult.isAlive && xrayResult.finalScore > 0) {
            finalAcceptedCounter.incrementAndGet()
            successCounter.incrementAndGet()
            
            val latency = xrayResult.latencyMs.toInt()
            if (latency > 0 && latency < bestLatency.get()) {
                bestLatency.set(latency)
            }

            val final = xrayResult.copy(stage = ScanStage.COMPLETED)
            updateActiveScan(final.ip, final)
            updateResult(final)
        } else {
            failureCounter.incrementAndGet()
            updateActiveScan(xrayResult.ip, xrayResult)
            updateResult(xrayResult)
        }
    }

    private fun calculateScore(stability: Int, jitter: Long, latencyMs: Long, handshakeTimeMs: Long, downloadSpeed: Float): Int {
        if (stability < 50) return 0
        var score = 0
        score += stability * 2
        score += (1000 - latencyMs.coerceAtMost(1000)).toInt() / 10
        score += (500 - jitter.coerceAtMost(500)).toInt() / 20
        score += (downloadSpeed * 10).toInt().coerceAtMost(500)
        return score
    }

    private fun updateStatistics() {
        val now = System.currentTimeMillis()
        val duration = (now - startTimeMs) / 1000f
        val speed = if (duration > 0) processedCounter.get() / duration else 0f
        val eta = if (speed > 0) ((totalTargetsCounter.get() - processedCounter.get()) / speed).toLong() else 0L

        _statistics.value = ScanStatistics(
            totalTargets = totalTargetsCounter.get(),
            processed = processedCounter.get(),
            successCount = successCounter.get(),
            failureCount = failureCounter.get(),
            finalAccepted = finalAcceptedCounter.get(),
            activeWorkers = workerCounter.get(),
            currentIp = currentIp,
            currentRange = currentRange,
            bestLatency = bestLatency.get().toLong(),
            processingRate = speed,
            elapsedTimeMs = (now - startTimeMs),
            etaSeconds = eta
        )
    }

    private fun updateActiveScan(ip: String, result: CloudflareScanResult) {
        val current = _activeScans.value.toMutableMap()
        current[ip] = result
        _activeScans.value = current
    }

    private fun updateResult(result: CloudflareScanResult) {
        val current = _results.value.toMutableList()
        val index = current.indexOfFirst { it.ip == result.ip }
        if (index != -1) {
            current[index] = result
        } else {
            current.add(result)
        }
        _results.value = current.sortedByDescending { it.finalScore }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
        _isPaused.value = false
    }

    fun pauseScan() {
        _isPaused.value = true
    }

    fun resumeScan() {
        _isPaused.value = false
    }

    fun clearResults() {
        _results.value = emptyList()
        seenIps.clear()
        resetCounters(0)
    }

    private fun resetCounters(total: Int) {
        totalTargetsCounter.set(total)
        processedCounter.set(0)
        aliveCounter.set(0)
        successCounter.set(0)
        failureCounter.set(0)
        finalAcceptedCounter.set(0)
        workerCounter.set(0)
        bestLatency.set(Int.MAX_VALUE)
    }

    private fun measureTlsHandshake(ip: String, port: Int, sni: String, timeoutMs: Int): TlsResult {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val duration = System.currentTimeMillis() - start
                TlsResult(true, duration)
            }
        } catch (e: Exception) {
            TlsResult(false, 0, errorMessage = e.message)
        }
    }

    private suspend fun runXrayTransportValidation(
        prelim: CloudflareScanResult,
        config: Config,
        speedTestSizeKb: Int,
        profile: ScannerProfile
    ): CloudflareScanResult {
        val workerId = Thread.currentThread().id
        val testProxyPort = 20000 + Random.nextInt(10000)
        val tempDir = File(context.cacheDir, "xray_test_${prelim.ip.replace(".", "_").replace(":", "_")}")
        if (!tempDir.exists()) tempDir.mkdirs()
        
        Log.d(TAG, "[Worker $workerId] Starting Xray validation for ${prelim.ip} on port $testProxyPort")
        
        // FIX (Bug #2): Removed runBlocking{} wrapper — it caused thread starvation deadlock
        // when 64+ concurrent scans were running and all IO threads were saturated.
        // settingsRepository.settings.first() is already a suspend function; calling it
        // directly (without runBlocking) is correct inside a suspend function.
        val settings = settingsRepository.settings.first()
        val testConfigJson = buildTestConfig(prelim.ip, prelim.port, config, testProxyPort, settings.logLevel)
        val configFile = File(tempDir, "config.json")
        configFile.writeText(testConfigJson)
        
        val binary = binaryManager.getXrayBinary()
        if (!binary.exists()) {
            Log.e(TAG, "[Worker $workerId] Xray binary not found at ${binary.absolutePath}")
            return prelim.copy(isAlive = false, stage = ScanStage.FAILED, errorMessage = "Binary not found")
        }
        
        val assetsDir = binaryManager.xrayDirectory
        if (!File(assetsDir, "geoip.dat").exists() || !File(assetsDir, "geosite.dat").exists()) {
            Log.w(TAG, "[Worker $workerId] Geo assets missing in ${assetsDir.absolutePath}")
        }

        return withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                Log.d(TAG, "[Worker $workerId] Launching Xray core: ${binary.absolutePath} with config ${configFile.absolutePath}")
                val pb = ProcessBuilder(binary.absolutePath, "run", "-c", configFile.absolutePath)
                pb.directory(tempDir)
                pb.environment()["XRAY_LOCATION_ASSET"] = assetsDir.absolutePath
                process = pb.start()
                
                // Readiness Polling
                val isReady = waitForLocalProxy(testProxyPort, workerId)
                if (!isReady) {
                    Log.e(TAG, "[Worker $workerId] Xray failed to bind to 127.0.0.1:$testProxyPort within timeout")
                    return@withContext prelim.copy(isAlive = false, stage = ScanStage.FAILED, errorMessage = context.getString(R.string.configs_cf_error_init))
                }

                val proxyClient = httpClient.newBuilder()
                    .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", testProxyPort)))
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                // Measure Real Delay through Proxy
                Log.d(TAG, "[Worker $workerId] Starting transport test for ${prelim.ip}")
                val startReal = System.currentTimeMillis()
                val traceRequest = Request.Builder().url("https://cp.cloudflare.com/generate_204").build()
                var realDelay: Long = 0
                try {
                    proxyClient.newCall(traceRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "[Worker $workerId] Transport test failed for ${prelim.ip} with code ${response.code}")
                            return@withContext prelim.copy(isAlive = false, stage = ScanStage.FAILED, errorMessage = context.getString(R.string.configs_cf_error_transport) + " (${response.code})")
                        }
                        realDelay = System.currentTimeMillis() - startReal
                        Log.d(TAG, "[Worker $workerId] Transport test successful for ${prelim.ip}, delay: ${realDelay}ms")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[Worker $workerId] Transport test exception for ${prelim.ip}: ${e.message}")
                    return@withContext prelim.copy(isAlive = false, stage = ScanStage.FAILED, errorMessage = context.getString(R.string.configs_cf_error_transport) + ": ${e.message}")
                }
                
                // Speed Test
                val speed = measureSpeedThroughProxy(proxyClient, speedTestSizeKb)
                
                val score = calculateScore(
                    stability = prelim.stability,
                    jitter = prelim.jitter,
                    latencyMs = realDelay,
                    handshakeTimeMs = prelim.handshakeTime,
                    downloadSpeed = speed
                )
                
                prelim.copy(
                    latencyMs = realDelay,
                    downloadSpeed = speed,
                    finalScore = score,
                    isAlive = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "[Worker $workerId] Critical failure during validation of ${prelim.ip}: ${e.message}")
                prelim.copy(isAlive = false, stage = ScanStage.FAILED, errorMessage = e.message ?: "Unknown error")
            } finally {
                Log.d(TAG, "[Worker $workerId] Cleaning up Xray instance for ${prelim.ip}")
                process?.destroy()
                tempDir.deleteRecursively()
            }
        }
    }

    private suspend fun waitForLocalProxy(port: Int, workerId: Long): Boolean {
        val start = System.currentTimeMillis()
        val timeout = 2000L
        val pollInterval = 50L
        
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                    Log.d(TAG, "[Worker $workerId] Local proxy listener ready on port $port after ${System.currentTimeMillis() - start}ms")
                    return true
                }
            } catch (_: Exception) {
                delay(pollInterval)
            }
        }
        return false
    }

    private suspend fun measureSpeedThroughProxy(client: OkHttpClient, sizeKb: Int): Float {
        if (sizeKb <= 0) return 0f
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val request = Request.Builder()
                    .url("https://speed.cloudflare.com/__down?bytes=${sizeKb * 1024}")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext 0f
                    val body = response.body ?: return@withContext 0f
                    val source = body.source()
                    var read = 0L
                    val buffer = ByteArray(8192)
                    while (true) {
                        val r = source.read(buffer)
                        if (r == -1) break
                        read += r
                    }
                    val duration = (System.currentTimeMillis() - start).toFloat() / 1000f
                    if (duration <= 0.1) 0f else (read / 1024f) / duration
                }
            } catch (_: Exception) { 0f }
        }
    }

    private fun buildTestConfig(ip: String, port: Int, base: Config, localPort: Int, logLevel: String): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", logLevel))
        
        val inbounds = JSONArray()
        inbounds.put(JSONObject()
            .put("protocol", "socks")
            .put("port", localPort)
            .put("listen", "127.0.0.1")
            .put("settings", JSONObject().put("udp", true)))
        root.put("inbounds", inbounds)
        
        val outbounds = JSONArray()
        val outbound = JSONObject()
        outbound.put("protocol", when(base.type) {
            ConfigType.VLESS -> "vless"
            ConfigType.VMESS -> "vmess"
            ConfigType.TROJAN -> "trojan"
            else -> "vless"
        })
        
        val settings = JSONObject()
        val user = JSONObject().put("id", base.userId)
        if (base.type == ConfigType.VLESS) {
            user.put("encryption", "none")
        }
        val server = JSONObject().put("address", ip).put("port", port).put("users", JSONArray().put(user))
        settings.put("vnext", JSONArray().put(server))
        outbound.put("settings", settings)
        
        val stream = JSONObject()
        val streamType = base.extraParams["type"] ?: "tcp"
        stream.put("network", streamType)
        stream.put("security", base.extraParams["security"] ?: "tls")
        
        if (stream.getString("security") == "tls") {
            stream.put("tlsSettings", JSONObject()
                .put("serverName", base.sni)
                .put("allowInsecure", true))
        }
        
        if (streamType == "ws") {
            stream.put("wsSettings", JSONObject()
                .put("path", base.extraParams["path"] ?: "/")
                .put("headers", JSONObject().put("Host", base.extraParams["host"] ?: base.sni)))
        } else if (streamType == "grpc") {
            stream.put("grpcSettings", JSONObject()
                .put("serviceName", base.extraParams["serviceName"] ?: ""))
        }
        
        outbound.put("streamSettings", stream)
        outbounds.put(outbound)
        
        outbounds.put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
        root.put("outbounds", outbounds)
        
        return root.toString()
    }

    private fun generateIpsFromCidr(cidr: String): List<String> {
        val ips = mutableListOf<String>()
        repeat(8) {
            ips.add(generateRandomIpInCidr(cidr))
        }
        return ips.distinct()
    }

    private fun generateRandomIpInCidr(cidr: String): String {
        return try {
            val parts = cidr.split("/")
            val base = parts[0]
            val prefix = parts.getOrNull(1)?.toInt() ?: 32
            
            if (base.contains(":")) {
                base
            } else {
                val octets = base.split(".").map { it.toInt() }.toMutableList()
                val hostBits = 32 - prefix
                if (hostBits <= 0) return base
                
                val maxHost = (1L shl hostBits) - 2
                if (maxHost <= 0) return base
                
                val randomHost = Random.nextLong(1, maxHost + 1)
                
                var remaining = randomHost
                for (i in 3 downTo 0) {
                    val add = (remaining % 256).toInt()
                    octets[i] = (octets[i] + add) % 256
                    remaining /= 256
                    if (remaining == 0L) break
                }
                octets.joinToString(".")
            }
        } catch (_: Exception) {
            cidr.substringBefore("/")
        }
    }

    data class TlsResult(
        val success: Boolean,
        val duration: Long,
        val errorMessage: String? = null
    )
}





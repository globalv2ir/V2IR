package com.v2ir.data.scanner

import android.content.Context
import com.v2ir.data.model.CloudflareScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerPersistence @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "cloudflare_scanner_state.json")

    data class ScannerState(
        val results: List<CloudflareScanResult> = emptyList(),
        val lastProfile: String = "NORMAL",
        val lastIpVersion: String = "IPV4",
        val lastConcurrency: Int = 32,
        val lastXrayConcurrency: Int = 5,
        val lastSpeedTestSize: Int = 512
    )

    suspend fun saveState(state: ScannerState) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val resultsArr = JSONArray()
            state.results.forEach { res ->
                val obj = JSONObject()
                obj.put("ip", res.ip)
                obj.put("port", res.port)
                obj.put("latency", res.latencyMs)
                obj.put("jitter", res.jitter)
                obj.put("loss", res.packetLoss.toDouble())
                obj.put("speed", res.downloadSpeed.toDouble())
                obj.put("handshake", res.handshakeTime)
                obj.put("connTime", res.connectionTime)
                obj.put("edge", res.edgeLocation)
                obj.put("country", res.countryCode)
                obj.put("score", res.finalScore)
                obj.put("stability", res.stability)
                obj.put("ipVer", res.ipVersion)
                obj.put("ts", res.timestamp)
                resultsArr.put(obj)
            }
            root.put("results", resultsArr)
            root.put("profile", state.lastProfile)
            root.put("ipVersion", state.lastIpVersion)
            root.put("concurrency", state.lastConcurrency)
            root.put("xrayConcurrency", state.lastXrayConcurrency)
            root.put("speedSize", state.lastSpeedTestSize)
            
            file.writeText(root.toString())
        } catch (_: Exception) {}
    }

    suspend fun loadState(): ScannerState? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val json = file.readText()
            val root = JSONObject(json)
            
            val results = mutableListOf<CloudflareScanResult>()
            val resultsArr = root.optJSONArray("results")
            if (resultsArr != null) {
                for (i in 0 until resultsArr.length()) {
                    val obj = resultsArr.getJSONObject(i)
                    results.add(CloudflareScanResult(
                        ip = obj.getString("ip"),
                        port = obj.getInt("port"),
                        latencyMs = obj.getLong("latency"),
                        jitter = obj.optLong("jitter", 0),
                        packetLoss = obj.optDouble("loss", 0.0).toFloat(),
                        downloadSpeed = obj.getDouble("speed").toFloat(),
                        handshakeTime = obj.optLong("handshake", 0),
                        connectionTime = obj.optLong("connTime", 0),
                        edgeLocation = obj.optString("edge", ""),
                        countryCode = obj.optString("country", ""),
                        finalScore = obj.getInt("score"),
                        stability = obj.optInt("stability", 0),
                        ipVersion = obj.optInt("ipVer", 4),
                        timestamp = obj.optLong("ts", System.currentTimeMillis()),
                        isAlive = true
                    ))
                }
            }
            
            ScannerState(
                results = results,
                lastProfile = root.optString("profile", "NORMAL"),
                lastIpVersion = root.optString("ipVersion", "IPV4"),
                lastConcurrency = root.optInt("concurrency", 32),
                lastXrayConcurrency = root.optInt("xrayConcurrency", 5),
                lastSpeedTestSize = root.optInt("speedSize", 512)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearState() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
    }
}





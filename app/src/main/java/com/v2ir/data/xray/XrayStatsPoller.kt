package com.v2ir.data.xray

import com.v2ir.data.model.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayStatsPoller @Inject constructor(
    // FIX (Bug #10): Inject OkHttpClient instead of constructing a separate instance.
    // Creating a new OkHttpClient internally wastes resources (separate thread pool,
    // connection pool) and creates inconsistent timeout settings across the app.
    // We override timeouts per-call where needed using newBuilder().
    private val httpClient: OkHttpClient
) {

    // Stats-specific client with shorter timeouts — derived from the shared client
    // to inherit proxy, interceptors, and other shared configuration.
    private val statsClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    private var lastDownBytes = 0L
    private var lastUpBytes = 0L
    private var lastSampleTime = 0L

    suspend fun poll(): TrafficStats? = withContext(Dispatchers.IO) {
        if (XrayNativeBridge.isAvailable()) {
            val native = XrayNativeBridge.queryStats()
            // FIX (Bug #29): Validate array size before indexing.
            // The stub returns a zero-length array; the real library may return 2 or 4 elements.
            // Accessing native[2] or native[3] on a 2-element array throws AIOOBE.
            if (native.size >= 4 && (native[0] > 0 || native[1] > 0)) {
                return@withContext TrafficStats(
                    downloadBytes = native[0],
                    uploadBytes = native[1],
                    downloadSpeed = native[2].toFloat(),
                    uploadSpeed = native[3].toFloat()
                )
            } else if (native.size >= 2 && (native[0] > 0 || native[1] > 0)) {
                // Real library returning only bytes (no speed) — fall through for speed calculation
                // by using the Traffic/API fallback below.
            }
        }

        // Fallback to TrafficStats for reliability
        val trafficStats = pollViaTrafficStats()
        if (trafficStats.downloadBytes > 0 || trafficStats.uploadBytes > 0) {
            return@withContext trafficStats
        }

        pollViaApi()
    }

    private fun pollViaTrafficStats(): TrafficStats {
        val uid = android.os.Process.myUid()
        val rx = android.net.TrafficStats.getUidRxBytes(uid)
        val tx = android.net.TrafficStats.getUidTxBytes(uid)

        if (rx == android.net.TrafficStats.UNSUPPORTED.toLong()) return TrafficStats()

        val now = System.currentTimeMillis()
        val elapsed = if (lastSampleTime > 0) (now - lastSampleTime).coerceAtLeast(1) else 1000L

        val dlSpeed = if (lastSampleTime > 0 && rx >= lastDownBytes) {
            ((rx - lastDownBytes) * 1000f / elapsed)
        } else 0f
        val ulSpeed = if (lastSampleTime > 0 && tx >= lastUpBytes) {
            ((tx - lastUpBytes) * 1000f / elapsed)
        } else 0f

        lastDownBytes = rx
        lastUpBytes = tx
        lastSampleTime = now

        return TrafficStats(
            downloadBytes = rx,
            uploadBytes = tx,
            downloadSpeed = dlSpeed / 1024f,
            uploadSpeed = ulSpeed / 1024f
        )
    }

    private fun pollViaApi(): TrafficStats? {
        return try {
            val body = """{"pattern":"","reset":false}""".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://127.0.0.1:${V2irConstants.API_PORT}/stats/query")
                .post(body)
                .build()
            val response = statsClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val stat = json.optJSONArray("stat") ?: return null

            var down = 0L
            var up = 0L
            for (i in 0 until stat.length()) {
                val item = stat.getJSONObject(i)
                val name = item.optString("name", "")
                val value = item.optLong("value", 0L)
                if (name.contains("api", ignoreCase = true)) continue

                when {
                    name.contains("downlink", ignoreCase = true) -> down += value
                    name.contains("uplink", ignoreCase = true) -> up += value
                }
            }

            val now = System.currentTimeMillis()
            val elapsed = if (lastSampleTime > 0) (now - lastSampleTime).coerceAtLeast(1) else 1000L
            val dlSpeed = if (lastSampleTime > 0) {
                ((down - lastDownBytes).coerceAtLeast(0) * 1000f / elapsed)
            } else 0f
            val ulSpeed = if (lastSampleTime > 0) {
                ((up - lastUpBytes).coerceAtLeast(0) * 1000f / elapsed)
            } else 0f

            lastDownBytes = down
            lastUpBytes = up
            lastSampleTime = now

            TrafficStats(
                downloadBytes = down,
                uploadBytes = up,
                downloadSpeed = dlSpeed / 1024f,
                uploadSpeed = ulSpeed / 1024f
            )
        } catch (_: Exception) {
            null
        }
    }

    fun reset() {
        lastDownBytes = 0L
        lastUpBytes = 0L
        lastSampleTime = 0L
    }
}





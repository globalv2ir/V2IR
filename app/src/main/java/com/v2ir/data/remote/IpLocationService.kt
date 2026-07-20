package com.v2ir.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpLocationService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    // FIX: Use ConcurrentHashMap instead of plain mutableMapOf() to prevent
    // data races when getCountryCode() is called concurrently from multiple coroutines
    // (e.g., during parallel subscription parsing).
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun getCountryCode(ip: String): String = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext ""
        cache[ip]?.let { return@withContext it }

        // 1. Offline Check for Iran — no network needed
        if (isIranIp(ip)) {
            cache[ip] = "IR"
            return@withContext "IR"
        }

        // 2. Online Check via HTTPS
        val info = getIpInfo(ip)
        val code = info?.countryCode.orEmpty()
        if (code.isNotBlank()) cache[ip] = code
        return@withContext code
    }

    suspend fun getIpInfo(ip: String = ""): IpInfo? = withContext(Dispatchers.IO) {
        try {
            // FIX (Bug #24): Changed http:// → https:// — plain HTTP is blocked by Android 9+
            // network security policy (cleartext traffic not permitted by default).
            val url = if (ip.isBlank()) "https://ip-api.com/json/?fields=status,country,countryCode,query,as"
                      else "https://ip-api.com/json/$ip?fields=status,country,countryCode,query,as"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.optString("status") == "success") {
                        return@withContext IpInfo(
                            query = json.optString("query"),
                            country = json.optString("country"),
                            countryCode = json.optString("countryCode"),
                            isp = json.optString("as")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Don't crash — location info is non-critical
            e.printStackTrace()
        }
        null
    }

    data class IpInfo(
        val query: String,
        val country: String,
        val countryCode: String,
        val isp: String
    )

    private fun isIranIp(ip: String): Boolean {
        return try {
            val address = InetAddress.getByName(ip)
            val ipLong = ByteBuffer.wrap(address.address).int.toLong() and 0xFFFFFFFFL
            irRanges.any { it.contains(ipLong) }
        } catch (e: Exception) {
            false
        }
    }

    private data class IpRange(val start: Long, val end: Long) {
        fun contains(ip: Long) = ip in start..end
    }

    private val irRanges = listOf(
        parseCidr("2.144.0.0/14"),
        parseCidr("5.72.0.0/13"),
        parseCidr("31.24.0.0/14"),
        parseCidr("37.10.0.0/15"),
        parseCidr("77.36.0.0/14"),
        parseCidr("78.38.0.0/15"),
        parseCidr("79.127.0.0/16"),
        parseCidr("80.191.0.0/16"),
        parseCidr("85.185.0.0/16"),
        parseCidr("89.165.0.0/16"),
        parseCidr("91.98.0.0/15"),
        parseCidr("92.242.0.0/16"),
        parseCidr("94.182.0.0/15"),
        parseCidr("95.38.0.0/16"),
        parseCidr("151.232.0.0/14"),
        parseCidr("176.101.0.0/16"),
        parseCidr("178.131.0.0/16"),
        parseCidr("185.88.0.0/14"),
        parseCidr("188.158.0.0/15")
    )

    private fun parseCidr(cidr: String): IpRange {
        val parts = cidr.split("/")
        val ip = parts[0]
        val prefix = parts[1].toInt()
        val address = InetAddress.getByName(ip)
        val ipLong = ByteBuffer.wrap(address.address).int.toLong() and 0xFFFFFFFFL
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val start = ipLong and mask
        val end = start or (mask.inv() and 0xFFFFFFFFL)
        return IpRange(start, end)
    }
}





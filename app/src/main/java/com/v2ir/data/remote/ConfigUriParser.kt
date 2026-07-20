package com.v2ir.data.remote

import android.util.Base64
import com.v2ir.data.cloudflare.CloudflareIpDatabase
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConfigType
import org.json.JSONObject
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigUriParser @Inject constructor(
    private val cloudflareIpDatabase: CloudflareIpDatabase,
    private val ipLocationService: IpLocationService
) {

    suspend fun parseSubscriptionBody(body: String, subscriptionId: Long): List<Config> = coroutineScope {
        val decoded = decodeSubscriptionBody(body.trim())
        val rawLines = decoded.split(Regex("[\\n\\r\\s|]+"))

        // Limit concurrency to avoid thread exhaustion on large subscriptions.
        // DNS resolution inside parseSingle can be slow on restricted networks —
        // too many parallel calls would saturate the resolver.
        val semaphore = Semaphore(10)

        rawLines.map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        try {
                            parseSingle(line, subscriptionId)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null // Skip malformed lines silently
                        }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    suspend fun parseSingle(input: String, subscriptionId: Long? = null): Config? {
        val line = input.trim()
        val config = when {
            line.startsWith("vmess://") -> parseVmess(line, subscriptionId)
            line.startsWith("vless://") -> parseVless(line, subscriptionId)
            line.startsWith("trojan://") -> parseTrojan(line, subscriptionId)
            line.startsWith("ss://") -> parseShadowsocks(line, subscriptionId)
            line.startsWith("hysteria2://") || line.startsWith("hy2://") -> parseHysteria2(line, subscriptionId)
            line.startsWith("tuic://") -> parseTuic(line, subscriptionId)
            line.contains("://") && line.startsWith("http") -> null
            else -> tryDecodeAsUri(line, subscriptionId)
        }
        
        return if (config != null) {
            // enrichLocation(config) // V2IR: Removed from import pipeline for performance
            config
        } else null
    }

    private suspend fun enrichLocation(config: Config): Config {
        val ip = config.address
        if (ip.isBlank()) return config
        
        var country = ipLocationService.getCountryCode(ip)
        
        if (country == "IR") {
            // Possible Tunnel. Try to resolve SNI/Host
            val sni = config.sni.ifBlank { queryParam(config.rawUri, "sni") ?: queryParam(config.rawUri, "host") ?: "" }
            if (sni.isNotBlank() && !sni.first().isDigit()) {
                try {
                    val resolvedIp = withContext(Dispatchers.IO) {
                        InetAddress.getByName(sni).hostAddress
                    }
                    if (resolvedIp != null) {
                        val destinationCountry = ipLocationService.getCountryCode(resolvedIp)
                        if (destinationCountry.isNotBlank() && destinationCountry != "IR") {
                            return config.copy(
                                countryLabel = "${getFlagEmoji(destinationCountry)} Tunnel 🇮🇷",
                                realLatency = -2L // Special flag for UI
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Resolution failed or same IR IP
                }
            }
            return config.copy(countryLabel = "❓ Tunnel 🇮🇷")
        }

        return if (country.isNotBlank()) {
            config.copy(countryLabel = "${getFlagEmoji(country)} $country")
        } else {
            config
        }
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "❓"
        val code = countryCode.uppercase()
        val firstLetter = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }

    private fun decodeSubscriptionBody(body: String): String {
        var current = body
        repeat(3) {
            val decoded = tryDecodeBase64(current)
            if (decoded == current) return current
            current = decoded
        }
        return current
    }

    private fun tryDecodeBase64(input: String): String {
        val clean = input.replace("\n", "").replace("\r", "").replace(" ", "")
        if (clean.length < 4) return input
        
        // Add padding if missing
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        
        return try {
            // Try DEFAULT then URL_SAFE
            val decodedBytes = try {
                Base64.decode(padded, Base64.DEFAULT)
            } catch (e: Exception) {
                Base64.decode(padded, Base64.URL_SAFE)
            }
            
            val decoded = String(decodedBytes)
            // If it contains non-printable characters, it might not be text
            if (decoded.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }) {
                input
            } else {
                decoded
            }
        } catch (_: Exception) {
            input
        }
    }

    private suspend fun tryDecodeAsUri(line: String, subscriptionId: Long?): Config? {
        val decoded = tryDecodeBase64(line)
        return if (decoded.contains("://")) parseSingle(decoded, subscriptionId) else null
    }

    private fun parseVless(uri: String, subscriptionId: Long?): Config? {
        val withoutScheme = uri.removePrefix("vless://")
        val hashIdx = withoutScheme.indexOf('#')
        val fragment = if (hashIdx >= 0) decodeURIComponent(withoutScheme.substring(hashIdx + 1)) else ""
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val qIdx = main.indexOf('?')
        val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
        val authority = if (qIdx >= 0) main.substring(0, qIdx) else main

        val atIdx = authority.indexOf('@')
        val userId = if (atIdx >= 0) authority.substring(0, atIdx) else ""
        if (!isValidUuid(userId)) return null

        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority
        val colonIdx = hostPort.lastIndexOf(':')
        val address = if (colonIdx >= 0) hostPort.substring(0, colonIdx) else hostPort
        val port = if (colonIdx >= 0) hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        
        // Fronting Detection Logic: Extract SNI from query params
        val sni = queryParam(query, "sni") ?: queryParam(query, "host") ?: ""

        return buildConfig(
            name = fragment.ifBlank { address },
            address = address,
            port = port,
            userId = userId,
            type = ConfigType.VLESS,
            subscriptionId = subscriptionId,
            sni = sni,
            rawUri = uri
        )
    }

    private fun parseTrojan(uri: String, subscriptionId: Long?): Config? {
        val decodedUri = decodeURIComponent(uri)
        val withoutScheme = decodedUri.removePrefix("trojan://")
        val hashIdx = withoutScheme.indexOf('#')
        val fragment = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else ""
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val qIdx = main.indexOf('?')
        val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
        val authority = if (qIdx >= 0) main.substring(0, qIdx) else main
        
        val atIdx = authority.indexOf('@')
        val password = if (atIdx >= 0) authority.substring(0, atIdx) else ""
        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority
        val colonIdx = hostPort.lastIndexOf(':')
        val host = if (colonIdx >= 0) hostPort.substring(0, colonIdx) else hostPort
        val port = if (colonIdx >= 0) hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val sni = queryParam(query, "sni") ?: queryParam(query, "host") ?: host
        
        return buildConfig(fragment.ifBlank { host }, host, port, password, ConfigType.TROJAN, subscriptionId, sni, uri)
    }

    private fun parseShadowsocks(uri: String, subscriptionId: Long?): Config? {
        val withoutFragment = uri.substringBefore('#')
        val name = uri.substringAfter('#', "Shadowsocks").let { decodeURIComponent(it) }
        val body = withoutFragment.removePrefix("ss://")

        return try {
            if (body.contains('@')) {
                val atSplit = body.split('@', limit = 2)
                val userPart = try { String(Base64.decode(atSplit[0], Base64.DEFAULT or Base64.NO_WRAP)) } catch(e: Exception) { atSplit[0] }
                val hostPart = atSplit[1]
                val colonIdx = hostPart.lastIndexOf(':')
                val host = if (colonIdx >= 0) hostPart.substring(0, colonIdx) else hostPart
                val port = if (colonIdx >= 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 8388 else 8388
                buildConfig(name, host, port, userPart, ConfigType.SHADOWSOCKS, subscriptionId, host, uri)
            } else {
                val decoded = String(Base64.decode(body, Base64.DEFAULT or Base64.NO_WRAP))
                val atIdx = decoded.lastIndexOf('@')
                if (atIdx < 0) return null
                val userPart = decoded.substring(0, atIdx)
                val hostPart = decoded.substring(atIdx + 1)
                val colonIdx = hostPart.lastIndexOf(':')
                val host = if (colonIdx >= 0) hostPart.substring(0, colonIdx) else hostPart
                val port = if (colonIdx >= 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 8388 else 8388
                buildConfig(name, host, port, userPart, ConfigType.SHADOWSOCKS, subscriptionId, host, uri)
            }
        } catch (_: Exception) { null }
    }

    private fun parseVmess(uri: String, subscriptionId: Long?): Config? {
        val encoded = uri.removePrefix("vmess://")
        val json = try {
            String(Base64.decode(encoded, Base64.DEFAULT or Base64.NO_WRAP))
        } catch (_: Exception) {
            return null
        }
        val jsonObj = JSONObject(json)
        val host = jsonObj.optString("add")
        val port = jsonObj.optInt("port", 443)
        val name = jsonObj.optString("ps", host)
        val id = jsonObj.optString("id")
        if (!isValidUuid(id)) return null

        val sni = jsonObj.optString("sni", jsonObj.optString("host", host))
        return buildConfig(name, host, port, id, ConfigType.VMESS, subscriptionId, sni, uri)
    }

    private fun parseHysteria2(uri: String, subscriptionId: Long?): Config? {
        val withoutScheme = if (uri.startsWith("hysteria2://")) uri.removePrefix("hysteria2://") else uri.removePrefix("hy2://")
        val hashIdx = withoutScheme.indexOf('#')
        val fragment = if (hashIdx >= 0) decodeURIComponent(withoutScheme.substring(hashIdx + 1)) else ""
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val qIdx = main.indexOf('?')
        val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
        val authority = if (qIdx >= 0) main.substring(0, qIdx) else main

        val atIdx = authority.indexOf('@')
        val auth = if (atIdx >= 0) authority.substring(0, atIdx) else ""
        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority
        val colonIdx = hostPort.lastIndexOf(':')
        val host = if (colonIdx >= 0) hostPort.substring(0, colonIdx) else hostPort
        val port = if (colonIdx >= 0) hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val sni = queryParam(query, "sni") ?: queryParam(query, "host") ?: host

        return buildConfig(fragment.ifBlank { host }, host, port, auth, ConfigType.HYSTERIA2, subscriptionId, sni, uri)
    }

    private fun parseTuic(uri: String, subscriptionId: Long?): Config? {
        val withoutScheme = uri.removePrefix("tuic://")
        val hashIdx = withoutScheme.indexOf('#')
        val fragment = if (hashIdx >= 0) decodeURIComponent(withoutScheme.substring(hashIdx + 1)) else ""
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val qIdx = main.indexOf('?')
        val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
        val authority = if (qIdx >= 0) main.substring(0, qIdx) else main

        val atIdx = authority.indexOf('@')
        val userPart = if (atIdx >= 0) authority.substring(0, atIdx) else ""
        val userId = userPart.substringBefore(':')
        val password = if (userPart.contains(':')) userPart.substringAfter(':') else ""
        
        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority
        val colonIdx = hostPort.lastIndexOf(':')
        val host = if (colonIdx >= 0) hostPort.substring(0, colonIdx) else hostPort
        val port = if (colonIdx >= 0) hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val sni = queryParam(query, "sni") ?: queryParam(query, "host") ?: host

        val config = buildConfig(fragment.ifBlank { host }, host, port, userId, ConfigType.TUIC, subscriptionId, sni, uri)
        if (password.isNotBlank()) {
            val mutableParams = config.extraParams.toMutableMap()
            mutableParams["pass"] = password
            return config.copy(extraParams = mutableParams)
        }
        return config
    }

    private fun buildConfig(
        name: String,
        address: String,
        port: Int,
        userId: String,
        type: ConfigType,
        subscriptionId: Long?,
        sni: String,
        rawUri: String
    ): Config {
        val extraParams = mutableMapOf<String, String>()
        val query = rawUri.substringAfter('?', "").substringBefore('#')
        if (query.isNotBlank()) {
            query.split('&').forEach { part ->
                val kv = part.split('=', limit = 2)
                if (kv.size == 2) {
                    extraParams[kv[0].lowercase()] = decodeURIComponent(kv[1])
                }
            }
        }
        
        // Specialized logic for VMess JSON
        if (type == ConfigType.VMESS) {
            try {
                val encoded = rawUri.removePrefix("vmess://")
                val json = JSONObject(String(Base64.decode(encoded, Base64.DEFAULT or Base64.NO_WRAP)))
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    extraParams[key.lowercase()] = json.optString(key)
                }
            } catch (_: Exception) {}
        }

        return Config(
            name = name,
            address = address,
            port = port,
            userId = userId,
            type = type,
            subscriptionId = subscriptionId,
            sni = sni,
            rawUri = rawUri,
            countryLabel = extractCountryLabel(name),
            isCloudflare = cloudflareIpDatabase.detectCloudflare(address, sni),
            extraParams = extraParams
        )
    }

    private fun extractCountryLabel(name: String): String {
        val flagMatch = Regex("""[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]""").find(name)
        if (flagMatch != null) return flagMatch.value
        val codeMatch = Regex("""\b([A-Z]{2})\b""").find(name)
        return codeMatch?.value.orEmpty()
    }

    private fun queryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        return query.split('&').mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2 && kv[0].equals(key, ignoreCase = true)) decodeURIComponent(kv[1]) else null
        }.firstOrNull()
    }

    private fun decodeURIComponent(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }

    private fun isValidUuid(uuid: String): Boolean {
        if (uuid.isBlank()) return false
        return try {
            java.util.UUID.fromString(uuid.trim())
            true
        } catch (_: Exception) {
            false
        }
    }
}





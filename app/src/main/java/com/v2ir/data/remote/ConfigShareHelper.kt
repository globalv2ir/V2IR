package com.v2ir.data.remote

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConfigType
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigShareHelper @Inject constructor() {

    /**
     * Convert a Config to its standard URI string for sharing.
     * Prefers rawUri if available (preserves original transport params).
     * Falls back to reconstructing from stored fields.
     */
    fun toShareText(config: Config): String {
        // If original URI is available, use it directly — most accurate
        if (config.rawUri.isNotBlank() && config.type != ConfigType.SUBSCRIPTION) {
            return config.rawUri
        }
        return when (config.type) {
            ConfigType.VLESS -> buildVlessUri(config)
            ConfigType.TROJAN -> buildTrojanUri(config)
            ConfigType.VMESS -> buildVmessUri(config)
            ConfigType.SHADOWSOCKS -> buildShadowsocksUri(config)
            ConfigType.HYSTERIA2 -> buildHysteria2Uri(config)
            ConfigType.TUIC -> buildTuicUri(config)
            ConfigType.SUBSCRIPTION -> config.subscriptionUrl
        }
    }

    fun toQrBitmap(text: String, size: Int = 512): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    // ─── Private URI builders ────────────────────────────────────────────────

    private fun buildVlessUri(config: Config): String {
        val query = if (config.extraParams.isNotEmpty()) {
            "?" + config.extraParams.entries.joinToString("&") {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            }
        } else {
            "?encryption=none&security=tls&sni=${urlEncode(config.sni)}&type=tcp"
        }
        return "vless://${config.userId}@${config.address}:${config.port}$query#${urlEncode(config.name)}"
    }

    private fun buildTrojanUri(config: Config): String {
        val query = if (config.extraParams.isNotEmpty()) {
            "?" + config.extraParams.entries.joinToString("&") {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            }
        } else {
            "?security=tls&sni=${urlEncode(config.sni)}&type=tcp"
        }
        return "trojan://${config.userId}@${config.address}:${config.port}$query#${urlEncode(config.name)}"
    }

    private fun buildVmessUri(config: Config): String {
        // Build VMess JSON with all available transport params from extraParams
        val net = config.extraParams["type"] ?: config.extraParams["network"] ?: "tcp"
        val host = config.extraParams["host"] ?: ""
        val path = config.extraParams["path"] ?: ""
        val tls = config.extraParams["security"] ?: if (config.sni.isNotBlank()) "tls" else ""
        val sni = config.extraParams["sni"] ?: config.sni

        val json = """{"v":"2","ps":"${config.name}","add":"${config.address}","port":"${config.port}","id":"${config.userId}","aid":"0","net":"$net","type":"none","host":"$host","path":"$path","tls":"$tls","sni":"$sni"}"""
        val encoded = android.util.Base64.encodeToString(json.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return "vmess://$encoded"
    }

    private fun buildShadowsocksUri(config: Config): String {
        // SS URI: ss://BASE64(method:password)@host:port#name
        // userId stores "method:password"
        val userInfo = android.util.Base64.encodeToString(
            config.userId.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "ss://$userInfo@${config.address}:${config.port}#${urlEncode(config.name)}"
    }

    private fun buildHysteria2Uri(config: Config): String {
        val query = if (config.extraParams.isNotEmpty()) {
            "?" + config.extraParams.entries.joinToString("&") {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            }
        } else if (config.sni.isNotBlank()) {
            "?sni=${urlEncode(config.sni)}"
        } else ""
        return "hysteria2://${config.userId}@${config.address}:${config.port}$query#${urlEncode(config.name)}"
    }

    private fun buildTuicUri(config: Config): String {
        val query = if (config.extraParams.isNotEmpty()) {
            "?" + config.extraParams.entries.joinToString("&") {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            }
        } else ""
        return "tuic://${config.userId}@${config.address}:${config.port}$query#${urlEncode(config.name)}"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

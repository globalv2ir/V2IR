package com.v2ir.data.xray

import android.content.Context
import java.io.File

/**
 * Bridge to hev-socks5-tunnel — high-performance TUN → SOCKS5 relay.
 *
 * Uses the prebuilt AAR from JitPack (com.github.heiher:hev-socks5-tunnel),
 * identical to how v2rayNG integrates it — no NDK compile required.
 *
 * The AAR exposes a JNI interface with these native methods:
 *   - TProxyStartService(configPath: String, fd: Int)
 *   - TProxyStopService()
 *   - TProxyGetStats(): LongArray? → [downBytes, upBytes]
 *
 * The native library inside the AAR is named "hev-socks5-tunnel".
 */
object HevTunnelBridge {

    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            libraryLoaded = false
        }
    }

    fun isAvailable(): Boolean = libraryLoaded

    // --- Native JNI declarations (provided by AAR) ---

    @JvmStatic
    private external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    private external fun TProxyStopService()

    @JvmStatic
    private external fun TProxyGetStats(): LongArray?

    // --- Public API ---

    /**
     * Start the TUN→SOCKS5 relay.
     * @param context  used to write the YAML config to filesDir
     * @param tunFd    file descriptor of the established VPN TUN interface
     * @return true if started successfully
     */
    fun start(context: Context, tunFd: Int): Boolean {
        if (!libraryLoaded) return false
        // FIX (Bug #19): Wrap config file write in try/catch and log the error.
        // Previously, an IOException here was silently swallowed by the outer runCatching
        // in the caller, returning false with no log entry — impossible to debug.
        val configFile = try {
            File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
                writeText(buildYamlConfig())
            }
        } catch (e: Exception) {
            android.util.Log.e("HevTunnelBridge", "Failed to write tunnel config: ${e.message}", e)
            return false
        }
        return try {
            TProxyStartService(configFile.absolutePath, tunFd)
            true
        } catch (e: Exception) {
            android.util.Log.e("HevTunnelBridge", "TProxyStartService failed: ${e.message}", e)
            false
        }
    }

    /** Stop the TUN→SOCKS5 relay. Safe to call even if not started. */
    fun stop() {
        if (!libraryLoaded) return
        runCatching { TProxyStopService() }
    }

    /**
     * Query traffic statistics.
     * @return LongArray[downBytes, upBytes] or null if unavailable
     */
    fun queryStats(): LongArray? {
        if (!libraryLoaded) return null
        return runCatching { TProxyGetStats() }.getOrNull()
    }

    /**
     * Build the YAML config for hev-socks5-tunnel.
     * Matches the format documented at: https://github.com/heiher/hev-socks5-tunnel
     */
    private fun buildYamlConfig(): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: ${V2irConstants.VPN_MTU}")
        appendLine("  ipv4: ${V2irConstants.VPN_ADDRESS}")
        appendLine("  ipv6: fd00:1::2")
        appendLine("socks5:")
        appendLine("  port: ${V2irConstants.SOCKS_PORT}")
        appendLine("  address: 127.0.0.1")
        appendLine("  udp: 'udp'")
        appendLine("misc:")
        appendLine("  tcp-read-write-timeout: 300000")
        appendLine("  udp-read-write-timeout: 60000")
        appendLine("  log-level: warn")
    }
}

package com.v2ir.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.v2ir.MainActivity
import com.v2ir.R
import com.v2ir.data.model.LogMessage
import com.v2ir.data.repository.LogRepository
import com.v2ir.data.xray.HevTunnelBridge
import com.v2ir.data.xray.VpnConnectionManager
import com.v2ir.data.xray.XrayBinaryManager
import com.v2ir.data.xray.V2irConstants
import com.v2ir.data.xray.XrayController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class V2irVpnService : VpnService() {

    @Inject lateinit var xrayController: XrayController
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var binaryManager: XrayBinaryManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null
    private var statsJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private val networkCallbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>(null)

    private var lastDownBytes = 0L
    private var lastUpBytes = 0L
    private var lastStatsTime = 0L

    override fun onCreate() {
        super.onCreate()
        xrayController.onCoreCrashed = {
            handleUnexpectedDisconnect(R.string.log_msg_core_unexpected_stop)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                connectionJob?.cancel()
                serviceScope.launch { tearDownAll() }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON)
                    ?: vpnConnectionManager.pendingConfigJson
                if (configJson.isNullOrBlank()) {
                    handleUnexpectedDisconnect(R.string.log_msg_no_config)
                    return START_NOT_STICKY
                }
                
                connectionJob?.cancel()
                connectionJob = serviceScope.launch { startVpnTunnel(configJson) }

                registerNetworkMonitor()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        networkCallbackRef.get()?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        serviceScope.cancel()
        tearDownSync()
        super.onDestroy()
    }

    override fun onRevoke() {
        // FIX (Bug #3): onRevoke() can be called when serviceScope is already cancelled
        // (e.g. when the OS kills the app). Must tear down synchronously here, not via coroutine,
        // to guarantee Xray process and TUN are cleaned up regardless of scope state.
        tearDownSync()
        vpnConnectionManager.setDisconnected(R.string.log_msg_vpn_revoked)
        logRepository.addRawSystem("VPN permission revoked by system", "VPN")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    private suspend fun startVpnTunnel(configJson: String) {
        logRepository.addRawSystem("Starting VPN Tunnel flow...", "VPN")
        if (!HevTunnelBridge.isAvailable()) {
            logRepository.addRawSystem("Error: HevTunnelBridge library not loaded!", "VPN")
            handleUnexpectedDisconnect(R.string.log_msg_tunnel_missing)
            return
        }

        vpnConnectionManager.setConnecting()
        binaryManager.ensureXray()

        logRepository.addRawSystem("Starting Xray Core...", "VPN")
        val coreStarted = xrayController.startAndAwait(configJson)
        if (!coreStarted) {
            logRepository.addRawSystem("Error: Xray Core failed to start!", "VPN")
            handleUnexpectedDisconnect(R.string.log_msg_xray_start_failed)
            return
        }

        // Wait for core to initialize and open ports
        var retryCount = 0
        while (retryCount < 5 && !xrayController.isProcessAlive()) {
            delay(200)
            retryCount++
        }

        logRepository.addRawSystem("Establishing TUN interface...", "VPN")

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(V2irConstants.VPN_MTU)
            .addAddress(V2irConstants.VPN_ADDRESS, 30)
            .addRoute(V2irConstants.VPN_ROUTE, 0)
            .addDnsServer(V2irConstants.VPN_DNS)
            // IPv6 Support to prevent leaks
            .addAddress("fd00:1::2", 126)
            .addRoute("::", 0)
            .setBlocking(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        runCatching { 
            builder.addDisallowedApplication(packageName) 
            logRepository.addRawSystem("Exempted $packageName from VPN tunnel", "VPN")
        }

        tunInterface = try {
            builder.establish()
        } catch (e: Exception) {
            logRepository.addRawSystem("Failed to establish TUN: ${e.message}", "VPN")
            null
        }

        if (tunInterface == null) {
            logRepository.addRawSystem("Error: tunInterface is null!", "VPN")
            xrayController.stopAndAwait()
            handleUnexpectedDisconnect(R.string.log_msg_vpn_establish_failed)
            return
        }

        logRepository.addRawSystem("Starting HevTunnel (TUN -> SOCKS5)...", "VPN")
        val tunStarted = HevTunnelBridge.start(this, tunInterface!!.fd)
        if (!tunStarted) {
            logRepository.addRawSystem("Error: HevTunnel failed to start!", "VPN")
            xrayController.stopAndAwait()
            closeTun()
            handleUnexpectedDisconnect(R.string.log_msg_tunnel_failed)
            return
        }

        vpnConnectionManager.setConnected()
        logRepository.add(LogMessage(R.string.log_msg_connected, tagRes = R.string.logs_tag_connection))
        startStatsPolling()
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            // Reset base values to avoid jump in stats on reconnect
            val initialStats = HevTunnelBridge.queryStats()
            if (initialStats != null && initialStats.size >= 2) {
                lastDownBytes = initialStats[0]
                lastUpBytes = initialStats[1]
            } else {
                lastDownBytes = 0L
                lastUpBytes = 0L
            }
            lastStatsTime = System.currentTimeMillis()

            while (isActive) {
                delay(1000)
                // Source 1: HevTunnelBridge (TUN level) - Most accurate for user data consumption
                val stats = HevTunnelBridge.queryStats()
                if (stats != null && stats.size >= 2) {
                    val down = stats[0]
                    val up = stats[1]
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastStatsTime).coerceAtLeast(1)
                    
                    val dlSpeed = (down - lastDownBytes).coerceAtLeast(0) * 1000f / elapsed / 1024f
                    val ulSpeed = (up - lastUpBytes).coerceAtLeast(0) * 1000f / elapsed / 1024f
                    
                    // Prioritize HevTunnel for total bytes, but maybe mix with Xray for ping
                    val currentXrayStats = xrayController.stats.value
                    
                    vpnConnectionManager.updateStats(com.v2ir.data.model.TrafficStats(
                        downloadBytes = down,
                        uploadBytes = up,
                        downloadSpeed = dlSpeed,
                        uploadSpeed = ulSpeed,
                        pingMs = currentXrayStats.pingMs
                    ))
                    
                    lastDownBytes = down
                    lastUpBytes = up
                    lastStatsTime = now
                } else {
                    // Fallback to Xray Stats if TUN stats are unavailable
                    val xrayStats = xrayController.stats.value
                    if (xrayStats.downloadBytes > 0 || xrayStats.uploadBytes > 0) {
                        vpnConnectionManager.updateStats(xrayStats)
                    }
                }
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // If we were disconnected, we might want to reconnect, 
                // but for now we just log it.
                logRepository.addRawSystem("Network available: $network", "VPN")
            }

            override fun onLost(network: Network) {
                logRepository.addRawSystem("Network lost: $network", "VPN")
                // Don't tear down immediately, wait for a few seconds to see if another network comes up
                serviceScope.launch {
                    delay(3000)
                    val activeNetwork = cm.activeNetwork
                    if (activeNetwork == null) {
                        tearDownAll(R.string.log_msg_network_lost)
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    logRepository.addRawSystem("Internet capability lost on $network", "VPN")
                }
            }
        }
        networkCallbackRef.set(callback)
        cm.registerDefaultNetworkCallback(callback)
    }

    private fun handleUnexpectedDisconnect(reasonRes: Int) {
        serviceScope.launch { tearDownAll(reasonRes) }
    }

    private suspend fun tearDownAll(reasonRes: Int? = null) {
        statsJob?.cancel()
        statsJob = null
        xrayController.stopAndAwait()
        HevTunnelBridge.stop()
        closeTun()
        vpnConnectionManager.setDisconnected(reasonRes)
        if (reasonRes == null) {
            logRepository.add(LogMessage(R.string.log_msg_disconnected, tagRes = R.string.logs_tag_connection))
        } else {
            logRepository.add(
                LogMessage(
                    messageRes = reasonRes,
                    tagRes = R.string.logs_tag_connection,
                    level = com.v2ir.data.model.LogLevel.WARNING
                )
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun tearDownSync() {
        xrayController.stop()
        HevTunnelBridge.stop()
        closeTun()
    }

    private fun closeTun() {
        tunInterface?.close()
        tunInterface = null
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.service_channel_desc) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.v2ir.START_VPN"
        const val ACTION_STOP = "com.v2ir.STOP_VPN"
        const val EXTRA_CONFIG_JSON = "extra_config_json"
        const val CHANNEL_ID = "smart_xray_vpn"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context, configJson: String): Intent =
            Intent(context, V2irVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, V2irVpnService::class.java).apply {
                action = ACTION_STOP
            }
    }
}





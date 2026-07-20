package com.v2ir.data.xray

import com.v2ir.data.model.ConnectionState
import com.v2ir.data.model.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class VpnConnectionState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val disconnectReasonRes: Int? = null,
    val trafficStats: TrafficStats = TrafficStats(),
    val ipInfo: com.v2ir.data.remote.IpLocationService.IpInfo? = null
)

@Singleton
class VpnConnectionManager @Inject constructor() {

    private val _state = MutableStateFlow(VpnConnectionState())
    val state: StateFlow<VpnConnectionState> = _state.asStateFlow()

    // @Volatile ensures pendingConfigJson is visible across threads without full synchronization
    @Volatile var pendingConfigJson: String? = null

    // FIX (Bug #4): Use AtomicReference<Float> to prevent race condition between
    // IO-dispatched statsJob and Main-thread setDisconnected() calls.
    private val smoothedDlSpeed = AtomicReference(0f)
    private val smoothedUlSpeed = AtomicReference(0f)
    private val alpha = 0.3f // EMA smoothing factor: lower = smoother, higher = more responsive

    fun setConnecting() {
        _state.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                disconnectReasonRes = null,
                trafficStats = TrafficStats()
            )
        }
    }

    fun setConnected(ipInfo: com.v2ir.data.remote.IpLocationService.IpInfo? = null) {
        _state.update {
            it.copy(
                connectionState = ConnectionState.CONNECTED,
                disconnectReasonRes = null,
                ipInfo = ipInfo
            )
        }
    }

    fun updateIpInfo(ipInfo: com.v2ir.data.remote.IpLocationService.IpInfo) {
        _state.update { it.copy(ipInfo = ipInfo) }
    }

    fun setDisconnected(reasonRes: Int? = null) {
        // Reset smoothing values atomically before updating state
        smoothedDlSpeed.set(0f)
        smoothedUlSpeed.set(0f)
        _state.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                disconnectReasonRes = reasonRes,
                trafficStats = TrafficStats(),
                ipInfo = null
            )
        }
        pendingConfigJson = null
    }

    fun updateStats(stats: TrafficStats) {
        // Apply EMA (Exponential Moving Average) for smoother UI using atomic compare-and-set
        // to avoid race conditions between the stats polling coroutine and setDisconnected().
        val dl = smoothedDlSpeed.get()
        val newDl = if (dl == 0f) stats.downloadSpeed
                    else (stats.downloadSpeed * alpha) + (dl * (1f - alpha))
        smoothedDlSpeed.set(newDl)

        val ul = smoothedUlSpeed.get()
        val newUl = if (ul == 0f) stats.uploadSpeed
                    else (stats.uploadSpeed * alpha) + (ul * (1f - alpha))
        smoothedUlSpeed.set(newUl)

        _state.update {
            it.copy(
                trafficStats = stats.copy(
                    downloadSpeed = newDl,
                    uploadSpeed = newUl,
                    pingMs = stats.pingMs
                )
            )
        }
    }
}





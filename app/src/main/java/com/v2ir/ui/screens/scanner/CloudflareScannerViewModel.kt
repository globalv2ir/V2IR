package com.v2ir.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ir.data.model.*
import com.v2ir.data.scanner.CloudflareScannerCore
import com.v2ir.data.scanner.IpVersion
import com.v2ir.data.scanner.ScannerPersistence
import com.v2ir.data.scanner.ScannerProfile
import com.v2ir.domain.config.ConfigDomainFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class ScannerUiState(
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val phase: ScanPhase = ScanPhase.IDLE,
    val results: List<CloudflareScanResult> = emptyList(),
    val selectedIp: String? = null,
    val selectedConfig: Config? = null,
    val concurrency: Int = 64,
    val xrayConcurrency: Int = 16,
    val speedTestSizeKb: Int = 512,
    val profile: ScannerProfile = ScannerProfile.NORMAL,
    val ipVersion: IpVersion = IpVersion.IPV4,
    val message: String? = null,
    val hasRecoverableState: Boolean = false,
    val sortColumn: String = "SCORE",
    val sortAscending: Boolean = false,
    val statistics: ScanStatistics = ScanStatistics(),
    val activeScans: Map<String, CloudflareScanResult> = emptyMap()
)

@HiltViewModel
class CloudflareScannerViewModel @Inject constructor(
    private val configDomain: ConfigDomainFacade,
    private val scannerCore: CloudflareScannerCore,
    private val persistence: ScannerPersistence
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val cloudflareConfigs = configDomain.observeAllConfigs()
        .map { configs -> configs.filter { it.isCloudflare } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val saved = persistence.loadState()
            if (saved != null && saved.results.isNotEmpty()) {
                _uiState.update { it.copy(
                    hasRecoverableState = true,
                    results = saved.results,
                    concurrency = saved.lastConcurrency.coerceIn(1, 200),
                    xrayConcurrency = saved.lastXrayConcurrency.coerceIn(1, 50),
                    speedTestSizeKb = saved.lastSpeedTestSize,
                    profile = try { ScannerProfile.valueOf(saved.lastProfile) } catch (_: Exception) { ScannerProfile.NORMAL },
                    ipVersion = try { IpVersion.valueOf(saved.lastIpVersion) } catch (_: Exception) { IpVersion.IPV4 }
                ) }
            }
            
            cloudflareConfigs.collectLatest { configs ->
                if (_uiState.value.selectedConfig == null && configs.isNotEmpty()) {
                    _uiState.update { it.copy(selectedConfig = configs.first()) }
                }
            }
        }
        
        viewModelScope.launch {
            combine(scannerCore.results, scannerCore.currentPhase) { results, phase ->
                results to phase
            }.collect { (results, phase) ->
                _uiState.update { it.copy(
                    results = sortResults(results, it.sortColumn, it.sortAscending),
                    phase = phase
                ) }
                if (phase == ScanPhase.IDLE) saveCurrentState()
            }
        }
        
        viewModelScope.launch {
            scannerCore.isScanning.collect { isScanning ->
                _uiState.update { it.copy(isScanning = isScanning) }
            }
        }

        viewModelScope.launch {
            scannerCore.isPaused.collect { isPaused ->
                _uiState.update { it.copy(isPaused = isPaused) }
            }
        }

        viewModelScope.launch {
            scannerCore.statistics.collect { stats ->
                _uiState.update { it.copy(statistics = stats) }
            }
        }

        viewModelScope.launch {
            scannerCore.activeScans.collect { scans ->
                _uiState.update { it.copy(activeScans = scans) }
            }
        }
    }

    private suspend fun saveCurrentState() {
        val state = _uiState.value
        persistence.saveState(ScannerPersistence.ScannerState(
            results = state.results,
            lastProfile = state.profile.name,
            lastIpVersion = state.ipVersion.name,
            lastConcurrency = state.concurrency,
            lastXrayConcurrency = state.xrayConcurrency,
            lastSpeedTestSize = state.speedTestSizeKb
        ))
    }

    fun onConfigSelected(config: Config) = _uiState.update { it.copy(selectedConfig = config) }
    fun onConcurrencyChanged(value: Int) = _uiState.update { it.copy(concurrency = value) }
    fun onXrayConcurrencyChanged(value: Int) = _uiState.update { it.copy(xrayConcurrency = value) }
    fun onSpeedTestSizeChanged(sizeKb: Int) = _uiState.update { it.copy(speedTestSizeKb = sizeKb) }
    fun onProfileChanged(profile: ScannerProfile) = _uiState.update { it.copy(profile = profile) }
    fun onIpVersionChanged(version: IpVersion) = _uiState.update { it.copy(ipVersion = version) }

    fun startScan() {
        if (_uiState.value.phase == ScanPhase.IDLE) {
            scannerCore.clearResults()
            scannerCore.startDiscovery(
                ipVersion = _uiState.value.ipVersion,
                concurrency = _uiState.value.concurrency
            )
        }
    }

    fun proceedToValidation() {
        val config = _uiState.value.selectedConfig ?: return
        if (_uiState.value.phase == ScanPhase.DISCOVERY) {
            scannerCore.startValidation(
                config = config,
                concurrency = _uiState.value.xrayConcurrency,
                speedTestSizeKb = _uiState.value.speedTestSizeKb,
                profile = _uiState.value.profile
            )
        }
    }

    fun stopScan() = scannerCore.stopScan()

    fun pauseScan() = scannerCore.pauseScan()

    fun resumeScan() = scannerCore.resumeScan()

    fun clearResults() {
        viewModelScope.launch {
            persistence.clearState()
            scannerCore.clearResults()
            _uiState.update { it.copy(results = emptyList(), hasRecoverableState = false) }
        }
    }

    fun toggleSort(column: String) {
        _uiState.update { current ->
            val newAscending = if (current.sortColumn == column) !current.sortAscending else false
            current.copy(
                sortColumn = column,
                sortAscending = newAscending,
                results = sortResults(current.results, column, newAscending)
            )
        }
    }

    private fun sortResults(
        results: List<CloudflareScanResult>,
        column: String,
        ascending: Boolean
    ): List<CloudflareScanResult> {
        // Type-safe sort — avoids unchecked Comparable<*> → Comparable<Any> cast warnings.
        // Each column uses a concrete typed key, which the compiler can verify.
        return when (column) {
            "IP"    -> if (ascending) results.sortedBy { it.ip }
                       else results.sortedByDescending { it.ip }
            "PING"  -> if (ascending) results.sortedBy { it.latencyMs }
                       else results.sortedByDescending { it.latencyMs }
            "LOSS"  -> if (ascending) results.sortedBy { it.packetLoss }
                       else results.sortedByDescending { it.packetLoss }
            "SPEED" -> if (ascending) results.sortedBy { it.downloadSpeed }
                       else results.sortedByDescending { it.downloadSpeed }
            else    -> if (ascending) results.sortedBy { it.finalScore }
                       else results.sortedByDescending { it.finalScore }
        }
    }

    fun selectIp(ip: String) = _uiState.update { it.copy(selectedIp = if (it.selectedIp == ip) null else ip) }

    fun applyToSelectedConfig() {
        val ip = _uiState.value.selectedIp ?: return
        val configId = _uiState.value.selectedConfig?.id ?: return
        viewModelScope.launch {
            configDomain.applyIpToConfigs(ip, listOf(configId))
            _uiState.update { it.copy(message = "آی‌پی با موفقیت روی کانفیگ اعمال شد", selectedIp = null) }
        }
    }

    fun applyToAllCloudflareConfigs() {
        val ip = _uiState.value.selectedIp ?: return
        val configIds = cloudflareConfigs.value.map { it.id }
        if (configIds.isEmpty()) return
        viewModelScope.launch {
            configDomain.applyIpToConfigs(ip, configIds)
            _uiState.update { it.copy(message = "آی‌پی با موفقیت روی تمام کانفیگ‌ها اعمال شد", selectedIp = null) }
        }
    }

    fun exportResults(format: String): String {
        val results = _uiState.value.results
        if (results.isEmpty()) return ""
        
        return when (format.uppercase()) {
            "CSV" -> {
                StringBuilder().apply {
                    append("IP,Port,Latency,Loss,Speed(KB/s),Score\n")
                    results.forEach { append("${it.ip},${it.port},${it.latencyMs},${it.packetLoss},${it.downloadSpeed},${it.finalScore}\n") }
                }.toString()
            }
            "JSON" -> {
                val array = JSONArray()
                results.forEach { res ->
                    val obj = JSONObject()
                    obj.put("ip", res.ip)
                    obj.put("port", res.port)
                    obj.put("latency", res.latencyMs)
                    obj.put("loss", res.packetLoss)
                    obj.put("speed", res.downloadSpeed)
                    obj.put("score", res.finalScore)
                    array.put(obj)
                }
                array.toString(2)
            }
            else -> results.joinToString("\n") { it.ip }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}





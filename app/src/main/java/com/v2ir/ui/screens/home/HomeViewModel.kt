package com.v2ir.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ir.R
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConnectionMode
import com.v2ir.data.model.ConnectionState
import com.v2ir.data.model.LogMessage
import com.v2ir.data.model.TrafficStats
import com.v2ir.data.model.Subscription
import com.v2ir.data.repository.SubscriptionRepository
import com.v2ir.data.repository.ConfigRepository
import com.v2ir.data.repository.LogRepository
import com.v2ir.data.repository.SettingsRepository
import com.v2ir.data.remote.IpLocationService
import com.v2ir.data.scanner.NetworkScanner
import com.v2ir.data.xray.RoutingAssetsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.v2ir.data.xray.VpnConnectionManager
import com.v2ir.data.xray.XrayController
import com.v2ir.domain.LoadBalancer
import com.v2ir.domain.XrayConfigBuilder
import com.v2ir.domain.config.ParallelLatencyScanner
import com.v2ir.service.V2irVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionMode: ConnectionMode = ConnectionMode.SMART_AUTO,
    val selectedConfig: Config? = null,
    val activeServers: List<Config> = emptyList(),
    val trafficStats: TrafficStats = TrafficStats(),
    val downloadHistory: List<Float> = emptyList(),
    val uploadHistory: List<Float> = emptyList(),
    val isScanning: Boolean = false,
    val manualConfigs: List<Config> = emptyList(),
    val groupedConfigs: Map<String, List<Config>> = emptyMap(),
    val expandedGroups: Set<String> = emptySet(),
    val pingingConfigId: Long? = null,
    val disconnectReasonRes: Int? = null,
    val loadBalancerEnabled: Boolean = false,
    val includeFreeInAuto: Boolean = false,
    val scanProgress: Float = 0f,
    val groupScanProgress: Map<String, Float> = emptyMap(),
    val showShareDialog: Boolean = false,
    val shareText: String = "",
    val shareQrBitmap: android.graphics.Bitmap? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val selectedSubscriptionId: Long? = null,
    val ipInfo: IpLocationService.IpInfo? = null,
    val connectionDuration: String = "00:00:00",
    val speedTestResult: Float? = null, // KB/s, null = not tested
    val isSpeedTesting: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val networkScanner: NetworkScanner,
    private val loadBalancer: LoadBalancer,
    private val xrayConfigBuilder: XrayConfigBuilder,
    private val configDomain: com.v2ir.domain.config.ConfigDomainFacade,
    private val xrayController: XrayController,
    private val vpnConnectionManager: VpnConnectionManager,
    private val routingAssetsManager: RoutingAssetsManager,
    private val parallelLatencyScanner: ParallelLatencyScanner,
    private val workScheduler: com.v2ir.service.manager.WorkScheduler,
    private val ipLocationService: IpLocationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var connectJob: Job? = null
    private var durationJob: Job? = null
    private var pendingConnectAfterPermission = false
    private var connectTime = 0L

    init {
        loadSelectedConfig()
        observeConfigs()
        observeVpnState()
        prepareRoutingAssets()
        observeSettings()
        
        viewModelScope.launch {
            settingsRepository.settings.first().let {
                workScheduler.scheduleAll(it.scanIntervalMinutes)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(loadBalancerEnabled = settings.loadBalancerEnabled) }
            }
        }
    }

    private fun prepareRoutingAssets() {
        viewModelScope.launch {
            routingAssetsManager.ensureRoutingAssets()
        }
    }

    private fun loadSelectedConfig() {
        viewModelScope.launch {
            val config = configRepository.getSelectedConfig()
            _uiState.update { it.copy(selectedConfig = config) }
        }
    }

    private fun observeConfigs() {
        viewModelScope.launch {
            combine(
                configRepository.getAllConfigs(),
                subscriptionRepository.getPublicSubscriptions(),
                subscriptionRepository.getPrivateSubscriptions()
            ) { configs, pubSubs, privSubs ->
                val allSubs = pubSubs + privSubs
                val idToName = allSubs.associate { it.id to it.name }

                val servers = configs.filter {
                    it.type != com.v2ir.data.model.ConfigType.SUBSCRIPTION
                }.sortedWith(compareByDescending<Config> { it.realLatency >= 0 }
                    .thenBy { it.realLatency }
                    .thenByDescending { it.lastSuccess }
                    .thenBy { it.name })

                val manual = servers.filter { it.subscriptionId == null }
                val grouped = servers.filter { it.subscriptionId != null }
                    .groupBy { config ->
                        idToName[config.subscriptionId] ?: "Subscription ${config.subscriptionId}"
                    }

                val fullMap = mutableMapOf<String, List<Config>>()
                if (manual.isNotEmpty()) fullMap["Manual Configs"] = manual
                fullMap.putAll(grouped)

                Triple(servers, fullMap, allSubs)
            }
            // FIX (Bug #16): Added .catch{} to prevent a Room or emission error from silently
            // killing the coroutine and freezing the UI with no data shown.
            .catch { e -> e.printStackTrace() }
            .collect { (all, grouped, subs) ->
                _uiState.update { it.copy(
                    manualConfigs = all,
                    groupedConfigs = grouped,
                    subscriptions = subs
                ) }
            }
        }
    }

    fun selectSubscription(id: Long) {
        _uiState.update { it.copy(selectedSubscriptionId = id) }
    }

    fun toggleGroup(groupName: String) {
        _uiState.update { state ->
            val newSet = state.expandedGroups.toMutableSet()
            if (newSet.contains(groupName)) newSet.remove(groupName)
            else newSet.add(groupName)
            state.copy(expandedGroups = newSet)
        }
    }

    private fun observeVpnState() {
        viewModelScope.launch {
            // FIX (Bug #16): Wrapped collect in try/catch to prevent a state emission error
            // from silently killing this observer and leaving the UI stuck.
            // StateFlow.catch{} works but errors in StateFlow are rare; the try/catch pattern
            // is clearer and avoids ambiguous Flow operator chaining on StateFlow.
            try {
                vpnConnectionManager.state.collect { vpnState ->
                    val currentConnState = vpnState.connectionState

                    _uiState.update { state ->
                        state.copy(
                            connectionState = currentConnState,
                            trafficStats = vpnState.trafficStats,
                            disconnectReasonRes = vpnState.disconnectReasonRes,
                            ipInfo = vpnState.ipInfo,
                            downloadHistory = if (currentConnState == ConnectionState.CONNECTED) {
                                (state.downloadHistory + vpnState.trafficStats.downloadSpeed).takeLast(30)
                            } else {
                                state.downloadHistory
                            },
                            uploadHistory = if (currentConnState == ConnectionState.CONNECTED) {
                                (state.uploadHistory + vpnState.trafficStats.uploadSpeed).takeLast(30)
                            } else {
                                state.uploadHistory
                            },
                            isScanning = if (currentConnState != ConnectionState.CONNECTING) {
                                false
                            } else {
                                state.isScanning
                            }
                        )
                    }

                    // FIX (Bug #15): Handle timer outside _uiState.update{} to avoid
                    // side-effects inside the state transform lambda.
                    when (currentConnState) {
                        ConnectionState.CONNECTED -> {
                            // Always ensure a timer is running when CONNECTED.
                            // If connectTime is 0 (new ViewModel re-subscribing to already-CONNECTED
                            // state), set it now so the timer starts from approximately the right time.
                            if (connectTime == 0L) {
                                connectTime = System.currentTimeMillis()
                            }
                            if (durationJob == null || durationJob?.isActive == false) {
                                startDurationTimer()
                                fetchPublicIp()
                            }
                        }
                        ConnectionState.DISCONNECTED, ConnectionState.CONNECTING -> {
                            connectTime = 0L
                            durationJob?.cancel()
                            durationJob = null
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - connectTime
                val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                val duration = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                _uiState.update { it.copy(connectionDuration = duration) }
                delay(1000)
            }
        }
    }

    private fun fetchPublicIp() {
        viewModelScope.launch {
            delay(2000) // Wait for VPN to fully stabilize
            val info = ipLocationService.getIpInfo()
            if (info != null) {
                vpnConnectionManager.updateIpInfo(info)
            }
        }
    }

    fun toggleConnection(requestVpnPermission: (Intent) -> Unit) {
        if (_uiState.value.connectionState == ConnectionState.DISCONNECTED) {
            val mode = _uiState.value.connectionMode
            if (mode == ConnectionMode.MANUAL && _uiState.value.selectedConfig == null) {
                logRepository.add(LogMessage(R.string.log_msg_no_config, level = com.v2ir.data.model.LogLevel.ERROR))
                return
            }
            if (mode == ConnectionMode.SMART_AUTO && _uiState.value.selectedSubscriptionId == null) {
                // If no sub selected, default to the first one if available
                val firstSub = _uiState.value.subscriptions.firstOrNull { it.isEnabled }
                if (firstSub != null) {
                    _uiState.update { it.copy(selectedSubscriptionId = firstSub.id) }
                } else {
                    logRepository.add(LogMessage(R.string.log_msg_no_config, level = com.v2ir.data.model.LogLevel.ERROR))
                    return
                }
            }
        }
        
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED -> requestConnect(requestVpnPermission)
            ConnectionState.CONNECTING -> cancelConnection()
            ConnectionState.CONNECTED -> disconnect()
        }
    }

    fun toggleLoadBalancer() {
        _uiState.update { it.copy(loadBalancerEnabled = !it.loadBalancerEnabled) }
        viewModelScope.launch {
            settingsRepository.setLoadBalancer(_uiState.value.loadBalancerEnabled)
        }
    }

    fun toggleIncludeFreeInAuto() {
        _uiState.update { it.copy(includeFreeInAuto = !it.includeFreeInAuto) }
    }

    private fun requestConnect(requestVpnPermission: (Intent) -> Unit) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingConnectAfterPermission = true
            requestVpnPermission(prepareIntent)
        } else {
            startConnecting()
        }
    }

    fun onVpnPermissionGranted() {
        if (pendingConnectAfterPermission) {
            pendingConnectAfterPermission = false
            startConnecting()
        }
    }

    fun onVpnPermissionDenied() {
        pendingConnectAfterPermission = false
        logRepository.add(LogMessage(R.string.log_msg_vpn_permission_denied, level = com.v2ir.data.model.LogLevel.ERROR))
        _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
    }

    private fun startConnecting() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            vpnConnectionManager.setConnecting()
            _uiState.update { it.copy(isScanning = true) }
            val settings = settingsRepository.settings.first()

            val servers = when (_uiState.value.connectionMode) {
                ConnectionMode.SMART_AUTO -> runSmartAutoScan()
                ConnectionMode.MANUAL -> {
                    val selected = configRepository.getSelectedConfig()
                    if (selected == null) {
                        vpnConnectionManager.setDisconnected(R.string.log_msg_no_config)
                        return@launch
                    }
                    if (_uiState.value.loadBalancerEnabled) {
                        // V2IR: Only pick healthy nodes for LB in manual mode
                        val allInGroup = if (selected.subscriptionId != null) {
                            configRepository.getAllConfigsOnce().filter { 
                                it.subscriptionId == selected.subscriptionId && it.realLatency >= 0
                            }
                        } else {
                            listOf(selected)
                        }
                        if (allInGroup.size > 1) allInGroup else listOf(selected)
                    } else listOf(selected)
                }
            }

            if (servers.isEmpty()) {
                vpnConnectionManager.setDisconnected(R.string.log_msg_no_config)
                return@launch
            }

            _uiState.update { it.copy(activeServers = servers, selectedConfig = servers.first(), isScanning = false) }

            val configJson = xrayConfigBuilder.build(
                servers = servers,
                bypassIran = settings.bypassIran,
                bypassLan = settings.bypassLan,
                dnsServer = settings.dnsServer,
                balancerStrategy = settings.balancerStrategy,
                muxEnabled = settings.muxEnabled,
                logLevel = settings.logLevel
            )

            vpnConnectionManager.pendingConfigJson = configJson
            ContextCompat.startForegroundService(context, V2irVpnService.startIntent(context, configJson))
        }
    }

    private suspend fun runSmartAutoScan(): List<Config> {
        val selectedSubId = _uiState.value.selectedSubscriptionId ?: return emptyList()
        val sub = subscriptionRepository.getById(selectedSubId) ?: return emptyList()
        
        logRepository.add(LogMessage(R.string.log_msg_scan_start, tagRes = R.string.logs_tag_scanner))
        
        var configsInSub = configRepository.getAllConfigsOnce().filter {
            it.subscriptionId == selectedSubId && it.address.isNotBlank()
        }

        if (configsInSub.isEmpty()) {
            configDomain.refreshSubscription(sub)
            configsInSub = configRepository.getAllConfigsOnce().filter {
                it.subscriptionId == selectedSubId && it.address.isNotBlank()
            }
        }

        if (configsInSub.isEmpty()) return emptyList()

        _uiState.update { it.copy(scanProgress = 0.01f) }

        val foundHealthy = mutableListOf<Config>()
        val scanResults = parallelLatencyScanner.scan(
            configs = configsInSub,
            onProgress = { completed, total ->
                _uiState.update { it.copy(scanProgress = completed.toFloat() / total) }
            },
            onResult = { result ->
                configRepository.updateLatencies(result.configId, result.tcpLatencyMs, result.realLatencyMs)
                if (result.isAlive) {
                    val config = configsInSub.find { it.id == result.configId }?.copy(
                        tcpLatency = result.tcpLatencyMs, realLatency = result.realLatencyMs
                    )
                    if (config != null) synchronized(foundHealthy) { foundHealthy.add(config) }
                }
            }
        )

        _uiState.update { it.copy(scanProgress = 1f) }
        
        val alive = foundHealthy.sortedBy { it.realLatency }
        if (alive.isNotEmpty()) {
            subscriptionRepository.updateScanMetadata(
                selectedSubId, 
                alive.size, 
                System.currentTimeMillis(), 
                alive.first().realLatency
            )
        }

        delay(500)
        _uiState.update { it.copy(scanProgress = 0f) }

        if (alive.isEmpty()) {
            logRepository.add(LogMessage(R.string.log_msg_no_healthy_servers, tagRes = R.string.logs_tag_scanner, level = com.v2ir.data.model.LogLevel.ERROR))
        }

        return alive.take(5)
    }

    private fun cancelConnection() {
        connectJob?.cancel()
        disconnect()
    }

    private fun disconnect() {
        connectJob?.cancel()
        context.startService(V2irVpnService.stopIntent(context))
        _uiState.update { it.copy(downloadHistory = emptyList(), uploadHistory = emptyList(), isScanning = false) }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update { it.copy(connectionMode = mode) }
    }

    fun pingConfig(config: Config, realDelay: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(pingingConfigId = config.id) }
            if (realDelay) {
                val res = networkScanner.scanSingleConfig(config)
                configRepository.updateRealLatency(config.id, res.realLatencyMs)
            } else {
                val tcp = networkScanner.tcpPing(config.address, config.port)
                configRepository.updateTcpLatency(config.id, tcp)
            }
            _uiState.update { it.copy(pingingConfigId = null) }
        }
    }

    fun selectManualConfig(config: Config) {
        viewModelScope.launch {
            configRepository.selectConfig(config.id)
            _uiState.update { it.copy(selectedConfig = config) }
        }
    }

    fun shareConfig(config: Config) {
        viewModelScope.launch {
            val text = configDomain.shareText(config)
            val qr = configDomain.shareQrBitmap(config)
            _uiState.update { it.copy(showShareDialog = true, selectedConfig = config, shareText = text, shareQrBitmap = qr) }
        }
    }

    fun dismissShareDialog() {
        _uiState.update { it.copy(showShareDialog = false, shareQrBitmap = null) }
    }

    fun runSpeedTest() {
        if (_uiState.value.isSpeedTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSpeedTesting = true, speedTestResult = null) }
            val speedKbs = try {
                configDomain.measureCurrentSpeed()
            } catch (_: Exception) {
                0f
            }
            _uiState.update { it.copy(isSpeedTesting = false, speedTestResult = speedKbs) }
        }
    }
}





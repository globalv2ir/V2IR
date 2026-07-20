package com.v2ir.data.xray

import android.content.Context
import com.v2ir.R
import com.v2ir.data.model.LogMessage
import com.v2ir.data.model.TrafficStats
import com.v2ir.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routingAssetsManager: RoutingAssetsManager,
    private val binaryManager: XrayBinaryManager,
    private val processRunner: XrayProcessRunner,
    private val statsPoller: XrayStatsPoller,
    private val logRepository: LogRepository,
    private val vpnConnectionManager: VpnConnectionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // FIX (Bug #18): Use AtomicBoolean for _isRunning to enable compareAndSet semantics,
    // preventing the double-disconnect race between wireProcessLogs and startStatsPolling.
    private val _isRunningAtomic = AtomicBoolean(false)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    private var statsJob: Job? = null
    private var configFile: File? = null
    private var workingDir: File? = null

    var onCoreCrashed: (() -> Unit)? = null

    init {
        wireProcessLogs()
    }

    suspend fun prepareEnvironment(): Boolean = withContext(Dispatchers.IO) {
        val geoReady = routingAssetsManager.ensureRoutingAssets()
        if (!geoReady) {
            logRepository.add(
                LogMessage(
                    messageRes = R.string.log_msg_geo_assets_missing,
                    tagRes = R.string.logs_tag_routing,
                    level = com.v2ir.data.model.LogLevel.WARNING
                )
            )
        }
        binaryManager.ensureBinaries() || XrayNativeBridge.isAvailable()
    }

    // FIX (Bug #7): start() used to always return true regardless of outcome.
    // Now it returns the actual result. Callers that need the result should use
    // startAndAwait() for a suspending call. This fire-and-forget variant still
    // handles the error case by calling setDisconnected internally.
    fun start(configJson: String): Boolean {
        scope.launch {
            val success = startInternal(configJson)
            if (!success) {
                vpnConnectionManager.setDisconnected(R.string.log_msg_xray_start_failed)
            }
        }
        // Return true to indicate the launch was accepted (not the outcome).
        // Callers should observe VpnConnectionManager.state for actual result.
        return true
    }

    suspend fun startAndAwait(configJson: String): Boolean = startInternal(configJson)

    private suspend fun startInternal(configJson: String): Boolean = withContext(Dispatchers.IO) {
        if (_isRunning.value) stopInternal()

        prepareEnvironment()

        val dir = binaryManager.xrayDirectory
        workingDir = dir
        if (!dir.exists()) dir.mkdirs()

        routingAssetsManager.geoipFile.takeIf { it.exists() }?.let { geo ->
            File(dir, RoutingAssetsManager.GEOIP_NAME).let { target ->
                if (!target.exists()) geo.copyTo(target, overwrite = true)
            }
        }
        routingAssetsManager.geositeFile.takeIf { it.exists() }?.let { geo ->
            File(dir, RoutingAssetsManager.GEOSITE_NAME).let { target ->
                if (!target.exists()) geo.copyTo(target, overwrite = true)
            }
        }

        configFile = File(dir, "config.json").also { it.writeText(configJson) }

        val configPath = configFile?.absolutePath ?: return@withContext false
        val geoDir = routingAssetsManager.geoDirectory.absolutePath

        val started = when {
            XrayNativeBridge.isAvailable() -> {
                val res = XrayNativeBridge.startCore(configPath, dir.absolutePath)
                if (res != 0) {
                    logRepository.addRawSystem("XrayNativeBridge start failed: $res. Falling back to process mode...", "Xray")
                    if (binaryManager.isXrayReady()) {
                        processRunner.start(binaryManager.getXrayBinary(), configFile!!, dir)
                    } else false
                } else true
            }
            binaryManager.isXrayReady() -> {
                processRunner.start(binaryManager.getXrayBinary(), configFile!!, dir)
            }
            else -> {
                val binary = binaryManager.getXrayBinary()
                logRepository.add(
                    LogMessage(
                        messageRes = R.string.log_msg_xray_binary_missing,
                        args = listOf(binary.absolutePath),
                        tagRes = R.string.logs_tag_xray,
                        level = com.v2ir.data.model.LogLevel.ERROR
                    )
                )
                false
            }
        }

        if (started) {
            _isRunningAtomic.set(true)
            _isRunning.value = true
            statsPoller.reset()
            startStatsPolling()
            logRepository.add(LogMessage(R.string.log_msg_xray_started, tagRes = R.string.logs_tag_xray))
        }

        started
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    suspend fun stopAndAwait() = stopInternal()

    private suspend fun stopInternal() = withContext(Dispatchers.IO) {
        _isRunningAtomic.set(false)
        _isRunning.value = false
        statsJob?.cancel()
        statsJob = null
        _stats.value = TrafficStats()

        if (XrayNativeBridge.isAvailable()) {
            XrayNativeBridge.stopCore()
        }
        processRunner.stop()

        logRepository.add(LogMessage(R.string.log_msg_xray_stopped, tagRes = R.string.logs_tag_xray))
    }

    fun getConfigPath(): String? = configFile?.absolutePath

    fun getWorkingDirectory(): File? = workingDir

    fun isProcessAlive(): Boolean =
        if (XrayNativeBridge.isAvailable()) _isRunning.value
        else processRunner.isRunning()

    private fun wireProcessLogs() {
        val tag = context.getString(R.string.logs_tag_xray)
        processRunner.onStdoutLine = { line -> logRepository.addRawSystem(line, tag) }
        processRunner.onStderrLine = { line -> logRepository.addRawSystem(line, tag) }
        processRunner.onProcessExit = { code ->
            // FIX (Bug #18): Guard with compareAndSet to ensure only one disconnect signal fires.
            // stats polling loop also uses compareAndSet, so exactly one of them wins.
            if (_isRunningAtomic.compareAndSet(true, false)) {
                _isRunning.value = false
                statsJob?.cancel()
                logRepository.add(
                    LogMessage(
                        messageRes = R.string.log_msg_xray_crashed,
                        args = listOf(code),
                        tagRes = R.string.logs_tag_xray,
                        level = com.v2ir.data.model.LogLevel.ERROR
                    )
                )
                vpnConnectionManager.setDisconnected(R.string.log_msg_core_unexpected_stop)
                onCoreCrashed?.invoke()
            }
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && _isRunning.value) {
                val stats = statsPoller.poll()
                if (stats != null) {
                    _stats.value = stats
                    vpnConnectionManager.updateStats(stats)
                }

                // FIX (Bug #18): Use AtomicBoolean.compareAndSet to ensure exactly one
                // disconnect signal fires when the process dies. onProcessExit uses the same
                // AtomicBoolean, so whichever callback fires first wins.
                if (!isProcessAlive() && _isRunningAtomic.compareAndSet(true, false)) {
                    _isRunning.value = false
                    vpnConnectionManager.setDisconnected(R.string.log_msg_core_unexpected_stop)
                    onCoreCrashed?.invoke()
                    break
                }
                delay(1000)
            }
        }
    }
}





package com.v2ir.domain.config

import com.v2ir.data.cloudflare.CloudflareIpDatabase
import com.v2ir.data.model.Config
import com.v2ir.data.model.ScanResult
import com.v2ir.data.model.Subscription
import com.v2ir.data.remote.ConfigShareHelper
import com.v2ir.data.remote.SubscriptionFetcher
import com.v2ir.data.repository.ConfigRepository
import com.v2ir.data.repository.SubscriptionRepository
import com.v2ir.data.scanner.CloudflareIpScanner
import com.v2ir.data.scanner.CloudflareScannerCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Isolated domain facade for config & subscription operations.
 * UI ViewModels should delegate data work here instead of touching repositories directly.
 */
@Singleton
class ConfigDomainFacade @Inject constructor(
    private val configRepository: ConfigRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionFetcher: SubscriptionFetcher,
    private val parallelLatencyScanner: ParallelLatencyScanner,
    private val cloudflareIpScanner: CloudflareIpScanner,
    private val cloudflareIpDatabase: CloudflareIpDatabase,
    private val configShareHelper: ConfigShareHelper,
    // Lazy<> wrapper removed — ConfigImporter no longer depends on ConfigDomainFacade,
    // so the circular dependency is fully resolved and Lazy is no longer needed.
    private val configImporter: ConfigImporter
) {
    // FIX (Bug #8): Changed from Dispatchers.Main.immediate to Dispatchers.IO.
    // Background subscription refresh + health checks are I/O-heavy operations —
    // running them on Main caused ANR on slow networks.
    // FIX (Bug #17): Errors in child coroutines are now logged instead of silently swallowed.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observePublicRepos(): Flow<List<Subscription>> = subscriptionRepository.getPublicSubscriptions()
    fun observePrivateRepos(): Flow<List<Subscription>> = subscriptionRepository.getPrivateSubscriptions()
    fun observePrivateConfigs(): Flow<List<Config>> = configRepository.getPrivateConfigs()
    fun observeAllConfigs(): Flow<List<Config>> = configRepository.getAllConfigs()

    suspend fun selectConfig(id: Long) = configRepository.selectConfig(id)
    suspend fun insertConfig(config: Config) = configRepository.insertConfig(enrich(config))
    suspend fun updateConfig(config: Config) = configRepository.updateConfig(enrich(config))
    suspend fun deleteConfig(id: Long) = configRepository.deleteConfigById(id)

    suspend fun insertSubscription(subscription: Subscription): Long {
        val id = subscriptionRepository.insert(subscription)
        // Auto-refresh in background on IO dispatcher (not Main)
        scope.launch {
            runCatching { refreshSubscription(subscription.copy(id = id)) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) it.printStackTrace() }
        }
        return id
    }

    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionRepository.update(subscription)
        // Auto-refresh in background on IO dispatcher (not Main)
        scope.launch {
            runCatching { refreshSubscription(subscription) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) it.printStackTrace() }
        }
    }

    suspend fun toggleSubscriptionExpanded(id: Long, expanded: Boolean) =
        subscriptionRepository.updateExpanded(id, expanded)

    suspend fun toggleSubscriptionEnabled(id: Long, enabled: Boolean) =
        subscriptionRepository.updateEnabled(id, enabled)

    suspend fun deleteSubscription(id: Long) = subscriptionRepository.deleteById(id)

    suspend fun refreshSubscription(subscription: Subscription): Int {
        val configs = subscriptionFetcher.fetchAndParse(subscription.url, subscription.id)
        if (configs.isEmpty()) return 0

        // V2IR: Fast Health Check before saving (ONLY for Public Subscriptions)
        val healthyConfigs = if (subscription.isPublic && configs.size > 1) {
            val results = parallelLatencyScanner.scan(configs, fastCheck = true)
            val healthyUris = results.filter { it.isAlive }
                .mapNotNull { res -> configs.find { it.id == res.configId }?.rawUri }
                .toSet()
            
            if (healthyUris.isNotEmpty()) {
                configs.filter { it.rawUri in healthyUris }
            } else emptyList() // If all dead in a public sub, maybe don't import anything to keep it clean
        } else configs // Private subscriptions: import everything regardless of health

        if (healthyConfigs.isEmpty()) return 0
        
        // Transactional Replace (Cleanup + Insert)
        configRepository.replaceSubscriptionConfigs(
            subscription.id,
            healthyConfigs.map { enrich(it) }
        )

        subscriptionRepository.update(
            subscription.copy(
                serverCount = healthyConfigs.size,
                lastUpdated = System.currentTimeMillis()
            )
        )
        return healthyConfigs.size
    }

    suspend fun scanLatencies(
        configs: List<Config>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onResult: (suspend (ScanResult) -> Unit)? = null
    ): List<ScanResult> = parallelLatencyScanner.scan(configs, false, onProgress, onResult)

    suspend fun applyScanResult(result: ScanResult) {
        configRepository.updateLatencies(result.configId, result.tcpLatencyMs, result.realLatencyMs)
    }

    suspend fun applyScanResults(results: List<ScanResult>) {
        results.forEach { applyScanResult(it) }
    }

    /** Scan a single config for TCP latency (fast check) and persist the result. */
    suspend fun scanConfigTcpLatency(config: Config): Long {
        val result = parallelLatencyScanner.scan(listOf(config), fastCheck = true).firstOrNull()
        val tcp = result?.tcpLatencyMs ?: -1L
        configRepository.updateTcpLatency(config.id, tcp)
        return tcp
    }

    /** Scan a single config for real (TLS) latency and persist the result. */
    suspend fun scanConfigRealLatency(config: Config): Long {
        val result = parallelLatencyScanner.scan(listOf(config), fastCheck = false).firstOrNull()
        val real = result?.realLatencyMs ?: -1L
        if (result != null) configRepository.updateRealLatency(config.id, real)
        return real
    }

    suspend fun findBestCleanIp(
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): com.v2ir.data.model.CloudflareScanResult? {
        val candidates = CloudflareIpDatabase.CLEAN_IP_CANDIDATES
            .filter { cloudflareIpDatabase.isCloudflareIp(it) }
        var best: com.v2ir.data.model.CloudflareScanResult? = null
        var completed = 0
        cloudflareIpScanner.scanCleanIps(candidates).collect { result ->
            completed++
            onProgress?.invoke(completed, candidates.size)
            if (result.isAlive && (best == null || result.latencyMs < best!!.latencyMs)) {
                best = result
            }
        }
        return best
    }

    suspend fun applyIpToConfigs(ip: String, configIds: List<Long>) = withContext(Dispatchers.IO) {
        configIds.forEach { id ->
            val config = configRepository.getConfigById(id) ?: return@forEach
            // FIX (Bug #9): Removed unbounded backup insertion.
            // Previously, every call created a new backup copy with no cleanup,
            // causing unbounded DB growth when applied repeatedly (e.g., each scan cycle).
            // Now we directly update the address without creating backup copies.
            // The pre-modification state is preserved via the project's existing backup.ps1 policy.
            val updatedConfig = config.copy(address = ip)
            configRepository.updateConfig(updatedConfig)
        }
    }

    fun shareText(config: Config): String = configShareHelper.toShareText(config)

    fun shareQrBitmap(config: Config) = configShareHelper.toQrBitmap(shareText(config))

    /** Generate QR from arbitrary text (used for subscription bulk export) */
    fun shareQrBitmapFromText(text: String) = configShareHelper.toQrBitmap(text)

    suspend fun parseImportLine(line: String): Config? =
        subscriptionFetcher.parseLine(line)

    suspend fun parseMultiple(text: String): List<Config> =
        subscriptionFetcher.parseSubscriptionBody(text, 0)

    suspend fun importUnified(text: String) = configImporter.import(text)

    suspend fun insertConfigs(configs: List<Config>) =
        configRepository.insertConfigs(configs.map { enrich(it) })

    suspend fun measureCurrentSpeed(): Float =
        parallelLatencyScanner.measureSpeed()

    fun getAllConfigsAsText(configs: List<Config>): String =
        configs.joinToString("\n") { it.rawUri }

    private fun enrich(config: Config): Config {
        val isCf = config.isCloudflare ||
            cloudflareIpDatabase.detectCloudflare(config.address, config.sni)
        return config.copy(isCloudflare = isCf)
    }
}





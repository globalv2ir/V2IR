package com.v2ir.domain.config

import com.v2ir.data.model.Config
import com.v2ir.data.repository.ConfigRepository
import com.v2ir.data.remote.SubscriptionFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles importing configs from text, URIs, or subscription bodies.
 *
 * FIX: Uses ConfigRepository directly instead of ConfigDomainFacade to break the
 * circular dependency (ConfigDomainFacade → Lazy<ConfigImporter> → ConfigDomainFacade).
 * ConfigImporter only needs insert + enrich — it doesn't need the full facade.
 */
@Singleton
class ConfigImporter @Inject constructor(
    private val configRepository: ConfigRepository,
    private val subscriptionFetcher: SubscriptionFetcher
) {

    suspend fun import(text: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val normalized = text.trim()
            if (normalized.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Empty input"))
            }

            val configs = if (isSingleConfig(normalized)) {
                val single = subscriptionFetcher.parseLine(normalized)
                if (single != null) listOf(single) else emptyList()
            } else {
                subscriptionFetcher.parseSubscriptionBody(normalized, 0)
            }

            val validConfigs = configs.filter { validate(it) }

            if (validConfigs.isEmpty()) {
                return@withContext Result.failure(Exception("No valid configs found"))
            }

            configRepository.insertConfigs(validConfigs)
            Result.success(validConfigs.size)
        } catch (e: CancellationException) {
            throw e // Never swallow cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isSingleConfig(text: String): Boolean {
        val protocols = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "tuic://")
        return protocols.any { text.startsWith(it, ignoreCase = true) }
    }

    private fun validate(config: Config): Boolean {
        if (config.address.isBlank() || config.port <= 0) return false
        return true
    }
}





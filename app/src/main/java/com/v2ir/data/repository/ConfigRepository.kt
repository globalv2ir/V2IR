package com.v2ir.data.repository

import com.v2ir.data.cloudflare.CloudflareIpDatabase
import com.v2ir.data.local.database.ConfigDao
import com.v2ir.data.local.entity.ConfigEntity
import com.v2ir.data.model.Config
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    private val configDao: ConfigDao,
    private val cloudflareIpDatabase: CloudflareIpDatabase
) {
    fun getAllConfigs(): Flow<List<Config>> =
        configDao.getAllConfigs().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getPrivateConfigs(): Flow<List<Config>> =
        configDao.getPrivateConfigs().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getSelectedConfig(): Config? =
        configDao.getSelectedConfig()?.toDomain()

    suspend fun getAllConfigsOnce(): List<Config> =
        configDao.getAllConfigsOnce().map { it.toDomain() }

    suspend fun getConfigById(id: Long): Config? =
        configDao.getConfigById(id)?.toDomain()

    suspend fun insertConfig(config: Config): Long {
        val enriched = enrichCloudflare(config)
        return configDao.insertConfig(ConfigEntity.fromDomain(enriched))
    }

    suspend fun insertConfigs(configs: List<Config>) =
        configDao.insertConfigs(configs.map { ConfigEntity.fromDomain(enrichCloudflare(it)) })

    suspend fun updateConfig(config: Config) =
        configDao.updateConfig(ConfigEntity.fromDomain(enrichCloudflare(config)))

    suspend fun deleteConfig(config: Config) =
        configDao.deleteConfig(ConfigEntity.fromDomain(config))

    suspend fun deleteConfigById(id: Long) =
        configDao.deleteConfigById(id)

    suspend fun selectConfig(id: Long) {
        // Atomic: clear all + select target in a single Room transaction
        configDao.selectConfigTransaction(id)
    }

    suspend fun updateLatency(id: Long, latency: Long) =
        configDao.updateLatency(id, latency)

    suspend fun updateLatencies(id: Long, tcp: Long, real: Long) =
        configDao.updateLatencies(id, tcp, real)

    suspend fun updateTcpLatency(id: Long, tcp: Long) =
        configDao.updateTcpLatency(id, tcp)

    suspend fun updateRealLatency(id: Long, real: Long) =
        configDao.updateRealLatency(id, real)

    suspend fun incrementFailCount(id: Long) =
        configDao.incrementFailCount(id)

    suspend fun resetFailCount(id: Long) =
        configDao.resetFailCount(id)

    suspend fun updateFragmentIp(id: Long, ip: String) =
        configDao.updateFragmentIp(id, ip)

    suspend fun deleteConfigsBySubscriptionId(subId: Long) =
        configDao.deleteConfigsBySubscriptionId(subId)

    suspend fun replaceSubscriptionConfigs(subId: Long, configs: List<Config>) =
        configDao.replaceSubscriptionConfigs(subId, configs.map { ConfigEntity.fromDomain(enrichCloudflare(it)) })

    suspend fun getConfigCount(): Int =
        configDao.getConfigCount()

    private fun enrichCloudflare(config: Config): Config {
        val isCf = cloudflareIpDatabase.detectCloudflare(config.address, config.sni)
        return config.copy(isCloudflare = config.isCloudflare || isCf)
    }
}





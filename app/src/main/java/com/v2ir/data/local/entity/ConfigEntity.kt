package com.v2ir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConfigType
import org.json.JSONObject

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int = 443,
    val userId: String = "",
    val remark: String = "",
    val type: String = ConfigType.VLESS.name,
    val subscriptionUrl: String = "",
    val subscriptionId: Long? = null,
    val sni: String = "",
    val fragmentIp: String = "",
    val isCloudflare: Boolean = false,
    val isSelected: Boolean = false,
    val latency: Long = -1L,
    val tcpLatency: Long = -1L,
    val realLatency: Long = -1L,
    val rawUri: String = "",
    val countryLabel: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val isFree: Boolean = false,
    val bandwidth: Float = 0f,
    val lastSuccess: Long = 0L,
    val lastChecked: Long = 0L,
    val failCount: Int = 0,
    val extraParams: String = "" // JSON string
) {
    fun toDomain(): Config {
        val params = try {
            if (extraParams.isNotBlank()) {
                val json = JSONObject(extraParams)
                val map = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getString(key)
                }
                map
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        return Config(
            id = id,
            name = name,
            address = address,
            port = port,
            userId = userId,
            remark = remark,
            type = try { ConfigType.valueOf(type) } catch (_: Exception) { ConfigType.VLESS },
            subscriptionUrl = subscriptionUrl,
            subscriptionId = subscriptionId,
            sni = sni,
            fragmentIp = fragmentIp,
            isCloudflare = isCloudflare,
            isSelected = isSelected,
            latency = latency,
            tcpLatency = tcpLatency,
            realLatency = realLatency,
            rawUri = rawUri,
            countryLabel = countryLabel,
            addedAt = addedAt,
            isFree = isFree,
            bandwidth = bandwidth,
            lastSuccess = lastSuccess,
            lastChecked = lastChecked,
            failCount = failCount,
            extraParams = params
        )
    }

    companion object {
        fun fromDomain(config: Config): ConfigEntity {
            val paramsJson = if (config.extraParams.isNotEmpty()) {
                JSONObject(config.extraParams as Map<*, *>).toString()
            } else ""

            return ConfigEntity(
                id = config.id,
                name = config.name,
                address = config.address,
                port = config.port,
                userId = config.userId,
                remark = config.remark,
                type = config.type.name,
                subscriptionUrl = config.subscriptionUrl,
                subscriptionId = config.subscriptionId,
                sni = config.sni,
                fragmentIp = config.fragmentIp,
                isCloudflare = config.isCloudflare,
                isSelected = config.isSelected,
                latency = config.latency,
                tcpLatency = config.tcpLatency,
                realLatency = config.realLatency,
                rawUri = config.rawUri,
                countryLabel = config.countryLabel,
                addedAt = config.addedAt,
                isFree = config.isFree,
                bandwidth = config.bandwidth,
                lastSuccess = config.lastSuccess,
                lastChecked = config.lastChecked,
                failCount = config.failCount,
                extraParams = paramsJson
            )
        }
    }
}





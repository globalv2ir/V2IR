package com.v2ir.domain

import com.v2ir.data.model.Config
import com.v2ir.data.model.ScanResult
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadBalancer @Inject constructor() {

    // FIX (Bug #5): Use AtomicInteger to prevent data races on roundRobinIndex.
    // LoadBalancer is a @Singleton and nextRoundRobin() can be called concurrently
    // from multiple coroutines without synchronization.
    private val roundRobinIndex = AtomicInteger(0)

    fun selectBest(configs: List<Config>): Config? {
        val now = System.currentTimeMillis()
        val twoHours = 2 * 60 * 60 * 1000L
        val alive = configs.filter {
            (it.realLatency >= 0 || it.latency >= 0) && (now - it.lastChecked < twoHours)
        }
        if (alive.isEmpty()) return configs.firstOrNull { it.realLatency >= 0 || it.latency >= 0 } ?: configs.firstOrNull()
        return alive.minByOrNull { if (it.realLatency >= 0) it.realLatency else it.latency }
    }

    fun selectTopN(scanResults: List<ScanResult>, n: Int = 3): List<Config> {
        return scanResults
            .filter { it.isAlive }
            .sortedBy { it.realLatencyMs }
            .take(n)
            .map { result ->
                Config(
                    id = result.configId,
                    name = result.address,
                    address = result.address,
                    realLatency = result.realLatencyMs,
                    tcpLatency = result.tcpLatencyMs,
                    latency = result.realLatencyMs
                )
            }
    }

    fun selectTopConfigs(configs: List<Config>, n: Int = 3): List<Config> {
        val now = System.currentTimeMillis()
        val twoHours = 2 * 60 * 60 * 1000L
        return configs
            .filter { (it.realLatency >= 0 || it.latency >= 0) && (now - it.lastChecked < twoHours) }
            .sortedBy { if (it.realLatency >= 0) it.realLatency else it.latency }
            .take(n)
            .ifEmpty {
                configs.filter { it.realLatency >= 0 || it.latency >= 0 }
                    .sortedBy { if (it.realLatency >= 0) it.realLatency else it.latency }
                    .take(n)
            }
    }

    fun nextRoundRobin(configs: List<Config>): Config? {
        if (configs.isEmpty()) return null
        // getAndIncrement() is atomic — safe for concurrent access
        val index = roundRobinIndex.getAndIncrement()
        return configs[index % configs.size]
    }

    fun reset() {
        roundRobinIndex.set(0)
    }
}





package com.v2ir.domain.config

import com.v2ir.data.model.Config
import com.v2ir.data.model.ScanResult
import com.v2ir.data.scanner.NetworkScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParallelLatencyScanner @Inject constructor(
    private val networkScanner: NetworkScanner,
    private val settingsRepository: com.v2ir.data.repository.SettingsRepository
) {
    suspend fun scan(
        configs: List<Config>,
        fastCheck: Boolean = false,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
        onResult: (suspend (ScanResult) -> Unit)? = null
    ): List<ScanResult> = coroutineScope {
        val servers = configs.filter { it.address.isNotBlank() }
        if (servers.isEmpty()) return@coroutineScope emptyList()

        val concurrency = if (fastCheck) 32
                          else settingsRepository.settings.first().scanConcurrency.coerceIn(1, 64)
        val semaphore = Semaphore(concurrency)
        val completed = AtomicInteger(0)

        servers.map { config ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = try {
                        if (fastCheck) {
                            val tcp = networkScanner.tcpPing(config.address, config.port, 1500)
                            ScanResult(config.id, config.address, tcp, -1L, tcp >= 0)
                        } else {
                            networkScanner.scanSingleConfig(config)
                        }
                    } catch (e: CancellationException) {
                        throw e // Must propagate
                    } catch (_: Exception) {
                        ScanResult(config.id, config.address, -1L, -1L, false)
                    }

                    val count = completed.incrementAndGet()
                    onProgress?.invoke(count, servers.size)
                    try {
                        onResult?.invoke(result)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Don't let a callback failure cancel the whole scan
                    }
                    result
                }
            }
        }.awaitAll()
    }

    suspend fun measureSpeed(): Float = networkScanner.measureSpeed()
}





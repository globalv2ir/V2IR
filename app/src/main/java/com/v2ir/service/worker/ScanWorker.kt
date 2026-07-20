package com.v2ir.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ir.data.repository.ConfigRepository
import com.v2ir.data.scanner.NetworkScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val configRepository: ConfigRepository,
    private val networkScanner: NetworkScanner
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val configs = configRepository.getAllConfigs().first()
            // FIX (Bug #13): Added .catch{} operator to handle mid-scan network errors
            // without crashing the entire worker. Errors are logged and scanning continues.
            networkScanner.scanConfigs(configs)
                .catch { e ->
                    // Don't swallow CancellationException — rethrow it
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    // Emit nothing on error — just continue with whatever results we have
                }
                .collect { result ->
                    if (result.isAlive) {
                        configRepository.updateLatencies(result.configId, result.tcpLatencyMs, result.realLatencyMs)
                    }
                }
            Result.success()
        } catch (e: CancellationException) {
            // FIX (Bug #12): Must rethrow CancellationException — catching it breaks
            // structured concurrency and prevents WorkManager from properly cancelling the job.
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}





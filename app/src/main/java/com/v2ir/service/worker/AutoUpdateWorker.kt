package com.v2ir.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ir.data.repository.SubscriptionRepository
import com.v2ir.data.repository.ConfigRepository
import com.v2ir.data.remote.SubscriptionFetcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val configRepository: ConfigRepository,
    private val subscriptionFetcher: SubscriptionFetcher
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // FIX: Update BOTH public and private subscriptions.
            // Previously only private subs were updated, leaving public repos stale forever.
            val publicSubs = subscriptionRepository.getPublicSubscriptions().first()
            val privateSubs = subscriptionRepository.getPrivateSubscriptions().first()
            val allSubs = publicSubs + privateSubs

            var updatedCount = 0
            allSubs.filter { it.isEnabled }.forEach { sub ->
                try {
                    val configs = subscriptionFetcher.fetchAndParse(sub.url, sub.id)
                    if (configs.isNotEmpty()) {
                        configRepository.replaceSubscriptionConfigs(sub.id, configs)
                        subscriptionRepository.update(
                            sub.copy(
                                serverCount = configs.size,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                        updatedCount++
                    }
                } catch (e: CancellationException) {
                    throw e // Propagate cancellation
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue with next subscription even if one fails
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}





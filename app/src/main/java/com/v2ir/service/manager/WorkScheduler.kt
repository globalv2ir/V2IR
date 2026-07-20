package com.v2ir.service.manager

import android.content.Context
import androidx.work.*
import com.v2ir.service.worker.AutoUpdateWorker
import com.v2ir.service.worker.ScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    // WorkManager enforces a minimum periodic interval of 15 minutes.
    // Any value below this is silently rounded up to 15 minutes by the OS.
    private val MINIMUM_INTERVAL_MINUTES = 15L

    fun scheduleAll(scanIntervalMin: Int = 15, updateIntervalHours: Int = 12) {
        scheduleScan(scanIntervalMin)
        scheduleUpdate(updateIntervalHours)
    }

    fun scheduleScan(intervalMin: Int) {
        // Enforce minimum — WorkManager silently ignores intervals below 15 min anyway
        val safeInterval = maxOf(intervalMin.toLong(), MINIMUM_INTERVAL_MINUTES)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ScanWorker>(safeInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "scan_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleUpdate(intervalHours: Int) {
        val safeInterval = maxOf(intervalHours.toLong(), 1L) // Minimum 1 hour

        // Use CONNECTED (not UNMETERED) so updates work on mobile data too.
        // UNMETERED (Wi-Fi only) was preventing updates for users without Wi-Fi.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoUpdateWorker>(safeInterval, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "update_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork("scan_worker")
        workManager.cancelUniqueWork("update_worker")
    }
}





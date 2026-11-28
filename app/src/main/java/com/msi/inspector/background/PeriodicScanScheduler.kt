package com.msi.inspector.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PeriodicScanScheduler {
    private const val UNIQUE_WORK_NAME = "msi_periodic_local_scan"
    private const val PREFERENCES_NAME = "msi_preferences"
    private const val KEY_PERIODIC_SCAN = "periodic_scan_enabled"
    const val KEY_SCAN_INTERVAL_MINUTES = "scheduled_scan_interval_minutes"
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 1440
    const val DEFAULT_INTERVAL_MINUTES = 1440

    fun normalizeIntervalMinutes(intervalMinutes: Int): Int =
        intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERIODIC_SCAN, false)

    fun intervalMinutes(context: Context): Int =
        normalizeIntervalMinutes(
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SCAN_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        )

    fun enable(context: Context, intervalMinutes: Int) {
        val interval = normalizeIntervalMinutes(intervalMinutes)
        val request = PeriodicWorkRequestBuilder<PeriodicScanWorker>(
            interval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}

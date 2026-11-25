package com.msi.inspector.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object PeriodicScanScheduler {
    private const val UNIQUE_WORK_NAME = "msi_periodic_local_scan"
    private const val PREFERENCES_NAME = "msi_preferences"
    private const val KEY_PERIODIC_SCAN = "periodic_scan_enabled"
    const val KEY_SCAN_HOUR = "scheduled_scan_hour"
    const val KEY_SCAN_MINUTE = "scheduled_scan_minute"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERIODIC_SCAN, false)

    fun scheduledTime(context: Context): Pair<Int, Int> {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return preferences.getInt(KEY_SCAN_HOUR, 9) to preferences.getInt(KEY_SCAN_MINUTE, 0)
    }

    fun enable(context: Context, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        val request = PeriodicWorkRequestBuilder<PeriodicScanWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}

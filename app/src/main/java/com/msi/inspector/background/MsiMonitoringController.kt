package com.msi.inspector.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MsiMonitoringController {
    private const val PREFERENCES_NAME = "msi_preferences"
    private const val KEY_ENABLED = "active_monitoring_enabled"
    private const val PERIODIC_WORK_NAME = "msi_active_monitoring_periodic"
    private const val EVENT_WORK_NAME = "msi_active_monitoring_event_scan"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun enable(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()

        val periodicRequest =
            PeriodicWorkRequestBuilder<MsiMonitoringWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, MsiMonitoringService::class.java)
        )
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()

        WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(appContext).cancelUniqueWork(EVENT_WORK_NAME)
        appContext.stopService(Intent(appContext, MsiMonitoringService::class.java))
    }

    fun queueEventScan(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return

        val request = OneTimeWorkRequestBuilder<MsiMonitoringWorker>()
            .setInitialDelay(8, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            EVENT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

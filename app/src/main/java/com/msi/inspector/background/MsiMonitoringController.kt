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
        val workManager = WorkManager.getInstance(appContext)
        val periodicRequest =
            PeriodicWorkRequestBuilder<MsiMonitoringWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()

        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, MsiMonitoringService::class.java)
            )
        } catch (exception: Exception) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, false)
                .apply()
            runCatching { workManager.cancelUniqueWork(PERIODIC_WORK_NAME) }
            runCatching { workManager.cancelUniqueWork(EVENT_WORK_NAME) }
            throw exception
        }
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()

        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(EVENT_WORK_NAME)
        }
        runCatching {
            appContext.stopService(Intent(appContext, MsiMonitoringService::class.java))
        }
    }

    fun queueEventScan(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return

        val request = OneTimeWorkRequestBuilder<MsiMonitoringWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            EVENT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

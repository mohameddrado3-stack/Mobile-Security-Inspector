package com.msi.inspector.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BatteryHourlyScheduler {
    private const val PREFERENCES_NAME = "msi_preferences"
    private const val KEY_ENABLED = "battery_tracking_enabled"
    private const val UNIQUE_WORK_NAME = "msi_hourly_battery_sample"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) schedule(context)
        else WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) schedule(appContext)
        else WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BatteryHourlyWorker>(
            1,
            TimeUnit.HOURS,
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

package com.msi.inspector.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.msi.inspector.data.battery.BatteryUsageRepository
import java.util.concurrent.TimeUnit

object BatteryHourlyScheduler {
    private const val UNIQUE_WORK_NAME = "msi_hourly_battery_sample"

    fun isEnabled(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(
            BatteryUsageRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        return synchronized(preferences) {
            preferences.getBoolean(BatteryUsageRepository.KEY_TRACKING_ENABLED, true)
        }
    }

    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) schedule(context)
        else WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            BatteryUsageRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        synchronized(preferences) {
            preferences.edit()
                .putBoolean(BatteryUsageRepository.KEY_TRACKING_ENABLED, enabled)
                .apply()
        }

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

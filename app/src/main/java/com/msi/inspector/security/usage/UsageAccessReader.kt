package com.msi.inspector.security.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

class UsageAccessReader(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isOptedIn(): Boolean = preferences.getBoolean(KEY_OPT_IN, false)

    fun setOptedIn(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_OPT_IN, enabled).apply()
    }

    fun isAccessGranted(): Boolean {
        return try {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun lastUsedByPackage(): Map<String, Long> {
        if (!isOptedIn() || !isAccessGranted()) return emptyMap()
        return try {
            val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - LOOKBACK_DAYS * DAY_MILLIS
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
                .filter { it.lastTimeUsed > 0L }
                .groupBy { it.packageName }
                .mapValues { (_, usages) -> usages.maxOf { it.lastTimeUsed } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "msi_usage_access"
        private const val KEY_OPT_IN = "usage_access_opt_in"
        private const val LOOKBACK_DAYS = 90L
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

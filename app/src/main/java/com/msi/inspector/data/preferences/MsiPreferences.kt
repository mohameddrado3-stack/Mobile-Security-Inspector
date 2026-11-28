package com.msi.inspector.data.preferences

import android.content.Context
import com.msi.inspector.background.PeriodicScanScheduler
import com.msi.inspector.design.theme.MsiThemeMode

class MsiPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun loadTheme(): Pair<MsiThemeMode, Boolean> {
        val mode = runCatching {
            MsiThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, MsiThemeMode.DARK.name)!!
            )
        }.getOrDefault(MsiThemeMode.DARK)
        return mode to preferences.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun saveTheme(mode: MsiThemeMode, useDynamicColor: Boolean) {
        preferences.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, useDynamicColor)
            .apply()
    }

    fun loadScanIntervalMinutes(): Int =
        PeriodicScanScheduler.normalizeIntervalMinutes(
            preferences.getInt(
                PeriodicScanScheduler.KEY_SCAN_INTERVAL_MINUTES,
                PeriodicScanScheduler.DEFAULT_INTERVAL_MINUTES
            )
        )

    fun isOnboardingComplete(): Boolean =
        preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    fun isPeriodicScanEnabled(): Boolean =
        preferences.getBoolean(KEY_PERIODIC_SCAN, false)

    fun setPeriodicScanEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PERIODIC_SCAN, enabled).apply()
    }

    fun saveScanIntervalMinutes(intervalMinutes: Int) {
        preferences.edit()
            .putInt(
                PeriodicScanScheduler.KEY_SCAN_INTERVAL_MINUTES,
                PeriodicScanScheduler.normalizeIntervalMinutes(intervalMinutes)
            )
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "msi_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PERIODIC_SCAN = "periodic_scan_enabled"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}

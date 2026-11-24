package com.msi.inspector.security.applock

import android.content.Context

class AppLockSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "msi_security_preferences"
        private const val KEY_ENABLED = "app_lock_enabled"
    }
}

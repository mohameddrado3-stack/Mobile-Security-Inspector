package com.msi.inspector.data.preferences

import android.content.Context

class AppLocalLists(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun trustedPackages(): Set<String> =
        preferences.getStringSet(KEY_TRUSTED, emptySet())?.toSet().orEmpty()

    fun ignoredPackages(): Set<String> =
        preferences.getStringSet(KEY_IGNORED, emptySet())?.toSet().orEmpty()

    fun markTrusted(packageName: String) {
        updateSet(KEY_TRUSTED) { it + packageName }
        updateSet(KEY_IGNORED) { it - packageName }
    }

    fun markIgnored(packageName: String) {
        updateSet(KEY_IGNORED) { it + packageName }
        updateSet(KEY_TRUSTED) { it - packageName }
    }

    fun clear(packageName: String) {
        updateSet(KEY_TRUSTED) { it - packageName }
        updateSet(KEY_IGNORED) { it - packageName }
    }

    private fun updateSet(key: String, transform: (Set<String>) -> Set<String>) {
        val updated = transform(preferences.getStringSet(key, emptySet())?.toSet().orEmpty())
        preferences.edit().putStringSet(key, updated).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "msi_local_app_lists"
        private const val KEY_TRUSTED = "trusted_packages"
        private const val KEY_IGNORED = "ignored_packages"
    }
}

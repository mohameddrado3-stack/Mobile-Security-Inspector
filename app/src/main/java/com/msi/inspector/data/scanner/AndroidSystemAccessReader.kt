package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

class AndroidSystemAccessReader(context: Context) {
    private val appContext = context.applicationContext

    fun enabledAccessibilityPackages(): Set<String>? {
        return try {
            val raw = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return emptySet()
            raw.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
                .toSet()
        } catch (_: Exception) {
            null
        }
    }

    fun activeAdminPackages(): Set<String>? {
        return try {
            val manager = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            manager.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            null
        }
    }

    fun vpnActive(): Boolean? {
        return try {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return null
            val capabilities = manager.getNetworkCapabilities(network) ?: return null
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            null
        }
    }
}

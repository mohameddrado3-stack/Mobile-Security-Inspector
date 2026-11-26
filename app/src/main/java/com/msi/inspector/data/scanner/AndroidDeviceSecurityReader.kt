package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability

/**
 * Reads OS-level security indicators independently of the application inventory.
 * Every source is isolated so one unavailable setting does not invalidate the others.
 */
class AndroidDeviceSecurityReader(context: Context) {
    private val appContext = context.applicationContext

    fun read(): DeviceSecuritySnapshot = DeviceSecuritySnapshot(
        androidRelease = capture { Build.VERSION.RELEASE?.takeIf(String::isNotBlank) },
        apiLevel = capture { Build.VERSION.SDK_INT.toString() },
        securityPatch = capture { Build.VERSION.SECURITY_PATCH?.takeIf(String::isNotBlank) },
        secureScreenLock = capture {
            val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                ?: throw IllegalStateException("Keyguard service unavailable")
            if (keyguard.isDeviceSecure) "enabled" else "disabled"
        },
        storageEncryption = readStorageEncryption(),
        adbEnabled = readGlobalBoolean(Settings.Global.ADB_ENABLED),
        developerOptionsEnabled = readGlobalBoolean(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        collectedAt = System.currentTimeMillis()
    )

    private fun readGlobalBoolean(key: String): DeviceSecurityEvidence {
        return capture {
            when (Settings.Global.getInt(appContext.contentResolver, key, -1)) {
                1 -> "enabled"
                0 -> "disabled"
                else -> null
            }
        }
    }

    private fun capture(source: () -> String?): DeviceSecurityEvidence {
        return try {
            val value = source()
            if (value.isNullOrBlank()) {
                DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE)
            } else {
                DeviceSecurityEvidence(value, EvidenceAvailability.AVAILABLE)
            }
        } catch (_: SecurityException) {
            DeviceSecurityEvidence(null, EvidenceAvailability.ACCESS_DENIED)
        } catch (_: Exception) {
            DeviceSecurityEvidence(null, EvidenceAvailability.ERROR)
        }
    }

    private fun readStorageEncryption(): DeviceSecurityEvidence {
        return try {
            val policy = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: throw IllegalStateException("Device policy service unavailable")
            mapStorageEncryptionStatus(policy.storageEncryptionStatus)
        } catch (_: SecurityException) {
            DeviceSecurityEvidence(null, EvidenceAvailability.ACCESS_DENIED)
        } catch (_: Exception) {
            DeviceSecurityEvidence(null, EvidenceAvailability.ERROR)
        }
    }
}

internal fun mapStorageEncryptionStatus(status: Int): DeviceSecurityEvidence =
    when (status) {
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ->
            DeviceSecurityEvidence("active", EvidenceAvailability.AVAILABLE)
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ->
            DeviceSecurityEvidence("active_per_user", EvidenceAvailability.AVAILABLE)
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ->
            DeviceSecurityEvidence("active_default_key", EvidenceAvailability.AVAILABLE)
        DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE ->
            DeviceSecurityEvidence("inactive", EvidenceAvailability.AVAILABLE)
        DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED ->
            DeviceSecurityEvidence(null, EvidenceAvailability.UNSUPPORTED)
        @Suppress("DEPRECATION")
        DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING ->
            DeviceSecurityEvidence("activating", EvidenceAvailability.AVAILABLE)
        else -> DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE)
    }

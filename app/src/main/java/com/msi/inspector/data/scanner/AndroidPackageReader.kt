package com.msi.inspector.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.msi.inspector.domain.model.PackageMetadataState
import java.security.MessageDigest

data class PackageInfoReadResult(
    val packageInfo: PackageInfo?,
    val state: PackageMetadataState
)

class AndroidPackageReader(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val dangerousPermissionCache = HashMap<String, Boolean?>()

    @Suppress("DEPRECATION")
    fun installedApplications(): List<ApplicationInfo> =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    @Suppress("DEPRECATION")
    fun readPackageInfo(packageName: String): PackageInfoReadResult {
        val flags = PackageManager.GET_PERMISSIONS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        return try {
            PackageInfoReadResult(
                packageInfo = packageManager.getPackageInfo(packageName, flags),
                state = PackageMetadataState.AVAILABLE
            )
        } catch (_: PackageManager.NameNotFoundException) {
            PackageInfoReadResult(null, PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE)
        } catch (_: SecurityException) {
            PackageInfoReadResult(null, PackageMetadataState.ACCESS_DENIED)
        } catch (_: Exception) {
            PackageInfoReadResult(null, PackageMetadataState.ERROR)
        }
    }

    @Suppress("DEPRECATION")
    fun packageInfo(packageName: String): PackageInfo? = readPackageInfo(packageName).packageInfo

    fun applicationLabel(applicationInfo: ApplicationInfo): String =
        try {
            packageManager.getApplicationLabel(applicationInfo).toString()
                .ifBlank { applicationInfo.packageName }
        } catch (_: Exception) {
            applicationInfo.packageName
        }

    fun signingFingerprint(info: PackageInfo): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners ?: return null
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return null
            signatures.map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02X".format(byte) }
            }.sorted().joinToString(",").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun categoryLabel(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "ألعاب"
        ApplicationInfo.CATEGORY_AUDIO -> "صوت"
        ApplicationInfo.CATEGORY_VIDEO -> "فيديو"
        ApplicationInfo.CATEGORY_IMAGE -> "صور"
        ApplicationInfo.CATEGORY_SOCIAL -> "اجتماعي"
        ApplicationInfo.CATEGORY_NEWS -> "أخبار"
        ApplicationInfo.CATEGORY_MAPS -> "خرائط"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "إنتاجية"
        else -> null
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun isDangerousPermission(permission: String): Boolean? {
        if (dangerousPermissionCache.containsKey(permission)) {
            return dangerousPermissionCache[permission]
        }
        val result = try {
            val info = packageManager.getPermissionInfo(permission, 0)
            (info.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS) != 0
        } catch (_: Exception) {
            null
        }
        dangerousPermissionCache[permission] = result
        return result
    }

    fun installerLabel(packageName: String): String {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= 30) {
                val source = packageManager.getInstallSourceInfo(packageName)
                source.installingPackageName ?: source.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            when (installer) {
                "com.android.vending" -> "Google Play"
                "com.sec.android.app.samsungapps" -> "Galaxy Store"
                null -> "غير معروف"
                else -> installer
            }
        } catch (_: Exception) {
            "غير معروف"
        }
    }
}

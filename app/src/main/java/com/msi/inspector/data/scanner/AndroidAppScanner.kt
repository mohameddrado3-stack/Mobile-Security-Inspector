package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanSnapshot
import java.util.Locale

class AndroidAppScanner(context: Context) : AppScanner {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val dangerousPermissionCache = HashMap<String, Boolean>()

    override fun scan(): ScanSnapshot {
        val accessibilityPackages = enabledAccessibilityPackages()
        val adminPackages = activeAdminPackages()
        val vpnActive = isVpnActive()
        val installedApplications = getInstalledApplications()
        val results = ArrayList<AppAudit>(installedApplications.size)

        installedApplications.forEach { appInfo ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("تم إلغاء فحص التطبيقات.")
            }

            val packageName = appInfo.packageName
            val packageInfo = getPackageInfo(packageName)
            val requested = packageInfo?.requestedPermissions?.toList().orEmpty()
            val flags = packageInfo?.requestedPermissionsFlags
            val granted = requested.filterIndexed { index, _ ->
                flags != null &&
                    index < flags.size &&
                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val grantedSensitive = granted.filter(::isDangerousPermission)
            val requestedSensitive = requested.filter(::isDangerousPermission)
            val requestedSpecial = requested.mapNotNull(::specialPermissionRequestLabel).distinct()
            val categories = grantedSensitive.mapNotNull(PermissionAnalyzer::categoryFor).distinct()
            val activeAccessibility = accessibilityPackages.contains(packageName)
            val activeAdmin = adminPackages.contains(packageName)
            val specialReasons = buildList {
                if (activeAccessibility) add("خدمة إمكانية الوصول مفعّلة لهذا التطبيق.")
                if (activeAdmin) add("هذا التطبيق مفعّل كمسؤول جهاز.")
            }
            val signals = buildList {
                addAll(specialReasons)
                if (requestedSpecial.isNotEmpty()) {
                    add("التطبيق يطلب إعدادات وصول خاصة في ملفه، لكن حالة تفعيلها لا يمكن تأكيدها من هذا الفحص.")
                }
                if (categories.isNotEmpty()) {
                    add(
                        "صلاحيات Android الحساسة الممنوحة: " +
                            categories.joinToString("، ") +
                            ". قد تكون مطلوبة لوظيفة التطبيق ولا تعني أنه ضار."
                    )
                }
                if (requestedSensitive.size > grantedSensitive.size && requestedSensitive.isNotEmpty()) {
                    add("هناك صلاحيات حساسة مطلوبة لكنها غير ممنوحة حاليًا.")
                }
                if (isEmpty()) add("لم تظهر المؤشرات المحددة في هذا الفحص.")
            }
            val label = try {
                packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
            } catch (_: Exception) {
                packageName
            }
            results.add(
                AppAudit(
                    label = label,
                    packageName = packageName,
                    version = packageInfo?.versionName?.takeIf { it.isNotBlank() } ?: "غير متاح",
                    installer = installerLabel(packageName),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    grantedSensitive = grantedSensitive,
                    requestedSensitive = requestedSensitive,
                    requestedSpecialAccess = requestedSpecial,
                    signals = signals,
                    specialAccessReasons = specialReasons
                )
            )
        }

        val sorted = results.sortedWith(
            compareByDescending<AppAudit> { it.specialAccessReasons.isNotEmpty() }
                .thenByDescending { it.requestedSpecialAccess.isNotEmpty() }
                .thenByDescending { it.grantedSensitive.size }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )

        return ScanSnapshot(
            applications = sorted,
            vpnActive = vpnActive,
            completedAt = System.currentTimeMillis()
        )
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications(): List<ApplicationInfo> =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String): PackageInfo? {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun isDangerousPermission(permission: String): Boolean {
        return dangerousPermissionCache.getOrPut(permission) {
            try {
                val info = packageManager.getPermissionInfo(permission, 0)
                (info.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS) != 0
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun specialPermissionRequestLabel(permission: String): String? {
        return when (permission) {
            "android.permission.SYSTEM_ALERT_WINDOW" ->
                "طلب صلاحية الظهور فوق التطبيقات — حالة التفعيل غير مؤكدة."
            "android.permission.WRITE_SETTINGS" ->
                "طلب تعديل إعدادات النظام — حالة التفعيل غير مؤكدة."
            "android.permission.PACKAGE_USAGE_STATS" ->
                "طلب الوصول إلى إحصاءات الاستخدام — حالة التفعيل غير مؤكدة."
            "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                "طلب الوصول الشامل للملفات — حالة التفعيل غير مؤكدة."
            "android.permission.REQUEST_INSTALL_PACKAGES" ->
                "طلب صلاحية تثبيت حزم التطبيقات — حالة التفعيل غير مؤكدة."
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ->
                "طلب استثناء من تحسين البطارية — حالة التفعيل غير مؤكدة."
            "android.permission.ACCESS_NOTIFICATION_POLICY" ->
                "طلب الوصول إلى سياسة عدم الإزعاج — حالة التفعيل غير مؤكدة."
            else -> null
        }
    }

    private fun installerLabel(packageName: String): String {
        return try {
            val installer = if (android.os.Build.VERSION.SDK_INT >= 30) {
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

    private fun enabledAccessibilityPackages(): Set<String> {
        val raw = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptySet()
        return raw.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .toSet()
    }

    private fun activeAdminPackages(): Set<String> {
        return try {
            val manager = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            manager.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isVpnActive(): Boolean {
        return try {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) {
            false
        }
    }
}

package com.msi.inspector.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.analyzer.SpecialPermissionCatalog
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.security.usage.UsageAccessReader
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AndroidAppScanner(context: Context) : AppScanner {
    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }

    private val appContext = context.applicationContext
    private val packageReader = AndroidPackageReader(appContext)
    private val systemAccessReader = AndroidSystemAccessReader(appContext)
    private val usageAccessReader = UsageAccessReader(appContext)

    override suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit): ScanSnapshot {
        val accessibilityPackages = systemAccessReader.enabledAccessibilityPackages()
        val adminPackages = systemAccessReader.activeAdminPackages()
        val specialAccessStateAvailable = accessibilityPackages != null && adminPackages != null
        val vpnActive = systemAccessReader.vpnActive()
        val lastUsedByPackage = usageAccessReader.lastUsedByPackage()
        val installedApplications = packageReader.installedApplications()
        val results = ArrayList<AppAudit>(installedApplications.size)
        onProgress(0, installedApplications.size)

        installedApplications.forEachIndexed { index, appInfo ->
            currentCoroutineContext().ensureActive()

            val packageName = appInfo.packageName
            val packageRead = packageReader.readPackageInfo(packageName)
            val packageInfo = packageRead.packageInfo
            val requested = packageInfo?.requestedPermissions?.toList().orEmpty()
            val flags = packageInfo?.requestedPermissionsFlags
            val granted = requested.filterIndexed { permissionIndex, _ ->
                flags != null &&
                    permissionIndex < flags.size &&
                    (flags[permissionIndex] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val classifications = requested.associateWith(packageReader::isDangerousPermission)
            val grantedSensitive = granted.filter { classifications[it] == true }
            val requestedSensitive = requested.filter { classifications[it] == true }
            val unclassifiedPermissions = requested.filter { classifications[it] == null }
            val requestedSpecial = requested.mapNotNull(SpecialPermissionCatalog::requestLabel).distinct()
            val categories = grantedSensitive.mapNotNull(PermissionAnalyzer::categoryFor).distinct()
            val activeAccessibility = accessibilityPackages?.contains(packageName) == true
            val activeAdmin = adminPackages?.contains(packageName) == true
            val lastUsedAt = lastUsedByPackage[packageName]
            val unusedForAtLeastThirtyDays = lastUsedAt != null &&
                System.currentTimeMillis() - lastUsedAt >= THIRTY_DAYS_MILLIS
            val specialReasons = buildList {
                if (activeAccessibility) add("خدمة إمكانية الوصول مفعّلة لهذا التطبيق.")
                if (activeAdmin) add("هذا التطبيق مفعّل كمسؤول جهاز.")
            }
            val signals = buildList {
                addAll(specialReasons)
                if (!specialAccessStateAvailable) {
                    add("تعذّر التحقق من بعض حالات الوصول الخاص؛ غياب المؤشر لا يعني أن الحالة غير مفعّلة.")
                }
                if (packageInfo == null) {
                    add(
                        when (packageRead.state) {
                            com.msi.inspector.domain.model.PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE ->
                                "تعذرت قراءة بيانات الحزمة؛ قد تكون غير متاحة أو تغيّرت أثناء الفحص."
                            com.msi.inspector.domain.model.PackageMetadataState.ACCESS_DENIED ->
                                "رفض Android الوصول إلى بيانات هذه الحزمة."
                            com.msi.inspector.domain.model.PackageMetadataState.ERROR ->
                                "حدث خطأ أثناء قراءة بيانات هذه الحزمة."
                            com.msi.inspector.domain.model.PackageMetadataState.AVAILABLE -> ""
                        }.takeIf { it.isNotBlank() } ?: ""
                    )
                }
                if (unclassifiedPermissions.isNotEmpty()) {
                    add("تعذر تحديد مستوى حساسية ${unclassifiedPermissions.size} صلاحية؛ لم تُحتسب كصلاحيات عادية أو حساسة.")
                }
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
                if (unusedForAtLeastThirtyDays && grantedSensitive.isNotEmpty()) {
                    add("لدى التطبيق صلاحيات حساسة ممنوحة، ولم يُرصد استخدام له خلال 30 يومًا على الأقل (حسب Usage Access المفعّل اختياريًا).")
                }
                if (isEmpty()) add("لم تظهر المؤشرات المحددة في هذا الفحص.")
            }
            val label = packageReader.applicationLabel(appInfo)
            results.add(
                AppAudit(
                    label = label,
                    packageName = packageName,
                    version = packageInfo?.versionName?.takeIf { it.isNotBlank() } ?: "غير متاح",
                    installer = packageReader.installerLabel(packageName),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    grantedSensitive = grantedSensitive,
                    requestedSensitive = requestedSensitive,
                    requestedSpecialAccess = requestedSpecial,
                    signals = signals.filter(String::isNotBlank),
                    specialAccessReasons = specialReasons,
                    targetSdk = appInfo.targetSdkVersion,
                    firstInstallTime = packageInfo?.firstInstallTime,
                    lastUpdateTime = packageInfo?.lastUpdateTime,
                    signingCertificateSha256 = packageInfo?.let(packageReader::signingFingerprint),
                    categoryLabel = packageReader.categoryLabel(appInfo.category),
                    lastUsedAt = lastUsedAt,
                    packageMetadataAvailable = packageInfo != null,
                    packageMetadataState = packageRead.state,
                    unclassifiedPermissions = unclassifiedPermissions,
                    specialAccessStateAvailable = specialAccessStateAvailable
                )
            )
            onProgress(index + 1, installedApplications.size)
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
}

package com.msi.inspector.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.security.usage.UsageAccessReader
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanSnapshot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

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
        val vpnActive = systemAccessReader.vpnActive()
        val lastUsedByPackage = usageAccessReader.lastUsedByPackage()
        val installedApplications = packageReader.installedApplications()
        val results = ArrayList<AppAudit>(installedApplications.size)
        onProgress(0, installedApplications.size)

        installedApplications.forEachIndexed { index, appInfo ->
            currentCoroutineContext().ensureActive()

            val packageName = appInfo.packageName
            val packageInfo = packageReader.packageInfo(packageName)
            val requested = packageInfo?.requestedPermissions?.toList().orEmpty()
            val flags = packageInfo?.requestedPermissionsFlags
            val granted = requested.filterIndexed { permissionIndex, _ ->
                flags != null &&
                    permissionIndex < flags.size &&
                    (flags[permissionIndex] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val grantedSensitive = granted.filter(packageReader::isDangerousPermission)
            val requestedSensitive = requested.filter(::isDangerousPermission)
            val requestedSpecial = requested.mapNotNull(::specialPermissionRequestLabel).distinct()
            val categories = grantedSensitive.mapNotNull(PermissionAnalyzer::categoryFor).distinct()
            val activeAccessibility = accessibilityPackages.contains(packageName)
            val activeAdmin = adminPackages.contains(packageName)
            val lastUsedAt = lastUsedByPackage[packageName]
            val unusedForAtLeastThirtyDays = lastUsedAt != null &&
                System.currentTimeMillis() - lastUsedAt >= THIRTY_DAYS_MILLIS
            val specialReasons = buildList {
                if (activeAccessibility) add("خدمة إمكانية الوصول مفعّلة لهذا التطبيق.")
                if (activeAdmin) add("هذا التطبيق مفعّل كمسؤول جهاز.")
            }
            val signals = buildList {
                addAll(specialReasons)
                if (requestedSpecial.isNotEmpty()) {
                    add("التطبيق يطلب إعدادات وصول خاصة في ملفه، لكن حالة تفعيلها لا يمكن تأكيدها من هذا الفحص.")
                }
                categoryCoherenceNote(appInfo.category, categories)?.let(::add)
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
                    signals = signals,
                    specialAccessReasons = specialReasons,
                    targetSdk = appInfo.targetSdkVersion,
                    firstInstallTime = packageInfo?.firstInstallTime,
                    lastUpdateTime = packageInfo?.lastUpdateTime,
                    signingCertificateSha256 = packageInfo?.let(packageReader::signingFingerprint),
                    categoryLabel = packageReader.categoryLabel(appInfo.category),
                    lastUsedAt = lastUsedAt,
                    packageMetadataAvailable = packageInfo != null
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

    private fun categoryCoherenceNote(category: Int, grantedCategories: List<String>): String? {
        if (category == ApplicationInfo.CATEGORY_UNDEFINED || grantedCategories.isEmpty()) return null
        val expected = when (category) {
            ApplicationInfo.CATEGORY_GAME -> setOf("الكاميرا", "الميكروفون", "الموقع", "الملفات والتخزين", "النشاط ومستشعرات الجسم", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_AUDIO -> setOf("الميكروفون", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_VIDEO -> setOf("الكاميرا", "الميكروفون", "الملفات والتخزين", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_IMAGE -> setOf("الكاميرا", "الملفات والتخزين", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_SOCIAL -> setOf("الكاميرا", "الميكروفون", "الموقع", "جهات الاتصال", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_NEWS -> setOf("الموقع", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_MAPS -> setOf("الموقع", "الكاميرا", "الميكروفون", "الأجهزة القريبة والبلوتوث", "الإشعارات")
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> setOf("الكاميرا", "الميكروفون", "جهات الاتصال", "التقويم", "الملفات والتخزين", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            else -> return null
        }
        val unexpected = grantedCategories.filterNot { it in expected }
        if (unexpected.isEmpty()) return null
        val categoryName = packageReader.categoryLabel(category) ?: return null
        return "قاعدة محلية تجريبية: بعض الصلاحيات الممنوحة قد لا تكون متوقعة تمامًا لتصنيف التطبيق ($categoryName): ${unexpected.joinToString("، ")}. قد تكون مبررة بوظائف فرعية؛ راجع سببها قبل اتخاذ قرار."
    }


}

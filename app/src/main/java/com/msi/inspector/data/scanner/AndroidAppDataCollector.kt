package com.msi.inspector.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.msi.inspector.domain.analyzer.SpecialPermissionCatalog
import com.msi.inspector.domain.model.CollectedAppEvidence
import com.msi.inspector.domain.model.CollectedDeviceEvidence
import com.msi.inspector.domain.model.CollectedPermissionEvidence
import com.msi.inspector.domain.model.PermissionProtection
import com.msi.inspector.security.usage.UsageAccessReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Android-facing adapter. It reads framework/package-manager state and returns
 * normalized evidence; it does not calculate findings or risk-like statuses.
 */
class AndroidAppDataCollector(context: Context) {
    private val appContext = context.applicationContext
    private val packageReader = AndroidPackageReader(appContext)
    private val systemAccessReader = AndroidSystemAccessReader(appContext)
    private val usageAccessReader = UsageAccessReader(appContext)

    suspend fun collect(
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): CollectedDeviceEvidence {
        val accessibilityPackages = systemAccessReader.enabledAccessibilityPackages()
        val adminPackages = systemAccessReader.activeAdminPackages()
        val vpnActive = systemAccessReader.vpnActive()
        val lastUsedByPackage = usageAccessReader.lastUsedByPackage()
        val installedApplications = packageReader.installedApplications()
        val collected = ArrayList<CollectedAppEvidence>(installedApplications.size)
        onProgress(0, installedApplications.size)

        installedApplications.forEachIndexed { index, applicationInfo ->
            currentCoroutineContext().ensureActive()
            collected += collectApplication(
                applicationInfo = applicationInfo,
                accessibilityPackages = accessibilityPackages,
                adminPackages = adminPackages,
                lastUsedAt = lastUsedByPackage[applicationInfo.packageName]
            )
            onProgress(index + 1, installedApplications.size)
        }

        return CollectedDeviceEvidence(
            applications = collected,
            vpnActive = vpnActive,
            completedAt = System.currentTimeMillis()
        )
    }

    private fun collectApplication(
        applicationInfo: ApplicationInfo,
        accessibilityPackages: Set<String>?,
        adminPackages: Set<String>?,
        lastUsedAt: Long?
    ): CollectedAppEvidence {
        val packageName = applicationInfo.packageName
        val read = packageReader.readPackageInfo(packageName)
        val packageInfo = read.packageInfo
        val requestedPermissions = packageInfo?.requestedPermissions.orEmpty()
        val permissionFlags = packageInfo?.requestedPermissionsFlags
        val permissions = requestedPermissions.mapIndexed { index, permission ->
            val grantStateKnown = permissionFlags != null && index < permissionFlags.size
            val granted = grantStateKnown &&
                (permissionFlags!![index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val protection = when (packageReader.isDangerousPermission(permission)) {
                true -> PermissionProtection.DANGEROUS
                false -> PermissionProtection.NON_DANGEROUS
                null -> PermissionProtection.UNKNOWN
            }
            CollectedPermissionEvidence(
                name = permission,
                granted = granted,
                protection = protection,
                grantStateKnown = grantStateKnown
            )
        }

        return CollectedAppEvidence(
            label = packageReader.applicationLabel(applicationInfo),
            packageName = packageName,
            version = packageInfo?.versionName?.takeIf(String::isNotBlank) ?: "غير متاح",
            installer = packageReader.installerLabel(packageName),
            isSystem = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            permissions = permissions,
            requestedSpecialAccess = packageInfo?.requestedPermissions.orEmpty()
                .mapNotNull(SpecialPermissionCatalog::requestLabel)
                .distinct(),
            accessibilityEnabled = accessibilityPackages?.contains(packageName),
            deviceAdminEnabled = adminPackages?.contains(packageName),
            lastUsedAt = lastUsedAt,
            categoryLabel = packageReader.categoryLabel(applicationInfo.category),
            targetSdk = applicationInfo.targetSdkVersion,
            firstInstallTime = packageInfo?.firstInstallTime,
            lastUpdateTime = packageInfo?.lastUpdateTime,
            signingCertificateSha256 = packageInfo?.let(packageReader::signingFingerprint),
            packageMetadataState = read.state
        )
    }
}

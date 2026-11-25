package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.CollectedAppEvidence
import com.msi.inspector.domain.model.CollectedDeviceEvidence
import com.msi.inspector.domain.model.CollectedPermissionEvidence
import com.msi.inspector.domain.model.PackageMetadataState
import com.msi.inspector.domain.model.PermissionProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAuditAnalyzerTest {
    @Test
    fun convertsCollectedPermissionEvidenceWithoutPromotingUnknownPermissions() {
        val now = 2_000_000_000L
        val app = CollectedAppEvidence(
            label = "Example app",
            packageName = "com.example.app",
            version = "1.0",
            installer = "غير معروف",
            isSystem = false,
            permissions = listOf(
                CollectedPermissionEvidence(
                    "android.permission.CAMERA",
                    granted = true,
                    protection = PermissionProtection.DANGEROUS
                ),
                CollectedPermissionEvidence(
                    "android.permission.RECORD_AUDIO",
                    granted = false,
                    protection = PermissionProtection.DANGEROUS
                ),
                CollectedPermissionEvidence(
                    "android.permission.INTERNET",
                    granted = true,
                    protection = PermissionProtection.NON_DANGEROUS
                ),
                CollectedPermissionEvidence(
                    "vendor.permission.UNKNOWN",
                    granted = true,
                    protection = PermissionProtection.UNKNOWN
                )
            ),
            requestedSpecialAccess = listOf("طلب إعداد خاص"),
            accessibilityEnabled = true,
            deviceAdminEnabled = null,
            lastUsedAt = now - 31L * 24L * 60L * 60L * 1000L,
            categoryLabel = "إنتاجية",
            targetSdk = 35,
            firstInstallTime = 10L,
            lastUpdateTime = 20L,
            signingCertificateSha256 = "ABC",
            packageMetadataState = PackageMetadataState.AVAILABLE
        )

        val result = AppAuditAnalyzer.analyze(
            CollectedDeviceEvidence(listOf(app), vpnActive = null, completedAt = now)
        ).applications.single()

        assertEquals(listOf("android.permission.CAMERA"), result.grantedSensitive)
        assertEquals(
            listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            result.requestedSensitive
        )
        assertEquals(listOf("vendor.permission.UNKNOWN"), result.unclassifiedPermissions)
        assertFalse(result.grantedSensitive.contains("android.permission.INTERNET"))
        assertEquals(1, result.specialAccessReasons.size)
        assertFalse(result.specialAccessStateAvailable)
        assertTrue(result.signals.any { it.contains("تعذر تحديد مستوى حساسية") })
        assertTrue(result.signals.any { it.contains("30 يومًا") })
        assertEquals(PackageMetadataState.AVAILABLE, result.packageMetadataState)
    }

    @Test
    fun unavailablePackageMetadataIsPreservedAsUnknownEvidence() {
        val app = CollectedAppEvidence(
            label = "Limited",
            packageName = "com.example.limited",
            version = "غير متاح",
            installer = "غير معروف",
            isSystem = false,
            permissions = emptyList(),
            requestedSpecialAccess = emptyList(),
            accessibilityEnabled = false,
            deviceAdminEnabled = false,
            lastUsedAt = null,
            categoryLabel = null,
            targetSdk = 35,
            firstInstallTime = null,
            lastUpdateTime = null,
            signingCertificateSha256 = null,
            packageMetadataState = PackageMetadataState.ACCESS_DENIED
        )

        val result = AppAuditAnalyzer.analyze(
            CollectedDeviceEvidence(listOf(app), vpnActive = null, completedAt = 100L)
        ).applications.single()

        assertFalse(result.packageMetadataAvailable)
        assertEquals(PackageMetadataState.ACCESS_DENIED, result.packageMetadataState)
        assertTrue(result.signals.any { it.contains("رفض Android الوصول") })
    }
}

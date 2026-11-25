package com.msi.inspector.data

import com.msi.inspector.data.serialization.AppAuditJsonCodec
import com.msi.inspector.domain.model.AppAudit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppAuditJsonCodecTest {
    @Test
    fun decodingOlderHistoryWithoutEvidenceFieldsUsesCompatibleDefaults() {
        val restored = AppAuditJsonCodec.decode(
            """[{"label":"Old app","packageName":"com.example.old","version":"1.0"}]"""
        ).single()

        assertEquals(true, restored.packageMetadataAvailable)
        assertEquals(
            com.msi.inspector.domain.model.PackageMetadataState.AVAILABLE,
            restored.packageMetadataState
        )
        assertEquals(emptyList<String>(), restored.unclassifiedPermissions)
        assertEquals(false, restored.specialAccessStateAvailable)
    }

    @Test
    fun roundTripPreservesPersistedApplicationEvidence() {
        val application = AppAudit(
            label = "Example",
            packageName = "com.example.app",
            version = "2.4.1",
            installer = "Google Play",
            isSystem = false,
            grantedSensitive = listOf("android.permission.CAMERA"),
            requestedSensitive = listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            requestedSpecialAccess = listOf("Overlay"),
            signals = listOf("Local signal"),
            specialAccessReasons = listOf("Accessibility enabled"),
            targetSdk = 35,
            firstInstallTime = 100L,
            lastUpdateTime = 200L,
            signingCertificateSha256 = "ABCD",
            categoryLabel = "Productivity",
            lastUsedAt = 300L,
            packageMetadataAvailable = true,
            packageMetadataState = com.msi.inspector.domain.model.PackageMetadataState.ACCESS_DENIED,
            unclassifiedPermissions = listOf("vendor.permission.UNKNOWN"),
            specialAccessStateAvailable = false
        )

        val restored = AppAuditJsonCodec.decode(AppAuditJsonCodec.encode(listOf(application))).single()

        assertEquals(application.label, restored.label)
        assertEquals(application.packageName, restored.packageName)
        assertEquals(application.version, restored.version)
        assertEquals(application.installer, restored.installer)
        assertEquals(application.isSystem, restored.isSystem)
        assertEquals(application.grantedSensitive, restored.grantedSensitive)
        assertEquals(application.requestedSensitive, restored.requestedSensitive)
        assertEquals(application.requestedSpecialAccess, restored.requestedSpecialAccess)
        assertEquals(application.specialAccessReasons, restored.specialAccessReasons)
        assertEquals(application.targetSdk, restored.targetSdk)
        assertEquals(application.firstInstallTime, restored.firstInstallTime)
        assertEquals(application.lastUpdateTime, restored.lastUpdateTime)
        assertEquals(application.signingCertificateSha256, restored.signingCertificateSha256)
        assertEquals(application.categoryLabel, restored.categoryLabel)
        assertEquals(application.lastUsedAt, restored.lastUsedAt)
        assertEquals(application.packageMetadataAvailable, restored.packageMetadataAvailable)
        assertEquals(application.packageMetadataState, restored.packageMetadataState)
        assertEquals(application.unclassifiedPermissions, restored.unclassifiedPermissions)
        assertEquals(application.specialAccessStateAvailable, restored.specialAccessStateAvailable)
        assertEquals(emptyList<String>(), restored.signals)
    }
}

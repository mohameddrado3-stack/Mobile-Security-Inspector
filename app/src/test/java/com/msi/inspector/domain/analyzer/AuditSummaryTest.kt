package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.PackageMetadataState
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditSummaryTest {
    @Test
    fun countsIncompleteEvidenceAcrossPackagePermissionsAndSpecialAccess() {
        val applications = listOf(
            app("com.example.complete"),
            app("com.example.metadata").copy(
                packageMetadataAvailable = false,
                packageMetadataState = PackageMetadataState.ACCESS_DENIED
            ),
            app("com.example.permission").copy(
                unclassifiedPermissions = listOf("vendor.permission.UNKNOWN")
            ),
            app("com.example.special").copy(
                specialAccessStateAvailable = false
            ),
            app("com.example.active").copy(
                specialAccessReasons = listOf("Accessibility enabled"),
                specialAccessStateAvailable = false
            )
        )

        val summary = AuditSummary.from(applications)

        assertEquals(5, summary.totalApplications)
        assertEquals(4, summary.applicationsWithIncompleteEvidence)
        assertEquals(1, summary.activeSpecialAccess)
    }

    private fun app(packageName: String) = AppAudit(
        label = packageName,
        packageName = packageName,
        version = "1.0",
        installer = "Test",
        isSystem = false,
        grantedSensitive = emptyList(),
        requestedSensitive = emptyList(),
        requestedSpecialAccess = emptyList(),
        signals = emptyList(),
        specialAccessReasons = emptyList(),
        packageMetadataAvailable = true,
        packageMetadataState = PackageMetadataState.AVAILABLE,
        specialAccessStateAvailable = true
    )
}

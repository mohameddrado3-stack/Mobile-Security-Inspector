package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.PackageMetadataState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {
    @Test
    fun incompleteFilterIncludesEveryApplicationWithPartialEvidence() {
        val metadataDenied = app("com.example.denied").copy(
            packageMetadataAvailable = false,
            packageMetadataState = PackageMetadataState.ACCESS_DENIED
        )
        val unknownPermission = app("com.example.permission").copy(
            unclassifiedPermissions = listOf("vendor.permission.UNKNOWN")
        )
        val specialAccessUnavailable = app("com.example.special").copy(
            specialAccessStateAvailable = false
        )
        val complete = app("com.example.complete")

        val result = filterApplications(
            applications = listOf(
                metadataDenied,
                unknownPermission,
                specialAccessUnavailable,
                complete
            ),
            query = "",
            filter = AppFilter.INCOMPLETE
        )

        assertEquals(
            listOf("com.example.denied", "com.example.permission", "com.example.special"),
            result.map { it.packageName }
        )
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

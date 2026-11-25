package com.msi.inspector.domain

import com.msi.inspector.domain.analyzer.compareApplications
import com.msi.inspector.domain.model.AppAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppComparisonTest {
    @Test
    fun comparisonShowsPermissionDifferencesInsteadOfOnlyCounts() {
        val first = app(
            granted = listOf("CAMERA", "RECORD_AUDIO"),
            requested = listOf("CAMERA", "LOCATION"),
            signature = "AA"
        )
        val second = app(
            granted = listOf("CAMERA", "LOCATION"),
            requested = listOf("CAMERA", "SMS"),
            signature = "BB"
        )
        val result = compareApplications(first, second)
        assertEquals(listOf("RECORD_AUDIO"), result.grantedOnlyInFirst)
        assertEquals(listOf("LOCATION"), result.grantedOnlyInSecond)
        assertEquals(listOf("LOCATION"), result.requestedOnlyInFirst)
        assertEquals(listOf("SMS"), result.requestedOnlyInSecond)
        assertEquals(false, result.signaturesMatch)
    }

    @Test
    fun comparisonDoesNotClaimSignatureMatchWhenFingerprintIsMissing() {
        val result = compareApplications(app(signature = "AA"), app(signature = null))
        assertNull(result.signaturesMatch)
        assertTrue(result.grantedOnlyInFirst.isEmpty())
        assertTrue(result.grantedOnlyInSecond.isEmpty())
    }

    private fun app(
        granted: List<String> = emptyList(),
        requested: List<String> = emptyList(),
        signature: String? = null
    ) = AppAudit(
        label = "Example", packageName = "com.example.app", version = "1.0", installer = "Test",
        isSystem = false, grantedSensitive = granted, requestedSensitive = requested,
        requestedSpecialAccess = emptyList(), signals = emptyList(), specialAccessReasons = emptyList(),
        signingCertificateSha256 = signature
    )
}

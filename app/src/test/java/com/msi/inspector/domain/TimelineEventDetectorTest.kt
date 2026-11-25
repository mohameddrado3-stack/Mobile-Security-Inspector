package com.msi.inspector.domain

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.domain.timeline.TimelineEventDetector
import com.msi.inspector.domain.timeline.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEventDetectorTest {
    @Test fun firstScanCreatesLocalBaseline() {
        val events = TimelineEventDetector.detect(null, snapshot())
        assertEquals(1, events.size)
        assertEquals(TimelineEventType.BASELINE_CREATED, events.single().type)
    }

    @Test fun detectsAppAndPermissionChangesWithoutRiskVerdicts() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app())),
            snapshot(listOf(app(granted = listOf("CAMERA")), app("com.example.new", "New app")), 2_000L)
        )
        assertTrue(events.any { it.type == TimelineEventType.APP_ADDED })
        assertTrue(events.any { it.type == TimelineEventType.SENSITIVE_PERMISSION_ADDED })
        assertFalse(events.any { it.title.contains("آمن") || it.title.contains("ضار") })
    }

    @Test fun unknownSignatureDoesNotBecomeSignatureChange() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app(signature = null))),
            snapshot(listOf(app(signature = "ABC")), 2_000L)
        )
        assertFalse(events.any { it.type == TimelineEventType.SIGNATURE_CHANGED })
    }

    @Test fun detectsSignatureChangeOnlyWhenBothFingerprintsExist() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app(signature = "OLD"))),
            snapshot(listOf(app(signature = "NEW")), 2_000L)
        )
        assertTrue(events.any { it.type == TimelineEventType.SIGNATURE_CHANGED })
    }

    private fun snapshot(apps: List<AppAudit> = emptyList(), at: Long = 1_000L) =
        ScanSnapshot(apps, false, at)

    private fun app(
        packageName: String = "com.example.app", label: String = "Example",
        granted: List<String> = emptyList(), signature: String? = null
    ) = AppAudit(
        label, packageName, "1.0", "unknown", false, granted, emptyList(),
        emptyList(), emptyList(), emptyList(), signingCertificateSha256 = signature
    )
}

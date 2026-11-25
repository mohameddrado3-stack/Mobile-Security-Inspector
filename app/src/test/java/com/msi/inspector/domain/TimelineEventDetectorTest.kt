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

    @Test fun unavailablePackageMetadataDoesNotLookLikePermissionRevocation() {
        val old = app(granted = listOf("android.permission.CAMERA"))
        val unavailable = app(granted = emptyList()).copy(packageMetadataAvailable = false)
        val events = TimelineEventDetector.detect(
            snapshot(listOf(old)),
            snapshot(listOf(unavailable), 2_000L)
        )
        assertFalse(events.any { it.type == TimelineEventType.SENSITIVE_PERMISSION_REMOVED })
        assertFalse(events.any { it.type == TimelineEventType.APP_VERSION_CHANGED })
    }

    @Test fun missingAppIsReportedAsNoLongerVisibleNotAsUninstalled() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app())),
            snapshot(emptyList(), 2_000L)
        )
        val event = events.single { it.type == TimelineEventType.APP_REMOVED }
        assertTrue(event.title.contains("ظاهرًا"))
        assertTrue(event.details.contains("لا يثبت"))
    }

    @Test fun detectsKnownVpnActiveToInactiveTransition() {
        val events = TimelineEventDetector.detect(
            ScanSnapshot(emptyList(), true, 1_000L),
            ScanSnapshot(emptyList(), false, 2_000L)
        )
        assertTrue(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
        assertTrue(events.single { it.type == TimelineEventType.VPN_STATE_CHANGED }.details.contains("لم يُرصد"))
    }

    @Test fun detectsKnownVpnInactiveToActiveTransition() {
        val events = TimelineEventDetector.detect(
            ScanSnapshot(emptyList(), false, 1_000L),
            ScanSnapshot(emptyList(), true, 2_000L)
        )
        assertTrue(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
    }

    @Test fun unknownVpnBaselineDoesNotCreateVpnChangeEvent() {
        val events = TimelineEventDetector.detect(
            null,
            ScanSnapshot(emptyList(), null, 1_000L)
        )
        assertFalse(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
    }

    @Test fun unknownPreviousVpnStateDoesNotCreateFalseTransition() {
        val events = TimelineEventDetector.detect(
            ScanSnapshot(emptyList(), null, 1_000L),
            ScanSnapshot(emptyList(), true, 2_000L)
        )
        assertFalse(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
    }

    @Test fun unknownCurrentVpnStateDoesNotCreateFalseTransition() {
        val events = TimelineEventDetector.detect(
            ScanSnapshot(emptyList(), true, 1_000L),
            ScanSnapshot(emptyList(), null, 2_000L)
        )
        assertFalse(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
    }

    @Test fun unavailableSpecialAccessEvidenceDoesNotCreateFalseChange() {
        val previous = snapshot(listOf(app())).copy(
            applications = listOf(app().copy(
                specialAccessReasons = listOf("Accessibility enabled"),
                specialAccessStateAvailable = true
            ))
        )
        val current = snapshot(listOf(app()), 2_000L).copy(
            applications = listOf(app().copy(
                specialAccessReasons = emptyList(),
                specialAccessStateAvailable = false
            ))
        )
        val events = TimelineEventDetector.detect(previous, current)
        assertFalse(events.any { it.type == TimelineEventType.SPECIAL_ACCESS_CHANGED })
    }

    @Test fun detectsRemovedSensitivePermissionAndVpnTransition() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app(granted = listOf("android.permission.CAMERA"))), 1_000L),
            ScanSnapshot(
                applications = listOf(app(granted = emptyList())),
                vpnActive = true,
                completedAt = 2_000L
            )
        )
        assertTrue(events.any { it.type == TimelineEventType.SENSITIVE_PERMISSION_REMOVED })
        assertTrue(events.any { it.type == TimelineEventType.VPN_STATE_CHANGED })
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

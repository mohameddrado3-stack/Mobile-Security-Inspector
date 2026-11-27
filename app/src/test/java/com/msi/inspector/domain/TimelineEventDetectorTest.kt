package com.msi.inspector.domain

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability
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

    @Test fun detectsKnownVersionNameChange() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app().copy(version = "1.0"))),
            snapshot(listOf(app().copy(version = "1.1")), 2_000L)
        )

        assertTrue(events.any { it.type == TimelineEventType.APP_VERSION_CHANGED })
    }

    @Test fun unavailableVersionNameDoesNotCreateFalseVersionChange() {
        val events = TimelineEventDetector.detect(
            snapshot(listOf(app().copy(version = "1.0"))),
            snapshot(listOf(app().copy(version = "غير متاح")), 2_000L)
        )

        assertFalse(events.any { it.type == TimelineEventType.APP_VERSION_CHANGED })
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

    @Test fun unknownGrantStateDoesNotCreateFalsePermissionRevocation() {
        val previous = app(
            granted = listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")
        )
        val current = app().copy(
            permissionsWithUnknownGrantState = listOf("android.permission.CAMERA")
        )

        val events = TimelineEventDetector.detect(
            snapshot(listOf(previous)),
            snapshot(listOf(current), 2_000L)
        )

        val event = events.single { it.type == TimelineEventType.SENSITIVE_PERMISSION_REMOVED }
        assertTrue(event.details.contains("android.permission.RECORD_AUDIO"))
        assertFalse(event.details.contains("android.permission.CAMERA"))
    }

    @Test fun previouslyUnknownPermissionDoesNotCreateFalsePermissionAddition() {
        val previous = app().copy(
            permissionsWithUnknownGrantState = listOf("android.permission.CAMERA")
        )
        val current = app(granted = listOf("android.permission.CAMERA"))

        val events = TimelineEventDetector.detect(
            snapshot(listOf(previous)),
            snapshot(listOf(current), 2_000L)
        )

        assertFalse(events.any { it.type == TimelineEventType.SENSITIVE_PERMISSION_ADDED })
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


    @Test fun recordsSystemSecurityChangesOnlyWhenBothSourcesAreAvailable() {
        val previousSecurity = deviceSecurity(
            lock = DeviceSecurityEvidence("disabled", EvidenceAvailability.AVAILABLE),
            patch = DeviceSecurityEvidence("2026-09-01", EvidenceAvailability.AVAILABLE),
            adb = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE)
        )
        val currentSecurity = deviceSecurity(
            lock = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE),
            patch = DeviceSecurityEvidence("2026-10-01", EvidenceAvailability.AVAILABLE),
            adb = DeviceSecurityEvidence("disabled", EvidenceAvailability.AVAILABLE)
        )
        val events = TimelineEventDetector.detect(
            snapshot().copy(deviceSecurity = previousSecurity),
            snapshot(at = 2_000L).copy(deviceSecurity = currentSecurity)
        )

        val event = events.single { it.type == TimelineEventType.DEVICE_SECURITY_CHANGED }
        assertTrue(event.details.contains("قفل الشاشة الآمن"))
        assertTrue(event.details.contains("مستوى تصحيح الأمان"))
        assertTrue(event.details.contains("حالة ADB"))
    }

    @Test fun unavailableSystemSecurityEvidenceDoesNotCreateFalseChange() {
        val previousSecurity = deviceSecurity(
            lock = DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE)
        )
        val currentSecurity = deviceSecurity(
            lock = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE)
        )
        val events = TimelineEventDetector.detect(
            snapshot().copy(deviceSecurity = previousSecurity),
            snapshot(at = 2_000L).copy(deviceSecurity = currentSecurity)
        )

        assertFalse(events.any { it.type == TimelineEventType.DEVICE_SECURITY_CHANGED })
    }

    private fun deviceSecurity(
        lock: DeviceSecurityEvidence = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE),
        patch: DeviceSecurityEvidence = DeviceSecurityEvidence("2026-09-01", EvidenceAvailability.AVAILABLE),
        adb: DeviceSecurityEvidence = DeviceSecurityEvidence("disabled", EvidenceAvailability.AVAILABLE)
    ) = DeviceSecuritySnapshot(
        androidRelease = DeviceSecurityEvidence("16", EvidenceAvailability.AVAILABLE),
        apiLevel = DeviceSecurityEvidence("36", EvidenceAvailability.AVAILABLE),
        securityPatch = patch,
        secureScreenLock = lock,
        storageEncryption = DeviceSecurityEvidence("active", EvidenceAvailability.AVAILABLE),
        adbEnabled = adb,
        developerOptionsEnabled = DeviceSecurityEvidence("disabled", EvidenceAvailability.AVAILABLE),
        collectedAt = 1_000L
    )

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

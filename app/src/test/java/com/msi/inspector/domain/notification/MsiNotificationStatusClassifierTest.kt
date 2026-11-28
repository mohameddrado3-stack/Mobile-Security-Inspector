package com.msi.inspector.domain.notification

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability
import com.msi.inspector.domain.model.ScanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class MsiNotificationStatusClassifierTest {
    @Test
    fun reportsSafeOnlyWhenCoreIndicatorsAreAvailableAndNoReviewFindingsExist() {
        assertEquals(
            MsiNotificationStatus.SAFE,
            MsiNotificationStatusClassifier.classify(snapshot())
        )
    }

    @Test
    fun reportsAlertForAdbEnabledWithNoSecureScreenLock() {
        assertEquals(
            MsiNotificationStatus.ALERT,
            MsiNotificationStatusClassifier.classify(
                snapshot(adb = "enabled", screenLock = "disabled")
            )
        )
    }

    @Test
    fun reportsReviewForSpecialAccessRequests() {
        val app = AppAudit(
            label = "Example",
            packageName = "com.example.app",
            version = "1",
            installer = "unknown",
            isSystem = false,
            grantedSensitive = emptyList(),
            requestedSensitive = emptyList(),
            requestedSpecialAccess = listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
            signals = emptyList(),
            specialAccessReasons = emptyList()
        )
        assertEquals(
            MsiNotificationStatus.REVIEW,
            MsiNotificationStatusClassifier.classify(snapshot(applications = listOf(app)))
        )
    }

    @Test
    fun reportsReviewWhenCoreEvidenceIsUnavailable() {
        assertEquals(
            MsiNotificationStatus.REVIEW,
            MsiNotificationStatusClassifier.classify(
                snapshot(encryptionAvailability = EvidenceAvailability.UNAVAILABLE)
            )
        )
    }

    private fun snapshot(
        adb: String = "disabled",
        screenLock: String = "enabled",
        applications: List<AppAudit> = emptyList(),
        encryptionAvailability: EvidenceAvailability = EvidenceAvailability.AVAILABLE
    ): ScanSnapshot {
        val available = EvidenceAvailability.AVAILABLE
        fun evidence(value: String) = DeviceSecurityEvidence(value, available)
        return ScanSnapshot(
            applications = applications,
            vpnActive = false,
            completedAt = 1L,
            deviceSecurity = DeviceSecuritySnapshot(
                androidRelease = evidence("15"),
                apiLevel = evidence("35"),
                securityPatch = evidence("2026-01-01"),
                secureScreenLock = evidence(screenLock),
                storageEncryption = DeviceSecurityEvidence("active", encryptionAvailability),
                adbEnabled = evidence(adb),
                developerOptionsEnabled = evidence("disabled"),
                collectedAt = 1L
            )
        )
    }
}

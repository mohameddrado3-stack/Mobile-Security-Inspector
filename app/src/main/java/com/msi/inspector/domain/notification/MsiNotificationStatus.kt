package com.msi.inspector.domain.notification

import com.msi.inspector.domain.model.EvidenceAvailability
import com.msi.inspector.domain.model.ScanSnapshot

enum class MsiNotificationStatus {
    SAFE,
    REVIEW,
    ALERT
}

object MsiNotificationStatusClassifier {
    fun classify(snapshot: ScanSnapshot): MsiNotificationStatus {
        val device = snapshot.deviceSecurity ?: return MsiNotificationStatus.REVIEW
        val adbEnabled = device.adbEnabled.value == "enabled"
        val screenLockDisabled = device.secureScreenLock.value == "disabled"

        if (adbEnabled && screenLockDisabled) return MsiNotificationStatus.ALERT

        val deviceNeedsReview =
            adbEnabled ||
                screenLockDisabled ||
                device.storageEncryption.value == "inactive" ||
                device.adbEnabled.availability != EvidenceAvailability.AVAILABLE ||
                device.secureScreenLock.availability != EvidenceAvailability.AVAILABLE ||
                device.developerOptionsEnabled.availability != EvidenceAvailability.AVAILABLE

        val appsNeedReview = snapshot.applications.any { app ->
            app.needsReview ||
                app.hasIncompleteEvidence ||
                app.requestedSpecialAccess.isNotEmpty()
        }

        return if (deviceNeedsReview || appsNeedReview) {
            MsiNotificationStatus.REVIEW
        } else {
            MsiNotificationStatus.SAFE
        }
    }
}

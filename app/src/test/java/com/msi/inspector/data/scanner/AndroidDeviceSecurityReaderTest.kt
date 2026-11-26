package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.EvidenceAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidDeviceSecurityReaderTest {
    @Test
    fun activeEncryptionStatesRemainDistinctAndAvailable() {
        assertEquals(
            DeviceSecurityEvidence("active", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE)
        )
        assertEquals(
            DeviceSecurityEvidence("active_per_user", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER)
        )
        assertEquals(
            DeviceSecurityEvidence("active_default_key", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY)
        )
    }

    @Test
    fun inactiveAndActivatingStatesAreNotReportedAsUnknown() {
        assertEquals(
            DeviceSecurityEvidence("inactive", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE)
        )
        assertEquals(
            DeviceSecurityEvidence("activating", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING)
        )
    }

    @Test
    fun unsupportedAndUnknownEncryptionStatesStayExplicit() {
        assertEquals(
            DeviceSecurityEvidence(null, EvidenceAvailability.UNSUPPORTED),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED)
        )
        assertEquals(
            DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE),
            mapStorageEncryptionStatus(Int.MIN_VALUE)
        )
    }
}

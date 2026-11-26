package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.EvidenceAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidDeviceSecurityReaderTest {
    @Test
    fun activeFullDiskEncryptionRemainsExplicitlyAvailable() {
        assertEquals(
            DeviceSecurityEvidence("active", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE)
        )
    }

    @Test
    fun activePerUserEncryptionIsNotReportedAsUnknown() {
        assertEquals(
            DeviceSecurityEvidence("active_per_user", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER)
        )
    }

    @Test
    fun activeDefaultKeyEncryptionIsDistinguishedFromCredentialProtectedEncryption() {
        assertEquals(
            DeviceSecurityEvidence("active_default_key", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY)
        )
    }

    @Test
    fun inactiveEncryptionIsNotConfusedWithUnavailableData() {
        assertEquals(
            DeviceSecurityEvidence("inactive", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE)
        )
    }

    @Test
    fun unsupportedEncryptionRemainsExplicitlyUnsupported() {
        assertEquals(
            DeviceSecurityEvidence(null, EvidenceAvailability.UNSUPPORTED),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED)
        )
    }

    @Test
    fun unknownEncryptionStatusDoesNotBecomeActiveOrInactive() {
        assertEquals(
            DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE),
            mapStorageEncryptionStatus(Int.MIN_VALUE)
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun legacyActivatingStatusRemainsDistinct() {
        assertEquals(
            DeviceSecurityEvidence("activating", EvidenceAvailability.AVAILABLE),
            mapStorageEncryptionStatus(DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING)
        )
    }
}

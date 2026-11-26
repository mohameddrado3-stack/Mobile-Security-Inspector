package com.msi.inspector.data.scanner

import android.app.admin.DevicePolicyManager
import android.os.Build
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.EvidenceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 30, 32, 34, 35])
class AndroidDeviceSecurityReaderTest {

    @Test
    fun readsDeviceSecuritySnapshotAcrossSupportedAndroidApiLevels() {
        val snapshot = AndroidDeviceSecurityReader(RuntimeEnvironment.getApplication()).read()

        assertEquals(Build.VERSION.SDK_INT.toString(), snapshot.apiLevel.value)
        assertEquals(EvidenceAvailability.AVAILABLE, snapshot.apiLevel.availability)
        assertTrue(snapshot.collectedAt > 0L)
    }

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

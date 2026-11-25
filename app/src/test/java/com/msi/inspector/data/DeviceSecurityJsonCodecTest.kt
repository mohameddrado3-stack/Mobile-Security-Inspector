package com.msi.inspector.data

import com.msi.inspector.data.serialization.DeviceSecurityJsonCodec
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceSecurityJsonCodecTest {
    @Test
    fun roundTripPreservesEachSourceAvailability() {
        val snapshot = DeviceSecuritySnapshot(
            androidRelease = DeviceSecurityEvidence("16", EvidenceAvailability.AVAILABLE),
            apiLevel = DeviceSecurityEvidence("36", EvidenceAvailability.AVAILABLE),
            securityPatch = DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE),
            secureScreenLock = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE),
            storageEncryption = DeviceSecurityEvidence(null, EvidenceAvailability.UNSUPPORTED),
            adbEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.ACCESS_DENIED),
            developerOptionsEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.ERROR),
            collectedAt = 123456L
        )

        assertEquals(snapshot, DeviceSecurityJsonCodec.decode(DeviceSecurityJsonCodec.encode(snapshot)))
    }

    @Test
    fun emptyLegacyColumnMeansNoSystemSnapshotRatherThanAnEmptySecureState() {
        assertNull(DeviceSecurityJsonCodec.decode(null))
        assertNull(DeviceSecurityJsonCodec.decode("null"))
    }
}

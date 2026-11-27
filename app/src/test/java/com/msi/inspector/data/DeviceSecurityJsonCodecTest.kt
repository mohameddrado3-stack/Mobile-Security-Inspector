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
            adbEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.RESTRICTED_BY_ANDROID),
            developerOptionsEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.RESTRICTED_BY_ANDROID),
            collectedAt = 123456L
        )

        assertEquals(snapshot, DeviceSecurityJsonCodec.decode(DeviceSecurityJsonCodec.encode(snapshot)))
    }

    @Test
    fun androidRestrictedSettingsRoundTripWithoutBecomingDisabled() {
        val snapshot = DeviceSecuritySnapshot(
            androidRelease = DeviceSecurityEvidence("16", EvidenceAvailability.AVAILABLE),
            apiLevel = DeviceSecurityEvidence("36", EvidenceAvailability.AVAILABLE),
            securityPatch = DeviceSecurityEvidence("2026-10-01", EvidenceAvailability.AVAILABLE),
            secureScreenLock = DeviceSecurityEvidence("enabled", EvidenceAvailability.AVAILABLE),
            storageEncryption = DeviceSecurityEvidence("active", EvidenceAvailability.AVAILABLE),
            adbEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.RESTRICTED_BY_ANDROID),
            developerOptionsEnabled = DeviceSecurityEvidence(null, EvidenceAvailability.RESTRICTED_BY_ANDROID),
            collectedAt = 123456L
        )

        val restored = DeviceSecurityJsonCodec.decode(DeviceSecurityJsonCodec.encode(snapshot))

        assertEquals(EvidenceAvailability.RESTRICTED_BY_ANDROID, restored?.adbEnabled?.availability)
        assertEquals(EvidenceAvailability.RESTRICTED_BY_ANDROID, restored?.developerOptionsEnabled?.availability)
        assertNull(restored?.adbEnabled?.value)
        assertNull(restored?.developerOptionsEnabled?.value)
    }

    @Test
    fun emptyLegacyColumnMeansNoSystemSnapshotRatherThanAnEmptySecureState() {
        assertNull(DeviceSecurityJsonCodec.decode(null))
        assertNull(DeviceSecurityJsonCodec.decode("null"))
    }
}

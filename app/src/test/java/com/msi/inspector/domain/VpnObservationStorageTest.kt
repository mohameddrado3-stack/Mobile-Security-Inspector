package com.msi.inspector.domain

import com.msi.inspector.domain.model.VpnObservationStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnObservationStorageTest {
    @Test
    fun encodeAndDecodePreserveAllThreeObservationStates() {
        assertEquals(true, VpnObservationStorage.decode(VpnObservationStorage.encode(true)))
        assertEquals(false, VpnObservationStorage.decode(VpnObservationStorage.encode(false)))
        assertEquals(null, VpnObservationStorage.decode(VpnObservationStorage.encode(null)))
    }

    @Test
    fun unknownOrFutureStorageValuesRemainUnknown() {
        assertEquals(null, VpnObservationStorage.decode(-1))
        assertEquals(null, VpnObservationStorage.decode(2))
        assertEquals(null, VpnObservationStorage.decode(Int.MIN_VALUE))
    }
}

package com.msi.inspector.background

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodicScanIntervalTest {
    @Test
    fun enforcesWorkManagerMinimumInterval() {
        assertEquals(PeriodicScanScheduler.MIN_INTERVAL_MINUTES, PeriodicScanScheduler.normalizeIntervalMinutes(1))
        assertEquals(PeriodicScanScheduler.MIN_INTERVAL_MINUTES, PeriodicScanScheduler.normalizeIntervalMinutes(14))
        assertEquals(15, PeriodicScanScheduler.normalizeIntervalMinutes(15))
    }

    @Test
    fun capsIntervalAtOneDay() {
        assertEquals(1440, PeriodicScanScheduler.normalizeIntervalMinutes(1440))
        assertEquals(PeriodicScanScheduler.MAX_INTERVAL_MINUTES, PeriodicScanScheduler.normalizeIntervalMinutes(5000))
    }
}

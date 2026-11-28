package com.msi.inspector.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryUsageCalculatorTest {
    @Test
    fun calculatesChargeCounterDischargeInMilliampHours() {
        val summary = BatteryUsageCalculator.calculate(
            sample(charge = 2_500_000L),
            sample(time = 3_601_000L, elapsed = 3_601_000L, charge = 2_250_000L)
        )

        assertEquals(BatteryMeasurementMethod.CHARGE_COUNTER, summary?.method)
        assertEquals(250.0, summary?.amount ?: 0.0, 0.001)
        assertEquals(BatteryTrend.DISCHARGING, summary?.trend)
        assertEquals(3_600_000L, summary?.durationMillis)
    }

    @Test
    fun identifiesNetChargeGainInsteadOfCallingItDischarge() {
        val summary = BatteryUsageCalculator.calculate(
            sample(charge = 2_100_000L),
            sample(time = 3_600_000L, elapsed = 3_600_000L, charge = 2_350_000L, charging = true)
        )

        assertEquals(250.0, summary?.amount ?: 0.0, 0.001)
        assertEquals(BatteryTrend.CHARGING, summary?.trend)
    }

    @Test
    fun reportsStableChargeCounter() {
        val summary = BatteryUsageCalculator.calculate(
            sample(charge = 2_100_000L),
            sample(time = 3_600_000L, elapsed = 3_600_000L, charge = 2_100_000L)
        )

        assertEquals(BatteryTrend.STABLE, summary?.trend)
        assertEquals(0.0, summary?.amount ?: -1.0, 0.001)
    }

    @Test
    fun usesEnergyCounterWhenChargeCounterIsUnavailable() {
        val summary = BatteryUsageCalculator.calculate(
            sample(charge = null, energy = 8_000_000L),
            sample(time = 3_600_000L, elapsed = 3_600_000L, charge = null, energy = 6_750_000L)
        )

        assertEquals(BatteryMeasurementMethod.ENERGY_COUNTER, summary?.method)
        assertEquals(1.25, summary?.amount ?: 0.0, 0.0001)
    }

    @Test
    fun usesPercentagePointsWhenHardwareCountersAreUnavailable() {
        val summary = BatteryUsageCalculator.calculate(
            sample(level = 84, charge = null, energy = null),
            sample(time = 3_600_000L, elapsed = 3_600_000L, level = 76, charge = null, energy = null)
        )

        assertEquals(BatteryMeasurementMethod.PERCENTAGE_POINTS, summary?.method)
        assertEquals(8.0, summary?.amount ?: 0.0, 0.001)
        assertEquals(BatteryTrend.DISCHARGING, summary?.trend)
    }

    @Test
    fun reportsChargingWhenPercentageIncreases() {
        val summary = BatteryUsageCalculator.calculate(
            sample(level = 35, charge = null, energy = null),
            sample(time = 3_600_000L, elapsed = 3_600_000L, level = 42, charge = null, energy = null)
        )

        assertEquals(BatteryTrend.CHARGING, summary?.trend)
        assertEquals(7.0, summary?.amount ?: 0.0, 0.001)
    }

    @Test
    fun returnsNoSummaryWhenNoSupportedMeasurementExists() {
        val summary = BatteryUsageCalculator.calculate(
            sample(level = null, charge = null, energy = null),
            sample(time = 3_600_000L, elapsed = 3_600_000L, level = null, charge = null, energy = null)
        )

        assertNull(summary)
    }

    @Test
    fun rejectsElapsedRealtimeRollbackAfterDeviceRestart() {
        val summary = BatteryUsageCalculator.calculate(
            sample(elapsed = 8_000_000L, charge = 2_000_000L),
            sample(time = 3_600_000L, elapsed = 500L, charge = 1_900_000L)
        )

        assertNull(summary)
    }

    @Test
    fun rejectsWallClockRollback() {
        val summary = BatteryUsageCalculator.calculate(
            sample(time = 3_600_000L, elapsed = 1_000L, charge = 2_000_000L),
            sample(time = 2_000L, elapsed = 2_000L, charge = 1_900_000L)
        )

        assertNull(summary)
    }

    @Test
    fun rejectsInvalidBatteryPercentages() {
        val summary = BatteryUsageCalculator.calculate(
            sample(level = 105, charge = null, energy = null),
            sample(time = 3_600_000L, elapsed = 3_600_000L, level = 80, charge = null, energy = null)
        )

        assertNull(summary)
    }

    private fun sample(
        time: Long = 1_000L,
        elapsed: Long = 1_000L,
        level: Int? = 80,
        charge: Long? = 2_000_000L,
        energy: Long? = 7_000_000L,
        charging: Boolean = false
    ) = BatterySample(
        sampledAtEpochMillis = time,
        elapsedRealtimeMillis = elapsed,
        levelPercent = level,
        chargeCounterMicroAmpHours = charge,
        energyCounterNanoWattHours = energy,
        isCharging = charging,
        isPlugged = charging
    )
}

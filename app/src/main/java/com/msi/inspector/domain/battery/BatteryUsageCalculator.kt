package com.msi.inspector.domain.battery

object BatteryUsageCalculator {
    fun calculate(
        previous: BatterySample?,
        current: BatterySample
    ): BatteryUsageSummary? {
        previous ?: return null

        if (current.sampledAtEpochMillis <= previous.sampledAtEpochMillis) return null
        if (current.elapsedRealtimeMillis <= previous.elapsedRealtimeMillis) return null

        val chargeBefore = previous.chargeCounterMicroAmpHours
        val chargeAfter = current.chargeCounterMicroAmpHours
        if (chargeBefore != null && chargeAfter != null &&
            chargeBefore >= 0L && chargeAfter >= 0L
        ) {
            val delta = chargeBefore.toDouble() - chargeAfter.toDouble()
            return BatteryUsageSummary(
                startSample = previous,
                endSample = current,
                method = BatteryMeasurementMethod.CHARGE_COUNTER,
                amount = kotlin.math.abs(delta) / 1_000.0,
                trend = trend(delta)
            )
        }

        val energyBefore = previous.energyCounterNanoWattHours
        val energyAfter = current.energyCounterNanoWattHours
        if (energyBefore != null && energyAfter != null &&
            energyBefore >= 0L && energyAfter >= 0L
        ) {
            val delta = energyBefore.toDouble() - energyAfter.toDouble()
            return BatteryUsageSummary(
                startSample = previous,
                endSample = current,
                method = BatteryMeasurementMethod.ENERGY_COUNTER,
                amount = kotlin.math.abs(delta) / 1_000_000.0,
                trend = trend(delta)
            )
        }

        val levelBefore = previous.levelPercent
        val levelAfter = current.levelPercent
        if (levelBefore != null && levelAfter != null &&
            levelBefore in 0..100 && levelAfter in 0..100
        ) {
            val delta = (levelBefore - levelAfter).toDouble()
            return BatteryUsageSummary(
                startSample = previous,
                endSample = current,
                method = BatteryMeasurementMethod.PERCENTAGE_POINTS,
                amount = kotlin.math.abs(delta),
                trend = trend(delta)
            )
        }

        return null
    }

    private fun trend(delta: Double): BatteryTrend = when {
        delta > 0.0 -> BatteryTrend.DISCHARGING
        delta < 0.0 -> BatteryTrend.CHARGING
        else -> BatteryTrend.STABLE
    }
}

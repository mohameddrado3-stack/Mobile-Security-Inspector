package com.msi.inspector.domain.battery

data class BatterySample(
    val sampledAtEpochMillis: Long,
    val elapsedRealtimeMillis: Long,
    val levelPercent: Int?,
    val chargeCounterMicroAmpHours: Long?,
    val energyCounterNanoWattHours: Long?,
    val isCharging: Boolean,
    val isPlugged: Boolean
)

enum class BatteryMeasurementMethod {
    CHARGE_COUNTER,
    ENERGY_COUNTER,
    PERCENTAGE_POINTS
}

enum class BatteryTrend {
    DISCHARGING,
    CHARGING,
    STABLE
}

data class BatteryUsageSummary(
    val startSample: BatterySample,
    val endSample: BatterySample,
    val method: BatteryMeasurementMethod,
    val amount: Double,
    val trend: BatteryTrend
) {
    val durationMillis: Long
        get() = endSample.elapsedRealtimeMillis - startSample.elapsedRealtimeMillis
}

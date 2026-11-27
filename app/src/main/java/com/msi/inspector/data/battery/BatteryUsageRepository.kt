package com.msi.inspector.data.battery

import android.content.Context
import com.msi.inspector.domain.battery.BatterySample
import com.msi.inspector.domain.battery.BatteryUsageCalculator
import com.msi.inspector.domain.battery.BatteryUsageSummary

class BatteryUsageRepository(context: Context) {
    private val reader = AndroidBatteryReader(context)
    private val database = BatteryUsageDatabase(context)

    @Synchronized
    fun captureIfDue(
        minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS
    ): BatteryUsageSummary? {
        val current = reader.read()
        val previous = database.recent(2).lastOrNull()

        if (previous != null) {
            val elapsedSincePrevious = current.elapsedRealtimeMillis -
                previous.elapsedRealtimeMillis
            if (elapsedSincePrevious in 1 until minIntervalMillis.coerceAtLeast(0L)) {
                return latestSummary()
            }
        }

        database.insert(current)
        return BatteryUsageCalculator.calculate(previous, current)
    }

    @Synchronized
    fun captureNow(): BatteryUsageSummary? = captureIfDue(0L)

    @Synchronized
    fun latestSummary(): BatteryUsageSummary? {
        val samples = database.recent(2)
        if (samples.size < 2) return null
        return BatteryUsageCalculator.calculate(samples[0], samples[1])
    }

    @Synchronized
    fun latestSample(): BatterySample? = database.recent(1).lastOrNull()

    fun close() = database.close()

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 50L * 60L * 1000L
    }
}

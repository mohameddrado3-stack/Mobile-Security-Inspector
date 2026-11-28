package com.msi.inspector.data.battery

import android.content.Context
import com.msi.inspector.domain.battery.BatterySample
import com.msi.inspector.domain.battery.BatteryUsageCalculator
import com.msi.inspector.domain.battery.BatteryUsageSummary

class BatteryUsageRepository(context: Context) {
    private val reader = AndroidBatteryReader(context)
    private val database = BatteryUsageDatabase(context)

    fun captureIfDue(
        minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS
    ): BatteryUsageSummary? = synchronized(CAPTURE_LOCK) {
        val current = reader.read()
        val previous = database.recent(2).lastOrNull()

        if (previous != null) {
            val elapsedSincePrevious = current.elapsedRealtimeMillis -
                previous.elapsedRealtimeMillis
            if (elapsedSincePrevious in 1 until minIntervalMillis.coerceAtLeast(0L)) {
                return@synchronized latestSummaryLocked()
            }
        }

        database.insert(current)
        BatteryUsageCalculator.calculate(previous, current)
    }

    fun captureNow(): BatteryUsageSummary? = captureIfDue(0L)

    fun latestSummary(): BatteryUsageSummary? = synchronized(CAPTURE_LOCK) {
        latestSummaryLocked()
    }

    fun latestSample(): BatterySample? = synchronized(CAPTURE_LOCK) {
        database.recent(1).lastOrNull()
    }

    private fun latestSummaryLocked(): BatteryUsageSummary? {
        val samples = database.recent(2)
        if (samples.size < 2) return null
        return BatteryUsageCalculator.calculate(samples[0], samples[1])
    }

    fun close() = synchronized(CAPTURE_LOCK) {
        database.close()
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 50L * 60L * 1000L
        private val CAPTURE_LOCK = Any()
    }
}

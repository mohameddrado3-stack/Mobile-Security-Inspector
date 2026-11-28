package com.msi.inspector.data.battery

import com.msi.inspector.domain.battery.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryUsageRepositoryTest {
    @Test
    fun doesNotPersistNewSamplesAfterTrackingIsDisabled() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("msi_battery_usage.db")
        context.getSharedPreferences("msi_preferences", 0)
            .edit()
            .putBoolean("battery_tracking_enabled", false)
            .commit()

        val database = BatteryUsageDatabase(context)
        database.insert(
            BatterySample(
                sampledAtEpochMillis = 1_000L,
                elapsedRealtimeMillis = 1_000L,
                levelPercent = 90,
                chargeCounterMicroAmpHours = 2_500_000L,
                energyCounterNanoWattHours = null,
                isCharging = false,
                isPlugged = false
            )
        )

        val repository = BatteryUsageRepository(context)
        repository.captureIfDue()

        val samples = database.recent()
        assertEquals(1, samples.size)
        assertEquals(1_000L, samples.single().sampledAtEpochMillis)

        repository.close()
        database.close()
    }
}

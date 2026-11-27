package com.msi.inspector.data.battery

import android.database.sqlite.SQLiteDatabase
import com.msi.inspector.domain.battery.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryUsageDatabaseTest {
    @Test
    fun storesAndReadsSamplesInChronologicalOrder() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val database = BatteryUsageDatabase(context)

        database.insert(sample(1_000L, 1_000L, 90, 3_000_000L))
        database.insert(sample(2_000L, 2_000L, 89, 2_950_000L))

        val samples = database.recent()
        assertEquals(2, samples.size)
        assertEquals(1_000L, samples[0].sampledAtEpochMillis)
        assertEquals(2_000L, samples[1].sampledAtEpochMillis)
        assertEquals(3_000_000L, samples[0].chargeCounterMicroAmpHours)
        database.close()
    }

    @Test
    fun retainsOnlyTheFortyEightNewestSamples() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val database = BatteryUsageDatabase(context)

        repeat(55) { index ->
            database.insert(
                sample(
                    time = (index + 1) * 1_000L,
                    elapsed = (index + 1) * 1_000L,
                    level = 100 - index,
                    charge = 3_000_000L - index * 10_000L
                )
            )
        }

        val samples = database.recent()
        assertEquals(48, samples.size)
        assertEquals(8_000L, samples.first().sampledAtEpochMillis)
        assertEquals(55_000L, samples.last().sampledAtEpochMillis)
        database.close()
    }

    @Test
    fun preservesUnavailableCountersAsNull() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val database = BatteryUsageDatabase(context)

        database.insert(sample(1_000L, 1_000L, null, null, null))

        val saved = database.recent().single()
        assertNull(saved.levelPercent)
        assertNull(saved.chargeCounterMicroAmpHours)
        assertNull(saved.energyCounterNanoWattHours)
        database.close()
    }

    @Test
    fun databaseCanBeReopenedWithoutLosingStoredSamples() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val first = BatteryUsageDatabase(context)
        first.insert(sample(4_000L, 4_000L, 62, 1_800_000L))
        first.close()

        val reopened = BatteryUsageDatabase(context)
        assertEquals(1, reopened.recent().size)
        assertEquals(62, reopened.recent().single().levelPercent)
        reopened.close()
    }

    private fun sample(
        time: Long,
        elapsed: Long,
        level: Int?,
        charge: Long?,
        energy: Long? = null
    ) = BatterySample(
        sampledAtEpochMillis = time,
        elapsedRealtimeMillis = elapsed,
        levelPercent = level,
        chargeCounterMicroAmpHours = charge,
        energyCounterNanoWattHours = energy,
        isCharging = false,
        isPlugged = false
    )

    companion object {
        private const val DATABASE_NAME = "msi_battery_usage.db"
    }
}

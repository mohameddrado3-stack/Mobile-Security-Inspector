package com.msi.inspector.data.battery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.msi.inspector.domain.battery.BatterySample

class BatteryUsageDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE battery_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sampled_at INTEGER NOT NULL,
                elapsed_realtime INTEGER NOT NULL,
                level_percent INTEGER,
                charge_counter_uah INTEGER,
                energy_counter_nwh INTEGER,
                is_charging INTEGER NOT NULL,
                is_plugged INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX battery_samples_sampled_at_idx ON battery_samples(sampled_at DESC, id DESC)"
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(sample: BatterySample) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("sampled_at", sample.sampledAtEpochMillis)
                put("elapsed_realtime", sample.elapsedRealtimeMillis)
                sample.levelPercent?.let { put("level_percent", it) } ?: putNull("level_percent")
                sample.chargeCounterMicroAmpHours?.let {
                    put("charge_counter_uah", it)
                } ?: putNull("charge_counter_uah")
                sample.energyCounterNanoWattHours?.let {
                    put("energy_counter_nwh", it)
                } ?: putNull("energy_counter_nwh")
                put("is_charging", if (sample.isCharging) 1 else 0)
                put("is_plugged", if (sample.isPlugged) 1 else 0)
            }
            database.insertOrThrow("battery_samples", null, values)
            database.execSQL(
                """
                DELETE FROM battery_samples
                WHERE id NOT IN (
                    SELECT id FROM battery_samples ORDER BY id DESC LIMIT $MAX_SAMPLES
                )
                """.trimIndent()
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun recent(limit: Int = MAX_SAMPLES): List<BatterySample> {
        val safeLimit = limit.coerceIn(1, MAX_SAMPLES)
        val samples = ArrayList<BatterySample>()
        readableDatabase.query(
            "battery_samples",
            arrayOf(
                "sampled_at",
                "elapsed_realtime",
                "level_percent",
                "charge_counter_uah",
                "energy_counter_nwh",
                "is_charging",
                "is_plugged"
            ),
            null,
            null,
            null,
            null,
            "id DESC",
            safeLimit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                samples += BatterySample(
                    sampledAtEpochMillis = cursor.getLong(0),
                    elapsedRealtimeMillis = cursor.getLong(1),
                    levelPercent = cursor.nullableInt(2),
                    chargeCounterMicroAmpHours = cursor.nullableLong(3),
                    energyCounterNanoWattHours = cursor.nullableLong(4),
                    isCharging = cursor.getInt(5) == 1,
                    isPlugged = cursor.getInt(6) == 1
                )
            }
        }
        return samples.asReversed()
    }

    private fun android.database.Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private fun android.database.Cursor.nullableLong(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    companion object {
        private const val DATABASE_NAME = "msi_battery_usage.db"
        private const val DATABASE_VERSION = 1
        private const val MAX_SAMPLES = 48
    }
}

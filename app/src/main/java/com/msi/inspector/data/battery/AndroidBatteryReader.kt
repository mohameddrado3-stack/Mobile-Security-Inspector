package com.msi.inspector.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.msi.inspector.domain.battery.BatterySample
import kotlin.math.roundToInt

class AndroidBatteryReader(context: Context) {
    private val appContext = context.applicationContext

    fun read(): BatterySample {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val rawScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (rawLevel >= 0 && rawScale > 0) {
            (rawLevel * 100.0 / rawScale).roundToInt().coerceIn(0, 100)
        } else {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
        }

        val rawChargeCounter = manager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
        )
        val chargeCounter = rawChargeCounter
            .takeIf { it >= 0 && it != Int.MIN_VALUE }
            ?.toLong()

        val rawEnergyCounter = manager.getLongProperty(
            BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER
        )
        val energyCounter = rawEnergyCounter
            .takeIf { it >= 0L && it != Long.MIN_VALUE }

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.isCharging()
        } else {
            status == BatteryManager.BATTERY_STATUS_CHARGING
        }
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        return BatterySample(
            sampledAtEpochMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            levelPercent = levelPercent,
            chargeCounterMicroAmpHours = chargeCounter,
            energyCounterNanoWattHours = energyCounter,
            isCharging = isCharging,
            isPlugged = plugged != 0
        )
    }
}

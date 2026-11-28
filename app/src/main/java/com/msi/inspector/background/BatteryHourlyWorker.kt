package com.msi.inspector.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msi.inspector.data.battery.BatteryUsageRepository
import kotlinx.coroutines.CancellationException

class BatteryHourlyWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BatteryHourlyScheduler.isEnabled(applicationContext)) return Result.success()

        val repository = BatteryUsageRepository(applicationContext)
        return try {
            repository.captureIfDue()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            repository.close()
        }
    }
}

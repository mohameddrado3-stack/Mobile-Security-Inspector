package com.msi.inspector.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.domain.timeline.TimelineEventType
import com.msi.inspector.widget.MsiWidgetStateUpdater
import kotlinx.coroutines.CancellationException

class PeriodicScanWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!PeriodicScanScheduler.isEnabled(applicationContext)) return Result.success()
        val repository = AppAuditRepository(applicationContext)
        return try {
            val snapshot = repository.scan()
            val events = repository.saveHistory(snapshot)

            runCatching { MsiWidgetStateUpdater.update(applicationContext, snapshot) }

            val changeCount = events.count { it.type != TimelineEventType.BASELINE_CREATED }
            if (changeCount > 0) {
                MsiScanNotifications.showChange(applicationContext, changeCount)
            }
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

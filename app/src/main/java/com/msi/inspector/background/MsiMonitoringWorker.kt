package com.msi.inspector.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.domain.timeline.TimelineNotificationPolicy
import com.msi.inspector.domain.notification.MsiNotificationStatus
import com.msi.inspector.domain.notification.MsiNotificationStatusClassifier
import com.msi.inspector.widget.MsiWidgetStateUpdater
import kotlinx.coroutines.CancellationException

class MsiMonitoringWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!MsiMonitoringController.isEnabled(applicationContext)) return Result.success()

        val repository = AppAuditRepository(applicationContext)
        return try {
            val scan = repository.scanAndSave(
                shouldPersist = { MsiMonitoringController.isEnabled(applicationContext) }
            )
            if (!MsiMonitoringController.isEnabled(applicationContext)) return Result.success()
            val snapshot = scan.snapshot
            val events = scan.events
            runCatching { MsiWidgetStateUpdater.update(applicationContext, snapshot) }

            val changeCount = TimelineNotificationPolicy.changeCount(events)
            if (changeCount > 0) {
                MsiScanNotifications.showChange(applicationContext, changeCount)
            }

            MsiMonitoringService.refreshNotification(
                applicationContext,
                MsiNotificationStatusClassifier.classify(snapshot)
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MsiMonitoringService.refreshNotification(
                applicationContext,
                MsiNotificationStatus.REVIEW
            )
            Result.retry()
        } finally {
            repository.close()
        }
    }
}

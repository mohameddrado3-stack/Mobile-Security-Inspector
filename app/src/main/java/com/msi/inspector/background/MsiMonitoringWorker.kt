package com.msi.inspector.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.domain.timeline.TimelineNotificationPolicy
import com.msi.inspector.widget.MsiWidgetStateUpdater
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

class MsiMonitoringWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!MsiMonitoringController.isEnabled(applicationContext)) return Result.success()

        val repository = AppAuditRepository(applicationContext)
        return try {
            val (snapshot, events) = repository.scanAndSave(
                shouldPersist = { MsiMonitoringController.isEnabled(applicationContext) }
            )
            if (!MsiMonitoringController.isEnabled(applicationContext)) return Result.success()
            runCatching { MsiWidgetStateUpdater.update(applicationContext, snapshot) }

            val changeCount = TimelineNotificationPolicy.changeCount(events)
            if (changeCount > 0) {
                MsiScanNotifications.showChange(applicationContext, changeCount)
            }

            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.completedAt))
            MsiMonitoringService.refreshNotification(
                applicationContext,
                "آخر فحص محلي: $time • تغيّرات مرصودة: $changeCount"
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MsiMonitoringService.refreshNotification(
                applicationContext,
                "المراقبة مفعّلة • تعذر إكمال آخر فحص؛ ستتم إعادة المحاولة."
            )
            Result.retry()
        } finally {
            repository.close()
        }
    }
}

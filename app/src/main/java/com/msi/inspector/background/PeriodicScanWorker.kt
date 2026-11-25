package com.msi.inspector.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.domain.analyzer.AuditSummary
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
            val previous = repository.loadHistory().firstOrNull()
            val snapshot = repository.scan()
            repository.saveHistory(snapshot)

            runCatching { MsiWidgetStateUpdater.update(applicationContext, snapshot) }

            val summary = AuditSummary.from(snapshot.applications)
            val meaningfulChange = previous != null && (
                previous.activeSpecialAccess != summary.activeSpecialAccess ||
                    previous.requestedSpecialAccess != summary.requestedSpecialAccess ||
                    previous.sensitivePermissions != summary.applicationsWithSensitivePermissions
                )
            if (meaningfulChange) {
                MsiScanNotifications.showChange(applicationContext, summary.activeSpecialAccess)
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

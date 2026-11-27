package com.msi.inspector.data.repository

import android.content.Context
import com.msi.inspector.data.database.ScanHistoryDatabase
import com.msi.inspector.data.serialization.AppAuditJsonCodec
import com.msi.inspector.data.scanner.AndroidAppScanner
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventDetector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppAuditRepository(context: Context) {
    private val scanner = AndroidAppScanner(context)
    private val history = ScanHistoryDatabase(context)

    suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ScanSnapshot =
        SCAN_MUTEX.withLock { scanner.scan(onProgress) }

    suspend fun scanAndSave(
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Pair<ScanSnapshot, List<TimelineEvent>> = SCAN_MUTEX.withLock {
        val snapshot = scanner.scan(onProgress)
        snapshot to saveHistory(snapshot)
    }

    fun saveHistory(snapshot: ScanSnapshot): List<TimelineEvent> = synchronized(SCAN_HISTORY_LOCK) {
        val previousEntry = history.recent(1).firstOrNull()
        val previous = previousEntry?.takeIf { it.applicationsJson != "[]" }?.let { entry ->
            runCatching {
                ScanSnapshot(
                    applications = AppAuditJsonCodec.decode(entry.applicationsJson),
                    vpnActive = entry.vpnActive,
                    completedAt = entry.scannedAt,
                    deviceSecurity = entry.deviceSecuritySnapshot
                )
            }.getOrNull()
        }
        val events = TimelineEventDetector.detect(previous, snapshot)
        history.saveWithTimeline(snapshot, events)
        events
    }

    fun loadHistory(): List<ScanHistoryEntry> = history.recent()

    fun loadTimeline(): List<TimelineEvent> = history.recentTimelineEvents()

    fun clearHistory() = synchronized(SCAN_HISTORY_LOCK) { history.clearHistory() }

    fun close() = history.close()

    private companion object {
        val SCAN_HISTORY_LOCK = Any()
        val SCAN_MUTEX = Mutex()
    }

}

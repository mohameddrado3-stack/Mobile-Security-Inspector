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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ScanStateSnapshot(
    val history: List<ScanHistoryEntry>,
    val timeline: List<TimelineEvent>,
    val latestSnapshot: ScanSnapshot?
)

data class ScanAndSaveResult(
    val snapshot: ScanSnapshot,
    val events: List<TimelineEvent>,
    val history: List<ScanHistoryEntry>,
    val timeline: List<TimelineEvent>
)

class AppAuditRepository(context: Context) {
    private val scanner = AndroidAppScanner(context)
    private val history = ScanHistoryDatabase(context)

    suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ScanSnapshot {
        SCAN_MUTEX.lock()
        return try {
            scanner.scan(onProgress)
        } finally {
            SCAN_MUTEX.unlock()
        }
    }

    suspend fun scanAndSave(
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        shouldPersist: () -> Boolean = { true }
    ): ScanAndSaveResult {
        SCAN_MUTEX.lock()
        return try {
            val snapshot = scanner.scan(onProgress)
            currentCoroutineContext().ensureActive()
            val events = if (shouldPersist()) saveHistory(snapshot) else emptyList()
            val savedData = synchronized(SCAN_HISTORY_LOCK) {
                history.recent() to history.recentTimelineEvents()
            }
            ScanAndSaveResult(
                snapshot = snapshot,
                events = events,
                history = savedData.first,
                timeline = savedData.second
            )
        } finally {
            SCAN_MUTEX.unlock()
        }
    }

    fun saveHistory(snapshot: ScanSnapshot): List<TimelineEvent> = synchronized(SCAN_HISTORY_LOCK) {
        val previousEntry = history.recent(1).firstOrNull()
        val previous = previousEntry?.takeIf { it.applicationsJson.isNotBlank() }?.let { entry ->
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

    fun loadHistory(): List<ScanHistoryEntry> = synchronized(SCAN_HISTORY_LOCK) {
        history.recent()
    }

    fun loadScanState(): ScanStateSnapshot = synchronized(SCAN_HISTORY_LOCK) {
        val entries = history.recent()
        val timeline = history.recentTimelineEvents()
        val latestSnapshot = entries.firstOrNull()?.toScanSnapshot()
        ScanStateSnapshot(
            history = entries,
            timeline = timeline,
            latestSnapshot = latestSnapshot
        )
    }

    private fun ScanHistoryEntry.toScanSnapshot(): ScanSnapshot? {
        if (applicationsJson.isBlank()) return null
        return runCatching {
            ScanSnapshot(
                applications = AppAuditJsonCodec.decode(applicationsJson),
                vpnActive = vpnActive,
                completedAt = scannedAt,
                deviceSecurity = deviceSecuritySnapshot
            )
        }.getOrNull()
    }

    fun loadTimeline(): List<TimelineEvent> = synchronized(SCAN_HISTORY_LOCK) {
        history.recentTimelineEvents()
    }

    fun loadLatestSnapshot(): ScanSnapshot? = synchronized(SCAN_HISTORY_LOCK) {
        history.recent(1).firstOrNull()?.toScanSnapshot()
    }

    suspend fun clearHistory() {
        SCAN_MUTEX.lock()
        try {
            synchronized(SCAN_HISTORY_LOCK) { history.clearHistory() }
        } finally {
            SCAN_MUTEX.unlock()
        }
    }

    fun close() = history.close()

    private companion object {
        val SCAN_HISTORY_LOCK = Any()
        val SCAN_MUTEX = Mutex()
    }

}

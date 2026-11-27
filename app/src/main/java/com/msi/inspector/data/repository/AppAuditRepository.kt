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

    fun loadHistory(): List<ScanHistoryEntry> = synchronized(SCAN_HISTORY_LOCK) {
        history.recent()
    }

    fun loadTimeline(): List<TimelineEvent> = synchronized(SCAN_HISTORY_LOCK) {
        history.recentTimelineEvents()
    }

    fun loadLatestSnapshot(): ScanSnapshot? = synchronized(SCAN_HISTORY_LOCK) {
        val entry = history.recent(1).firstOrNull() ?: return@synchronized null
        if (entry.applicationsJson.isBlank() || entry.applicationsJson == "[]") {
            return@synchronized null
        }
        runCatching {
            ScanSnapshot(
                applications = AppAuditJsonCodec.decode(entry.applicationsJson),
                vpnActive = entry.vpnActive,
                completedAt = entry.scannedAt,
                deviceSecurity = entry.deviceSecuritySnapshot
            )
        }.getOrNull()
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

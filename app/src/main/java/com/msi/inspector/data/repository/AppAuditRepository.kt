package com.msi.inspector.data.repository

import android.content.Context
import com.msi.inspector.data.database.ScanHistoryDatabase
import com.msi.inspector.data.scanner.AndroidAppScanner
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot

class AppAuditRepository(context: Context) {
    private val scanner = AndroidAppScanner(context)
    private val history = ScanHistoryDatabase(context)

    suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ScanSnapshot =
        scanner.scan(onProgress)

    fun saveHistory(snapshot: ScanSnapshot) = history.save(snapshot)

    fun loadHistory(): List<ScanHistoryEntry> = history.recent()

    fun clearHistory() = history.clearHistory()

    fun close() = history.close()
}

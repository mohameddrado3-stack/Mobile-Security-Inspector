package com.msi.inspector.data.repository

import android.content.Context
import com.msi.inspector.data.database.ScanHistoryDatabase
import com.msi.inspector.data.scanner.AndroidAppScanner
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventDetector
import org.json.JSONArray
import org.json.JSONObject

class AppAuditRepository(context: Context) {
    private val scanner = AndroidAppScanner(context)
    private val history = ScanHistoryDatabase(context)

    suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ScanSnapshot =
        scanner.scan(onProgress)

    fun saveHistory(snapshot: ScanSnapshot): List<TimelineEvent> = synchronized(SCAN_HISTORY_LOCK) {
        val previousEntry = history.recent(1).firstOrNull()
        val previous = previousEntry?.takeIf { it.applicationsJson != "[]" }?.let { entry ->
            runCatching {
                ScanSnapshot(
                    applications = decodeApplications(entry.applicationsJson),
                    vpnActive = entry.vpnActive,
                    completedAt = entry.scannedAt
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
        // Foreground scans and WorkManager share this process-wide read/compare/write boundary.
        val SCAN_HISTORY_LOCK = Any()
    }

    private fun decodeApplications(json: String): List<AppAudit> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    AppAudit(
                        label = item.optString("label", "تطبيق غير معروف"),
                        packageName = item.optString("packageName"),
                        version = item.optString("version", "غير متاح"),
                        installer = item.optString("installer", "غير معروف"),
                        isSystem = item.optBoolean("isSystem", false),
                        grantedSensitive = item.stringList("grantedSensitive"),
                        requestedSensitive = item.stringList("requestedSensitive"),
                        requestedSpecialAccess = item.stringList("requestedSpecialAccess"),
                        signals = emptyList(),
                        specialAccessReasons = item.stringList("activeSpecialAccess"),
                        targetSdk = item.nullableInt("targetSdk"),
                        firstInstallTime = item.nullableLong("firstInstallTime"),
                        lastUpdateTime = item.nullableLong("lastUpdateTime"),
                        signingCertificateSha256 = item.nullableString("signatureSha256"),
                        categoryLabel = item.nullableString("category"),
                        lastUsedAt = item.nullableLong("lastUsedAt")
                    )
                )
            }
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}

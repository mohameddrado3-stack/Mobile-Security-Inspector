package com.msi.inspector.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.msi.inspector.data.serialization.AppAuditJsonCodec
import com.msi.inspector.data.serialization.DeviceSecurityJsonCodec
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.domain.model.VpnObservationStorage
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventType

class ScanHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, ScanHistorySchema.VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        ScanHistorySchema.create(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ScanHistorySchema.upgrade(database, oldVersion)
    }

    fun save(snapshot: ScanSnapshot) = saveWithTimeline(snapshot, emptyList())

    fun saveWithTimeline(snapshot: ScanSnapshot, events: List<TimelineEvent>) {
        val summary = AuditSummary.from(snapshot.applications)
        val values = ContentValues().apply {
            put("scanned_at", snapshot.completedAt)
            put("total_applications", summary.totalApplications)
            put("active_special_access", summary.activeSpecialAccess)
            put("requested_special_access", summary.requestedSpecialAccess)
            put("sensitive_permissions", summary.applicationsWithSensitivePermissions)
            put("vpn_active", VpnObservationStorage.encode(snapshot.vpnActive))
            put("applications_json", AppAuditJsonCodec.encode(snapshot.applications))
            put("device_security_json", DeviceSecurityJsonCodec.encode(snapshot.deviceSecurity))
        }
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.insertOrThrow("scan_history", null, values)
            trimScanHistory(database)
            insertTimelineEvents(database, events)
            trimTimelineEvents(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun saveTimelineEvents(events: List<TimelineEvent>) {
        if (events.isEmpty()) return
        val database = writableDatabase
        database.beginTransaction()
        try {
            insertTimelineEvents(database, events)
            trimTimelineEvents(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun trimScanHistory(database: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - SCAN_HISTORY_RETENTION_MILLIS
        // Keep the newest entry as the current comparison baseline even if it
        // is older than the normal retention window.
        database.execSQL(
            """
            DELETE FROM scan_history
            WHERE id != (
                SELECT id FROM scan_history ORDER BY scanned_at DESC, id DESC LIMIT 1
            )
            AND (
                scanned_at < ?
                OR id NOT IN (
                    SELECT id FROM scan_history
                    ORDER BY scanned_at DESC, id DESC
                    LIMIT $MAX_SCAN_SESSIONS
                )
            )
            """.trimIndent(),
            arrayOf(cutoff)
        )
    }

    private fun insertTimelineEvents(database: SQLiteDatabase, events: List<TimelineEvent>) {
        events.forEach { event ->
            val values = ContentValues().apply {
                put("occurred_at", event.occurredAt)
                put("event_type", event.type.name)
                put("package_name", event.packageName)
                put("app_label", event.appLabel)
                put("title", event.title)
                put("details", event.details)
            }
            database.insertOrThrow("timeline_events", null, values)
        }
    }

    private fun trimTimelineEvents(database: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - TIMELINE_RETENTION_MILLIS
        database.execSQL(
            """
            DELETE FROM timeline_events
            WHERE occurred_at < ?
               OR id NOT IN (
                    SELECT id FROM timeline_events
                    ORDER BY occurred_at DESC, id DESC
                    LIMIT $MAX_TIMELINE_EVENTS
               )
            """.trimIndent(),
            arrayOf(cutoff)
        )
    }

    fun recentTimelineEvents(limit: Int = 500): List<TimelineEvent> {
        val events = ArrayList<TimelineEvent>()
        readableDatabase.query(
            "timeline_events",
            arrayOf("id", "occurred_at", "event_type", "package_name", "app_label", "title", "details"),
            null, null, null, null, "occurred_at DESC, id DESC",
            limit.coerceIn(1, MAX_TIMELINE_EVENTS).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = runCatching { TimelineEventType.valueOf(cursor.getString(2)) }
                    .getOrDefault(TimelineEventType.BASELINE_CREATED)
                events += TimelineEvent(
                    id = cursor.getLong(0),
                    occurredAt = cursor.getLong(1),
                    type = type,
                    packageName = cursor.getString(3),
                    appLabel = cursor.getString(4),
                    title = cursor.getString(5),
                    details = cursor.getString(6)
                )
            }
        }
        return events
    }

    fun recent(limit: Int = 50): List<ScanHistoryEntry> {
        val entries = ArrayList<ScanHistoryEntry>()
        readableDatabase.query(
            "scan_history",
            arrayOf("id", "scanned_at", "total_applications", "active_special_access",
                "requested_special_access", "sensitive_permissions", "vpn_active", "applications_json",
                "device_security_json"),
            null, null, null, null, "scanned_at DESC, id DESC",
            limit.coerceIn(1, MAX_SCAN_SESSIONS).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += ScanHistoryEntry(
                    id = cursor.getLong(0), scannedAt = cursor.getLong(1),
                    totalApplications = cursor.getInt(2), activeSpecialAccess = cursor.getInt(3),
                    requestedSpecialAccess = cursor.getInt(4), sensitivePermissions = cursor.getInt(5),
                    vpnActive = VpnObservationStorage.decode(cursor.getInt(6)),
                    applicationsJson = cursor.getString(7) ?: "[]",
                    deviceSecuritySnapshot = DeviceSecurityJsonCodec.decode(cursor.getString(8))
                )
            }
        }
        return entries
    }

    fun clearHistory() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("scan_history", null, null)
            writableDatabase.delete("timeline_events", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    companion object {
        private const val DATABASE_NAME = "msi_security_history.db"
        private const val MAX_SCAN_SESSIONS = 10
        private const val MAX_TIMELINE_EVENTS = 500
        private const val SCAN_HISTORY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
        private const val TIMELINE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}

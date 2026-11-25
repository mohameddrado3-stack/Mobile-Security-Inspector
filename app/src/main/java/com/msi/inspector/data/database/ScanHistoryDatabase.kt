package com.msi.inspector.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.data.serialization.AppAuditJsonCodec
import com.msi.inspector.domain.model.VpnObservationStorage
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventType

class ScanHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        createScanHistoryTable(database)
        createTimelineTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE scan_history ADD COLUMN applications_json TEXT NOT NULL DEFAULT '[]'"
            )
        }
        if (oldVersion < 3) createTimelineTable(database)
        if (oldVersion < 4) {
            database.execSQL("UPDATE scan_history SET vpn_active = -1 WHERE vpn_active = 0")
        }
    }

    private fun createScanHistoryTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scanned_at INTEGER NOT NULL,
                total_applications INTEGER NOT NULL,
                active_special_access INTEGER NOT NULL,
                requested_special_access INTEGER NOT NULL,
                sensitive_permissions INTEGER NOT NULL,
                vpn_active INTEGER NOT NULL,
                applications_json TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX scan_history_scanned_at_idx ON scan_history(scanned_at DESC)")
    }

    private fun createTimelineTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE timeline_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                package_name TEXT,
                app_label TEXT,
                title TEXT NOT NULL,
                details TEXT NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX timeline_events_occurred_at_idx ON timeline_events(occurred_at DESC)")
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
        database.execSQL(
            "DELETE FROM scan_history WHERE id NOT IN " +
                "(SELECT id FROM scan_history ORDER BY scanned_at DESC, id DESC LIMIT 50)"
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
        database.execSQL(
            "DELETE FROM timeline_events WHERE id NOT IN " +
                "(SELECT id FROM timeline_events ORDER BY occurred_at DESC, id DESC LIMIT 500)"
        )
    }

    fun recentTimelineEvents(limit: Int = 500): List<TimelineEvent> {
        val events = ArrayList<TimelineEvent>()
        readableDatabase.query(
            "timeline_events",
            arrayOf("id", "occurred_at", "event_type", "package_name", "app_label", "title", "details"),
            null, null, null, null, "occurred_at DESC, id DESC",
            limit.coerceIn(1, 500).toString()
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
                "requested_special_access", "sensitive_permissions", "vpn_active", "applications_json"),
            null, null, null, null, "scanned_at DESC, id DESC", limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += ScanHistoryEntry(
                    id = cursor.getLong(0), scannedAt = cursor.getLong(1),
                    totalApplications = cursor.getInt(2), activeSpecialAccess = cursor.getInt(3),
                    requestedSpecialAccess = cursor.getInt(4), sensitivePermissions = cursor.getInt(5),
                    vpnActive = VpnObservationStorage.decode(cursor.getInt(6)),
                    applicationsJson = cursor.getString(7) ?: "[]"
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
        private const val DATABASE_VERSION = 4
    }
}

package com.msi.inspector.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot

class ScanHistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scanned_at INTEGER NOT NULL,
                total_applications INTEGER NOT NULL,
                active_special_access INTEGER NOT NULL,
                requested_special_access INTEGER NOT NULL,
                sensitive_permissions INTEGER NOT NULL,
                vpn_active INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX scan_history_scanned_at_idx ON scan_history(scanned_at DESC)"
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) onCreate(database)
    }

    fun save(snapshot: ScanSnapshot) {
        val summary = AuditSummary.from(snapshot.applications)
        val values = ContentValues().apply {
            put("scanned_at", snapshot.completedAt)
            put("total_applications", summary.totalApplications)
            put("active_special_access", summary.activeSpecialAccess)
            put("requested_special_access", summary.requestedSpecialAccess)
            put("sensitive_permissions", summary.applicationsWithSensitivePermissions)
            put("vpn_active", if (snapshot.vpnActive) 1 else 0)
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("scan_history", null, values)
            writableDatabase.execSQL(
                "DELETE FROM scan_history WHERE id NOT IN (SELECT id FROM scan_history ORDER BY scanned_at DESC LIMIT 50)"
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun recent(limit: Int = 20): List<ScanHistoryEntry> {
        val entries = ArrayList<ScanHistoryEntry>()
        readableDatabase.query(
            "scan_history",
            arrayOf(
                "id",
                "scanned_at",
                "total_applications",
                "active_special_access",
                "requested_special_access",
                "sensitive_permissions",
                "vpn_active"
            ),
            null,
            null,
            null,
            null,
            "scanned_at DESC",
            limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    ScanHistoryEntry(
                        id = cursor.getLong(0),
                        scannedAt = cursor.getLong(1),
                        totalApplications = cursor.getInt(2),
                        activeSpecialAccess = cursor.getInt(3),
                        requestedSpecialAccess = cursor.getInt(4),
                        sensitivePermissions = cursor.getInt(5),
                        vpnActive = cursor.getInt(6) == 1
                    )
                )
            }
        }
        return entries
    }

    fun clearHistory() {
        writableDatabase.delete("scan_history", null, null)
    }

    companion object {
        private const val DATABASE_NAME = "msi_security_history.db"
        private const val DATABASE_VERSION = 1
    }
}

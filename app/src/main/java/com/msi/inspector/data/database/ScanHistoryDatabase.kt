package com.msi.inspector.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.ScanSnapshot
import org.json.JSONArray
import org.json.JSONObject

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
                vpn_active INTEGER NOT NULL,
                applications_json TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX scan_history_scanned_at_idx ON scan_history(scanned_at DESC)"
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE scan_history ADD COLUMN applications_json TEXT NOT NULL DEFAULT '[]'"
            )
        }
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
            put("applications_json", encodeApplications(snapshot.applications))
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

    fun recent(limit: Int = 50): List<ScanHistoryEntry> {
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
                "vpn_active",
                "applications_json"
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
                        vpnActive = cursor.getInt(6) == 1,
                        applicationsJson = cursor.getString(7) ?: "[]"
                    )
                )
            }
        }
        return entries
    }

    fun clearHistory() {
        writableDatabase.delete("scan_history", null, null)
    }

    private fun encodeApplications(applications: List<AppAudit>): String {
        val result = JSONArray()
        applications.forEach { app ->
            result.put(
                JSONObject().apply {
                    put("label", app.label)
                    put("packageName", app.packageName)
                    put("version", app.version)
                    put("installer", app.installer)
                    put("isSystem", app.isSystem)
                    put("targetSdk", app.targetSdk ?: JSONObject.NULL)
                    put("firstInstallTime", app.firstInstallTime ?: JSONObject.NULL)
                    put("lastUpdateTime", app.lastUpdateTime ?: JSONObject.NULL)
                    put("signatureSha256", app.signingCertificateSha256 ?: JSONObject.NULL)
                    put("category", app.categoryLabel ?: JSONObject.NULL)
                    put("grantedSensitive", JSONArray(app.grantedSensitive))
                    put("requestedSensitive", JSONArray(app.requestedSensitive))
                    put("requestedSpecialAccess", JSONArray(app.requestedSpecialAccess))
                    put("activeSpecialAccess", JSONArray(app.specialAccessReasons))
                }
            )
        }
        return result.toString()
    }

    companion object {
        private const val DATABASE_NAME = "msi_security_history.db"
        private const val DATABASE_VERSION = 2
    }
}

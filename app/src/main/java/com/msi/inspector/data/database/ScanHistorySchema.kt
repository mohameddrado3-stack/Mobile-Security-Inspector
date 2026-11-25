package com.msi.inspector.data.database

import android.database.sqlite.SQLiteDatabase

internal object ScanHistorySchema {
    const val VERSION = 4

    fun create(database: SQLiteDatabase) {
        createScanHistoryTable(database)
        createTimelineTable(database)
    }

    fun upgrade(database: SQLiteDatabase, oldVersion: Int) {
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
}

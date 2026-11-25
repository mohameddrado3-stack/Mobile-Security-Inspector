package com.msi.inspector.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.msi.inspector.data.database.ScanHistoryDatabase
import com.msi.inspector.domain.model.ScanSnapshot
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanHistoryDatabaseTest {
    @Test
    fun upgradeFromVersionOnePreservesRowsAndMigratesAmbiguousVpnValues() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        val oldDatabase = SQLiteDatabase.openOrCreateDatabase(file, null)
        oldDatabase.execSQL(
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
        oldDatabase.execSQL(
            "INSERT INTO scan_history VALUES (1, 100, 4, 1, 2, 3, 0)"
        )
        oldDatabase.execSQL(
            "INSERT INTO scan_history VALUES (2, 200, 5, 0, 1, 2, 1)"
        )
        oldDatabase.version = 1
        oldDatabase.close()

        val helper = ScanHistoryDatabase(context)
        val database = helper.writableDatabase
        val values = database.rawQuery(
            "SELECT vpn_active FROM scan_history ORDER BY id",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getInt(0))
            }
        }
        val columns = database.rawQuery("PRAGMA table_info(scan_history)", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }
        val timelineExists = database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='timeline_events'",
            null
        ).use { it.moveToFirst() }

        assertEquals(5, database.version)
        assertEquals(listOf(-1, 1), values)
        assertTrue("applications_json" in columns)
        assertTrue("device_security_json" in columns)
        assertTrue(timelineExists)
        assertEquals(2L, database.rawQuery(
            "SELECT COUNT(*) FROM scan_history",
            null
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        helper.close()
    }

    @Test
    fun failedTimelineInsertRollsBackTheNewScanSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val helper = ScanHistoryDatabase(context)
        val database = helper.writableDatabase
        database.execSQL(
            """
            CREATE TRIGGER reject_timeline_event
            BEFORE INSERT ON timeline_events
            BEGIN
                SELECT RAISE(ABORT, 'forced timeline insert failure');
            END
            """.trimIndent()
        )
        val snapshot = ScanSnapshot(
            applications = emptyList(),
            vpnActive = null,
            completedAt = 500L
        )
        val event = TimelineEvent(
            occurredAt = 500L,
            type = TimelineEventType.BASELINE_CREATED,
            packageName = null,
            appLabel = null,
            title = "Baseline",
            details = "Created"
        )

        assertThrows(SQLiteException::class.java) {
            helper.saveWithTimeline(snapshot, listOf(event))
        }
        val scanCount = database.rawQuery(
            "SELECT COUNT(*) FROM scan_history",
            null
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

        assertEquals(0L, scanCount)
        assertFalse(database.inTransaction())
        helper.close()
    }

    @Test
    fun clearHistoryRemovesSnapshotsAndTimelineEventsTogether() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val helper = ScanHistoryDatabase(context)
        helper.saveWithTimeline(
            ScanSnapshot(
                applications = emptyList(),
                vpnActive = null,
                completedAt = 900L
            ),
            listOf(
                TimelineEvent(
                    occurredAt = 900L,
                    type = TimelineEventType.BASELINE_CREATED,
                    packageName = null,
                    appLabel = null,
                    title = "Baseline",
                    details = "Created"
                )
            )
        )

        helper.clearHistory()

        val database = helper.readableDatabase
        val scanCount = database.rawQuery(
            "SELECT COUNT(*) FROM scan_history",
            null
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        val eventCount = database.rawQuery(
            "SELECT COUNT(*) FROM timeline_events",
            null
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

        assertEquals(0L, scanCount)
        assertEquals(0L, eventCount)
        helper.close()
    }


    @Test
    fun keepsOnlyTenRecentSessionsWithinTheRetentionWindow() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val helper = ScanHistoryDatabase(context)
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        repeat(12) { index ->
            helper.save(
                ScanSnapshot(
                    applications = emptyList(),
                    vpnActive = null,
                    completedAt = now - index * day
                )
            )
        }

        val retained = helper.recent(50)
        assertEquals(10, retained.size)
        assertEquals(now, retained.first().scannedAt)
        assertTrue(retained.last().scannedAt >= now - 9L * day)
        helper.close()
    }

    @Test
    fun keepsTheLatestScanAsBaselineEvenWhenItIsOlderThanThirtyDays() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        val helper = ScanHistoryDatabase(context)
        val oldBaselineAt = System.currentTimeMillis() - 45L * 24L * 60L * 60L * 1000L

        helper.save(
            ScanSnapshot(
                applications = emptyList(),
                vpnActive = null,
                completedAt = oldBaselineAt
            )
        )

        val retained = helper.recent()
        assertEquals(1, retained.size)
        assertEquals(oldBaselineAt, retained.single().scannedAt)
        helper.close()
    }

    private companion object {
        const val DATABASE_NAME = "msi_security_history.db"
    }
}

package com.msi.inspector.data.repository

import com.msi.inspector.domain.model.ScanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppAuditRepositoryStateTest {
    @Test
    fun loadsHistoryTimelineAndLatestSnapshotAsOneConsistentState() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("msi_security_history.db")
        val repository = AppAuditRepository(context)
        val scannedAt = 5_500L

        repository.saveHistory(
            ScanSnapshot(
                applications = emptyList(),
                vpnActive = null,
                completedAt = scannedAt
            )
        )

        val state = repository.loadScanState()

        assertEquals(scannedAt, state.history.first().scannedAt)
        assertEquals(scannedAt, state.timeline.first().occurredAt)
        assertNotNull(state.latestSnapshot)
        assertEquals(scannedAt, state.latestSnapshot?.completedAt)
        repository.close()
    }
}

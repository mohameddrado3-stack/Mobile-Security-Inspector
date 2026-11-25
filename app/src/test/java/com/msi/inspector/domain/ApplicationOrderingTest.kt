package com.msi.inspector.domain

import com.msi.inspector.domain.analyzer.ApplicationSortOrder
import com.msi.inspector.domain.analyzer.sortApplications
import com.msi.inspector.domain.model.AppAudit
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationOrderingTest {
    @Test
    fun mostRecentlyInstalledUsesFirstInstallTimeNotLastUpdateTime() {
        val olderInstallRecentlyUpdated = app("Updated old app", "com.example.old", 1_000L, 9_000L)
        val newerInstallOldUpdate = app("New install", "com.example.new", 8_000L, 2_000L)
        val sorted = sortApplications(
            listOf(olderInstallRecentlyUpdated, newerInstallOldUpdate),
            ApplicationSortOrder.MOST_RECENTLY_INSTALLED
        )
        assertEquals(listOf("com.example.new", "com.example.old"), sorted.map { it.packageName })
    }

    @Test
    fun missingInstallTimeAppearsAfterKnownInstallTimes() {
        val unknown = app("Unknown", "com.example.unknown", null, 99_000L)
        val known = app("Known", "com.example.known", 1_000L, 1_000L)
        val sorted = sortApplications(listOf(unknown, known), ApplicationSortOrder.MOST_RECENTLY_INSTALLED)
        assertEquals(listOf("com.example.known", "com.example.unknown"), sorted.map { it.packageName })
    }

    private fun app(label: String, packageName: String, firstInstallTime: Long?, lastUpdateTime: Long?) =
        AppAudit(
            label = label, packageName = packageName, version = "1.0", installer = "Test",
            isSystem = false, grantedSensitive = emptyList(), requestedSensitive = emptyList(),
            requestedSpecialAccess = emptyList(), signals = emptyList(), specialAccessReasons = emptyList(),
            firstInstallTime = firstInstallTime, lastUpdateTime = lastUpdateTime
        )
}

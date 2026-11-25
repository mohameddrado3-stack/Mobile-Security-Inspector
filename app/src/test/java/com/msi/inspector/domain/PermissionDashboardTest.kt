package com.msi.inspector.domain

import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.model.AppAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDashboardTest {
    @Test
    fun dashboardAlwaysShowsTwelveDefinedPermissionCategories() {
        val categories = PermissionAnalyzer.dashboardCategories(emptyList())
        assertEquals(12, categories.size)
        assertTrue(categories.all { it.applicationCount == 0 })
    }

    @Test
    fun dashboardSeparatesGrantedAndRequestedApplications() {
        val app = AppAudit(
            label = "Camera",
            packageName = "com.example.camera",
            version = "1",
            installer = "Store",
            isSystem = false,
            grantedSensitive = listOf("android.permission.CAMERA"),
            requestedSensitive = listOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO"
            ),
            requestedSpecialAccess = emptyList(),
            signals = emptyList(),
            specialAccessReasons = emptyList()
        )
        val categories = PermissionAnalyzer.dashboardCategories(listOf(app))
        val camera = categories.single { it.title == "الكاميرا" }
        val microphone = categories.single { it.title == "الميكروفون" }

        assertEquals(1, camera.applicationCount)
        assertEquals(1, camera.grantedApplicationCount)
        assertEquals(1, microphone.requestedApplicationCount)
        assertEquals(0, microphone.grantedApplicationCount)
    }
}

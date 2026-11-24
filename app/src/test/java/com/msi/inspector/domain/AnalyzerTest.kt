package com.msi.inspector.domain

import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.analyzer.filterApplications
import com.msi.inspector.domain.model.AppAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerTest {
    private val applications = listOf(
        AppAudit(
            label = "Camera Utility",
            packageName = "com.example.camera",
            version = "1.0",
            installer = "Google Play",
            isSystem = false,
            grantedSensitive = listOf("android.permission.CAMERA"),
            requestedSensitive = listOf("android.permission.CAMERA"),
            requestedSpecialAccess = emptyList(),
            signals = emptyList(),
            specialAccessReasons = emptyList()
        ),
        AppAudit(
            label = "System Service",
            packageName = "com.example.system",
            version = "2.0",
            installer = "غير معروف",
            isSystem = true,
            grantedSensitive = listOf("android.permission.RECORD_AUDIO"),
            requestedSensitive = listOf("android.permission.RECORD_AUDIO"),
            requestedSpecialAccess = listOf("طلب إعداد خاص"),
            signals = listOf("طلب إعداد خاص"),
            specialAccessReasons = listOf("خدمة إمكانية الوصول مفعّلة")
        ),
        AppAudit(
            label = "Maps",
            packageName = "com.example.maps",
            version = "3.0",
            installer = "Google Play",
            isSystem = false,
            grantedSensitive = emptyList(),
            requestedSensitive = listOf("android.permission.ACCESS_FINE_LOCATION"),
            requestedSpecialAccess = emptyList(),
            signals = emptyList(),
            specialAccessReasons = emptyList()
        )
    )

    @Test
    fun filterByUserApplicationsExcludesSystemPackages() {
        val result = filterApplications(applications, "", AppFilter.USER)
        assertEquals(2, result.size)
        assertTrue(result.none { it.isSystem })
    }

    @Test
    fun searchMatchesPackageNameWithoutCaseSensitivity() {
        val result = filterApplications(applications, "EXAMPLE.MAPS", AppFilter.ALL)
        assertEquals(listOf("com.example.maps"), result.map { it.packageName })
    }

    @Test
    fun activeSpecialFilterOnlyReturnsActiveSignals() {
        val result = filterApplications(applications, "", AppFilter.ACTIVE_SPECIAL)
        assertEquals(listOf("com.example.system"), result.map { it.packageName })
    }

    @Test
    fun permissionAnalyzerGroupsSensitivePermissions() {
        val result = PermissionAnalyzer.categories(applications)
        assertEquals(2, result.size)
        assertTrue(result.any { it.title == "الكاميرا" && it.applicationCount == 1 })
        assertTrue(result.any { it.title == "الميكروفون" && it.applicationCount == 1 })
    }

    @Test
    fun auditSummaryCountsApplicationsNotIndividualPermissions() {
        val summary = AuditSummary.from(applications)
        assertEquals(3, summary.totalApplications)
        assertEquals(1, summary.systemApplications)
        assertEquals(1, summary.activeSpecialAccess)
        assertEquals(1, summary.requestedSpecialAccess)
        assertEquals(2, summary.applicationsWithSensitivePermissions)
    }

    @Test
    fun unknownPermissionsDoNotCreateCategories() {
        assertFalse(PermissionAnalyzer.categoryFor("com.example.permission.UNKNOWN") != null)
    }
}

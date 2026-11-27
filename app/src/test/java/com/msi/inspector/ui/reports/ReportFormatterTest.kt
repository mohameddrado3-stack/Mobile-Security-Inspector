package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.AuditStatus
import com.msi.inspector.domain.model.PackageMetadataState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {
    @Test
    fun textReportIncludesEveryApplicationWithoutLegacyCap() {
        val apps = (1..305).map { index ->
            app(
                label = "Application $index",
                packageName = "com.example.app$index"
            )
        }
        val report = ReportFormatter.format(apps, 1_700_000_000_000L)

        assertTrue(report.contains("عدد التطبيقات: 305"))
        assertTrue(report.contains("التطبيق: Application 305"))
        assertFalse(report.contains("تم اختصار التقرير"))
    }

    @Test
    fun csvQuotesAndEscapesValues() {
        val report = ReportFormatter.formatCsv(
            listOf(app(label = "A, \"B\"", packageName = "com.example.test")),
            1_700_000_000_000L
        )
        assertTrue(report.contains("\"A, \"\"B\"\"\""))
        assertTrue(report.contains("\"com.example.test\""))
    }

    @Test
    fun jsonReportContainsNullForUnavailableMetadata() {
        val report = ReportFormatter.formatJson(
            listOf(app(label = "Example", packageName = "com.example")),
            null
        )
        assertTrue(report.contains("\"targetSdk\": null"))
        assertTrue(report.contains("\"signatureSha256\": null"))
        assertTrue(report.contains("\"applicationCount\": 1"))
    }

    @Test
    fun allExportFormatsPreserveIncompleteEvidenceAndUnknownPermissions() {
        val incomplete = app("Limited", "com.example.limited").copy(
            packageMetadataAvailable = false,
            packageMetadataState = PackageMetadataState.ACCESS_DENIED,
            unclassifiedPermissions = listOf("vendor.permission.UNKNOWN"),
            permissionsWithUnknownGrantState = listOf("android.permission.CAMERA"),
            specialAccessStateAvailable = false,
            signals = listOf(
                "رفض Android الوصول إلى بيانات هذه الحزمة.",
                "تعذر تحديد مستوى حساسية صلاحية واحدة."
            )
        )
        val activeWithPartialEvidence = incomplete.copy(
            label = "Active and incomplete",
            packageName = "com.example.active.partial",
            specialAccessReasons = listOf("Accessibility enabled")
        )
        assertEquals(AuditStatus.REVIEW, activeWithPartialEvidence.status)

        val applications = listOf(incomplete, activeWithPartialEvidence)
        val textReport = ReportFormatter.format(applications, 1_700_000_000_000L)
        val csvReport = ReportFormatter.formatCsv(applications, 1_700_000_000_000L)
        val jsonReport = ReportFormatter.formatJson(applications, 1_700_000_000_000L)

        assertTrue(textReport.contains("تطبيقات ببيانات فحص غير مكتملة: 2"))
        assertTrue(textReport.contains("حالة بيانات الحزمة: رفض Android الوصول"))
        assertTrue(textReport.contains("vendor.permission.UNKNOWN"))
        assertTrue(textReport.contains("صلاحيات تعذّر التحقق من حالة منحها: android.permission.CAMERA"))
        assertTrue(textReport.contains("رفض Android الوصول إلى بيانات هذه الحزمة."))

        assertTrue(csvReport.contains("\"ACCESS_DENIED\""))
        assertTrue(csvReport.contains("\"false\""))
        assertTrue(csvReport.contains("vendor.permission.UNKNOWN"))
        assertTrue(csvReport.contains("android.permission.CAMERA"))
        assertTrue(csvReport.contains("رفض Android الوصول إلى بيانات هذه الحزمة."))

        assertTrue(jsonReport.contains("\"incompleteEvidenceCount\": 2"))
        assertTrue(jsonReport.contains("\"packageMetadataState\": \"ACCESS_DENIED\""))
        assertTrue(jsonReport.contains("\"specialAccessStateAvailable\": false"))
        assertTrue(jsonReport.contains("\"unclassifiedPermissions\": [\"vendor.permission.UNKNOWN\"]"))
        assertTrue(jsonReport.contains("\"permissionsWithUnknownGrantState\": [\"android.permission.CAMERA\"]"))
        assertTrue(jsonReport.contains("رفض Android الوصول إلى بيانات هذه الحزمة."))
    }

    @Test
    fun allExportFormatsIncludeDeviceSecurityEvidenceAndAndroidRestrictions() {
        val device = com.msi.inspector.domain.model.DeviceSecuritySnapshot(
            androidRelease = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                "16",
                com.msi.inspector.domain.model.EvidenceAvailability.AVAILABLE
            ),
            apiLevel = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                "36",
                com.msi.inspector.domain.model.EvidenceAvailability.AVAILABLE
            ),
            securityPatch = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                "2026-10-01",
                com.msi.inspector.domain.model.EvidenceAvailability.AVAILABLE
            ),
            secureScreenLock = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                "enabled",
                com.msi.inspector.domain.model.EvidenceAvailability.AVAILABLE
            ),
            storageEncryption = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                "active",
                com.msi.inspector.domain.model.EvidenceAvailability.AVAILABLE
            ),
            adbEnabled = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                null,
                com.msi.inspector.domain.model.EvidenceAvailability.RESTRICTED_BY_ANDROID
            ),
            developerOptionsEnabled = com.msi.inspector.domain.model.DeviceSecurityEvidence(
                null,
                com.msi.inspector.domain.model.EvidenceAvailability.RESTRICTED_BY_ANDROID
            ),
            collectedAt = 1_700_000_000_000L
        )
        val applications = listOf(app("Example", "com.example.app"))

        val textReport = ReportFormatter.format(applications, 1_700_000_000_000L, device)
        val csvReport = ReportFormatter.formatCsv(applications, 1_700_000_000_000L, device)
        val jsonReport = ReportFormatter.formatJson(applications, 1_700_000_000_000L, device)

        assertTrue(textReport.contains("مؤشرات أمان نظام Android"))
        assertTrue(textReport.contains("Android يحجب القراءة عن التطبيقات العادية"))
        assertTrue(csvReport.contains("\"device\""))
        assertTrue(csvReport.contains("\"RESTRICTED_BY_ANDROID\""))
        assertTrue(csvReport.contains("\"application\""))
        assertTrue(jsonReport.contains("\"deviceSecurity\""))
        assertTrue(jsonReport.contains("\"RESTRICTED_BY_ANDROID\""))
    }

    private fun app(label: String, packageName: String) = AppAudit(
        label = label,
        packageName = packageName,
        version = "1.0",
        installer = "Test",
        isSystem = false,
        grantedSensitive = listOf("android.permission.CAMERA"),
        requestedSensitive = listOf("android.permission.CAMERA"),
        requestedSpecialAccess = emptyList(),
        signals = emptyList(),
        specialAccessReasons = emptyList()
    )
}

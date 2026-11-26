package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
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
    fun csvTreatsFormulaLikeApplicationNamesAsPlainText() {
        val report = ReportFormatter.formatCsv(
            listOf(app(label = "=HYPERLINK(\\"https://example.invalid\\",\\"open\\")", packageName = "com.example.test")),
            1_700_000_000_000L
        )

        assertTrue(report.contains("\\\"'=HYPERLINK"))
    }

    @Test
    fun textAndJsonReportsPreserveEvidenceAvailabilityAndExplanations() {
        val application = app("Example", "com.example")
            .copy(
                packageMetadataAvailable = false,
                packageMetadataState = com.msi.inspector.domain.model.PackageMetadataState.ACCESS_DENIED,
                specialAccessStateAvailable = false,
                unclassifiedPermissions = listOf("vendor.permission.UNKNOWN"),
                signals = listOf("تعذر قراءة بيانات الحزمة")
            )

        val textReport = ReportFormatter.format(listOf(application), null)
        val jsonReport = ReportFormatter.formatJson(listOf(application), null)

        assertTrue(textReport.contains("رفض Android الوصول"))
        assertTrue(textReport.contains("vendor.permission.UNKNOWN"))
        assertTrue(textReport.contains("تعذر قراءة بيانات الحزمة"))
        assertTrue(jsonReport.contains("\\"packageMetadataState\\": \\"ACCESS_DENIED\\""))
        assertTrue(jsonReport.contains("\\"specialAccessStateAvailable\\": false"))
        assertTrue(jsonReport.contains("\\"signals\\": [\\"تعذر قراءة بيانات الحزمة\\"]"))
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

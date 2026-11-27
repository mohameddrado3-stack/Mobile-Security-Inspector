package com.msi.inspector.ui.reports

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.DeviceSecuritySnapshot

fun shareReport(
    context: Context,
    applications: List<AppAudit>,
    scannedAt: Long?,
    format: ReportFileFormat,
    deviceSecurity: DeviceSecuritySnapshot? = null
) {
    try {
        val content = when (format) {
            ReportFileFormat.TXT -> ReportFormatter.format(applications, scannedAt, deviceSecurity)
            ReportFileFormat.CSV -> ReportFormatter.formatCsv(applications, scannedAt, deviceSecurity)
            ReportFileFormat.JSON -> ReportFormatter.formatJson(applications, scannedAt, deviceSecurity)
            ReportFileFormat.PDF -> ReportFormatter.format(applications, scannedAt, deviceSecurity)
        }
        val uri = ReportExportManager.export(context, format, content)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_SUBJECT, "MSI — تقرير الفحص المحلي")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "MSI report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة تقرير MSI"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر إنشاء التقرير أو فتح قائمة المشاركة.", Toast.LENGTH_SHORT).show()
    }
}

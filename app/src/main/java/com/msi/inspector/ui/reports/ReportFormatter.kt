package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportFormatter {
    fun format(applications: List<AppAudit>, scannedAt: Long?): String {
        val date = scannedAt?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("ar", "EG"))
                .apply { timeZone = TimeZone.getTimeZone("Africa/Cairo") }
                .format(Date(it))
        } ?: "غير متاح"
        val report = StringBuilder()
        report.append("Mobile Security Inspector — تقرير فحص محلي\n")
        report.append("وقت الفحص: ").append(date).append("\n")
        report.append("عدد التطبيقات: ").append(applications.size).append("\n")
        report.append("إعدادات وصول خاصة نشطة: ")
            .append(applications.count { it.needsReview }).append("\n")
        report.append("تطبيقات بصلاحيات حساسة ممنوحة: ")
            .append(applications.count { it.grantedSensitive.isNotEmpty() }).append("\n\n")
        applications.take(MAX_APPLICATIONS).forEach { app ->
            report.append("التطبيق: ").append(app.label).append("\n")
            report.append("الحزمة: ").append(app.packageName).append("\n")
            report.append("الإصدار: ").append(app.version).append("\n")
            report.append("مصدر التثبيت: ").append(app.installer).append("\n")
            if (app.specialAccessReasons.isNotEmpty()) {
                report.append("إعداد وصول خاص نشط: ")
                    .append(app.specialAccessReasons.joinToString("؛ ")).append("\n")
            }
            if (app.requestedSpecialAccess.isNotEmpty()) {
                report.append("إعدادات خاصة مطلوبة وغير مؤكدة: ")
                    .append(app.requestedSpecialAccess.joinToString("؛ ")).append("\n")
            }
            if (app.grantedSensitive.isNotEmpty()) {
                report.append("صلاحيات حساسة ممنوحة: ")
                    .append(app.grantedSensitive.joinToString("؛ ")).append("\n")
            }
            report.append("\n")
        }
        if (applications.size > MAX_APPLICATIONS) {
            report.append("تم اختصار التقرير إلى أول ")
                .append(MAX_APPLICATIONS)
                .append(" تطبيقًا من أصل ")
                .append(applications.size)
                .append(".\n")
        }
        report.append("\nهذا التقرير يعرض مؤشرات متاحة من Android ولا يثبت أن أي تطبيق ضار.")
        return report.toString()
    }

    private const val MAX_APPLICATIONS = 300
}

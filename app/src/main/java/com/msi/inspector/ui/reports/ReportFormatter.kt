package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportFormatter {
    fun format(applications: List<AppAudit>, scannedAt: Long?): String {
        val date = formatDate(scannedAt)
        val report = StringBuilder()
        report.append("MSI — تقرير فحص محلي\n")
        report.append("وقت الفحص: ").append(date).append("\n")
        report.append("عدد التطبيقات: ").append(applications.size).append("\n")
        report.append("إعدادات وصول خاصة نشطة: ").append(applications.count { it.needsReview }).append("\n")
        report.append("طلبات وصول خاص: ").append(applications.count { it.requestedSpecialAccess.isNotEmpty() }).append("\n")
        report.append("تطبيقات بصلاحيات حساسة ممنوحة: ")
            .append(applications.count { it.grantedSensitive.isNotEmpty() }).append("\n\n")

        applications.forEach { app ->
            report.append("التطبيق: ").append(app.label).append("\n")
            report.append("الحزمة: ").append(app.packageName).append("\n")
            report.append("الإصدار: ").append(app.version).append("\n")
            report.append("النوع: ").append(if (app.isSystem) "نظام" else "مثبّت").append("\n")
            report.append("مصدر التثبيت: ").append(app.installer).append("\n")
            report.append("تصنيف Android: ").append(app.categoryLabel ?: "غير محدد").append("\n")
            report.append("Target SDK: ").append(app.targetSdk ?: "غير متاح").append("\n")
            report.append("SHA-256 للشهادة: ").append(app.signingCertificateSha256 ?: "غير متاح").append("\n")
            report.append("تاريخ التثبيت: ").append(formatDate(app.firstInstallTime)).append("\n")
            report.append("آخر تحديث: ").append(formatDate(app.lastUpdateTime)).append("\n")
            appendList(report, "إعدادات وصول خاصة نشطة", app.specialAccessReasons)
            appendList(report, "إعدادات خاصة مطلوبة (غير مؤكدة التفعيل)", app.requestedSpecialAccess)
            appendList(report, "صلاحيات حساسة ممنوحة", app.grantedSensitive)
            appendList(report, "صلاحيات حساسة مطلوبة", app.requestedSensitive)
            report.append("\n")
        }
        report.append("هذا التقرير يعرض مؤشرات متاحة من Android ولا يثبت أن أي تطبيق ضار.")
        return report.toString()
    }

    fun formatCsv(applications: List<AppAudit>, scannedAt: Long?): String {
        val output = StringBuilder()
        output.append(
            listOf(
                "scan_time", "app_name", "package_name", "version", "installer",
                "is_system", "target_sdk", "category", "signature_sha256",
                "first_install", "last_update", "active_special_access",
                "requested_special_access", "granted_sensitive", "requested_sensitive"
            ).joinToString(",") { csv(it) }
        ).append("\n")
        applications.forEach { app ->
            val row = listOf(
                formatDate(scannedAt),
                app.label,
                app.packageName,
                app.version,
                app.installer,
                app.isSystem.toString(),
                app.targetSdk?.toString().orEmpty(),
                app.categoryLabel.orEmpty(),
                app.signingCertificateSha256.orEmpty(),
                formatDate(app.firstInstallTime),
                formatDate(app.lastUpdateTime),
                app.specialAccessReasons.joinToString(" | "),
                app.requestedSpecialAccess.joinToString(" | "),
                app.grantedSensitive.joinToString(" | "),
                app.requestedSensitive.joinToString(" | ")
            )
            output.append(row.joinToString(",") { csv(it) }).append("\n")
        }
        return output.toString()
    }

    fun formatJson(applications: List<AppAudit>, scannedAt: Long?): String {
        val apps = JSONArray()
        applications.forEach { app ->
            apps.put(
                JSONObject().apply {
                    put("label", app.label)
                    put("packageName", app.packageName)
                    put("version", app.version)
                    put("installer", app.installer)
                    put("isSystem", app.isSystem)
                    put("targetSdk", app.targetSdk ?: JSONObject.NULL)
                    put("category", app.categoryLabel ?: JSONObject.NULL)
                    put("signatureSha256", app.signingCertificateSha256 ?: JSONObject.NULL)
                    put("firstInstallTime", app.firstInstallTime ?: JSONObject.NULL)
                    put("lastUpdateTime", app.lastUpdateTime ?: JSONObject.NULL)
                    put("activeSpecialAccess", JSONArray(app.specialAccessReasons))
                    put("requestedSpecialAccess", JSONArray(app.requestedSpecialAccess))
                    put("grantedSensitive", JSONArray(app.grantedSensitive))
                    put("requestedSensitive", JSONArray(app.requestedSensitive))
                }
            )
        }
        return JSONObject()
            .put("product", "MSI")
            .put("scanTime", scannedAt ?: JSONObject.NULL)
            .put("applicationCount", applications.size)
            .put("applications", apps)
            .put("disclaimer", "Indicators only; not a malware verdict.")
            .toString(2)
    }

    private fun appendList(output: StringBuilder, label: String, values: List<String>) {
        output.append(label).append(": ")
            .append(values.ifEmpty { listOf("لا توجد ضمن المعلومات المتاحة") }.joinToString("؛ "))
            .append("\n")
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    private fun formatDate(value: Long?): String =
        value?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .apply { timeZone = TimeZone.getDefault() }
                .format(Date(it))
        } ?: "غير متاح"
}

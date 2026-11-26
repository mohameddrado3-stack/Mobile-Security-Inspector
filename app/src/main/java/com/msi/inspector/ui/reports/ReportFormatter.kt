package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.AuditStatus
import com.msi.inspector.domain.model.PackageMetadataState
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
            .append(applications.count { it.grantedSensitive.isNotEmpty() }).append("\n")
        report.append("تطبيقات ببيانات فحص غير مكتملة: ")
            .append(applications.count { it.status == AuditStatus.INCOMPLETE }).append("\n\n")

        applications.forEach { app ->
            report.append("التطبيق: ").append(app.label).append("\n")
            report.append("الحزمة: ").append(app.packageName).append("\n")
            report.append("الإصدار: ").append(app.version).append("\n")
            report.append("النوع: ").append(if (app.isSystem) "نظام" else "مثبّت").append("\n")
            report.append("مصدر التثبيت: ").append(app.installer).append("\n")
            report.append("حالة بيانات الحزمة: ").append(packageMetadataLabel(app.packageMetadataState)).append("\n")
            report.append("التحقق من الوصول الخاص: ")
                .append(if (app.specialAccessStateAvailable) "متاح" else "غير مكتمل").append("\n")
            report.append("تصنيف Android: ").append(app.categoryLabel ?: "غير محدد").append("\n")
            report.append("Target SDK: ").append(app.targetSdk ?: "غير متاح").append("\n")
            report.append("SHA-256 للشهادة: ").append(app.signingCertificateSha256 ?: "غير متاح").append("\n")
            report.append("تاريخ التثبيت: ").append(formatDate(app.firstInstallTime)).append("\n")
            report.append("آخر تحديث: ").append(formatDate(app.lastUpdateTime)).append("\n")
            report.append("آخر استخدام معروف: ").append(formatDate(app.lastUsedAt)).append("\n")
            appendList(report, "إعدادات وصول خاصة نشطة", app.specialAccessReasons)
            appendList(report, "إعدادات خاصة مطلوبة (غير مؤكدة التفعيل)", app.requestedSpecialAccess)
            appendList(report, "صلاحيات حساسة ممنوحة", app.grantedSensitive)
            appendList(report, "صلاحيات حساسة مطلوبة", app.requestedSensitive)
            appendList(report, "صلاحيات غير مصنفة", app.unclassifiedPermissions)
            appendList(report, "ملاحظات وحدود الفحص", app.signals)
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
                "is_system", "package_metadata_state", "special_access_evidence_available",
                "unclassified_permissions", "signals", "target_sdk", "category", "signature_sha256",
                "first_install", "last_update", "last_used", "active_special_access",
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
                app.packageMetadataState.name,
                app.specialAccessStateAvailable.toString(),
                app.unclassifiedPermissions.joinToString(" | "),
                app.signals.joinToString(" | "),
                app.targetSdk?.toString().orEmpty(),
                app.categoryLabel.orEmpty(),
                app.signingCertificateSha256.orEmpty(),
                formatDate(app.firstInstallTime),
                formatDate(app.lastUpdateTime),
                formatDate(app.lastUsedAt),
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
        val rows = applications.joinToString(",\n") { app ->
            """
            {
              "label": ${jsonString(app.label)},
              "packageName": ${jsonString(app.packageName)},
              "version": ${jsonString(app.version)},
              "installer": ${jsonString(app.installer)},
              "isSystem": ${app.isSystem},
              "packageMetadataAvailable": ${app.packageMetadataAvailable},
              "packageMetadataState": ${jsonString(app.packageMetadataState.name)},
              "specialAccessStateAvailable": ${app.specialAccessStateAvailable},
              "unclassifiedPermissions": ${jsonArray(app.unclassifiedPermissions)},
              "signals": ${jsonArray(app.signals)},
              "targetSdk": ${app.targetSdk?.toString() ?: "null"},
              "category": ${app.categoryLabel?.let(::jsonString) ?: "null"},
              "signatureSha256": ${app.signingCertificateSha256?.let(::jsonString) ?: "null"},
              "firstInstallTime": ${app.firstInstallTime?.toString() ?: "null"},
              "lastUpdateTime": ${app.lastUpdateTime?.toString() ?: "null"},
              "lastUsedAt": ${app.lastUsedAt?.toString() ?: "null"},
              "activeSpecialAccess": ${jsonArray(app.specialAccessReasons)},
              "requestedSpecialAccess": ${jsonArray(app.requestedSpecialAccess)},
              "grantedSensitive": ${jsonArray(app.grantedSensitive)},
              "requestedSensitive": ${jsonArray(app.requestedSensitive)}
            }
            """.trimIndent()
        }
        return """
        {
          "product": "MSI",
          "scanTime": ${scannedAt?.toString() ?: "null"},
          "applicationCount": ${applications.size},
          "incompleteEvidenceCount": ${applications.count { it.status == AuditStatus.INCOMPLETE }},
          "applications": [
        ${rows.prependIndent("    ")}
          ],
          "disclaimer": "Indicators only; not a malware verdict."
        }
        """.trimIndent()
    }

    private fun packageMetadataLabel(state: PackageMetadataState): String = when (state) {
        PackageMetadataState.AVAILABLE -> "متاحة"
        PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE -> "غير متاحة أو غير ظاهرة"
        PackageMetadataState.ACCESS_DENIED -> "رفض Android الوصول"
        PackageMetadataState.ERROR -> "تعذرت القراءة بسبب خطأ"
    }

    private fun jsonArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ", ") { jsonString(it) }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
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

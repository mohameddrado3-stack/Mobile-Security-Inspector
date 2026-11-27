package com.msi.inspector.ui.reports

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.PackageMetadataState
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportFormatter {
    fun format(
        applications: List<AppAudit>,
        scannedAt: Long?,
        deviceSecurity: DeviceSecuritySnapshot? = null
    ): String {
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
            .append(applications.count { it.hasIncompleteEvidence }).append("\n\n")

        appendDeviceSecuritySection(report, deviceSecurity)

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
            appendList(report, "صلاحيات تعذّر التحقق من حالة منحها", app.permissionsWithUnknownGrantState)
            appendList(report, "ملاحظات وحدود الفحص", app.signals)
            report.append("\n")
        }
        report.append("هذا التقرير يعرض مؤشرات متاحة من Android ولا يثبت أن أي تطبيق ضار.")
        return report.toString()
    }

    fun formatCsv(
        applications: List<AppAudit>,
        scannedAt: Long?,
        deviceSecurity: DeviceSecuritySnapshot? = null
    ): String {
        val headers = listOf(
            "record_type", "scan_time", "app_name", "package_name", "version", "installer",
            "is_system", "package_metadata_state", "special_access_evidence_available",
            "unclassified_permissions", "permissions_with_unknown_grant_state", "signals",
            "target_sdk", "category", "signature_sha256",
            "first_install", "last_update", "last_used", "active_special_access",
            "requested_special_access", "granted_sensitive", "requested_sensitive",
            "android_release", "android_release_availability",
            "api_level", "api_level_availability",
            "security_patch", "security_patch_availability",
            "secure_screen_lock", "secure_screen_lock_availability",
            "storage_encryption", "storage_encryption_availability",
            "adb_enabled", "adb_enabled_availability",
            "developer_options_enabled", "developer_options_enabled_availability"
        )
        val output = StringBuilder()
        output.append(headers.joinToString(",") { csv(it) }).append("\n")

        if (deviceSecurity != null) {
            val row = mutableMapOf(
                "record_type" to "device",
                "scan_time" to formatDate(scannedAt)
            )
            row.putAll(deviceSecurityCsvFields(deviceSecurity))
            output.append(headers.joinToString(",") { csv(row[it].orEmpty()) }).append("\n")
        }

        applications.forEach { app ->
            val row = mapOf(
                "record_type" to "application",
                "scan_time" to formatDate(scannedAt),
                "app_name" to app.label,
                "package_name" to app.packageName,
                "version" to app.version,
                "installer" to app.installer,
                "is_system" to app.isSystem.toString(),
                "package_metadata_state" to app.packageMetadataState.name,
                "special_access_evidence_available" to app.specialAccessStateAvailable.toString(),
                "unclassified_permissions" to app.unclassifiedPermissions.joinToString(" | "),
                "permissions_with_unknown_grant_state" to app.permissionsWithUnknownGrantState.joinToString(" | "),
                "signals" to app.signals.joinToString(" | "),
                "target_sdk" to app.targetSdk?.toString().orEmpty(),
                "category" to app.categoryLabel.orEmpty(),
                "signature_sha256" to app.signingCertificateSha256.orEmpty(),
                "first_install" to formatDate(app.firstInstallTime),
                "last_update" to formatDate(app.lastUpdateTime),
                "last_used" to formatDate(app.lastUsedAt),
                "active_special_access" to app.specialAccessReasons.joinToString(" | "),
                "requested_special_access" to app.requestedSpecialAccess.joinToString(" | "),
                "granted_sensitive" to app.grantedSensitive.joinToString(" | "),
                "requested_sensitive" to app.requestedSensitive.joinToString(" | ")
            )
            output.append(headers.joinToString(",") { csv(row[it].orEmpty()) }).append("\n")
        }
        return output.toString()
    }

    fun formatJson(
        applications: List<AppAudit>,
        scannedAt: Long?,
        deviceSecurity: DeviceSecuritySnapshot? = null
    ): String {
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
              "permissionsWithUnknownGrantState": ${jsonArray(app.permissionsWithUnknownGrantState)},
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
          "incompleteEvidenceCount": ${applications.count { it.hasIncompleteEvidence }},
          "deviceSecurity": ${deviceSecurityJson(deviceSecurity)},
          "applications": [
        ${rows.prependIndent("    ")}
          ],
          "disclaimer": "Indicators only; not a malware verdict."
        }
        """.trimIndent()
    }

    private fun appendDeviceSecuritySection(
        report: StringBuilder,
        snapshot: DeviceSecuritySnapshot?
    ) {
        report.append("مؤشرات أمان نظام Android\\n")
        if (snapshot == null) {
            report.append("لقطة أمان النظام غير محفوظة ضمن هذا الفحص.\\n\\n")
            return
        }
        report.append("إصدار Android: ").append(deviceEvidenceLabel(snapshot.androidRelease)).append("\\n")
        report.append("مستوى API: ").append(deviceEvidenceLabel(snapshot.apiLevel)).append("\\n")
        report.append("مستوى تصحيح الأمان: ").append(deviceEvidenceLabel(snapshot.securityPatch)).append("\\n")
        report.append("قفل الشاشة الآمن: ").append(deviceEvidenceLabel(snapshot.secureScreenLock)).append("\\n")
        report.append("تشفير التخزين: ").append(deviceEvidenceLabel(snapshot.storageEncryption)).append("\\n")
        report.append("حالة ADB: ").append(deviceEvidenceLabel(snapshot.adbEnabled)).append("\\n")
        report.append("خيارات المطوّر: ").append(deviceEvidenceLabel(snapshot.developerOptionsEnabled)).append("\\n")
        report.append("وقت قراءة مؤشرات النظام: ").append(formatDate(snapshot.collectedAt)).append("\\n\\n")
    }

    private fun deviceEvidenceLabel(evidence: DeviceSecurityEvidence): String =
        when (evidence.availability) {
            EvidenceAvailability.UNAVAILABLE -> "غير متاح"
            EvidenceAvailability.UNSUPPORTED -> "غير مدعوم"
            EvidenceAvailability.ACCESS_DENIED -> "رفض Android الوصول"
            EvidenceAvailability.RESTRICTED_BY_ANDROID -> "Android يحجب القراءة عن التطبيقات العادية"
            EvidenceAvailability.ERROR -> "تعذرت القراءة"
            EvidenceAvailability.AVAILABLE -> when (evidence.value) {
                "enabled" -> "مفعّل"
                "disabled" -> "غير مفعّل"
                "active" -> "التشفير نشط"
                "active_per_user" -> "التشفير نشط ومفتاحه مرتبط بالمستخدم أو الملف الشخصي"
                "active_default_key" -> "التشفير نشط لكن المفتاح الافتراضي غير محمي ببيانات اعتماد المستخدم"
                "inactive" -> "غير نشط"
                "activating" -> "جارٍ التفعيل"
                else -> evidence.value ?: "غير متاح"
            }
        }

    private fun deviceSecurityCsvFields(snapshot: DeviceSecuritySnapshot): Map<String, String> =
        mapOf(
            "android_release" to snapshot.androidRelease.value.orEmpty(),
            "android_release_availability" to snapshot.androidRelease.availability.name,
            "api_level" to snapshot.apiLevel.value.orEmpty(),
            "api_level_availability" to snapshot.apiLevel.availability.name,
            "security_patch" to snapshot.securityPatch.value.orEmpty(),
            "security_patch_availability" to snapshot.securityPatch.availability.name,
            "secure_screen_lock" to snapshot.secureScreenLock.value.orEmpty(),
            "secure_screen_lock_availability" to snapshot.secureScreenLock.availability.name,
            "storage_encryption" to snapshot.storageEncryption.value.orEmpty(),
            "storage_encryption_availability" to snapshot.storageEncryption.availability.name,
            "adb_enabled" to snapshot.adbEnabled.value.orEmpty(),
            "adb_enabled_availability" to snapshot.adbEnabled.availability.name,
            "developer_options_enabled" to snapshot.developerOptionsEnabled.value.orEmpty(),
            "developer_options_enabled_availability" to snapshot.developerOptionsEnabled.availability.name
        )

    private fun deviceSecurityJson(snapshot: DeviceSecuritySnapshot?): String {
        if (snapshot == null) return "null"
        return """
            {
              "androidRelease": ${jsonEvidence(snapshot.androidRelease)},
              "apiLevel": ${jsonEvidence(snapshot.apiLevel)},
              "securityPatch": ${jsonEvidence(snapshot.securityPatch)},
              "secureScreenLock": ${jsonEvidence(snapshot.secureScreenLock)},
              "storageEncryption": ${jsonEvidence(snapshot.storageEncryption)},
              "adbEnabled": ${jsonEvidence(snapshot.adbEnabled)},
              "developerOptionsEnabled": ${jsonEvidence(snapshot.developerOptionsEnabled)},
              "collectedAt": ${snapshot.collectedAt}
            }
        """.trimIndent()
    }

    private fun jsonEvidence(evidence: DeviceSecurityEvidence): String =
        """{"value": ${evidence.value?.let(::jsonString) ?: "null"}, "availability": ${jsonString(evidence.availability.name)}}"""

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

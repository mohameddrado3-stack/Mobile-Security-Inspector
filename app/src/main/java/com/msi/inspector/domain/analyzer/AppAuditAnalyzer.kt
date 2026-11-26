package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.CollectedAppEvidence
import com.msi.inspector.domain.model.CollectedDeviceEvidence
import com.msi.inspector.domain.model.PackageMetadataState
import com.msi.inspector.domain.model.PermissionProtection
import com.msi.inspector.domain.model.ScanSnapshot
import java.util.Locale

/**
 * Pure transformation from normalized evidence to user-facing audit findings.
 * No Android framework or persistence APIs are used here.
 */
object AppAuditAnalyzer {
    private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L

    fun analyze(evidence: CollectedDeviceEvidence): ScanSnapshot {
        val applications = evidence.applications
            .map { analyzeApplication(it, evidence.completedAt) }
            .sortedWith(
                compareByDescending<AppAudit> { it.specialAccessReasons.isNotEmpty() }
                    .thenByDescending { it.hasIncompleteEvidence }
                    .thenByDescending { it.requestedSpecialAccess.isNotEmpty() }
                    .thenByDescending { it.grantedSensitive.size }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
            )
        return ScanSnapshot(
            applications = applications,
            vpnActive = evidence.vpnActive,
            completedAt = evidence.completedAt
        )
    }

    private fun analyzeApplication(
        evidence: CollectedAppEvidence,
        scannedAt: Long
    ): AppAudit {
        val requested = evidence.permissions
        val grantedSensitive = requested
            .filter { it.granted && it.protection == PermissionProtection.DANGEROUS }
            .map { it.name }
        val requestedSensitive = requested
            .filter { it.protection == PermissionProtection.DANGEROUS }
            .map { it.name }
        val unclassified = requested
            .filter { it.protection == PermissionProtection.UNKNOWN }
            .map { it.name }
        val categories = grantedSensitive.mapNotNull(PermissionAnalyzer::categoryFor).distinct()
        val specialAccessStateAvailable =
            evidence.accessibilityEnabled != null && evidence.deviceAdminEnabled != null
        val specialReasons = buildList {
            if (evidence.accessibilityEnabled == true) {
                add("خدمة إمكانية الوصول مفعّلة لهذا التطبيق.")
            }
            if (evidence.deviceAdminEnabled == true) {
                add("هذا التطبيق مفعّل كمسؤول جهاز.")
            }
        }
        val unusedForAtLeastThirtyDays = evidence.lastUsedAt?.let {
            scannedAt - it >= THIRTY_DAYS_MILLIS
        } == true

        val signals = buildList {
            addAll(specialReasons)
            if (!specialAccessStateAvailable) {
                add("تعذّر التحقق من بعض حالات الوصول الخاص؛ غياب المؤشر لا يعني أن الحالة غير مفعّلة.")
            }
            if (evidence.packageMetadataState != PackageMetadataState.AVAILABLE) {
                add(
                    when (evidence.packageMetadataState) {
                        PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE ->
                            "تعذرت قراءة بيانات الحزمة؛ قد تكون غير متاحة أو تغيّرت أثناء الفحص."
                        PackageMetadataState.ACCESS_DENIED ->
                            "رفض Android الوصول إلى بيانات هذه الحزمة."
                        PackageMetadataState.ERROR ->
                            "حدث خطأ أثناء قراءة بيانات هذه الحزمة."
                        PackageMetadataState.AVAILABLE -> ""
                    }
                )
            }
            if (unclassified.isNotEmpty()) {
                add("تعذر تحديد مستوى حساسية ${unclassified.size} صلاحية؛ لم تُحتسب كصلاحيات عادية أو حساسة.")
            }
            if (evidence.requestedSpecialAccess.isNotEmpty()) {
                add("التطبيق يطلب إعدادات وصول خاصة في ملفه، لكن حالة تفعيلها لا يمكن تأكيدها من هذا الفحص.")
            }
            if (categories.isNotEmpty()) {
                add(
                    "صلاحيات Android الحساسة الممنوحة: " +
                        categories.joinToString("، ") +
                        ". قد تكون مطلوبة لوظيفة التطبيق ولا تعني أنه ضار."
                )
            }
            if (requestedSensitive.size > grantedSensitive.size && requestedSensitive.isNotEmpty()) {
                add("هناك صلاحيات حساسة مطلوبة لكنها غير ممنوحة حاليًا.")
            }
            if (unusedForAtLeastThirtyDays && grantedSensitive.isNotEmpty()) {
                add("لدى التطبيق صلاحيات حساسة ممنوحة، ولم يُرصد استخدام له خلال 30 يومًا على الأقل (حسب Usage Access المفعّل اختياريًا).")
            }
            if (isEmpty()) add("لم تظهر المؤشرات المحددة في هذا الفحص.")
        }

        return AppAudit(
            label = evidence.label,
            packageName = evidence.packageName,
            version = evidence.version,
            installer = evidence.installer,
            isSystem = evidence.isSystem,
            grantedSensitive = grantedSensitive,
            requestedSensitive = requestedSensitive,
            requestedSpecialAccess = evidence.requestedSpecialAccess,
            signals = signals.filter(String::isNotBlank),
            specialAccessReasons = specialReasons,
            targetSdk = evidence.targetSdk,
            firstInstallTime = evidence.firstInstallTime,
            lastUpdateTime = evidence.lastUpdateTime,
            signingCertificateSha256 = evidence.signingCertificateSha256,
            categoryLabel = evidence.categoryLabel,
            lastUsedAt = evidence.lastUsedAt,
            packageMetadataAvailable = evidence.packageMetadataState == PackageMetadataState.AVAILABLE,
            packageMetadataState = evidence.packageMetadataState,
            unclassifiedPermissions = unclassified,
            specialAccessStateAvailable = specialAccessStateAvailable
        )
    }
}

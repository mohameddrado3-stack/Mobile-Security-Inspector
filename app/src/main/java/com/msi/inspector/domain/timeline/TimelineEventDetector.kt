package com.msi.inspector.domain.timeline

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanSnapshot

/** Factual local change log. It never classifies apps as safe, malicious, or dangerous. */
object TimelineEventDetector {
    fun detect(previous: ScanSnapshot?, current: ScanSnapshot): List<TimelineEvent> {
        if (previous == null) return listOf(
            TimelineEvent(
                occurredAt = current.completedAt,
                type = TimelineEventType.BASELINE_CREATED,
                packageName = null,
                appLabel = null,
                title = "تم إنشاء خط أساس محلي",
                details = "تم حفظ أول مجموعة مؤشرات للمقارنة مع الفحوصات القادمة."
            )
        )

        val events = mutableListOf<TimelineEvent>()
        val oldApps = previous.applications.associateBy(AppAudit::packageName)
        val newApps = current.applications.associateBy(AppAudit::packageName)

        (newApps.keys - oldApps.keys).sorted().forEach { key ->
            val app = newApps.getValue(key)
            events += event(current, TimelineEventType.APP_ADDED, app,
                "ظهر تطبيق في الفحص",
                "ظهر ${app.label} ضمن التطبيقات التي أعاد Android عرضها في هذا الفحص.")
        }
        (oldApps.keys - newApps.keys).sorted().forEach { key ->
            val app = oldApps.getValue(key)
            events += event(current, TimelineEventType.APP_REMOVED, app,
                "لم يعد التطبيق ظاهرًا",
                "لم يعد ${app.label} ظاهرًا ضمن قائمة Android. لا يثبت ذلك وحده أنه أُزيل.")
        }

        (oldApps.keys intersect newApps.keys).sorted().forEach { key ->
            val old = oldApps.getValue(key)
            val new = newApps.getValue(key)
            // PackageManager may fail to return metadata for a package that changed
            // during scanning. Unknown data must never be interpreted as revoked access.
            if (old.packageMetadataAvailable && new.packageMetadataAvailable) {
                if (old.version != new.version) {
                    events += event(current, TimelineEventType.APP_VERSION_CHANGED, new,
                        "تغيّر إصدار التطبيق",
                        "السابق: ${old.version.ifBlank { "غير متاح" }}؛ الحالي: ${new.version.ifBlank { "غير متاح" }}.")
                }
                val oldSignature = old.signingCertificateSha256
                val newSignature = new.signingCertificateSha256
                if (oldSignature != null && newSignature != null && oldSignature != newSignature) {
                    events += event(current, TimelineEventType.SIGNATURE_CHANGED, new,
                        "تغيّرت بصمة شهادة التوقيع",
                        "اختلفت بصمة SHA-256 المتاحة بين الفحصين. راجع مصدر التطبيق وتفاصيله يدويًا.")
                }
                val addedGranted = (new.grantedSensitive.toSet() - old.grantedSensitive.toSet()).sorted()
                val removedGranted = (old.grantedSensitive.toSet() - new.grantedSensitive.toSet()).sorted()
                if (addedGranted.isNotEmpty()) {
                    events += event(current, TimelineEventType.SENSITIVE_PERMISSION_ADDED, new,
                        "ظهرت صلاحيات حساسة ممنوحة",
                        "الصلاحيات التي ظهرت كممنوحة: ${addedGranted.joinToString()}.")
                }
                if (removedGranted.isNotEmpty()) {
                    events += event(current, TimelineEventType.SENSITIVE_PERMISSION_REMOVED, new,
                        "اختفت صلاحيات حساسة ممنوحة",
                        "الصلاحيات التي لم تعد تظهر كممنوحة: ${removedGranted.joinToString()}.")
                }
            }
            val oldSpecial = old.specialAccessReasons.toSet()
            val newSpecial = new.specialAccessReasons.toSet()
            if (oldSpecial != newSpecial) {
                val added = (newSpecial - oldSpecial).sorted()
                val removed = (oldSpecial - newSpecial).sorted()
                val parts = buildList {
                    if (added.isNotEmpty()) add("ظهرت: ${added.joinToString()}")
                    if (removed.isNotEmpty()) add("لم تعد ظاهرة: ${removed.joinToString()}")
                }
                events += event(current, TimelineEventType.SPECIAL_ACCESS_CHANGED, new,
                    "تغيّر الوصول الخاص النشط", parts.joinToString("؛ ") + ".")
            }
        }

        if (previous.vpnActive != current.vpnActive) {
            events += TimelineEvent(
                occurredAt = current.completedAt,
                type = TimelineEventType.VPN_STATE_CHANGED,
                packageName = null,
                appLabel = null,
                title = "تغيّرت حالة VPN المرصودة",
                details = if (current.vpnActive) "رُصد اتصال VPN نشط وقت الفحص الحالي."
                    else "لم يُرصد اتصال VPN نشط وقت الفحص الحالي."
            )
        }
        return events.take(MAX_EVENTS_PER_SCAN)
    }

    private fun event(
        snapshot: ScanSnapshot, type: TimelineEventType, app: AppAudit,
        title: String, details: String
    ) = TimelineEvent(
        occurredAt = snapshot.completedAt,
        type = type,
        packageName = app.packageName,
        appLabel = app.label,
        title = title,
        details = details
    )

    private const val MAX_EVENTS_PER_SCAN = 500
}

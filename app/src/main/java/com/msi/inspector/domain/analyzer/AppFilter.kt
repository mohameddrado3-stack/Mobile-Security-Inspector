package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit
import java.util.Locale

enum class AppFilter(val title: String) {
    ALL("الكل"),
    USER("التطبيقات المثبتة"),
    SYSTEM("تطبيقات النظام"),
    ACTIVE_SPECIAL("وصول خاص نشط"),
    REQUESTED_SPECIAL("طلبات وصول خاص"),
    SENSITIVE("صلاحيات حساسة"),
    INCOMPLETE("بيانات غير مكتملة"),
    TRUSTED("موثوق لدي"),
    IGNORED("متجاهل")
}

fun filterApplications(
    applications: List<AppAudit>,
    query: String,
    filter: AppFilter,
    trustedPackages: Set<String> = emptySet(),
    ignoredPackages: Set<String> = emptySet()
): List<AppAudit> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return applications.filter { app ->
        val matchesFilter = when (filter) {
            AppFilter.ALL -> true
            AppFilter.USER -> !app.isSystem
            AppFilter.SYSTEM -> app.isSystem
            AppFilter.ACTIVE_SPECIAL -> app.specialAccessReasons.isNotEmpty()
            AppFilter.REQUESTED_SPECIAL -> app.requestedSpecialAccess.isNotEmpty()
            AppFilter.SENSITIVE -> app.grantedSensitive.isNotEmpty()
            AppFilter.INCOMPLETE -> app.hasIncompleteEvidence
            AppFilter.TRUSTED -> app.packageName in trustedPackages
            AppFilter.IGNORED -> app.packageName in ignoredPackages
        }
        val matchesQuery = normalizedQuery.isEmpty() ||
            app.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
        matchesFilter && matchesQuery
    }
}

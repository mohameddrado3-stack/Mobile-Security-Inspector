package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit
import java.util.Locale

enum class ApplicationSortOrder(val title: String) {
    INDICATORS("الأكثر مؤشرات"),
    MOST_RECENTLY_INSTALLED("الأحدث تثبيتًا"),
    NAME("الاسم")
}

fun sortApplications(
    applications: List<AppAudit>,
    order: ApplicationSortOrder
): List<AppAudit> = when (order) {
    ApplicationSortOrder.INDICATORS -> applications.sortedWith(
        compareByDescending<AppAudit> { it.specialAccessReasons.isNotEmpty() }
            .thenByDescending { it.requestedSpecialAccess.isNotEmpty() }
            .thenByDescending { it.grantedSensitive.size }
            .thenBy { it.label.lowercase(Locale.ROOT) }
    )
    ApplicationSortOrder.MOST_RECENTLY_INSTALLED -> applications.sortedWith(
        compareByDescending<AppAudit> { it.firstInstallTime ?: Long.MIN_VALUE }
            .thenBy { it.label.lowercase(Locale.ROOT) }
    )
    ApplicationSortOrder.NAME -> applications.sortedBy { it.label.lowercase(Locale.ROOT) }
}

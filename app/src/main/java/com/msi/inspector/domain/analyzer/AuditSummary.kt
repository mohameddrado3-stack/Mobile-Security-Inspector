package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit

data class AuditSummary(
    val totalApplications: Int,
    val systemApplications: Int,
    val userApplications: Int,
    val activeSpecialAccess: Int,
    val requestedSpecialAccess: Int,
    val applicationsWithSensitivePermissions: Int,
    val specialAccessEvidenceUnavailable: Boolean = false,
    val applicationsWithIncompleteEvidence: Int = 0
) {
    companion object {
        fun from(applications: List<AppAudit>) = AuditSummary(
            totalApplications = applications.size,
            systemApplications = applications.count { it.isSystem },
            userApplications = applications.count { !it.isSystem },
            activeSpecialAccess = applications.count { it.needsReview },
            requestedSpecialAccess = applications.count { it.requestedSpecialAccess.isNotEmpty() },
            applicationsWithSensitivePermissions = applications.count { it.grantedSensitive.isNotEmpty() },
            specialAccessEvidenceUnavailable = applications.any { !it.specialAccessStateAvailable },
            applicationsWithIncompleteEvidence = applications.count { it.hasIncompleteEvidence }
        )
    }
}

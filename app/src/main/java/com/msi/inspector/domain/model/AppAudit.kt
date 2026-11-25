package com.msi.inspector.domain.model

data class AppAudit(
    val label: String,
    val packageName: String,
    val version: String,
    val installer: String,
    val isSystem: Boolean,
    val grantedSensitive: List<String>,
    val requestedSensitive: List<String>,
    val requestedSpecialAccess: List<String>,
    val signals: List<String>,
    val specialAccessReasons: List<String>,
    val targetSdk: Int? = null,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val signingCertificateSha256: String? = null,
    val categoryLabel: String? = null,
    val lastUsedAt: Long? = null
) {
    val needsReview: Boolean
        get() = specialAccessReasons.isNotEmpty()

    val status: AuditStatus
        get() = when {
            specialAccessReasons.isNotEmpty() -> AuditStatus.REVIEW
            requestedSpecialAccess.isNotEmpty() -> AuditStatus.REQUESTED
            grantedSensitive.isNotEmpty() -> AuditStatus.SENSITIVE
            else -> AuditStatus.INFORMATIONAL
        }
}

enum class AuditStatus {
    REVIEW,
    REQUESTED,
    SENSITIVE,
    INFORMATIONAL
}

data class ScanSnapshot(
    val applications: List<AppAudit>,
    val vpnActive: Boolean,
    val completedAt: Long
)

data class ScanHistoryEntry(
    val id: Long,
    val scannedAt: Long,
    val totalApplications: Int,
    val activeSpecialAccess: Int,
    val requestedSpecialAccess: Int,
    val sensitivePermissions: Int,
    val vpnActive: Boolean,
    val applicationsJson: String = "[]"
)

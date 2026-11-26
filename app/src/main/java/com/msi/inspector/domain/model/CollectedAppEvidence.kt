package com.msi.inspector.domain.model

enum class PermissionProtection {
    DANGEROUS,
    NON_DANGEROUS,
    UNKNOWN
}

data class CollectedPermissionEvidence(
    val name: String,
    val granted: Boolean,
    val protection: PermissionProtection,
    val grantStateKnown: Boolean = true
)

data class CollectedAppEvidence(
    val label: String,
    val packageName: String,
    val version: String,
    val installer: String,
    val isSystem: Boolean,
    val permissions: List<CollectedPermissionEvidence>,
    val requestedSpecialAccess: List<String>,
    val accessibilityEnabled: Boolean?,
    val deviceAdminEnabled: Boolean?,
    val lastUsedAt: Long?,
    val categoryLabel: String?,
    val targetSdk: Int?,
    val firstInstallTime: Long?,
    val lastUpdateTime: Long?,
    val signingCertificateSha256: String?,
    val packageMetadataState: PackageMetadataState
)

data class CollectedDeviceEvidence(
    val applications: List<CollectedAppEvidence>,
    val vpnActive: Boolean?,
    val completedAt: Long
)

package com.msi.inspector.domain.model

enum class EvidenceAvailability {
    AVAILABLE,
    UNSUPPORTED,
    UNAVAILABLE,
    ACCESS_DENIED,
    ERROR
}

data class DeviceSecurityEvidence(
    val value: String?,
    val availability: EvidenceAvailability
)

data class DeviceSecuritySnapshot(
    val androidRelease: DeviceSecurityEvidence,
    val apiLevel: DeviceSecurityEvidence,
    val securityPatch: DeviceSecurityEvidence,
    val secureScreenLock: DeviceSecurityEvidence,
    val storageEncryption: DeviceSecurityEvidence,
    val adbEnabled: DeviceSecurityEvidence,
    val developerOptionsEnabled: DeviceSecurityEvidence,
    val collectedAt: Long
)

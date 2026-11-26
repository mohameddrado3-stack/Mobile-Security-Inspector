package com.msi.inspector.domain.timeline

data class TimelineEvent(
    val id: Long = 0L,
    val occurredAt: Long,
    val type: TimelineEventType,
    val packageName: String?,
    val appLabel: String?,
    val title: String,
    val details: String
)

enum class TimelineEventType {
    BASELINE_CREATED, APP_ADDED, APP_REMOVED, APP_VERSION_CHANGED,
    SIGNATURE_CHANGED, SENSITIVE_PERMISSION_ADDED, SENSITIVE_PERMISSION_REMOVED,
    SPECIAL_ACCESS_CHANGED, VPN_STATE_CHANGED, DEVICE_SECURITY_CHANGED
}

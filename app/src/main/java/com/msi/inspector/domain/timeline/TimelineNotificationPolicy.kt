package com.msi.inspector.domain.timeline

object TimelineNotificationPolicy {
    fun changeCount(events: List<TimelineEvent>): Int =
        events.count { it.type != TimelineEventType.BASELINE_CREATED }
}

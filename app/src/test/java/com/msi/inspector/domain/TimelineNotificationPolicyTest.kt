package com.msi.inspector.domain

import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventType
import com.msi.inspector.domain.timeline.TimelineNotificationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineNotificationPolicyTest {
    @Test
    fun baselineAloneDoesNotCreateChangeNotification() {
        val events = listOf(event(TimelineEventType.BASELINE_CREATED))
        assertEquals(0, TimelineNotificationPolicy.changeCount(events))
    }

    @Test
    fun baselineIsExcludedWhenRealChangesArePresent() {
        val events = listOf(
            event(TimelineEventType.BASELINE_CREATED),
            event(TimelineEventType.APP_ADDED),
            event(TimelineEventType.DEVICE_SECURITY_CHANGED)
        )
        assertEquals(2, TimelineNotificationPolicy.changeCount(events))
    }

    @Test
    fun emptyEventListDoesNotCreateChangeNotification() {
        assertEquals(0, TimelineNotificationPolicy.changeCount(emptyList()))
    }

    private fun event(type: TimelineEventType) = TimelineEvent(
        occurredAt = 1_000L,
        type = type,
        packageName = null,
        appLabel = null,
        title = "حدث",
        details = "تفاصيل"
    )
}

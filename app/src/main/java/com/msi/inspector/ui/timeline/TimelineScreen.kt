package com.msi.inspector.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiKeyValueRow
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.components.MsiStatusChip
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.domain.timeline.TimelineEventType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(events: List<TimelineEvent>) {
    val todayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayEvents = events.filter { it.occurredAt >= todayStart }
    val appChanges = todayEvents.count { it.packageName != null }
    val accessChanges = todayEvents.count {
        it.type == TimelineEventType.SENSITIVE_PERMISSION_ADDED ||
            it.type == TimelineEventType.SENSITIVE_PERMISSION_REMOVED ||
            it.type == TimelineEventType.SPECIAL_ACCESS_CHANGED
    }
    val deviceSecurityChanges = todayEvents.count {
        it.type == TimelineEventType.DEVICE_SECURITY_CHANGED
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "ما تغيّر بين الفحوصات",
            "أحداث محلية مستنتجة من مقارنة نتائج الفحص، وليست مراقبة لكل حركة داخل التطبيقات."
        )

        MsiCard {
            Text("ملخص اليوم", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyMetric("كل الأحداث", todayEvents.size.toString(), Modifier.weight(1f))
                DailyMetric("التطبيقات", appChanges.toString(), Modifier.weight(1f))
                DailyMetric("الصلاحيات", accessChanges.toString(), Modifier.weight(1f))
            }
            MsiKeyValueRow("تغيّرات أمان Android", deviceSecurityChanges.toString())
            Text(
                "الأعداد تصف الفروق التي ظهرت بين نتائج الفحص اليوم فقط. قد لا تظهر تغيّرات حدثت بين فحصين.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("سجل الأحداث", style = MaterialTheme.typography.titleMedium)
            MsiStatusChip(
                text = "${events.size} حدث محفوظ",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (events.isEmpty()) {
            MsiCard {
                Text("لا توجد أحداث بعد", style = MaterialTheme.typography.titleMedium)
                Text(
                    "أجرِ فحصًا لإنشاء خط أساس محلي. ستظهر هنا الفروق التي يرصدها الفحص التالي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    TimelineEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun DailyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelineEvent) {
    val category = eventCategory(event.type)
    val accent = when (event.type) {
        TimelineEventType.SIGNATURE_CHANGED,
        TimelineEventType.DEVICE_SECURITY_CHANGED -> MaterialTheme.colorScheme.tertiary
        TimelineEventType.SENSITIVE_PERMISSION_ADDED,
        TimelineEventType.SPECIAL_ACCESS_CHANGED -> MaterialTheme.colorScheme.secondary
        TimelineEventType.BASELINE_CREATED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MsiStatusChip(text = category, color = accent)
                Text(
                    formatEventDate(event.occurredAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall
            )
            event.appLabel?.let { label ->
                Text(
                    label + event.packageName?.let { " • $it" }.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                event.details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun eventCategory(type: TimelineEventType): String = when (type) {
    TimelineEventType.BASELINE_CREATED -> "خط أساس"
    TimelineEventType.APP_ADDED,
    TimelineEventType.APP_REMOVED,
    TimelineEventType.APP_VERSION_CHANGED,
    TimelineEventType.SIGNATURE_CHANGED -> "تطبيقات"
    TimelineEventType.SENSITIVE_PERMISSION_ADDED,
    TimelineEventType.SENSITIVE_PERMISSION_REMOVED,
    TimelineEventType.SPECIAL_ACCESS_CHANGED -> "صلاحيات ووصول"
    TimelineEventType.VPN_STATE_CHANGED -> "اتصال الشبكة"
    TimelineEventType.DEVICE_SECURITY_CHANGED -> "إعدادات الجهاز"
}

private fun formatEventDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(value))

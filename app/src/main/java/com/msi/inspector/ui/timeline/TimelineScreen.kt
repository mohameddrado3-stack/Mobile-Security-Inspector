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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiKeyValueRow
import com.msi.inspector.design.components.MsiSectionHeading
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading("ما تغيّر بين الفحوصات", "سجل محلي يوضح الفروق بين نتائج الفحص، دون إصدار أحكام أمنية.")
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("الملخص اليومي", style = MaterialTheme.typography.titleMedium)
                MsiKeyValueRow("أحداث مرصودة اليوم", todayEvents.size.toString())
                MsiKeyValueRow("تغيّرات مرتبطة بالتطبيقات", appChanges.toString())
                MsiKeyValueRow("تغيّرات الصلاحيات والوصول", accessChanges.toString())
                MsiKeyValueRow("تغيّرات أمان Android", deviceSecurityChanges.toString())
                Text(
                    "الأعداد تصف فروقًا بين نتائج الفحص فقط، ولا تعني أن تطبيقًا ضار أو آمن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            }
        }
        if (events.isEmpty()) {
            Text("لا توجد أحداث بعد. أجرِ فحصًا جديدًا لإنشاء خط أساس محلي.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(event.title, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall)
                                Text(formatEventDate(event.occurredAt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            event.appLabel?.let {
                                Text("$it • ${event.packageName.orEmpty()}",
                                    color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                            Text(event.details, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatEventDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(value))

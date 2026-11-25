package com.msi.inspector.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.MsiColors
import com.msi.inspector.domain.model.ScanHistoryEntry
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoryScreen(
    history: List<ScanHistoryEntry>,
    onClearHistory: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val selected = history.filter { it.id in selectedIds }.take(2)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading("سجل الفحوصات", "يحفظ آخر 50 فحصًا محليًا على الجهاز.")
        if (history.size >= 2) {
            MsiSectionHeading("اتجاه المؤشرات")
            HistoryTrend(history = history)
            Text(
                "اختر فحصين للمقارنة التفصيلية. السجلات القديمة التي لا تحتوي على لقطة تطبيقات ستعرض مقارنة الأعداد فقط.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        OutlinedButton(
            onClick = { confirmClear = true },
            enabled = history.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("مسح سجل الفحوصات") }

        if (selected.size == 2) {
            HistoryComparisonCard(selected[0], selected[1])
        }
        if (history.isEmpty()) {
            Text(
                "لا يوجد سجل حتى الآن. أجرِ فحصًا لحفظ أول نتيجة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                itemsIndexed(history, key = { _, item -> item.id }) { index, entry ->
                    val olderEntry = history.getOrNull(index + 1)
                    val isSelected = entry.id in selectedIds
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(formatDate(entry.scannedAt), style = MaterialTheme.typography.titleSmall)
                                    Text("الفحص ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (selectedIds.size == 2) selectedIds.removeAt(0)
                                            selectedIds.add(entry.id)
                                        } else {
                                            selectedIds.remove(entry.id)
                                        }
                                    }
                                )
                            }
                            Text("التطبيقات الظاهرة: ${entry.totalApplications}", fontSize = 12.sp)
                            Text("وصول خاص نشط: ${entry.activeSpecialAccess}", fontSize = 12.sp)
                            Text("طلبات وصول خاص: ${entry.requestedSpecialAccess}", fontSize = 12.sp)
                            Text("تطبيقات بصلاحيات حساسة: ${entry.sensitivePermissions}", fontSize = 12.sp)
                            Text(
                                if (entry.vpnActive) "● VPN نشط وقت الفحص"
                                else "○ VPN غير نشط أو غير متاح",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            if (entry.applicationsJson == "[]") {
                                Text(
                                    "هذا السجل لا يحتوي على تفاصيل التطبيقات؛ تم حفظ الأعداد فقط في إصدار أقدم.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            if (olderEntry != null) {
                                val delta = entry.totalApplications - olderEntry.totalApplications
                                Text(
                                    "مقارنة بالفحص الأقدم: ${signed(delta)} تطبيقًا",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح سجل الفحوصات؟") },
            text = { Text("سيتم حذف ملخصات ولقطات الفحوصات المحفوظة على هذا الجهاز.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    selectedIds.clear()
                    onClearHistory()
                }) { Text("مسح") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun HistoryTrend(history: List<ScanHistoryEntry>) {
    val points = history.take(50).reversed()
    val maximum = (points.maxOfOrNull { it.activeSpecialAccess } ?: 0).coerceAtLeast(1)
    val chartColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier.fillMaxWidth().height(104.dp)
    ) {
        if (points.size == 1) {
            drawCircle(chartColor, radius = 5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
        } else if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { index, entry ->
                val x = size.width * index / (points.size - 1).toFloat()
                val y = size.height - (entry.activeSpecialAccess.toFloat() / maximum) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(
                    color = if (entry.vpnActive) MsiColors.Attention else chartColor,
                    radius = if (entry.vpnActive) 4.dp.toPx() else 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
            drawPath(path, chartColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "● فحص مع VPN نشط",
            color = MsiColors.Attention,
            fontSize = 10.sp
        )
        Text(
            "● فحص دون VPN نشط",
            color = chartColor,
            fontSize = 10.sp
        )
    }
    Text(
        "الخط يوضح عدد التطبيقات التي لديها وصول خاص نشط في كل فحص، وليس مستوى خطورة. تختلف علامة النقطة حسب حالة VPN المسجلة وقت الفحص.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
    )
}

@Composable
private fun HistoryComparisonCard(first: ScanHistoryEntry, second: ScanHistoryEntry) {
    val newest = if (first.scannedAt >= second.scannedAt) first else second
    val oldest = if (newest.id == first.id) second else first
    val diff = compareSnapshots(newest.applicationsJson, oldest.applicationsJson)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("مقارنة الفحصين", style = MaterialTheme.typography.titleMedium)
            Text("الأحدث: ${formatDate(newest.scannedAt)}")
            Text("الأقدم: ${formatDate(oldest.scannedAt)}")
            Text("تطبيقات أضيفت: ${diff.added}")
            Text("تطبيقات لم تعد ظاهرة: ${diff.removed}")
            Text("تطبيقات تغيّرت بياناتها: ${diff.changed}")
            if (!diff.hasDetails) {
                Text(
                    "المقارنة التفصيلية غير متاحة لأحد الفحصين لأن بيانات التطبيقات لم تكن محفوظة وقتها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private data class SnapshotDiff(val added: Int, val removed: Int, val changed: Int, val hasDetails: Boolean)

private fun compareSnapshots(newerJson: String, olderJson: String): SnapshotDiff {
    if (newerJson == "[]" || olderJson == "[]") return SnapshotDiff(0, 0, 0, false)
    return runCatching {
        val newer = parseSnapshot(newerJson)
        val older = parseSnapshot(olderJson)
        val newerKeys = newer.keys
        val olderKeys = older.keys
        val common = newerKeys intersect olderKeys
        val changed = common.count { key -> newer[key] != older[key] }
        SnapshotDiff(
            added = (newerKeys - olderKeys).size,
            removed = (olderKeys - newerKeys).size,
            changed = changed,
            hasDetails = true
        )
    }.getOrDefault(SnapshotDiff(0, 0, 0, false))
}

private fun parseSnapshot(json: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val array = JSONArray(json)
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val packageName = item.optString("packageName")
        result[packageName] = listOf(
            item.optString("version"),
            item.optString("signatureSha256"),
            item.optJSONArray("grantedSensitive")?.toString().orEmpty(),
            item.optJSONArray("requestedSensitive")?.toString().orEmpty(),
            item.optJSONArray("activeSpecialAccess")?.toString().orEmpty()
        ).joinToString("|")
    }
    return result
}

private fun formatDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .apply { timeZone = TimeZone.getDefault() }
        .format(Date(value))

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    else -> value.toString()
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    MsiTheme { HistoryScreen(history = emptyList(), onClearHistory = {}) }
}

package com.msi.inspector.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.model.ScanHistoryEntry
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading("سجل الفحوصات", "يحفظ التطبيق ملخص آخر 50 فحصًا على الجهاز.")
        OutlinedButton(
            onClick = { confirmClear = true },
            enabled = history.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("مسح سجل الفحوصات")
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
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                formatDate(entry.scannedAt),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text("التطبيقات الظاهرة: ${entry.totalApplications}", fontSize = 12.sp)
                            Text("وصول خاص نشط: ${entry.activeSpecialAccess}", fontSize = 12.sp)
                            Text("طلبات وصول خاص: ${entry.requestedSpecialAccess}", fontSize = 12.sp)
                            Text("تطبيقات بصلاحيات حساسة: ${entry.sensitivePermissions}", fontSize = 12.sp)
                            Text(
                                if (entry.vpnActive) "VPN: نشط وقت الفحص" else "VPN: غير نشط أو غير متاح",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
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
            text = { Text("سيتم حذف ملخصات الفحوصات المحفوظة على هذا الجهاز.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) {
                    Text("مسح")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun formatDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("ar", "EG"))
        .apply { timeZone = TimeZone.getTimeZone("Africa/Cairo") }
        .format(Date(value))

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    else -> value.toString()
}

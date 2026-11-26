package com.msi.inspector.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiBrandHeader
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiMetricCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.theme.MsiColors
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.EvidenceAvailability
import com.msi.inspector.ui.state.MsiUiState
import com.msi.inspector.ui.navigation.MsiDestination
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MsiUiState,
    onScan: () -> Unit,
    onNavigate: (MsiDestination) -> Unit,
    onShareReport: () -> Unit,
    onOpenFilter: (AppFilter) -> Unit = {}
) {
    var showIndicatorInfo by remember { mutableStateOf(false) }
    val summary = AuditSummary.from(state.applications)
    val indicatorTitle = when {
        state.applications.isEmpty() && state.lastScanAt == null -> "لم يُفحص بعد"
        state.applications.isEmpty() && state.lastScanAt != null -> "بيانات الفحص غير محمّلة"
        summary.activeSpecialAccess > 0 -> "يحتاج انتباه"
        summary.specialAccessEvidenceUnavailable -> "بيانات غير مكتملة"
        summary.requestedSpecialAccess > 0 -> "يحتاج مراجعة"
        else -> "هادئ"
    }
    val indicatorAccent = when (indicatorTitle) {
        "يحتاج انتباه" -> MsiColors.Attention
        "يحتاج مراجعة", "بيانات غير مكتملة", "بيانات الفحص غير محمّلة" -> MsiColors.Review
        else -> MsiColors.Calm
    }
    val indicatorDescription = when (indicatorTitle) {
        "بيانات غير مكتملة" ->
            "تعذّر التحقق من بعض حالات الوصول الخاص. لا يمكن اعتبار غياب المؤشر دليلًا على عدم التفعيل."
        "بيانات الفحص غير محمّلة" ->
            "يوجد سجل سابق، لكن قائمة التطبيقات الحالية لم تُحمّل بعد. أعد الفحص لعرض بياناتها."
        "هادئ" ->
            "لم تظهر مؤشرات الوصول الخاص المحددة ضمن البيانات التي أمكن قراءتها. هذا ليس تقييمًا شاملًا لأمان الجهاز أو التطبيقات."
        else ->
            "مؤشر إرشادي مبني على إعدادات الوصول الخاصة النشطة والطلبات المعلنة، وليس حكمًا على أن التطبيق ضار أو آمن."
    }
    val previous = state.history.getOrNull(1)
    PullToRefreshBox(
        isRefreshing = state.isScanning,
        onRefresh = onScan,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        MsiBrandHeader()
        MsiCard {
            Text("مؤشر الانتباه", style = MaterialTheme.typography.titleLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    when (indicatorTitle) {
                        "يحتاج انتباه" -> "◆"
                        "يحتاج مراجعة" -> "◈"
                        "هادئ" -> "✓"
                        else -> "○"
                    },
                    color = indicatorAccent,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    indicatorTitle,
                    color = indicatorAccent,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Text(
                indicatorDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            TextButton(onClick = { showIndicatorInfo = true }) {
                Text("كيف حُسب المؤشر؟")
            }
            if (state.isScanning) {
                val progress = if (state.scanTotal > 0) {
                    state.scanProgress.toFloat() / state.scanTotal.toFloat()
                } else {
                    0f
                }
                if (state.scanTotal > 0) {
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "تم فحص ${state.scanProgress.coerceAtMost(state.scanTotal)} من ${state.scanTotal} تطبيقًا",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("جارٍ تجهيز قائمة التطبيقات للفحص")
                }
            }
            Text(
                when {
                    state.isScanning -> "الفحص جارٍ"
                    state.error != null -> "تعذر إكمال آخر فحص"
                    state.applications.isNotEmpty() -> "اكتمل آخر فحص"
                    state.lastScanAt != null -> "يوجد سجل سابق؛ أعد الفحص لتحميل قائمة التطبيقات الحالية."
                    else -> "لم يتم إجراء فحص بعد"
                },
                color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
            )
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
                onClick = onScan,
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    when {
                        state.isScanning -> "جارٍ الفحص"
                        state.applications.isEmpty() && state.lastScanAt != null -> "تحميل قائمة التطبيقات الحالية"
                        state.lastScanAt == null -> "ابدأ الفحص"
                        else -> "إعادة الفحص"
                    }
                )
            }
            state.lastScanAt?.let {
                Text(
                    "آخر فحص: ${formatLocalDate(it)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        MsiSectionHeading("مؤشرات الفحص", "كل بطاقة تفتح التطبيقات المرتبطة بالمؤشر.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MsiMetricCard(
                title = "إجمالي التطبيقات",
                value = summary.totalApplications.toString(),
                detail = "تطبيقات ظهرت للنظام",
                accent = MaterialTheme.colorScheme.primary,
                statusSymbol = "▦",
                onClick = { onOpenFilter(AppFilter.ALL) },
                modifier = Modifier.weight(1f)
            )
            MsiMetricCard(
                title = "وصول خاص نشط",
                value = summary.activeSpecialAccess.toString(),
                detail = "يستحق مراجعة السياق",
                accent = MsiColors.Attention,
                statusSymbol = "!",
                onClick = { onOpenFilter(AppFilter.ACTIVE_SPECIAL) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MsiMetricCard(
                title = "صلاحيات حساسة",
                value = summary.applicationsWithSensitivePermissions.toString(),
                detail = "لدى التطبيقات",
                accent = Color(0xFF85B8FF),
                statusSymbol = "◈",
                onClick = { onOpenFilter(AppFilter.SENSITIVE) },
                modifier = Modifier.weight(1f)
            )
            MsiMetricCard(
                title = "طلبات وصول خاص",
                value = summary.requestedSpecialAccess.toString(),
                detail = "لا تعني أنها مفعّلة",
                accent = MsiColors.Review,
                statusSymbol = "○",
                onClick = { onOpenFilter(AppFilter.REQUESTED_SPECIAL) },
                modifier = Modifier.weight(1f)
            )
        }
        MsiCard {
            MsiSectionHeading("تغيّر منذ آخر فحص")
            if (previous == null) {
                Text(
                    "سيظهر التغيّر بعد إتمام فحص ثانٍ. السجلات القديمة قبل هذا الإصدار لا تحتوي على تفاصيل التطبيقات.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                DeltaLine("عدد التطبيقات", summary.totalApplications - previous.totalApplications)
                DeltaLine("وصول خاص نشط", summary.activeSpecialAccess - previous.activeSpecialAccess)
                DeltaLine("طلبات وصول خاص", summary.requestedSpecialAccess - previous.requestedSpecialAccess)
                DeltaLine(
                    "تطبيقات بصلاحيات حساسة",
                    summary.applicationsWithSensitivePermissions - previous.sensitivePermissions
                )
            }
        }
        MsiCard {
            MsiSectionHeading("أمان نظام Android", "لقطة مؤشرات النظام من آخر فحص محفوظ.")
            val device = state.deviceSecurity
            if (device == null) {
                Text(
                    "لا توجد لقطة أمان للنظام في سجل الفحص بعد. ابدأ فحصًا لقراءة البيانات المتاحة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                Text("إصدار Android: ${displayDeviceEvidence(device.androidRelease)}", fontSize = 12.sp)
                Text("مستوى تصحيح الأمان: ${displayDeviceEvidence(device.securityPatch)}", fontSize = 12.sp)
                Text("قفل الشاشة: ${displayDeviceEvidence(device.secureScreenLock)}", fontSize = 12.sp)
                Text("تشفير التخزين: ${displayDeviceEvidence(device.storageEncryption)}", fontSize = 12.sp)
                Text("حالة ADB: ${displayDeviceEvidence(device.adbEnabled)}", fontSize = 12.sp)
                Text(
                    "آخر قراءة: ${formatLocalDate(device.collectedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.TOOLS) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تفاصيل أمان النظام") }
        }
        MsiCard {
            MsiSectionHeading("حالة اتصال الجهاز")
            Text(
                when (state.vpnActive) {
                    true -> "● تم رصد VPN على الشبكة الافتراضية الحالية التي يراها MSI."
                    false -> "○ رُصد عدم وجود اتصال VPN نشط وقت الفحص."
                    null -> "؟ تعذّر تحديد حالة VPN من بيانات Android المتاحة."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                "تعكس المعلومة الشبكة الافتراضية التي يراها MSI فقط؛ ولا تحدد التطبيق الذي أنشأ VPN أو مسارات اتصال كل التطبيقات.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        MsiCard {
            MsiSectionHeading("آخر الأحداث")
            val latestEvents = state.timelineEvents.take(3)
            if (latestEvents.isEmpty()) {
                Text(
                    "لا توجد أحداث مسجلة بعد. ستظهر تغيّرات الأدلة بين الفحوصات هنا.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                latestEvents.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(event.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(event.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatLocalDate(event.occurredAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.HISTORY) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("عرض سجل الأحداث") }
        }
        MsiSectionHeading("الوصول السريع")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.APPLICATIONS) },
                modifier = Modifier.weight(1f)
            ) { Text("التطبيقات") }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.PERMISSIONS) },
                modifier = Modifier.weight(1f)
            ) { Text("الصلاحيات") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.HISTORY) },
                modifier = Modifier.weight(1f)
            ) { Text("سجل الفحص") }
            OutlinedButton(
                onClick = onShareReport,
                modifier = Modifier.weight(1f)
            ) { Text("تصدير التقرير") }
        }
        Text(
            "مبدأ MSI: إظهار الأدلة المتاحة وحدودها، وترك قرارات تغيير الصلاحيات للمستخدم داخل إعدادات Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
    }

    if (showIndicatorInfo) {
        AlertDialog(
            onDismissRequest = { showIndicatorInfo = false },
            title = { Text("طريقة حساب مؤشر الانتباه") },
            text = {
                Text(
                    "يحتاج انتباه: عند رصد خدمة إمكانية وصول مفعّلة أو مسؤول جهاز نشط. بيانات غير مكتملة: عندما يتعذر التحقق من بعض مصادر الوصول الخاص، ولا يجوز افتراض أنها غير مفعّلة. يحتاج مراجعة: عند وجود طلب إعداد وصول خاص دون إثبات تفعيله. هادئ: لم تظهر المؤشرات المحددة ضمن البيانات التي أمكن قراءتها فقط. هذا المؤشر ليس شهادة أمان شاملة."
                )
            },
            confirmButton = {
                TextButton(onClick = { showIndicatorInfo = false }) { Text("فهمت") }
            }
        )
    }
}

@Composable
private fun DeltaLine(label: String, delta: Int) {
    val sign = if (delta > 0) "+" else ""
    Text(
        "$label: $sign$delta",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

private fun displayDeviceEvidence(evidence: DeviceSecurityEvidence): String =
    when (evidence.availability) {
        EvidenceAvailability.UNAVAILABLE -> "غير متاح"
        EvidenceAvailability.UNSUPPORTED -> "غير مدعوم"
        EvidenceAvailability.ACCESS_DENIED -> "رفض Android الوصول"
        EvidenceAvailability.ERROR -> "تعذرت القراءة"
        EvidenceAvailability.AVAILABLE -> when (evidence.value) {
            "enabled" -> "مفعّل"
            "disabled" -> "غير مفعّل"
            "active" -> "التشفير نشط"
            "active_per_user" -> "التشفير نشط والمفتاح مرتبط بالمستخدم أو الملف الشخصي"
            "active_default_key" -> "التشفير نشط لكن المفتاح الافتراضي غير محمي ببيانات اعتماد المستخدم"
            "inactive" -> "غير نشط"
            "activating" -> "جارٍ التفعيل"
            else -> evidence.value ?: "غير متاح"
        }
    }

private fun formatLocalDate(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .apply { timeZone = TimeZone.getDefault() }
        .format(Date(time))

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    MsiTheme {
        DashboardScreen(
            state = MsiUiState(),
            onScan = {},
            onNavigate = {},
            onShareReport = {},
            onOpenFilter = {}
        )
    }
}

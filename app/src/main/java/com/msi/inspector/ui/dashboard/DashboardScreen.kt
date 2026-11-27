package com.msi.inspector.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msi.inspector.R
import com.msi.inspector.design.components.MsiActionTile
import com.msi.inspector.ui.dashboard.BatteryUsageCard
import com.msi.inspector.design.components.MsiBrandHeader
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiDivider
import com.msi.inspector.design.components.MsiIconBadge
import com.msi.inspector.design.components.MsiKeyValueRow
import com.msi.inspector.design.components.MsiMetricCard
import com.msi.inspector.design.components.MsiMetricRow
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.components.MsiStatusChip
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.msiSemanticColors
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

private val HeroShape = RoundedCornerShape(24.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

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
    val semantic = msiSemanticColors()
    val summary = AuditSummary.from(state.applications)
    val indicatorTitle = when {
        state.applications.isEmpty() && state.lastScanAt == null -> "لم يُفحص بعد"
        state.applications.isEmpty() && state.lastScanAt != null -> "بيانات الفحص غير محمّلة"
        summary.activeSpecialAccess > 0 -> "يحتاج انتباه"
        summary.applicationsWithIncompleteEvidence > 0 -> "بيانات غير مكتملة"
        summary.requestedSpecialAccess > 0 -> "يحتاج مراجعة"
        else -> "هادئ"
    }
    val indicatorAccent = when (indicatorTitle) {
        "يحتاج انتباه" -> semantic.attention
        "يحتاج مراجعة", "بيانات غير مكتملة", "بيانات الفحص غير محمّلة" -> semantic.review
        else -> semantic.calm
    }
    val indicatorIcon = when (indicatorTitle) {
        "يحتاج انتباه" -> R.drawable.ic_msi_shield_alert
        "يحتاج مراجعة" -> R.drawable.ic_msi_shield_eye
        "هادئ" -> R.drawable.ic_msi_shield_check
        "بيانات غير مكتملة", "بيانات الفحص غير محمّلة" -> R.drawable.ic_msi_info
        else -> R.drawable.ic_msi_shield
    }
    val indicatorDescription = when (indicatorTitle) {
        "بيانات غير مكتملة" ->
            "توجد تطبيقات لم يتمكن Android من إتاحة كل بياناتها أو تعذّر تصنيف بعض صلاحياتها. افتح الفلتر لمراجعتها؛ نقص البيانات ليس دليلًا على الضرر ولا على السلامة."
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MsiBrandHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HeroShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            indicatorAccent.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(1.dp, indicatorAccent.copy(alpha = 0.30f), HeroShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MsiIconBadge(iconRes = indicatorIcon, tint = indicatorAccent, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "مؤشر الانتباه",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        indicatorTitle,
                        color = indicatorAccent,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Text(
                indicatorDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.isScanning) {
                val progress = if (state.scanTotal > 0) {
                    state.scanProgress.toFloat() / state.scanTotal.toFloat()
                } else {
                    0f
                }
                if (state.scanTotal > 0) {
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "تم فحص ${state.scanProgress.coerceAtMost(state.scanTotal)} من ${state.scanTotal} تطبيقًا",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "جارٍ تجهيز قائمة التطبيقات للفحص",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusColor = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Icon(
                    painter = painterResource(
                        when {
                            state.isScanning -> R.drawable.ic_msi_refresh
                            state.error != null -> R.drawable.ic_msi_warning
                            state.applications.isNotEmpty() -> R.drawable.ic_msi_check
                            else -> R.drawable.ic_msi_info
                        }
                    ),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    when {
                        state.isScanning -> "الفحص جارٍ"
                        state.error != null -> "تعذر إكمال آخر فحص"
                        state.applications.isNotEmpty() -> "اكتمل آخر فحص"
                        state.lastScanAt != null -> "يوجد سجل سابق؛ أعد الفحص لتحميل قائمة التطبيقات الحالية."
                        else -> "لم يتم إجراء فحص بعد"
                    },
                    color = statusColor,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.error != null) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onScan,
                enabled = !state.isScanning,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_msi_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.isScanning -> "جارٍ الفحص"
                        state.applications.isEmpty() && state.lastScanAt != null -> "تحميل قائمة التطبيقات الحالية"
                        state.lastScanAt == null -> "ابدأ الفحص"
                        else -> "إعادة الفحص"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showIndicatorInfo = true }) {
                    Text("كيف حُسب المؤشر؟")
                }
                state.lastScanAt?.let {
                    Text(
                        "آخر فحص: ${formatLocalDate(it)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        BatteryUsageCard(
            summary = state.batteryUsage,
            isLoading = state.isLoadingBatteryUsage,
            isTrackingEnabled = state.batteryTrackingEnabled
        )

        MsiSectionHeading("مؤشرات الفحص", "كل بطاقة تفتح التطبيقات المرتبطة بالمؤشر.")
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsiMetricCard(
                title = "إجمالي التطبيقات",
                value = summary.totalApplications.toString(),
                detail = "تطبيقات ظهرت للنظام",
                accent = semantic.calm,
                iconRes = R.drawable.ic_msi_apps,
                onClick = { onOpenFilter(AppFilter.ALL) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MsiMetricCard(
                title = "وصول خاص نشط",
                value = summary.activeSpecialAccess.toString(),
                detail = "يستحق مراجعة السياق",
                accent = semantic.attention,
                iconRes = R.drawable.ic_msi_key,
                onClick = { onOpenFilter(AppFilter.ACTIVE_SPECIAL) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsiMetricCard(
                title = "صلاحيات حساسة",
                value = summary.applicationsWithSensitivePermissions.toString(),
                detail = "لدى التطبيقات",
                accent = semantic.info,
                iconRes = R.drawable.ic_msi_eye,
                onClick = { onOpenFilter(AppFilter.SENSITIVE) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MsiMetricCard(
                title = "طلبات وصول خاص",
                value = summary.requestedSpecialAccess.toString(),
                detail = "لا تعني أنها مفعّلة",
                accent = semantic.review,
                iconRes = R.drawable.ic_msi_permissions,
                onClick = { onOpenFilter(AppFilter.REQUESTED_SPECIAL) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        MsiMetricRow(
            title = "بيانات فحص غير مكتملة",
            value = summary.applicationsWithIncompleteEvidence.toString(),
            detail = "تطبيقات تحتاج مراجعة حدود القراءة",
            accent = semantic.review,
            iconRes = R.drawable.ic_msi_warning,
            onClick = { onOpenFilter(AppFilter.INCOMPLETE) }
        )

        MsiCard {
            MsiSectionHeading("تغيّر منذ آخر فحص")
            if (previous == null) {
                Text(
                    "سيظهر التغيّر بعد إتمام فحص ثانٍ. السجلات القديمة قبل هذا الإصدار لا تحتوي على تفاصيل التطبيقات.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                DeltaLine("عدد التطبيقات", summary.totalApplications - previous.totalApplications, neutral = true)
                MsiDivider()
                DeltaLine("وصول خاص نشط", summary.activeSpecialAccess - previous.activeSpecialAccess)
                MsiDivider()
                DeltaLine("طلبات وصول خاص", summary.requestedSpecialAccess - previous.requestedSpecialAccess)
                MsiDivider()
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
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column {
                    MsiKeyValueRow("إصدار Android", displayDeviceEvidence(device.androidRelease))
                    MsiDivider()
                    MsiKeyValueRow("مستوى تصحيح الأمان", displayDeviceEvidence(device.securityPatch))
                    MsiDivider()
                    MsiKeyValueRow("قفل الشاشة", displayDeviceEvidence(device.secureScreenLock))
                    MsiDivider()
                    MsiKeyValueRow("تشفير التخزين", displayDeviceEvidence(device.storageEncryption))
                    MsiDivider()
                    MsiKeyValueRow("حالة ADB", displayDeviceEvidence(device.adbEnabled))
                }
                Text(
                    "آخر قراءة: ${formatLocalDate(device.collectedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.TOOLS) },
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("تفاصيل أمان النظام") }
        }

        MsiCard {
            MsiSectionHeading("آخر الأحداث")
            val latestEvents = state.timelineEvents.take(3)
            if (latestEvents.isEmpty()) {
                Text(
                    "لا توجد أحداث مسجلة بعد. ستظهر تغيّرات الأدلة بين الفحوصات هنا.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                latestEvents.forEachIndexed { index, event ->
                    if (index > 0) MsiDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            event.title,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            event.details,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            formatLocalDate(event.occurredAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.HISTORY) },
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("عرض سجل الأحداث") }
        }

        MsiCard {
            MsiSectionHeading("حالة اتصال الجهاز")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                MsiIconBadge(
                    iconRes = R.drawable.ic_msi_network,
                    tint = when (state.vpnActive) {
                        true -> semantic.info
                        false -> MaterialTheme.colorScheme.onSurfaceVariant
                        null -> semantic.review
                    },
                    size = 40.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when (state.vpnActive) {
                            true -> "تم رصد VPN على الشبكة الافتراضية الحالية التي يراها MSI."
                            false -> "رُصد عدم وجود اتصال VPN نشط وقت الفحص."
                            null -> "تعذّر تحديد حالة VPN من بيانات Android المتاحة."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "تعكس المعلومة الشبكة الافتراضية التي يراها MSI فقط؛ ولا تحدد التطبيق الذي أنشأ VPN أو مسارات اتصال كل التطبيقات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        MsiSectionHeading("الوصول السريع")
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsiActionTile(
                title = "التطبيقات",
                iconRes = R.drawable.ic_msi_apps,
                onClick = { onNavigate(MsiDestination.APPLICATIONS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MsiActionTile(
                title = "الصلاحيات",
                iconRes = R.drawable.ic_msi_permissions,
                onClick = { onNavigate(MsiDestination.PERMISSIONS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsiActionTile(
                title = "سجل الفحص",
                iconRes = R.drawable.ic_msi_history,
                onClick = { onNavigate(MsiDestination.HISTORY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MsiActionTile(
                title = "تصدير التقرير",
                iconRes = R.drawable.ic_msi_export,
                onClick = onShareReport,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Text(
            "مبدأ MSI: إظهار الأدلة المتاحة وحدودها، وترك قرارات تغيير الصلاحيات للمستخدم داخل إعدادات Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp)
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
private fun DeltaLine(label: String, delta: Int, neutral: Boolean = false) {
    val semantic = msiSemanticColors()
    val color: Color = when {
        delta == 0 || neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> semantic.review
        else -> semantic.calm
    }
    val sign = if (delta > 0) "+" else ""
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        MsiStatusChip(text = "\u200E$sign$delta", color = color)
    }
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

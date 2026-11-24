package com.msi.inspector.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiBrandHeader
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiMetricCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.ui.MsiUiState
import com.msi.inspector.ui.navigation.MsiDestination

@Composable
fun DashboardScreen(
    state: MsiUiState,
    onScan: () -> Unit,
    onNavigate: (MsiDestination) -> Unit,
    onShareReport: () -> Unit
) {
    val summary = AuditSummary.from(state.applications)
    val scanProgress by animateFloatAsState(
        targetValue = if (state.isScanning) 0.78f else if (state.applications.isNotEmpty()) 1f else 0f,
        label = "scan-progress"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        MsiBrandHeader()
        MsiCard {
            Text("مركز الأمان", style = MaterialTheme.typography.titleLarge)
            Text(
                "راجع مؤشرات التطبيقات المثبتة وصلاحياتها من مكان واحد. التحليل محلي على جهازك.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            LinearProgressIndicator(
                progress = scanProgress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                when {
                    state.isScanning -> "يتم فحص التطبيقات..."
                    state.error != null -> "تعذر إكمال آخر فحص"
                    state.applications.isNotEmpty() -> "اكتمل آخر فحص بنجاح"
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
        }
        MsiSectionHeading("مؤشرات الفحص", "معلومات مرصودة وليست حكمًا بأن التطبيق ضار.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MsiMetricCard(
                title = "إجمالي التطبيقات",
                value = summary.totalApplications.toString(),
                detail = "تطبيقات ظهرت للنظام",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MsiMetricCard(
                title = "تحتاج مراجعة",
                value = summary.activeSpecialAccess.toString(),
                detail = "إعداد وصول خاص نشط",
                accent = Color(0xFFFFC278),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MsiMetricCard(
                title = "صلاحيات حساسة",
                value = summary.applicationsWithSensitivePermissions.toString(),
                detail = "تطبيقات لديها صلاحيات ممنوحة",
                accent = Color(0xFF85B8FF),
                modifier = Modifier.weight(1f)
            )
            MsiMetricCard(
                title = "طلبات وصول خاص",
                value = summary.requestedSpecialAccess.toString(),
                detail = "لا تعني أن الإعداد مفعّل",
                accent = Color(0xFFFFC278),
                modifier = Modifier.weight(1f)
            )
        }
        MsiCard {
            MsiSectionHeading("حالة اتصال الجهاز")
            Text(
                if (state.vpnActive) "تم رصد اتصال VPN على مستوى الجهاز."
                else "لم يتم رصد اتصال VPN نشط أو أن الحالة غير متاحة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                "هذه المعلومة لا تحدد التطبيق الذي أنشأ اتصال VPN.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        MsiSectionHeading("الوصول السريع")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.APPLICATIONS) },
                modifier = Modifier.weight(1f)
            ) {
                Text("التطبيقات")
            }
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.PERMISSIONS) },
                modifier = Modifier.weight(1f)
            ) {
                Text("الصلاحيات")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onNavigate(MsiDestination.HISTORY) },
                modifier = Modifier.weight(1f)
            ) {
                Text("سجل الفحص")
            }
            OutlinedButton(
                onClick = onShareReport,
                modifier = Modifier.weight(1f)
            ) {
                Text("تصدير التقرير")
            }
        }
        Text(
            "مبدأ MSI: إظهار الأدلة المتاحة وحدودها، وترك قرارات تغيير الصلاحيات للمستخدم داخل إعدادات Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

package com.msi.inspector.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.BuildConfig
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability

@Composable
fun ToolsScreen(
    appLockEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onSecuritySettings: () -> Unit,
    onGeneralSettings: () -> Unit,
    onDeveloperSettings: () -> Unit = onGeneralSettings,
    themeMode: MsiThemeMode,
    useDynamicColor: Boolean,
    onThemeChanged: (MsiThemeMode, Boolean) -> Unit,
    periodicScanEnabled: Boolean = false,
    onPeriodicScanChanged: (Boolean) -> Unit = {},
    activeMonitoringEnabled: Boolean = false,
    onActiveMonitoringChanged: (Boolean) -> Unit = {},
    batteryTrackingEnabled: Boolean = true,
    onBatteryTrackingChanged: (Boolean) -> Unit = {},
    scheduledHour: Int = 9,
    scheduledMinute: Int = 0,
    onScheduledTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    usageAccessOptIn: Boolean = false,
    usageAccessGranted: Boolean = false,
    onUsageAccessChanged: (Boolean) -> Unit = {},
    deviceSecurity: DeviceSecuritySnapshot? = null,
    isLoadingDeviceSecurity: Boolean = false,
    onRefreshDeviceSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        MsiSectionHeading("مركز التحكم", "إعدادات واضحة ومؤشرات قراءة فقط — أنت تتحكم في كل ميزة.")
        UpdateCenterCard(currentVersion = BuildConfig.VERSION_NAME)
        ActiveMonitoringCard(
            enabled = activeMonitoringEnabled,
            onEnabledChanged = onActiveMonitoringChanged
        )
        MsiCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.msi.inspector.design.components.MsiIconBadge(
                    iconRes = com.msi.inspector.R.drawable.ic_msi_refresh,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 42.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("قياس البطارية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "محاولة تسجيل عينة كل ساعة تقريبًا",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = batteryTrackingEnabled,
                    onCheckedChange = onBatteryTrackingChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                com.msi.inspector.design.components.MsiStatusChip(
                    text = if (batteryTrackingEnabled) "التسجيل مفعّل" else "التسجيل متوقف",
                    color = if (batteryTrackingEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "محلي • دون إنترنت",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "يقارن مستوى بطارية الهاتف بين عينتين. لا يحدد استهلاك تطبيق بعينه، وقد يؤخر Android موعد العينة وفق إدارة الطاقة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        MsiCard {
            Text("صحة الجهاز", style = MaterialTheme.typography.titleMedium)
            val security = deviceSecurity
            if (security == null) {
                Text(
                    if (isLoadingDeviceSecurity) "جارٍ قراءة مؤشرات أمان Android..."
                    else "لم تُقرأ مؤشرات أمان النظام بعد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                DeviceSecurityRow("إصدار Android", security.androidRelease)
                DeviceSecurityRow("مستوى API", security.apiLevel)
                DeviceSecurityRow("مستوى تصحيح الأمان", security.securityPatch)
                DeviceSecurityRow("قفل شاشة آمن", security.secureScreenLock, onSecuritySettings)
                DeviceSecurityRow("تشفير التخزين", security.storageEncryption, onSecuritySettings)
                DeviceSecurityRow("تصحيح USB (ADB)", security.adbEnabled, onDeveloperSettings)
                DeviceSecurityRow("خيارات المطوّر", security.developerOptionsEnabled, onDeveloperSettings)
                Text(
                    "آخر قراءة: " + java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT
                    ).format(java.util.Date(security.collectedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onRefreshDeviceSecurity,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingDeviceSecurity
            ) {
                Text(if (isLoadingDeviceSecurity) "جارٍ التحديث..." else "تحديث مؤشرات النظام")
            }
            Text(
                "قراءة فقط. لا يغيّر MSI أي إعداد في الجهاز.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            OutlinedButton(onClick = onSecuritySettings, modifier = Modifier.fillMaxWidth()) {
                Text("فتح إعدادات الأمان")
            }
            OutlinedButton(onClick = onGeneralSettings, modifier = Modifier.fillMaxWidth()) {
                Text("فتح إعدادات المطوّر")
            }
        }
        MsiCard {
            Text("مظهر التطبيق", style = MaterialTheme.typography.titleMedium)
            Text("اختيار الثيم", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeButton("داكن", MsiThemeMode.DARK, themeMode, useDynamicColor, onThemeChanged)
                ThemeButton("فاتح", MsiThemeMode.LIGHT, themeMode, useDynamicColor, onThemeChanged)
                ThemeButton("AMOLED", MsiThemeMode.AMOLED, themeMode, useDynamicColor, onThemeChanged)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ألوان النظام الديناميكية", fontSize = 13.sp)
                    Text(
                        "متاحة على Android 12 وما بعده.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = useDynamicColor,
                    onCheckedChange = { onThemeChanged(themeMode, it) },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
            }
        }
        MsiCard {
            Text("الفحص الدوري الاختياري", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("فحص محلي يوميًا في الوقت الذي تحدده", fontSize = 13.sp)
                    Text(
                        "لا يحتاج اتصال شبكة. يرسل إشعارًا فقط إذا تغيّرت مؤشرات محددة.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = periodicScanEnabled, onCheckedChange = onPeriodicScanChanged)
            }
            Text(
                "الموعد الحالي: %02d:%02d".format(scheduledHour, scheduledMinute),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onScheduledTimeChanged(hour, minute) },
                        scheduledHour,
                        scheduledMinute,
                        true
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) { Text("تحديد موعد الفحص") }
            Text(
                "يطلب Android إذن الإشعارات عند التفعيل فقط. قد يؤخر Android التنفيذ بسبب البطارية أو قيود النظام؛ الموعد ليس ضمانًا بالدقيقة. إيقاف الخيار يلغي الجدولة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        MsiCard {
            Text("استخدام بيانات الاستخدام (اختياري)", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("مراجعة التطبيقات غير المستخدمة", fontSize = 13.sp)
                    Text(
                        if (usageAccessGranted && usageAccessOptIn) "الوصول الخاص مفعّل بإذن المستخدم."
                        else "مغلق افتراضيًا. يفتح إعدادات Usage Access فقط بعد تفعيل الخيار.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = usageAccessOptIn, onCheckedChange = onUsageAccessChanged)
            }
            Text(
                "عند تفعيله، يقرأ MSI آخر وقت استخدام معروف محليًا ليشير إلى تطبيقات لديها صلاحيات حساسة ولم تُستخدم منذ 30 يومًا. قد تكون البيانات غير مكتملة، ولا يُسحب أي إذن تلقائيًا.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        MsiCard {
            Text("التحكم في صلاحيات التطبيقات", style = MaterialTheme.typography.titleMedium)
            Text(
                "يعرض MSI المؤشرات المتاحة فقط. Android هو المسؤول عن منح صلاحيات التطبيقات الأخرى أو سحبها، وأنت من يؤكد أي تغيير.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Button(onClick = onManageApps, modifier = Modifier.fillMaxWidth()) {
                Text("إدارة التطبيقات المثبتة")
            }
            OutlinedButton(onClick = onAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text("إعدادات إمكانية الوصول")
            }
            OutlinedButton(onClick = onSecuritySettings, modifier = Modifier.fillMaxWidth()) {
                Text("إعدادات الأمان ومسؤولو الجهاز")
            }
            OutlinedButton(onClick = onGeneralSettings, modifier = Modifier.fillMaxWidth()) {
                Text("إعدادات Android")
            }
        }
        MsiCard {
            Text("حماية تطبيق MSI", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("طلب تحقق عند فتح MSI", fontSize = 13.sp)
                    Text(
                        "يستخدم البصمة القوية أو قفل شاشة الجهاز إذا كان مدعومًا.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = onToggleAppLock
                )
            }
            Text(
                "يمكن إيقاف الميزة من داخل MSI بعد فتحه. لا يخزن التطبيق رمز PIN أو بيانات البصمة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        MsiCard {
            Text("خصوصية MSI", style = MaterialTheme.typography.titleMedium)
            Text(
                "قائمة التطبيقات ونتائج الفحص وسجل الملخصات تحفظ محليًا. لا يرفع التطبيق هذه البيانات إلى خادم. مشاركة التقرير لا تحدث إلا بعد اختيارك ذلك.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                "التغييرات التي تتطلب صلاحيات نظام خاصة تُفتح في إعدادات Android ولا ينفذها MSI بصمت.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    selectedMode: MsiThemeMode,
    currentMode: MsiThemeMode,
    dynamic: Boolean,
    onThemeChanged: (MsiThemeMode, Boolean) -> Unit
) {
    if (selectedMode == currentMode) {
        Button(onClick = { onThemeChanged(selectedMode, dynamic) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onThemeChanged(selectedMode, dynamic) }) { Text(label) }
    }
}

@Composable
private fun HealthRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "$value  ›",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun deviceEvidenceLabel(evidence: DeviceSecurityEvidence): String =
    when (evidence.availability) {
        EvidenceAvailability.UNAVAILABLE -> "غير متاح"
        EvidenceAvailability.UNSUPPORTED -> "غير مدعوم"
        EvidenceAvailability.ACCESS_DENIED -> "رفض Android الوصول"
        EvidenceAvailability.ERROR -> "تعذرت القراءة"
        EvidenceAvailability.AVAILABLE -> when (evidence.value) {
            "enabled" -> "مفعّل"
            "disabled" -> "غير مفعّل"
            "active" -> "مشفّر ونشط"
            "inactive" -> "غير نشط"
            "activating" -> "جارٍ التفعيل"
            "unsupported" -> "غير مدعوم"
            else -> evidence.value ?: "غير متاح"
        }
    }

@Composable
private fun DeviceSecurityRow(
    label: String,
    evidence: DeviceSecurityEvidence,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "${deviceEvidenceLabel(evidence)}  ›",
            fontSize = 12.sp,
            color = when (evidence.availability) {
                EvidenceAvailability.AVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.tertiary
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsScreenPreview() {
    MsiTheme {
        ToolsScreen(
            appLockEnabled = false,
            onToggleAppLock = {},
            onManageApps = {},
            onAccessibilitySettings = {},
            onSecuritySettings = {},
            onGeneralSettings = {},
            themeMode = MsiThemeMode.DARK,
            useDynamicColor = false,
            onThemeChanged = { _, _ -> }
        )
    }
}

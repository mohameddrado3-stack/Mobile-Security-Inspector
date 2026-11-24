package com.msi.inspector.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiSectionHeading

@Composable
fun ToolsScreen(
    appLockEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onSecuritySettings: () -> Unit,
    onGeneralSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        MsiSectionHeading("مركز التحكم", "اختصارات تفتح صفحات إعدادات Android الرسمية.")
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
                        fontSize = 10.sp
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
                fontSize = 10.sp
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
                fontSize = 11.sp
            )
        }
    }
}

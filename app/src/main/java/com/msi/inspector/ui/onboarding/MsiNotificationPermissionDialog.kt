package com.msi.inspector.ui.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MsiNotificationPermissionDialog(
    featureName: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إشعارات MSI مطلوبة") },
        text = {
            Text(
                "لتشغيل $featureName يحتاج MSI إلى إذن الإشعارات. تم رفض الإذن أو لم يعد Android يعرض نافذة الطلب. افتح إعدادات إشعارات MSI وفعّل السماح بالإشعارات، ثم ارجع للتطبيق. لن يتم تشغيل الميزة قبل تأكيد التفعيل.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("فتح إعدادات الإشعارات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ليس الآن")
            }
        }
    )
}

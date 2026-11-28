package com.msi.inspector.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MsiPermissionSetupDialog(
    step: Int,
    notificationsGranted: Boolean,
    usageAccessGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val currentStep = step.coerceIn(0, 2)
    val notificationStatus = if (notificationsGranted) "مفعّل" else "لم يُفعّل"
    val usageStatus = if (usageAccessGranted) "مفعّل" else "اختياري وغير مفعّل"

    AlertDialog(
        onDismissRequest = onFinish,
        title = {
            Text(
                when (currentStep) {
                    0 -> "إعداد صلاحيات MSI • 1/2"
                    1 -> "إعداد صلاحيات MSI • 2/2"
                    else -> "اكتمل إعداد الصلاحيات"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "MSI يطلب فقط الوصول المرتبط بميزة واضحة. تقدر تتخطى أي خطوة وتستخدم الوظائف الأساسية."
                )
                PermissionStatusRow("الإشعارات", notificationStatus)
                PermissionStatusRow("بيانات الاستخدام", usageStatus)
                PermissionStatusRow("رؤية التطبيقات المثبتة", "معلنة في MSI")
                PermissionStatusRow("المراقبة الخلفية", "تعمل عند التفعيل فقط")
                Text(
                    "قراءة قائمة التطبيقات تُعلن للنظام في ملف MSI ولا تحتاج نافذة سماح عادية. المراقبة الخلفية اختيارية ولا تبدأ إلا بعد تشغيلها من مركز التحكم."
                )
                when (currentStep) {
                    0 -> Text(
                        "الإشعارات مطلوبة لتنبيهات المراقبة والفحص الدوري. على Android 13 وما بعده يمكنك تفعيلها من نافذة النظام."
                    )
                    1 -> Text(
                        "بيانات الاستخدام اختيارية، وتساعد MSI على عرض مؤشرات التطبيقات غير المستخدمة. افتح إعداد Usage Access وفعّله بنفسك، ثم ارجع إلى التطبيق."
                    )
                    else -> Text(
                        "يمكنك تعديل هذه الخيارات لاحقًا من مركز التحكم. لا يفعّل MSI صلاحية خاصة أو يغيّر إعدادًا نيابةً عنك."
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (currentStep) {
                        0 -> if (notificationsGranted) onSkip() else onRequestNotifications()
                        1 -> if (usageAccessGranted) onSkip() else onOpenUsageAccess()
                        else -> onFinish()
                    }
                }
            ) {
                Text(
                    when (currentStep) {
                        0 -> if (notificationsGranted) "التالي" else "تفعيل الإشعارات"
                        1 -> if (usageAccessGranted) "التالي" else "فتح الإعدادات"
                        else -> "إنهاء"
                    }
                )
            }
        },
        dismissButton = {
            if (currentStep < 2) {
                TextButton(onClick = onSkip) { Text("تخطي") }
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            status,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

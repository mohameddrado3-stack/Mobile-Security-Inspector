package com.msi.inspector.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.domain.analyzer.AppPermissionGuidance
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.PackageMetadataState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ApplicationDetailsDialog(
    application: AppAudit,
    onDismiss: () -> Unit,
    isTrusted: Boolean,
    isIgnored: Boolean,
    onMarkTrusted: () -> Unit,
    onMarkIgnored: () -> Unit,
    onClearLabel: () -> Unit,
    onCopyPackage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLaunch: () -> Unit,
    onOpenStore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(application.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("اسم الحزمة: ${application.packageName}", fontSize = 11.sp)
                Text("الإصدار: ${application.version}", fontSize = 12.sp)
                Text("حالة قراءة بيانات الحزمة", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(
                    when (application.packageMetadataState) {
                        PackageMetadataState.AVAILABLE -> "متاحة من Android"
                        PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE -> "غير موجودة أو غير ظاهرة لهذا التطبيق"
                        PackageMetadataState.ACCESS_DENIED -> "رفض Android الوصول إليها"
                        PackageMetadataState.ERROR -> "حدث خطأ أثناء قراءتها"
                    },
                    fontSize = 11.sp,
                    color = if (application.packageMetadataAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    "الصلاحيات الحساسة: ${application.grantedSensitive.size} ممنوحة / ${application.requestedSensitive.size} مطلوبة",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("النوع: ${if (application.isSystem) "تطبيق نظام" else "تطبيق مثبت"}", fontSize = 12.sp)
                Text("مصدر التثبيت: ${application.installer}", fontSize = 12.sp)
                Text("تصنيف Android: ${application.categoryLabel ?: "غير محدد من التطبيق"}", fontSize = 12.sp)
                Text("Target SDK: ${application.targetSdk ?: "غير متاح"}", fontSize = 12.sp)
                Text("تاريخ التثبيت: ${formatLocalDate(application.firstInstallTime)}", fontSize = 12.sp)
                Text("آخر تحديث: ${formatLocalDate(application.lastUpdateTime)}", fontSize = 12.sp)
                Text("آخر استخدام معروف: ${formatLocalDate(application.lastUsedAt)}", fontSize = 12.sp)
                Text("SHA-256 لشهادة التوقيع:", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(
                    application.signingCertificateSha256 ?: "غير متاح من Android لهذا التطبيق",
                    fontSize = 10.sp
                )
                Text("التصنيف المحلي", color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        isTrusted -> "أنت وضعت هذا التطبيق في قائمة موثوق لدي. هذا لا يثبت سلامته."
                        isIgnored -> "أنت وضعت هذا التطبيق في قائمة متجاهل لتقليل الضوضاء. بياناته لا تُحذف."
                        else -> "يمكنك وضع تسمية محلية لتسهيل مراجعة القوائم."
                    },
                    fontSize = 11.sp
                )
                Text("ماذا أفعل؟", color = MaterialTheme.colorScheme.primary)
                application.specialAccessReasons.forEach { reason ->
                    Text(reason, fontSize = 11.sp)
                    Text(
                        com.msi.inspector.domain.analyzer.AppPermissionGuidance.explainSpecialAccess(reason),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                (application.grantedSensitive + application.requestedSensitive).distinct().forEach { permission ->
                    Text(permission.substringAfterLast('.'), fontSize = 10.sp)
                    Text(
                        com.msi.inspector.domain.analyzer.AppPermissionGuidance.explain(permission),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Text("ملاحظات الفحص", color = MaterialTheme.colorScheme.primary)
                application.signals.forEach { signal -> Text("• $signal", fontSize = 11.sp) }
                Text("إعدادات وصول خاصة نشطة", color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        !application.specialAccessStateAvailable ->
                            "تعذر التحقق من بعض مصادر الوصول الخاص. لا يمكن اعتبار غياب النتيجة دليلًا على عدم التفعيل."
                        application.specialAccessReasons.isEmpty() ->
                            "لم يتم رصد تفعيل Accessibility أو Device Admin لهذا التطبيق."
                        else -> application.specialAccessReasons.joinToString("\n")
                    },
                    fontSize = 11.sp,
                    color = if (application.specialAccessStateAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.tertiary
                )
                Text("إعدادات خاصة مطلوبة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.requestedSpecialAccess.ifEmpty {
                        listOf("لم تُرصد طلبات الإعدادات الخاصة المعروفة.")
                    }.joinToString("\n"),
                    fontSize = 11.sp
                )
                Text("صلاحيات تعذر تحديد مستوى حمايتها", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.unclassifiedPermissions.ifEmpty {
                        listOf("لا توجد صلاحيات غير مصنفة ضمن البيانات المتاحة.")
                    }.joinToString("\n"),
                    fontSize = 10.sp,
                    color = if (application.unclassifiedPermissions.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.tertiary
                )
                Text("الصلاحيات الحساسة الممنوحة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.grantedSensitive.ifEmpty { listOf("لا توجد ضمن المعلومات المتاحة.") }
                        .joinToString("\n"),
                    fontSize = 10.sp
                )
                Text("الصلاحيات الحساسة المطلوبة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.requestedSensitive.ifEmpty { listOf("لا توجد ضمن المعلومات المتاحة.") }
                        .joinToString("\n"),
                    fontSize = 10.sp
                )
                Text(
                    "وجود صلاحية أو إعداد خاص لا يثبت أن التطبيق ضار. قد تكون الصلاحيات ضرورية لوظيفته.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("إعدادات التطبيق") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onCopyPackage) { Text("نسخ اسم الحزمة") }
                if (!isTrusted) TextButton(onClick = onMarkTrusted) { Text("أضف إلى موثوق لدي") }
                if (!isIgnored) TextButton(onClick = onMarkIgnored) { Text("تجاهل هذا التطبيق") }
                if (isTrusted || isIgnored) TextButton(onClick = onClearLabel) { Text("إلغاء التسمية المحلية") }
                TextButton(onClick = onOpenNotifications) { Text("الإشعارات") }
                TextButton(onClick = onLaunch) { Text("فتح التطبيق") }
                if (application.installer == "Google Play") {
                    TextButton(onClick = onOpenStore) { Text("Google Play") }
                }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

private fun formatLocalDate(value: Long?): String =
    value?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(it))
    } ?: "غير متاح"

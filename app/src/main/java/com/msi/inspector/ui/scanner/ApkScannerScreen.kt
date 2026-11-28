package com.msi.inspector.ui.scanner

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.msi.inspector.data.apk.AndroidApkInspector
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.model.ApkInspectionResult
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ApkScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inspector = remember(context) { AndroidApkInspector(context) }
    var result by remember { mutableStateOf<ApkInspectionResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            result = null
            error = null
            scanning = true
            scope.launch {
                try {
                    result = inspector.inspect(uri)
                } catch (exception: Exception) {
                    error = exception.localizedMessage?.takeIf(String::isNotBlank)
                        ?: "تعذر فحص الملف المحدد."
                } finally {
                    scanning = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "فحص ملف APK",
            "افحص بيانات الملف وصلاحياته قبل أن تقرر فتح مثبّت Android."
        )
        MsiCard {
            Text("فحص محلي قبل التثبيت", style = MaterialTheme.typography.titleMedium)
            Text(
                "يقرأ MSI بيانات Manifest واسم الحزمة وإصدارها ومستوى Android المستهدف وبصمة التوقيع المتاحة. لا يثبت الملف ولا يشغّله أثناء الفحص.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !scanning
            ) {
                Text(if (scanning) "جارٍ فحص الملف..." else "اختيار ملف APK")
            }
            if (scanning) {
                Text(
                    "يتم تحليل الملف محليًا. الملفات الأكبر من 512 ميجابايت غير مدعومة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        result?.let { report ->
            MsiCard {
                Text("نتيجة الفحص", style = MaterialTheme.typography.titleMedium)
                Text(report.appName, style = MaterialTheme.typography.headlineSmall)
                DetailRow("اسم الحزمة", report.packageName)
                DetailRow("الإصدار", report.versionName)
                DetailRow("Android المستهدف", report.targetSdk.toString())
                DetailRow("حجم الملف", formatBytes(report.fileSizeBytes))
                DetailRow("صلاحيات Manifest", report.requestedPermissions.size.toString())
                DetailRow("صلاحيات حساسة", report.dangerousPermissions.size.toString())
                DetailRow("Debuggable", if (report.isDebuggable) "مفعّل" else "غير مفعّل")
                DetailRow(
                    "اتصالات HTTP",
                    if (report.allowsCleartextTraffic) "مسموح بها وفق بيانات الملف" else "غير معلنة كمسموحة"
                )
                DetailRow("SHA-256 للتوقيع", report.signerSha256 ?: "غير متاح")
            }

            MsiCard {
                Text("مؤشرات تستحق المراجعة", style = MaterialTheme.typography.titleMedium)
                report.warnings.forEach { warning ->
                    Text(
                        "• $warning",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "هذه مؤشرات فنية وليست حكمًا بأن الملف خبيث أو آمن. لا يملك MSI حاليًا قاعدة سمعة عالمية أو تحليلًا ساكنًا كاملًا للكود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (report.dangerousPermissions.isNotEmpty()) {
                MsiCard {
                    Text("الصلاحيات الحساسة المطلوبة", style = MaterialTheme.typography.titleMedium)
                    report.dangerousPermissions.forEach { permission ->
                        Text(permission, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (report.unclassifiedPermissions.isNotEmpty()) {
                MsiCard {
                    Text("صلاحيات تعذر تصنيفها", style = MaterialTheme.typography.titleMedium)
                    report.unclassifiedPermissions.forEach { permission ->
                        Text(permission, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            MsiCard {
                Text("الخطوة التالية", style = MaterialTheme.typography.titleMedium)
                Text(
                    "لو قررت المتابعة، سيفتح MSI مثبّت Android الرسمي. راجع تحذيرات النظام ومصدر الملف بنفسك؛ MSI لا يتجاوز فحص النظام أو موافقتك.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        runCatching {
                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                report.cachedFile
                            )
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(contentUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(installIntent)
                        }.onFailure {
                            error = "تعذر فتح مثبّت Android لهذا الملف. يمكنك الرجوع إلى مدير الملفات ومراجعته يدويًا."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("متابعة إلى مثبّت Android")
                }
                OutlinedButton(
                    onClick = {
                        report.cachedFile.delete()
                        result = null
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق التقرير وحذف النسخة المؤقتة")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall
    )
    Text(value, style = MaterialTheme.typography.bodySmall)
}

private fun formatBytes(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return String.format(Locale.ROOT, "%.1f MB", megabytes)
}

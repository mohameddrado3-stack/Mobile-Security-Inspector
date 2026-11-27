package com.msi.inspector.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.sp
import com.msi.inspector.BuildConfig
import com.msi.inspector.data.updates.GitHubReleaseChecker
import com.msi.inspector.data.updates.ReleaseCheckResult
import com.msi.inspector.design.components.MsiCard
import kotlinx.coroutines.launch

@Composable
fun UpdateCenterCard(currentVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("لم يتم التحقق من التحديثات بعد.") }
    var result by remember { mutableStateOf<ReleaseCheckResult?>(null) }

    MsiCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("مركز التحديثات", style = MaterialTheme.typography.titleMedium)
            Text(
                "الإصدار المثبت: $currentVersion",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                if (BuildConfig.DEBUG) "ميزة التحقق الشبكي مفعّلة في نسخة Release فقط؛ نسخة Debug تظل دون اتصال." else "يتصل MSI بـ GitHub فقط عند ضغطك على زر التحقق. لا يتم إرسال بيانات الجهاز أو سجل الفحص، ولا يحدث تنزيل أو تثبيت تلقائي.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Button(
                onClick = {
                    if (checking) return@Button
                    checking = true
                    message = "جارٍ التحقق من أحدث إصدار مستقر..."
                    result = null
                    scope.launch {
                        try {
                            val checked = GitHubReleaseChecker.check(currentVersion)
                            result = checked
                            message = if (checked.updateAvailable) {
                                "يتوفر إصدار أحدث: ${checked.latestVersion}"
                            } else {
                                "أنت تستخدم أحدث إصدار منشور: ${checked.latestVersion}"
                            }
                        } catch (exception: Exception) {
                            message = exception.message ?: "تعذر التحقق من التحديثات."
                        } finally {
                            checking = false
                        }
                    }
                },
                enabled = !checking && !BuildConfig.DEBUG,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        checking -> "جارٍ التحقق..."
                        BuildConfig.DEBUG -> "التحقق متاح في نسخة Release"
                        else -> "التحقق من التحديثات"
                    }
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            result?.let { release ->
                if (release.releaseNotes.isNotBlank()) {
                    Text("ملاحظات الإصدار", style = MaterialTheme.typography.titleSmall)
                    Text(
                        release.releaseNotes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("فتح صفحة الإصدار الرسمية")
                }
            }
        }
    }
}

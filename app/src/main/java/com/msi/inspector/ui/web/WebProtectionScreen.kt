package com.msi.inspector.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.web.WebLinkAnalysis
import com.msi.inspector.domain.web.WebLinkAnalyzer
import com.msi.inspector.domain.web.WebLinkReviewLevel

@Composable
fun WebProtectionScreen(
    address: String,
    onAddressChanged: (String) -> Unit
) {
    var analysis by remember { mutableStateOf<WebLinkAnalysis?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "حماية الويب",
            "شارك رابطًا من Chrome مع MSI أو الصقه هنا لمراجعة مؤشرات أولية."
        )
        MsiCard {
            Text("فحص الرابط محليًا", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = address,
                onValueChange = {
                    onAddressChanged(it)
                    analysis = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("الرابط") },
                placeholder = { Text("https://example.com") },
                maxLines = 3
            )
            Button(
                onClick = { analysis = WebLinkAnalyzer.analyze(address) },
                modifier = Modifier.fillMaxWidth(),
                enabled = address.isNotBlank()
            ) {
                Text("تحليل الرابط")
            }
        }

        analysis?.let { report ->
            val heading = when (report.level) {
                WebLinkReviewLevel.REVIEW -> "يحتاج إلى مراجعة"
                WebLinkReviewLevel.CAUTION -> "ملاحظات على الاتصال"
                WebLinkReviewLevel.NO_CLEAR_INDICATORS -> "لم تظهر مؤشرات شكلية واضحة"
            }
            val tone = when (report.level) {
                WebLinkReviewLevel.REVIEW -> MaterialTheme.colorScheme.error
                WebLinkReviewLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
                WebLinkReviewLevel.NO_CLEAR_INDICATORS -> MaterialTheme.colorScheme.primary
            }
            MsiCard {
                Text(heading, color = tone, style = MaterialTheme.typography.titleMedium)
                Text("النطاق: ${report.host ?: "غير متاح"}", style = MaterialTheme.typography.bodyMedium)
                report.findings.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        MsiCard {
            Text("حدود الفحص", style = MaterialTheme.typography.titleMedium)
            Text(
                "هذا الفحص يراجع شكل الرابط فقط. لا يتصل بخدمة سمعة نطاقات، ولا يفحص محتوى صفحة HTTPS، ولا يستطيع تأكيد أن الموقع خبيث أو آمن. عند مشاركة رابط من Chrome، تتم مراجعته بعد أن تختار أنت مشاركته مع MSI.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "لم يتم تثبيت إضافة داخل Chrome أو اعتراض التصفح. مشاركة الرابط هي نقطة التكامل المدعومة حاليًا على Android.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

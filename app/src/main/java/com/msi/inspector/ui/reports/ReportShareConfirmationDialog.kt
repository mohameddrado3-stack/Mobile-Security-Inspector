package com.msi.inspector.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ReportShareConfirmationDialog(
    onDismiss: () -> Unit,
    onExport: (ReportFileFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشاركة تقرير الفحص") },
        text = {
            Text(
                "سيحتوي التقرير على تفاصيل التطبيقات ومؤشرات أمان Android المتاحة، مع توضيح المصادر التي يحجبها النظام أو يتعذر قراءتها. لن يُرسل تلقائيًا؛ ستختار بنفسك التطبيق أو الجهة التي تشاركه معها."
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onExport(ReportFileFormat.TXT) }) { Text("تصدير TXT") }
                TextButton(onClick = { onExport(ReportFileFormat.CSV) }) { Text("تصدير CSV") }
                TextButton(onClick = { onExport(ReportFileFormat.JSON) }) { Text("تصدير JSON") }
                TextButton(onClick = { onExport(ReportFileFormat.PDF) }) { Text("تصدير PDF") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

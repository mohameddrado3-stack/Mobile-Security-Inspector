package com.msi.inspector.ui.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MsiOnboardingDialog(
    page: Int,
    onPageChange: (Int) -> Unit,
    onComplete: () -> Unit
) {
    val pages = listOf(
        "MSI يقرأ مؤشرات التطبيقات المثبتة والصلاحيات المتاحة من Android، ويعرض التفاصيل بصورة مفهومة.",
        "MSI لا يتصل بخادم ولا يرفع بيانات الفحص، ولا يمنح أو يسحب صلاحيات تطبيق آخر. أي تغيير يتم من إعدادات Android وبقرارك.",
        "قد يقرأ MSI قائمة التطبيقات المسموح للنظام بعرضها، وبيانات الحزم والصلاحيات وبعض مؤشرات الجهاز. النتائج مؤشرات وليست أحكامًا على سلامة التطبيق."
    )
    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text("مرحبًا بك في MSI — ${page + 1}/3") },
        text = { Text(pages[page.coerceIn(pages.indices)]) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (page < pages.lastIndex) onPageChange(page + 1) else onComplete()
                }
            ) {
                Text(if (page < pages.lastIndex) "التالي" else "ابدأ")
            }
        },
        dismissButton = {
            TextButton(onClick = onComplete) { Text("تخطي") }
        }
    )
}

package com.msi.inspector.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.model.AppAudit

@Composable
fun PermissionsScreen(applications: List<AppAudit>) {
    val categories = PermissionAnalyzer.categories(applications)
    val ungrantedRequests = applications.sumOf { app ->
        (app.requestedSensitive.toSet() - app.grantedSensitive.toSet()).size
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "مركز الصلاحيات",
            "يعرض الصلاحيات الحساسة الممنوحة التي أمكن قراءتها من Android."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("فئات مرصودة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text(categories.size.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("طلبات غير ممنوحة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text(ungrantedRequests.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        if (categories.isEmpty()) {
            Text(
                if (applications.isEmpty()) "ابدأ الفحص لقراءة بيانات الصلاحيات."
                else "لم تُرصد صلاحيات حساسة ممنوحة ضمن المعلومات المتاحة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "موجودة لدى ${category.applicationCount} تطبيقًا",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            category.permissions.forEach { permission ->
                                Text(
                                    permission,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "الصلاحية المعلنة ليست بالضرورة ممنوحة أو مستخدمة. وجود صلاحية حساسة لا يثبت أن التطبيق ضار.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

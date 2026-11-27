package com.msi.inspector.ui.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.model.AppAudit

@Composable
fun PermissionsScreen(
    applications: List<AppAudit>,
    onCategorySelected: (Set<String>) -> Unit = {}
) {
    val categories = PermissionAnalyzer.dashboardCategories(applications)
    var sortBySensitivity by remember { mutableStateOf(false) }
    val sensitivityOrder = listOf(
        "sms", "phone", "contacts", "location", "microphone", "camera",
        "storage", "sensors", "nearby", "media", "calendar", "notifications"
    )
    val displayCategories = if (sortBySensitivity) {
        categories.sortedBy { sensitivityOrder.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }
    } else categories
    val ungrantedRequests = applications.sumOf { app ->
        (app.requestedSensitive.toSet() - app.grantedSensitive.toSet()).size
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "مركز الصلاحيات",
            "الفئات الـ12 المعروفة في هذا الفحص. العدد يميز التطبيقات التي طلبت الفئة والممنوح منها."
        )
        OutlinedButton(
            onClick = { sortBySensitivity = !sortBySensitivity },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sortBySensitivity) "الترتيب حسب الحساسية مفعّل" else "رتّب حسب الحساسية")
        }
        Text(
            "طلبات حساسة غير ممنوحة: $ungrantedRequests",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        if (applications.isEmpty()) {
            Text(
                "ابدأ الفحص لقراءة بيانات الصلاحيات.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 145.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(displayCategories, key = { it.id }) { category ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category.packageNames) }
                            .semantics {
                                contentDescription = "${category.title}. عدد التطبيقات ${category.applicationCount}. الممنوح في ${category.grantedApplicationCount} تطبيقات. افتح التطبيقات المرتبطة."
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(category.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "التطبيقات: ${category.applicationCount}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "ممنوحة لدى: ${category.grantedApplicationCount}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Text(
                                "مطلوبة لدى: ${category.requestedApplicationCount}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        Text(
            "الصلاحية المعلنة ليست بالضرورة ممنوحة أو مستخدمة. وجود صلاحية حساسة لا يثبت أن التطبيق ضار.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    MsiTheme { PermissionsScreen(applications = emptyList()) }
}

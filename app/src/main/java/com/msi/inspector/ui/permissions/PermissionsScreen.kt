package com.msi.inspector.ui.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.components.MsiStatusChip
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.domain.analyzer.PermissionAnalyzer
import com.msi.inspector.domain.model.AppAudit

@Composable
fun PermissionsScreen(
    applications: List<AppAudit>,
    onCategorySelected: (Set<String>) -> Unit = {},
    hasLoadedScanSnapshot: Boolean = false
) {
    val categories = PermissionAnalyzer.dashboardCategories(applications)
    var sortBySensitivity by remember { mutableStateOf(false) }
    val sensitivityOrder = listOf(
        "sms", "phone", "contacts", "location", "microphone", "camera",
        "storage", "sensors", "nearby", "media", "calendar", "notifications"
    )
    val displayCategories = if (sortBySensitivity) {
        categories.sortedBy {
            sensitivityOrder.indexOf(it.id).let { index ->
                if (index < 0) Int.MAX_VALUE else index
            }
        }
    } else {
        categories
    }
    val ungrantedRequests = applications.sumOf { app ->
        (app.requestedSensitive.toSet() - app.grantedSensitive.toSet()).size
    }
    val grantedPermissions = applications.sumOf { it.grantedSensitive.distinct().size }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading(
            "مركز الصلاحيات",
            "اعرف ما تطلبه التطبيقات وما يظهر كممنوح، ثم افتح التطبيقات المرتبطة."
        )

        MsiCard {
            Text("ملخص الصلاحيات", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    label = "تطبيقات مفحوصة",
                    value = applications.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "تصنيفات",
                    value = categories.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "طلبات غير ممنوحة",
                    value = ungrantedRequests.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "عدد الصلاحيات الحساسة التي تظهر كممنوحة: $grantedPermissions",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedButton(
            onClick = { sortBySensitivity = !sortBySensitivity },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sortBySensitivity) "الترتيب حسب الحساسية مفعّل" else "رتّب حسب الحساسية")
        }

        if (applications.isEmpty()) {
            MsiCard {
                Text(
                    if (hasLoadedScanSnapshot) "لم تظهر تطبيقات في آخر فحص"
                    else "لا توجد بيانات صلاحيات بعد",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (hasLoadedScanSnapshot) {
                        "اكتمل الفحص دون ظهور تطبيقات قابلة للعرض؛ لذلك لا توجد صلاحيات يمكن تلخيصها لهذه القراءة."
                    } else {
                        "ابدأ فحص التطبيقات من الشاشة الرئيسية، وستظهر هنا الفئات التي أمكن قراءتها."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (displayCategories.isEmpty()) {
            MsiCard {
                Text("لم تظهر فئات صلاحيات قابلة للعرض", style = MaterialTheme.typography.titleMedium)
                Text(
                    "قد لا تكون بيانات الصلاحيات متاحة لبعض التطبيقات أو لهذا الجهاز.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(displayCategories, key = { it.id }) { category ->
                    val grantRatio = if (category.applicationCount > 0) {
                        (category.grantedApplicationCount.toFloat() / category.applicationCount)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category.packageNames) }
                            .semantics {
                                contentDescription = "${category.title}. عدد التطبيقات ${category.applicationCount}. الممنوح في ${category.grantedApplicationCount} تطبيقات. افتح التطبيقات المرتبطة."
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                category.title,
                                style = MaterialTheme.typography.titleSmall,
                                minLines = 1
                            )
                            Text(
                                category.applicationCount.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "تطبيقات طلبت الفئة",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { grantRatio },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "ممنوحة ${category.grantedApplicationCount}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "مطلوبة ${category.requestedApplicationCount}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            MsiStatusChip(
                                text = "عرض التطبيقات المرتبطة",
                                color = MaterialTheme.colorScheme.secondary
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

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    MsiTheme { PermissionsScreen(applications = emptyList()) }
}

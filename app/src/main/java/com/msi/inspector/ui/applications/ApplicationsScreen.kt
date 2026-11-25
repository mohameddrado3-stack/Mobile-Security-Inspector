package com.msi.inspector.ui.applications

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.analyzer.ApplicationSortOrder
import com.msi.inspector.domain.analyzer.compareApplications
import com.msi.inspector.domain.analyzer.sortApplications
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.AuditStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ApplicationsScreen(
    applications: List<AppAudit>,
    query: String,
    filter: AppFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onSelect: (AppAudit) -> Unit,
    trustedPackages: Set<String> = emptySet(),
    ignoredPackages: Set<String> = emptySet()
) {
    val filters = AppFilter.values().toList()
    var sort by remember { mutableStateOf(ApplicationSortOrder.INDICATORS) }
    var groupBySource by remember { mutableStateOf(false) }
    val selectedForComparison = remember { mutableStateListOf<String>() }
    val selectedApps = applications.filter { it.packageName in selectedForComparison }.take(2)
    val sorted = sortApplications(applications, sort)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading("التطبيقات المثبتة", "بحث بالاسم أو الحزمة، وفرز وتجميع ومقارنة.")
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("بحث عن تطبيق") },
            placeholder = { Text("اسم التطبيق أو package name") }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(item.title) }
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ApplicationSortOrder.values().toList()) { item ->
                FilterChip(
                    selected = sort == item,
                    onClick = { sort = item },
                    label = { Text(item.title) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("تجميع حسب مصدر التثبيت", fontSize = 12.sp)
            Switch(checked = groupBySource, onCheckedChange = { groupBySource = it })
        }
        if (selectedApps.size == 2) {
            ComparisonCard(selectedApps[0], selectedApps[1])
        } else if (selectedForComparison.isNotEmpty()) {
            Text(
                "اختر تطبيقًا آخر لإكمال المقارنة (${selectedForComparison.size}/2).",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
        }
        if (applications.isEmpty()) {
            EmptyApplications()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text(
                        "عدد النتائج: ${applications.size} — حدّد تطبيقين للمقارنة",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (groupBySource) {
                    sorted.groupBy { it.installer }.toSortedMap().forEach { (source, apps) ->
                        item(key = "source-$source") {
                            Text(
                                "مصدر التثبيت: $source",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
                            )
                        }
                        items(apps, key = { it.packageName }) { app ->
                            ApplicationCard(
                                app = app,
                                selectedForComparison = app.packageName in selectedForComparison,
                                isTrusted = app.packageName in trustedPackages,
                                isIgnored = app.packageName in ignoredPackages,
                                onCompareChange = { checked ->
                                    if (checked) {
                                        if (selectedForComparison.size == 2) selectedForComparison.removeAt(0)
                                        selectedForComparison.add(app.packageName)
                                    } else {
                                        selectedForComparison.remove(app.packageName)
                                    }
                                },
                                onClick = { onSelect(app) }
                            )
                        }
                    }
                } else {
                    items(sorted, key = { it.packageName }) { app ->
                        ApplicationCard(
                            app = app,
                            selectedForComparison = app.packageName in selectedForComparison,
                            isTrusted = app.packageName in trustedPackages,
                            isIgnored = app.packageName in ignoredPackages,
                            onCompareChange = { checked ->
                                if (checked) {
                                    if (selectedForComparison.size == 2) selectedForComparison.removeAt(0)
                                    selectedForComparison.add(app.packageName)
                                } else {
                                    selectedForComparison.remove(app.packageName)
                                }
                            },
                            onClick = { onSelect(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(first: AppAudit, second: AppAudit) {
    val comparison = compareApplications(first, second)
    val signatureStatus = when (comparison.signaturesMatch) {
        true -> "بصمتا التوقيع متطابقتان"
        false -> "بصمتا التوقيع مختلفتان"
        null -> "مقارنة التوقيع غير متاحة: بصمة واحدة على الأقل غير متوفرة"
    }
    fun display(values: List<String>): String =
        values.ifEmpty { listOf("لا يوجد اختلاف") }
            .joinToString("، ") { it.substringAfterLast('.') }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("مقارنة تطبيقين", style = MaterialTheme.typography.titleSmall)
            Text("${first.label}  مقابل  ${second.label}", fontSize = 12.sp)
            Text("الإصدار: ${first.version} / ${second.version}", fontSize = 11.sp)
            Text("Target SDK: ${first.targetSdk ?: "غير متاح"} / ${second.targetSdk ?: "غير متاح"}", fontSize = 11.sp)
            Text("مصدر التثبيت: ${first.installer} / ${second.installer}", fontSize = 11.sp)
            Text(signatureStatus, fontSize = 11.sp)
            Text("صلاحيات ممنوحة في الأول فقط: ${display(comparison.grantedOnlyInFirst)}", fontSize = 11.sp)
            Text("صلاحيات ممنوحة في الثاني فقط: ${display(comparison.grantedOnlyInSecond)}", fontSize = 11.sp)
            Text("صلاحيات مطلوبة في الأول فقط: ${display(comparison.requestedOnlyInFirst)}", fontSize = 11.sp)
            Text("صلاحيات مطلوبة في الثاني فقط: ${display(comparison.requestedOnlyInSecond)}", fontSize = 11.sp)
            Text(
                "المقارنة تعرض اختلافات البيانات المتاحة، ولا تعني أن تطبيقًا أكثر أمانًا من الآخر.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptyApplications() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("لا توجد نتائج للعرض", style = MaterialTheme.typography.titleMedium)
        Text(
            "ابدأ فحصًا من الشاشة الرئيسية أو غيّر البحث والفلتر الحالي.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ApplicationCard(
    app: AppAudit,
    selectedForComparison: Boolean,
    isTrusted: Boolean,
    isIgnored: Boolean,
    onCompareChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        }
    }
    val accent = when (app.status) {
        AuditStatus.REVIEW -> Color(0xFFFF7A6B)
        AuditStatus.REQUESTED -> Color(0xFFFFC857)
        AuditStatus.SENSITIVE -> Color(0xFF85B8FF)
        AuditStatus.INFORMATIONAL -> Color(0xFF70E7D1)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AndroidView(
                    factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { it.setImageDrawable(icon) },
                    modifier = Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(5.dp)
                )
            } else {
                Text(
                    app.label.firstOrNull()?.uppercase() ?: "A",
                    modifier = Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                Text(
                    when (app.status) {
                        AuditStatus.REVIEW -> "● وصول خاص نشط — راجع السبب"
                        AuditStatus.REQUESTED -> "◷ طلب وصول خاص — التفعيل غير مؤكد"
                        AuditStatus.SENSITIVE -> "◈ صلاحيات حساسة ممنوحة"
                        AuditStatus.INFORMATIONAL -> "○ لم تُرصد المؤشرات المحددة"
                    },
                    color = accent,
                    fontSize = 11.sp
                )
                Text(
                    "${if (app.isSystem) "تطبيق نظام" else "تطبيق مثبت"} • ${app.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                if (isTrusted || isIgnored) {
                    Text(
                        if (isTrusted) "✓ موثوق لدي — تسمية محلية" else "↘ متجاهل — تسمية محلية",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
            Checkbox(
                checked = selectedForComparison,
                onCheckedChange = onCompareChange
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApplicationsScreenPreview() {
    MsiTheme {
        ApplicationsScreen(
            applications = emptyList(),
            query = "",
            filter = AppFilter.ALL,
            onQueryChange = {},
            onFilterChange = {},
            onSelect = {}
        )
    }
}

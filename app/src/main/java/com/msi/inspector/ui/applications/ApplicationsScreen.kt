package com.msi.inspector.ui.applications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.msi.inspector.R
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.msiSemanticColors
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
    onCopyPackage: (String) -> Unit = {},
    onOpenSettings: (String) -> Unit = {},
    trustedPackages: Set<String> = emptySet(),
    ignoredPackages: Set<String> = emptySet(),
    hasLoadedScanSnapshot: Boolean = false,
    totalApplicationCount: Int = applications.size
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
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_msi_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("بحث عن تطبيق") },
            placeholder = { Text("اسم التطبيق أو package name") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { onQueryChange("") }) {
                        Text("مسح")
                    }
                }
            }
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("تجميع حسب مصدر التثبيت", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (groupBySource) "التطبيقات مرتبة داخل مجموعات مصدر التثبيت."
                    else "عرض قائمة موحّدة حسب ترتيب المؤشرات.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Switch(
                checked = groupBySource,
                onCheckedChange = { groupBySource = it },
                modifier = Modifier.semantics {
                    contentDescription = "تجميع التطبيقات حسب مصدر التثبيت"
                }
            )
        }
        if (selectedApps.size == 2) {
            ComparisonCard(selectedApps[0], selectedApps[1])
        } else if (selectedForComparison.isNotEmpty()) {
            Text(
                "اختر تطبيقًا آخر لإكمال المقارنة (${selectedForComparison.size}/2).",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
        if (applications.isEmpty()) {
            val emptyTitle: String
            val emptyDescription: String
            when {
                totalApplicationCount > 0 -> {
                    emptyTitle = "لا توجد نتائج مطابقة"
                    emptyDescription = "جرّب تعديل البحث أو اختيار فلتر مختلف لعرض التطبيقات."
                }
                hasLoadedScanSnapshot -> {
                    emptyTitle = "لم تظهر تطبيقات في آخر فحص"
                    emptyDescription = "اكتمل الفحص، لكن Android لم يُرجع تطبيقات قابلة للعرض في هذه القراءة."
                }
                else -> {
                    emptyTitle = "لا توجد بيانات تطبيقات بعد"
                    emptyDescription = "ابدأ فحصًا من الشاشة الرئيسية لقراءة التطبيقات التي يتيحها Android."
                }
            }
            EmptyApplications(emptyTitle, emptyDescription)
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
                            SwipeableApplicationCard(
                                app = app,
                                onCopyPackage = onCopyPackage,
                                onOpenSettings = onOpenSettings,
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
                        SwipeableApplicationCard(
                            app = app,
                            onCopyPackage = onCopyPackage,
                            onOpenSettings = onOpenSettings,
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
            Text("الإصدار: ${first.version} / ${second.version}", fontSize = 12.sp)
            Text("Target SDK: ${first.targetSdk ?: "غير متاح"} / ${second.targetSdk ?: "غير متاح"}", fontSize = 12.sp)
            Text("مصدر التثبيت: ${first.installer} / ${second.installer}", fontSize = 12.sp)
            Text(signatureStatus, fontSize = 12.sp)
            Text("صلاحيات ممنوحة في الأول فقط: ${display(comparison.grantedOnlyInFirst)}", fontSize = 12.sp)
            Text("صلاحيات ممنوحة في الثاني فقط: ${display(comparison.grantedOnlyInSecond)}", fontSize = 12.sp)
            Text("صلاحيات مطلوبة في الأول فقط: ${display(comparison.requestedOnlyInFirst)}", fontSize = 12.sp)
            Text("صلاحيات مطلوبة في الثاني فقط: ${display(comparison.requestedOnlyInSecond)}", fontSize = 12.sp)
            Text(
                "المقارنة تعرض اختلافات البيانات المتاحة، ولا تعني أن تطبيقًا أكثر أمانًا من الآخر.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyApplications(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableApplicationCard(
    app: AppAudit,
    selectedForComparison: Boolean,
    isTrusted: Boolean,
    isIgnored: Boolean,
    onCompareChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onCopyPackage: (String) -> Unit,
    onOpenSettings: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onCopyPackage(app.packageName)
            SwipeToDismissBoxValue.EndToStart -> onOpenSettings(app.packageName)
            SwipeToDismissBoxValue.Settled -> Unit
        }
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نسخ اسم الحزمة", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text("إعدادات التطبيق", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        content = {
            ApplicationCard(
                app = app,
                selectedForComparison = selectedForComparison,
                isTrusted = isTrusted,
                isIgnored = isIgnored,
                onCompareChange = onCompareChange,
                onClick = onClick
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    var showQuickActions by remember(app.packageName) { mutableStateOf(false) }
    val icon by produceState<Drawable?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        }
    }
    val semantic = msiSemanticColors()
    val accent: Color = when (app.status) {
        AuditStatus.REVIEW -> semantic.attention
        AuditStatus.REQUESTED -> semantic.review
        AuditStatus.SENSITIVE -> semantic.info
        AuditStatus.INFORMATIONAL -> semantic.calm
        AuditStatus.INCOMPLETE -> semantic.review
    }
    val statusIcon = when (app.status) {
        AuditStatus.REVIEW -> R.drawable.ic_msi_shield_alert
        AuditStatus.REQUESTED -> R.drawable.ic_msi_key
        AuditStatus.SENSITIVE -> R.drawable.ic_msi_eye
        AuditStatus.INFORMATIONAL -> R.drawable.ic_msi_check
        AuditStatus.INCOMPLETE -> R.drawable.ic_msi_warning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (selectedForComparison) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showQuickActions = true }
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AndroidView(
                    factory = {
                        viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = "أيقونة ${app.label}"
                        }
                    },
                    update = { it.setImageDrawable(icon) },
                    modifier = Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(5.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.label.firstOrNull()?.uppercase() ?: "A",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(statusIcon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        when (app.status) {
                            AuditStatus.REVIEW -> "وصول خاص نشط — راجع السبب"
                            AuditStatus.REQUESTED -> "طلب وصول خاص — التفعيل غير مؤكد"
                            AuditStatus.SENSITIVE -> "صلاحيات حساسة ممنوحة"
                            AuditStatus.INFORMATIONAL -> "لم تُرصد المؤشرات المحددة"
                            AuditStatus.INCOMPLETE -> "بيانات الفحص غير مكتملة — راجع التفاصيل"
                        },
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2
                    )
                }
                Text(
                    "${if (app.isSystem) "تطبيق نظام" else "تطبيق مثبت"} • ${app.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (isTrusted || isIgnored) {
                    Text(
                        if (isTrusted) "موثوق لدي — تسمية محلية" else "متجاهل — تسمية محلية",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
            Checkbox(
                checked = selectedForComparison,
                onCheckedChange = onCompareChange
            )
        }
        DropdownMenu(
            expanded = showQuickActions,
            onDismissRequest = { showQuickActions = false }
        ) {
            DropdownMenuItem(
                text = { Text("نسخ اسم الحزمة") },
                onClick = {
                    showQuickActions = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("اسم الحزمة", app.packageName))
                    Toast.makeText(context, "تم نسخ اسم الحزمة", Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("مشاركة مؤشرات التطبيق") },
                onClick = {
                    showQuickActions = false
                    val shareText = buildString {
                        appendLine("MSI — مؤشرات التطبيق")
                        appendLine(app.label)
                        appendLine(app.packageName)
                        appendLine("الإصدار: ${app.version}")
                        appendLine("Target SDK: ${app.targetSdk ?: "غير متاح"}")
                        appendLine("مصدر التثبيت: ${app.installer}")
                        appendLine("إعدادات وصول خاصة نشطة: ${app.specialAccessReasons.joinToString("، ").ifBlank { "لا يوجد ضمن المؤشرات المتاحة" }}")
                        appendLine("صلاحيات حساسة ممنوحة: ${app.grantedSensitive.joinToString("، ").ifBlank { "لا يوجد ضمن المعلومات المتاحة" }}")
                        appendLine("صلاحيات حساسة مطلوبة: ${app.requestedSensitive.joinToString("، ").ifBlank { "لا يوجد ضمن المعلومات المتاحة" }}")
                        appendLine("هذه مؤشرات وليست حكمًا على سلامة التطبيق.")
                    }
                    runCatching {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة مؤشرات MSI"))
                    }.onFailure {
                        Toast.makeText(context, "تعذر فتح قائمة المشاركة.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
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

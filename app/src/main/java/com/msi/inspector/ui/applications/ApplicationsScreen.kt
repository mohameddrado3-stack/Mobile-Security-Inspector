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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.domain.analyzer.AppFilter
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
    onSelect: (AppAudit) -> Unit
) {
    val filters = AppFilter.values().toList()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsiSectionHeading("التطبيقات المثبتة", "ابحث بالاسم أو اسم الحزمة وحدد نوع المؤشر.")
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
        if (applications.isEmpty()) {
            EmptyApplications()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text(
                        "عدد النتائج: ${applications.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                items(applications, key = { it.packageName }) { app ->
                    ApplicationCard(app = app, onClick = { onSelect(app) })
                }
            }
        }
    }
}

@Composable
private fun EmptyApplications() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
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
private fun ApplicationCard(app: AppAudit, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
            }.getOrNull()
        }
    }
    val accent = when (app.status) {
        AuditStatus.REVIEW -> Color(0xFFFFC278)
        AuditStatus.REQUESTED -> Color(0xFFFFC278)
        AuditStatus.SENSITIVE -> Color(0xFF85B8FF)
        AuditStatus.INFORMATIONAL -> Color(0xFF70E5CD)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { it.setImageDrawable(icon) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp))
                        .padding(5.dp)
                )
            } else {
                Text(
                    text = app.label.firstOrNull()?.uppercase() ?: "A",
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp))
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    when (app.status) {
                        AuditStatus.REVIEW -> "إعداد وصول خاص نشط — راجع السبب"
                        AuditStatus.REQUESTED -> "طلب إعداد وصول خاص — غير مؤكد التفعيل"
                        AuditStatus.SENSITIVE -> "صلاحيات حساسة ممنوحة"
                        AuditStatus.INFORMATIONAL -> "لم تُرصد المؤشرات المحددة"
                    },
                    color = accent,
                    fontSize = 11.sp
                )
                Text(
                    "${if (app.isSystem) "تطبيق نظام" else "تطبيق مثبت"}  •  ${app.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

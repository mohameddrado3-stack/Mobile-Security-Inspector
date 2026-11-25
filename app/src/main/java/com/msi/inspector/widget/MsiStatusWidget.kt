package com.msi.inspector.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.msi.inspector.MainActivity
import com.msi.inspector.domain.analyzer.AuditSummary
import com.msi.inspector.domain.model.ScanSnapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val LAST_SCAN = longPreferencesKey("msi_widget_last_scan")
private val TOTAL_APPS = intPreferencesKey("msi_widget_total_apps")
private val ACTIVE_SPECIAL = intPreferencesKey("msi_widget_active_special")
private val REQUESTED_SPECIAL = intPreferencesKey("msi_widget_requested_special")
private val VPN_STATE = intPreferencesKey("msi_widget_vpn_state")

class MsiStatusWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val preferences = currentState<Preferences>()
            WidgetContent(
                lastScan = preferences[LAST_SCAN] ?: 0L,
                total = preferences[TOTAL_APPS] ?: 0,
                active = preferences[ACTIVE_SPECIAL] ?: 0,
                requested = preferences[REQUESTED_SPECIAL] ?: 0,
                vpnState = preferences[VPN_STATE] ?: -1
            )
        }
    }

    @Composable
    private fun WidgetContent(lastScan: Long, total: Int, active: Int, requested: Int, vpnState: Int) {
        val context = androidx.glance.LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF080D18)))
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.Top,
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            Text(
                "MSI  •  فحص مؤشرات الأمان",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF70E7D1)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                if (lastScan > 0L) "آخر فحص: ${formatTime(lastScan)}" else "لم يُجر فحص بعد",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp)
            )
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    "التطبيقات: $total",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp)
                )
                Text(
                    "   وصول نشط: $active",
                    style = TextStyle(color = ColorProvider(Color(0xFFFF7A6B)), fontSize = 11.sp)
                )
                Text(
                    "   طلبات: $requested",
                    style = TextStyle(color = ColorProvider(Color(0xFFFFC857)), fontSize = 11.sp)
                )
            }
            Text(
                when (vpnState) {
                    1 -> "VPN: نشط وقت الفحص"
                    0 -> "VPN: غير نشط وقت الفحص"
                    else -> "VPN: الحالة غير معروفة"
                },
                style = TextStyle(color = ColorProvider(Color(0xFFB8C7D9)), fontSize = 10.sp)
            )
            androidx.glance.Button(
                text = "افحص الآن",
                onClick = actionStartActivity(
                    Intent(context, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_SHORTCUT_SCAN
                    }
                )
            )
        }
    }

    private fun formatTime(value: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
            .format(Date(value))
}

class MsiStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MsiStatusWidget()
}

object MsiWidgetStateUpdater {
    suspend fun update(context: Context, snapshot: ScanSnapshot) {
        val summary = AuditSummary.from(snapshot.applications)
        val manager = GlanceAppWidgetManager(context)
        val widget = MsiStatusWidget()
        manager.getGlanceIds(MsiStatusWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { preferences ->
                preferences[LAST_SCAN] = snapshot.completedAt
                preferences[TOTAL_APPS] = summary.totalApplications
                preferences[ACTIVE_SPECIAL] = summary.activeSpecialAccess
                preferences[REQUESTED_SPECIAL] = summary.requestedSpecialAccess
                preferences[VPN_STATE] = snapshot.vpnActive?.let { if (it) 1 else 0 } ?: -1
            }
            widget.update(context, id)
        }
    }
}

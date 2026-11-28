package com.msi.inspector.ui.dashboard

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.R
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiIconBadge
import com.msi.inspector.design.components.MsiKeyValueRow
import com.msi.inspector.design.components.MsiSectionHeading
import com.msi.inspector.design.components.MsiStatusChip
import com.msi.inspector.domain.battery.BatteryMeasurementMethod
import com.msi.inspector.domain.battery.BatteryTrend
import com.msi.inspector.domain.battery.BatteryUsageSummary
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryUsageCard(
    summary: BatteryUsageSummary?,
    isLoading: Boolean,
    isTrackingEnabled: Boolean
) {
    val accent = when (summary?.trend) {
        BatteryTrend.DISCHARGING -> MaterialTheme.colorScheme.tertiary
        BatteryTrend.CHARGING -> MaterialTheme.colorScheme.primary
        BatteryTrend.STABLE, null -> MaterialTheme.colorScheme.primary
    }
    MsiCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MsiIconBadge(
                    iconRes = R.drawable.ic_msi_refresh,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 44.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("مراقبة البطارية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "سجل محلي يتحدث دوريًا",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                MsiStatusChip(
                    text = when {
                        !isTrackingEnabled -> "متوقف"
                        isLoading -> "يحدّث"
                        summary == null -> "تهيئة"
                        else -> "نشط"
                    },
                    color = when {
                        !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        summary?.trend == BatteryTrend.DISCHARGING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 17.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        when {
                            !isTrackingEnabled -> "التسجيل متوقف"
                            summary == null -> "بانتظار قراءتين"
                            else -> trendLabel(summary.trend)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    val amountText = summary?.let(::formatAmount) ?: "—"
                    AnimatedContent(
                        targetState = amountText,
                        transitionSpec = {
                            if (ValueAnimator.areAnimatorsEnabled()) {
                                (fadeIn() + slideInVertically { it / 4 })
                                    .togetherWith(fadeOut() + slideOutVertically { -it / 4 })
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None
                            }
                        },
                        label = "battery-usage-value"
                    ) { value ->
                        Text(
                            value,
                            fontSize = 31.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                    Text(
                        when (summary?.method) {
                            BatteryMeasurementMethod.CHARGE_COUNTER -> "فرق عداد الشحنة"
                            BatteryMeasurementMethod.ENERGY_COUNTER -> "فرق عداد الطاقة"
                            BatteryMeasurementMethod.PERCENTAGE_POINTS -> "فرق مستوى الشحن"
                            null -> if (isTrackingEnabled) {
                                "ستظهر النتيجة بعد توفر عينتين صالحتين."
                            } else {
                                "أعد تفعيل التسجيل لاستكمال جمع القراءات."
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (summary != null) {
                val startLevel = summary.startSample.levelPercent
                val endLevel = summary.endSample.levelPercent
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LevelValue("البداية", startLevel)
                        LevelValue("النهاية", endLevel, alignEnd = true)
                    }
                    if (startLevel != null && endLevel != null) {
                        LinearProgressIndicator(
                            progress = { endLevel / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50)),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                MsiKeyValueRow("مدة القياس", formatDuration(summary.durationMillis))
                MsiKeyValueRow("مصدر القراءة", methodLabel(summary.method))
                MsiKeyValueRow("حالة الطاقة", chargingLabel(summary.endSample.isCharging, summary.endSample.isPlugged))
                MsiKeyValueRow(
                    "آخر عينة",
                    DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.getDefault()
                    ).format(Date(summary.endSample.sampledAtEpochMillis))
                )
            } else {
                Text(
                    if (isTrackingEnabled) {
                        "تُحفظ أول قراءة محليًا عند فتح التطبيق، ثم يحاول Android أخذ عينة كل ساعة تقريبًا. لن يظهر فرق قبل وجود قراءتين."
                    } else {
                        "القراءات السابقة تظل محفوظة على الجهاز. أثناء الإيقاف لن تُضاف عينات جديدة."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "حدود القياس: يعرض التغير الصافي في بطارية الهاتف بالكامل، وليس استهلاك MSI أو تطبيق آخر. قد يؤخر Android التحديث، وقد لا يدعم الجهاز عدادات mAh أو mWh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LevelValue(label: String, level: Int?, alignEnd: Boolean = false) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            level?.let { "$it%" } ?: "غير متاح",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
    }
}

private fun trendLabel(trend: BatteryTrend): String = when (trend) {
    BatteryTrend.DISCHARGING -> "انخفاض صافي في الشحنة"
    BatteryTrend.CHARGING -> "زيادة صافية في الشحنة"
    BatteryTrend.STABLE -> "لا تغير ملحوظ في الشحنة"
}

private fun formatAmount(summary: BatteryUsageSummary): String {
    val value = String.format(Locale.getDefault(), "%.1f", summary.amount)
    return when (summary.method) {
        BatteryMeasurementMethod.CHARGE_COUNTER -> "$value mAh"
        BatteryMeasurementMethod.ENERGY_COUNTER -> "$value mWh"
        BatteryMeasurementMethod.PERCENTAGE_POINTS -> "$value نقطة مئوية"
    }
}

private fun methodLabel(method: BatteryMeasurementMethod): String = when (method) {
    BatteryMeasurementMethod.CHARGE_COUNTER -> "عداد الشحنة (mAh)"
    BatteryMeasurementMethod.ENERGY_COUNTER -> "عداد الطاقة (mWh)"
    BatteryMeasurementMethod.PERCENTAGE_POINTS -> "فرق النسبة المئوية"
}

private fun chargingLabel(isCharging: Boolean, isPlugged: Boolean): String = when {
    isCharging -> "جارٍ الشحن"
    isPlugged -> "متصل بالطاقة"
    else -> "على البطارية"
}

private fun formatDuration(durationMillis: Long): String {
    val minutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours > 0 -> "$hours ساعة و$remainder دقيقة"
        else -> "$remainder دقيقة"
    }
}

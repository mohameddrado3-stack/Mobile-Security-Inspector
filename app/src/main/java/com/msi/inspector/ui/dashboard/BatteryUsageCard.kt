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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.design.components.MsiCard
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
    MsiCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MsiSectionHeading(
                "مراقبة البطارية",
                "قراءات محلية دورية من مؤشرات بطارية Android."
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        when {
                            !isTrackingEnabled -> "التسجيل متوقف مؤقتًا"
                            isLoading -> "جارٍ تحديث قراءة البطارية"
                            summary == null -> "نجمع خط الأساس"
                            else -> trendLabel(summary.trend)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when {
                            !isTrackingEnabled -> "يمكن استئناف القياس من مركز التحكم."
                            summary == null -> "سيظهر الفرق بعد توفر قراءتين صالحـتين."
                            else -> "التغير الصافي في بطارية الجهاز خلال الفترة المقاسة"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MsiStatusChip(
                    text = when {
                        !isTrackingEnabled -> "متوقف"
                        isLoading -> "تحديث"
                        summary == null -> "تجميع"
                        else -> "نشط"
                    },
                    color = when {
                        !isTrackingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        summary?.trend == BatteryTrend.DISCHARGING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            val amountText = summary?.let(::formatAmount) ?: "—"
            AnimatedContent(
                targetState = amountText,
                transitionSpec = {
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        (fadeIn() + slideInVertically { it / 3 })
                            .togetherWith(fadeOut() + slideOutVertically { -it / 3 })
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "battery-usage-value"
            ) { value ->
                Text(
                    value,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (summary != null) {
                val startLevel = summary.startSample.levelPercent
                val endLevel = summary.endSample.levelPercent
                if (endLevel != null) {
                    LinearProgressIndicator(
                        progress = { endLevel / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                MsiKeyValueRow(
                    "مستوى البداية",
                    startLevel?.let { "$it%" } ?: "غير متاح"
                )
                MsiKeyValueRow(
                    "مستوى النهاية",
                    endLevel?.let { "$it%" } ?: "غير متاح"
                )
                MsiKeyValueRow("مدة القياس الفعلية", formatDuration(summary.durationMillis))
                MsiKeyValueRow(
                    "مصدر القياس",
                    when (summary.method) {
                        BatteryMeasurementMethod.CHARGE_COUNTER -> "عداد الشحنة (mAh)"
                        BatteryMeasurementMethod.ENERGY_COUNTER -> "عداد الطاقة (mWh)"
                        BatteryMeasurementMethod.PERCENTAGE_POINTS -> "فرق النسبة المئوية"
                    }
                )
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
                        "تُسجَّل أول قراءة عند تشغيل التطبيق، ثم يحاول Android تحديثها كل ساعة تقريبًا. لا يمكن حساب الفرق قبل وجود عينتين."
                    } else {
                        "أُبقيت القراءات السابقة محفوظة محليًا، لكن لن تُضاف عينات جديدة حتى تعيد تشغيل الميزة."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "تنبيه دقة: هذه مقارنة صافية لبطارية الهاتف بالكامل، وليست استهلاك MSI أو استهلاك تطبيق بعينه. قد يتأخر التحديث بسبب إدارة الطاقة في Android، كما قد لا يدعم الجهاز عدّادات mAh أو mWh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
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

private fun formatDuration(durationMillis: Long): String {
    val minutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours > 0 -> "$hours ساعة و$remainder دقيقة"
        else -> "$remainder دقيقة"
    }
}

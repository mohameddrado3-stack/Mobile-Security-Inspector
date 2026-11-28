package com.msi.inspector.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.msi.inspector.R
import com.msi.inspector.design.components.MsiCard
import com.msi.inspector.design.components.MsiIconBadge
import com.msi.inspector.design.components.MsiStatusChip

@Composable
fun ActiveMonitoringCard(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    MsiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MsiIconBadge(
                iconRes = R.drawable.ic_msi_shield_eye,
                tint = MaterialTheme.colorScheme.primary,
                size = 42.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.monitor_card_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "رصد تغيّرات محددة في إعدادات أمان Android",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MsiStatusChip(
                text = if (enabled) stringResource(R.string.monitor_card_enabled)
                else stringResource(R.string.monitor_card_disabled),
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "اختياري",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            stringResource(R.string.monitor_card_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            stringResource(R.string.monitor_card_privacy),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            stringResource(R.string.monitor_card_limits),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

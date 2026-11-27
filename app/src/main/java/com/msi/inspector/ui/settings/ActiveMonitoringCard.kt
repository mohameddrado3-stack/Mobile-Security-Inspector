package com.msi.inspector.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.inspector.R
import com.msi.inspector.design.components.MsiCard

@Composable
fun ActiveMonitoringCard(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    MsiCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                stringResource(R.string.monitor_card_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.monitor_card_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (enabled) stringResource(R.string.monitor_card_enabled)
                        else stringResource(R.string.monitor_card_disabled),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged
                )
            }
            Text(
                stringResource(R.string.monitor_card_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                stringResource(R.string.monitor_card_limits),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { onEnabledChanged(!enabled) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (enabled) stringResource(R.string.monitor_disable)
                    else stringResource(R.string.monitor_enable)
                )
            }
        }
    }
}

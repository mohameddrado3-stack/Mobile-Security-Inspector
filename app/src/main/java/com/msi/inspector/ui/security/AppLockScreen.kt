package com.msi.inspector.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.components.MsiBrandHeader

@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MsiBrandHeader()
        Text("MSI محمي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "يتطلب فتح التطبيق التحقق باستخدام وسيلة حماية الجهاز.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Button(onClick = onUnlock) {
            Text("التحقق وفتح التطبيق")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    MsiTheme { AppLockScreen(onUnlock = {}) }
}

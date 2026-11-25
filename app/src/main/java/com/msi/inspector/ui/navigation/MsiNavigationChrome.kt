package com.msi.inspector.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun MsiBottomNavigation(
    selected: MsiDestination,
    onNavigate: (MsiDestination) -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        MsiDestination.values().forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onNavigate(destination) },
                icon = {
                    Text(
                        destination.symbol,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text(destination.title, fontSize = 10.sp) }
            )
        }
    }
}

@Composable
fun MsiNavigationRail(
    selected: MsiDestination,
    onNavigate: (MsiDestination) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        MsiDestination.values().forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onNavigate(destination) },
                icon = {
                    Text(
                        destination.symbol,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text(destination.title, fontSize = 10.sp) }
            )
        }
    }
}

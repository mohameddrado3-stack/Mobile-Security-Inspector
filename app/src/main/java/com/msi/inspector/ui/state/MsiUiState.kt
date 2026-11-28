package com.msi.inspector.ui.state

import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.battery.BatteryUsageSummary
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.timeline.TimelineEvent
import com.msi.inspector.ui.navigation.MsiDestination

data class MsiUiState(
    val destination: MsiDestination = MsiDestination.HOME,
    val applications: List<AppAudit> = emptyList(),
    val history: List<ScanHistoryEntry> = emptyList(),
    val timelineEvents: List<TimelineEvent> = emptyList(),
    val selectedApplication: AppAudit? = null,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val packageScope: Set<String>? = null,
    val trustedPackages: Set<String> = emptySet(),
    val ignoredPackages: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val vpnActive: Boolean? = null,
    val lastScanAt: Long? = null,
    val hasLoadedScanSnapshot: Boolean = false,
    val batteryUsage: BatteryUsageSummary? = null,
    val isLoadingBatteryUsage: Boolean = false,
    val batteryTrackingEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val usageAccessOptIn: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val deviceSecurity: DeviceSecuritySnapshot? = null,
    val isLoadingDeviceSecurity: Boolean = false,
    val error: String? = null
)

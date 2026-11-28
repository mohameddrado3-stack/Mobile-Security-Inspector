package com.msi.inspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msi.inspector.background.BatteryHourlyScheduler
import com.msi.inspector.data.battery.BatteryUsageRepository
import com.msi.inspector.data.preferences.AppLocalLists
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.data.scanner.AndroidDeviceSecurityReader
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.security.usage.UsageAccessReader
import com.msi.inspector.ui.navigation.MsiDestination
import com.msi.inspector.ui.state.MsiUiState
import com.msi.inspector.widget.MsiWidgetStateUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MsiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppAuditRepository(application)
    private val batteryRepository = BatteryUsageRepository(application)
    private val localLists = AppLocalLists(application)
    private val usageAccessReader = UsageAccessReader(application)
    private val deviceSecurityReader = AndroidDeviceSecurityReader(application)
    private val appLockSettings = AppLockSettings(application)
    private val mutableState = MutableStateFlow(
        MsiUiState(
            appLockEnabled = appLockSettings.isEnabled(),
            batteryTrackingEnabled = BatteryHourlyScheduler.isEnabled(application)
        )
    )
    val state = mutableState.asStateFlow()

    init {
        runCatching { BatteryHourlyScheduler.ensureScheduled(application) }
        refreshBatteryUsage()
        mutableState.update {
            it.copy(
                trustedPackages = localLists.trustedPackages(),
                ignoredPackages = localLists.ignoredPackages(),
                usageAccessOptIn = usageAccessReader.isOptedIn(),
                usageAccessGranted = usageAccessReader.isAccessGranted()
            )
        }
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    repository.loadScanState()
                }
                val entries = loaded.history
                val latestSnapshot = loaded.latestSnapshot
                mutableState.update { current ->
                    current.copy(
                        applications = current.applications.ifEmpty { latestSnapshot?.applications.orEmpty() },
                        hasLoadedScanSnapshot = latestSnapshot != null,
                        history = entries,
                        timelineEvents = loaded.timeline,
                        deviceSecurity = current.deviceSecurity ?: latestSnapshot?.deviceSecurity ?: entries.firstOrNull()?.deviceSecuritySnapshot,
                        vpnActive = if (latestSnapshot != null) latestSnapshot.vpnActive else current.vpnActive ?: entries.firstOrNull()?.vpnActive,
                        lastScanAt = current.lastScanAt ?: latestSnapshot?.completedAt ?: entries.firstOrNull()?.scannedAt
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(error = exception.localizedMessage?.takeIf(String::isNotBlank)
                        ?: "تعذر تحميل سجل الفحوصات.")
                }
            }
        }
    }

    fun navigate(destination: MsiDestination) {
        mutableState.update { it.copy(destination = destination, selectedApplication = null) }
        if (destination == MsiDestination.TOOLS) refreshDeviceSecurity()
    }

    fun refreshDeviceSecurity() {
        if (mutableState.value.isLoadingDeviceSecurity) return
        mutableState.update { it.copy(isLoadingDeviceSecurity = true) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { deviceSecurityReader.read() }
                mutableState.update {
                    it.copy(deviceSecurity = snapshot, isLoadingDeviceSecurity = false)
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(isLoadingDeviceSecurity = false) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoadingDeviceSecurity = false) }
            }
        }
    }

    fun refreshScanHistory() {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    repository.loadScanState()
                }
                mutableState.update { current ->
                    val latest = loaded.latestSnapshot
                    if (latest != null && current.lastScanAt != null &&
                        latest.completedAt < current.lastScanAt
                    ) {
                        current
                    } else {
                        current.copy(
                            applications = latest?.applications ?: current.applications,
                            hasLoadedScanSnapshot = current.hasLoadedScanSnapshot || latest != null,
                            history = loaded.history,
                            timelineEvents = loaded.timeline,
                            deviceSecurity = latest?.deviceSecurity ?: current.deviceSecurity,
                            vpnActive = if (latest != null) latest.vpnActive else current.vpnActive,
                            lastScanAt = latest?.completedAt ?: current.lastScanAt
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        error = exception.localizedMessage?.takeIf(String::isNotBlank)
                            ?: "تعذر تحديث سجل الفحوصات المحلي."
                    )
                }
            }
        }
    }

    fun refreshBatteryUsage() {
        if (mutableState.value.isLoadingBatteryUsage) return
        val enabled = BatteryHourlyScheduler.isEnabled(getApplication())
        mutableState.update {
            it.copy(
                isLoadingBatteryUsage = true,
                batteryTrackingEnabled = enabled
            )
        }
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    if (enabled) batteryRepository.captureIfDue()
                    else batteryRepository.latestSummary()
                }
                mutableState.update {
                    it.copy(
                        batteryUsage = summary,
                        isLoadingBatteryUsage = false
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(isLoadingBatteryUsage = false) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoadingBatteryUsage = false) }
            }
        }
    }

    fun setBatteryTrackingEnabled(enabled: Boolean) {
        BatteryHourlyScheduler.setEnabled(getApplication(), enabled)
        mutableState.update { it.copy(batteryTrackingEnabled = enabled) }
        if (enabled) refreshBatteryUsage()
    }

    fun updateQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun updateFilter(filter: AppFilter) {
        mutableState.update {
            it.copy(filter = filter, packageScope = null, destination = MsiDestination.APPLICATIONS)
        }
    }

    fun showPackageScope(packageNames: Set<String>) {
        mutableState.update {
            it.copy(
                packageScope = packageNames,
                filter = AppFilter.ALL,
                query = "",
                destination = MsiDestination.APPLICATIONS
            )
        }
    }

    fun markApplicationTrusted(packageName: String) {
        localLists.markTrusted(packageName)
        mutableState.update {
            it.copy(
                trustedPackages = localLists.trustedPackages(),
                ignoredPackages = localLists.ignoredPackages()
            )
        }
    }

    fun markApplicationIgnored(packageName: String) {
        localLists.markIgnored(packageName)
        mutableState.update {
            it.copy(
                trustedPackages = localLists.trustedPackages(),
                ignoredPackages = localLists.ignoredPackages()
            )
        }
    }

    fun clearApplicationLabel(packageName: String) {
        localLists.clear(packageName)
        mutableState.update {
            it.copy(
                trustedPackages = localLists.trustedPackages(),
                ignoredPackages = localLists.ignoredPackages()
            )
        }
    }

    fun selectApplication(application: AppAudit?) {
        mutableState.update { it.copy(selectedApplication = application) }
    }

    fun setUsageAccessOptIn(enabled: Boolean) {
        usageAccessReader.setOptedIn(enabled)
        mutableState.update {
            it.copy(
                usageAccessOptIn = enabled,
                usageAccessGranted = usageAccessReader.isAccessGranted()
            )
        }
    }

    fun refreshUsageAccessState() {
        mutableState.update {
            it.copy(usageAccessGranted = usageAccessReader.isAccessGranted())
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        appLockSettings.setEnabled(enabled)
        mutableState.update { it.copy(appLockEnabled = enabled) }
    }

    fun startScan() {
        if (mutableState.value.isScanning) return
        mutableState.update {
            it.copy(
                isScanning = true,
                scanProgress = 0,
                scanTotal = 0,
                error = null,
                destination = MsiDestination.HOME
            )
        }
        viewModelScope.launch {
            val scanResult = try {
                withContext(Dispatchers.IO) {
                    repository.scanAndSave(onProgress = { completed, total ->
                        mutableState.update {
                            it.copy(scanProgress = completed, scanTotal = total)
                        }
                    })
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(isScanning = false) }
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        isScanning = false,
                        error = exception.localizedMessage?.takeIf(String::isNotBlank)
                            ?: "حدث خطأ غير معروف أثناء الفحص."
                    )
                }
                return@launch
            }

            val snapshot = scanResult.snapshot
            try {
                withContext(Dispatchers.IO) {
                    runCatching { MsiWidgetStateUpdater.update(getApplication(), snapshot) }
                }
                val history = scanResult.history
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        hasLoadedScanSnapshot = true,
                        history = history,
                        timelineEvents = scanResult.timeline,
                        vpnActive = snapshot.vpnActive,
                        deviceSecurity = snapshot.deviceSecurity,
                        lastScanAt = snapshot.completedAt,
                        isScanning = false,
                        scanProgress = snapshot.applications.size,
                        scanTotal = snapshot.applications.size,
                        error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        vpnActive = snapshot.vpnActive,
                        deviceSecurity = snapshot.deviceSecurity,
                        lastScanAt = snapshot.completedAt,
                        isScanning = false
                    )
                }
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        vpnActive = snapshot.vpnActive,
                        lastScanAt = snapshot.completedAt,
                        isScanning = false,
                        error = "اكتمل الفحص لكن تعذر حفظ السجل: " +
                            (exception.localizedMessage ?: "خطأ غير معروف")
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.clearHistory() }
                mutableState.update { it.copy(history = emptyList(), timelineEvents = emptyList(), error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        error = "تعذر مسح سجل الفحوصات: " +
                            (exception.localizedMessage ?: "خطأ غير معروف")
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repository.close()
        batteryRepository.close()
        super.onCleared()
    }
}

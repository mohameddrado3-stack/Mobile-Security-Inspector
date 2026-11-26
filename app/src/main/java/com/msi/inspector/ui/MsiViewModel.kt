package com.msi.inspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val localLists = AppLocalLists(application)
    private val usageAccessReader = UsageAccessReader(application)
    private val deviceSecurityReader = AndroidDeviceSecurityReader(application)
    private val appLockSettings = AppLockSettings(application)
    private val mutableState = MutableStateFlow(
        MsiUiState(appLockEnabled = appLockSettings.isEnabled())
    )
    val state = mutableState.asStateFlow()

    init {
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
                val loaded = withContext(Dispatchers.IO) { repository.loadHistory() to repository.loadTimeline() }
                val entries = loaded.first
                mutableState.update { current ->
                    current.copy(
                        history = entries,
                        timelineEvents = loaded.second,
                        deviceSecurity = current.deviceSecurity ?: entries.firstOrNull()?.deviceSecuritySnapshot,
                        vpnActive = current.vpnActive ?: entries.firstOrNull()?.vpnActive,
                        lastScanAt = current.lastScanAt ?: entries.firstOrNull()?.scannedAt
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
            val snapshot = try {
                withContext(Dispatchers.IO) {
                    repository.scan { completed, total ->
                        mutableState.update {
                            it.copy(scanProgress = completed, scanTotal = total)
                        }
                    }
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

            try {
                val recorded = withContext(Dispatchers.IO) {
                    repository.saveHistory(snapshot)
                    runCatching { MsiWidgetStateUpdater.update(getApplication(), snapshot) }
                    repository.loadHistory() to repository.loadTimeline()
                }
                val history = recorded.first
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        history = history,
                        timelineEvents = recorded.second,
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
        super.onCleared()
    }
}

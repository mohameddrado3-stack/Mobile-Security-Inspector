package com.msi.inspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.data.preferences.AppLocalLists
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.navigation.MsiDestination
import com.msi.inspector.widget.MsiWidgetStateUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MsiUiState(
    val destination: MsiDestination = MsiDestination.HOME,
    val applications: List<AppAudit> = emptyList(),
    val history: List<ScanHistoryEntry> = emptyList(),
    val selectedApplication: AppAudit? = null,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val packageScope: Set<String>? = null,
    val trustedPackages: Set<String> = emptySet(),
    val ignoredPackages: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val vpnActive: Boolean = false,
    val lastScanAt: Long? = null,
    val appLockEnabled: Boolean = false,
    val error: String? = null
)

class MsiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppAuditRepository(application)
    private val localLists = AppLocalLists(application)
    private val appLockSettings = AppLockSettings(application)
    private val mutableState = MutableStateFlow(
        MsiUiState(appLockEnabled = appLockSettings.isEnabled())
    )
    val state = mutableState.asStateFlow()

    init {
        mutableState.update {
            it.copy(
                trustedPackages = localLists.trustedPackages(),
                ignoredPackages = localLists.ignoredPackages()
            )
        }
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { repository.loadHistory() }
                mutableState.update { current ->
                    current.copy(
                        history = entries,
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
                val history = withContext(Dispatchers.IO) {
                    repository.saveHistory(snapshot)
                    runCatching { MsiWidgetStateUpdater.update(getApplication(), snapshot) }
                    repository.loadHistory()
                }
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        history = history,
                        vpnActive = snapshot.vpnActive,
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
                mutableState.update { it.copy(history = emptyList(), error = null) }
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

package com.msi.inspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.msi.inspector.data.repository.AppAuditRepository
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.ScanHistoryEntry
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.navigation.MsiDestination
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
    val isScanning: Boolean = false,
    val vpnActive: Boolean = false,
    val lastScanAt: Long? = null,
    val appLockEnabled: Boolean = false,
    val error: String? = null
)

class MsiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppAuditRepository(application)
    private val appLockSettings = AppLockSettings(application)
    private val mutableState = MutableStateFlow(
        MsiUiState(appLockEnabled = appLockSettings.isEnabled())
    )
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { repository.loadHistory() }
            mutableState.update { current ->
                current.copy(
                    history = entries,
                    lastScanAt = current.lastScanAt ?: entries.firstOrNull()?.scannedAt
                )
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
        mutableState.update { it.copy(filter = filter) }
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
        mutableState.update { it.copy(isScanning = true, error = null, destination = MsiDestination.HOME) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.scan() }
                val history = withContext(Dispatchers.IO) {
                    repository.saveHistory(snapshot)
                    repository.loadHistory()
                }
                mutableState.update {
                    it.copy(
                        applications = snapshot.applications,
                        history = history,
                        vpnActive = snapshot.vpnActive,
                        lastScanAt = snapshot.completedAt,
                        isScanning = false,
                        error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(isScanning = false) }
                throw cancelled
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        isScanning = false,
                        error = exception.localizedMessage?.takeIf { message -> message.isNotBlank() }
                            ?: "حدث خطأ غير معروف أثناء الفحص."
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearHistory() }
            mutableState.update { it.copy(history = emptyList()) }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

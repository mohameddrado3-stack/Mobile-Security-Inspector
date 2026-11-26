package com.msi.inspector.data.scanner

import android.content.Context
import com.msi.inspector.domain.analyzer.AppAuditAnalyzer
import com.msi.inspector.domain.model.ScanSnapshot

/**
 * Coordinates independent Android data collectors and the domain analyzer.
 */
class AndroidAppScanner(context: Context) : AppScanner {
    private val appCollector = AndroidAppDataCollector(context.applicationContext)
    private val deviceSecurityReader = AndroidDeviceSecurityReader(context.applicationContext)

    override suspend fun scan(
        onProgress: (completed: Int, total: Int) -> Unit
    ): ScanSnapshot {
        val appEvidence = appCollector.collect(onProgress)
        val appSnapshot = AppAuditAnalyzer.analyze(appEvidence)
        return appSnapshot.copy(deviceSecurity = deviceSecurityReader.read())
    }
}

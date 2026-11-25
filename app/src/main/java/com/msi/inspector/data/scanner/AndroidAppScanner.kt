package com.msi.inspector.data.scanner

import android.content.Context
import com.msi.inspector.domain.analyzer.AppAuditAnalyzer
import com.msi.inspector.domain.model.ScanSnapshot

/**
 * Coordinates the Android collector and domain analyzer without coupling either
 * responsibility to the other.
 */
class AndroidAppScanner(context: Context) : AppScanner {
    private val collector = AndroidAppDataCollector(context.applicationContext)

    override suspend fun scan(
        onProgress: (completed: Int, total: Int) -> Unit
    ): ScanSnapshot {
        val evidence = collector.collect(onProgress)
        return AppAuditAnalyzer.analyze(evidence)
    }
}

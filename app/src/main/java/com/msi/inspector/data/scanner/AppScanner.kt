package com.msi.inspector.data.scanner

import com.msi.inspector.domain.model.ScanSnapshot

interface AppScanner {
    suspend fun scan(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ScanSnapshot
}

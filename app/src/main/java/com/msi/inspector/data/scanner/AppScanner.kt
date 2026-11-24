package com.msi.inspector.data.scanner

import com.msi.inspector.domain.model.ScanSnapshot

interface AppScanner {
    suspend fun scan(): ScanSnapshot
}

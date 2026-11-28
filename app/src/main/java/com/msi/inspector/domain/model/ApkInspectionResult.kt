package com.msi.inspector.domain.model

import java.io.File

data class ApkInspectionResult(
    val cachedFile: File,
    val fileName: String,
    val fileSizeBytes: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val targetSdk: Int,
    val isDebuggable: Boolean,
    val allowsCleartextTraffic: Boolean,
    val requestedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val unclassifiedPermissions: List<String>,
    val signerSha256: String?,
    val warnings: List<String>
)

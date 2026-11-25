package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit

data class AppComparison(
    val grantedOnlyInFirst: List<String>,
    val grantedOnlyInSecond: List<String>,
    val requestedOnlyInFirst: List<String>,
    val requestedOnlyInSecond: List<String>,
    val signaturesMatch: Boolean?
)

fun compareApplications(first: AppAudit, second: AppAudit): AppComparison =
    AppComparison(
        grantedOnlyInFirst = (first.grantedSensitive.toSet() - second.grantedSensitive.toSet()).sorted(),
        grantedOnlyInSecond = (second.grantedSensitive.toSet() - first.grantedSensitive.toSet()).sorted(),
        requestedOnlyInFirst = (first.requestedSensitive.toSet() - second.requestedSensitive.toSet()).sorted(),
        requestedOnlyInSecond = (second.requestedSensitive.toSet() - first.requestedSensitive.toSet()).sorted(),
        signaturesMatch = if (
            first.signingCertificateSha256 == null || second.signingCertificateSha256 == null
        ) null else first.signingCertificateSha256 == second.signingCertificateSha256
    )

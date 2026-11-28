package com.msi.inspector.domain.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLinkAnalyzerTest {
    @Test
    fun httpsUrlWithoutVisibleIndicatorsDoesNotClaimVerifiedSafety() {
        val result = WebLinkAnalyzer.analyze("https://example.com/path")
        assertEquals(WebLinkReviewLevel.NO_CLEAR_INDICATORS, result.level)
        assertTrue(result.findings.any { it.contains("لم تظهر مؤشرات شكلية") })
    }

    @Test
    fun plainHttpUrlNeedsConnectionReview() {
        val result = WebLinkAnalyzer.analyze("http://example.com")
        assertEquals(WebLinkReviewLevel.CAUTION, result.level)
        assertTrue(result.findings.any { it.contains("HTTPS") })
    }

    @Test
    fun urlShortenerIsMarkedForReviewWithoutCallingItMalicious() {
        val result = WebLinkAnalyzer.analyze("https://bit.ly/example")
        assertEquals(WebLinkReviewLevel.REVIEW, result.level)
        assertTrue(result.findings.any { it.contains("خدمة اختصار") })
    }

    @Test
    fun credentialsInAuthorityAreMarkedForReview() {
        val result = WebLinkAnalyzer.analyze("https://user:pass@example.com/login")
        assertEquals(WebLinkReviewLevel.REVIEW, result.level)
        assertTrue(result.findings.any { it.contains("بيانات اعتماد") })
    }

    @Test
    fun extractsUrlFromTextSharedByBrowser() {
        val result = WebLinkAnalyzer.analyze("شاهد هذا الرابط https://example.com/page الآن")
        assertEquals("example.com", result.host)
        assertEquals("https://example.com/page", result.normalizedUrl)
    }
}

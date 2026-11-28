package com.msi.inspector.domain.web

import java.net.IDN
import java.net.URI
import java.util.Locale

enum class WebLinkReviewLevel {
    REVIEW,
    CAUTION,
    NO_CLEAR_INDICATORS
}

data class WebLinkAnalysis(
    val input: String,
    val normalizedUrl: String?,
    val host: String?,
    val level: WebLinkReviewLevel,
    val findings: List<String>
)

object WebLinkAnalyzer {
    private val shortenerHosts = setOf(
        "bit.ly",
        "t.co",
        "tinyurl.com",
        "goo.gl",
        "rebrand.ly",
        "cutt.ly",
        "is.gd",
        "ow.ly"
    )

    fun analyze(input: String): WebLinkAnalysis {
        val candidate = extractUrl(input)
        if (candidate.isBlank()) {
            return WebLinkAnalysis(
                input = input,
                normalizedUrl = null,
                host = null,
                level = WebLinkReviewLevel.REVIEW,
                findings = listOf("لم يتم العثور على رابط صالح في النص.")
            )
        }

        val normalized = if ("://" in candidate) candidate else "https://$candidate"
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return invalid(input, normalized)
        val host = uri.host?.trim('[', ']')
            ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')?.trim('[', ']')
            ?: return invalid(input, normalized)
        val normalizedHost = host.lowercase(Locale.ROOT)
        val ipAddress = isIpAddress(normalizedHost)
        val asciiHost = if (ipAddress) normalizedHost else {
            runCatching { IDN.toASCII(normalizedHost) }.getOrNull()
                ?: return invalid(input, normalized)
        }
        val findings = mutableListOf<String>()

        if (uri.userInfo != null) {
            findings += "يحتوي الرابط على بيانات اعتماد قبل اسم النطاق؛ لا تدخل بياناتك من خلاله."
        }
        if (asciiHost.startsWith("xn--") || asciiHost.contains(".xn--")) {
            findings += "اسم النطاق يستخدم ترميز IDN/Punycode؛ راجع تهجئة النطاق بعناية."
        }
        if (ipAddress) {
            findings += "الرابط يستخدم عنوان IP بدل اسم نطاق واضح."
        }
        if (shortenerHosts.any { asciiHost == it || asciiHost.endsWith(".$it") }) {
            findings += "الرابط يستخدم خدمة اختصار؛ الوجهة النهائية غير ظاهرة من الرابط وحده."
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            findings += "الرابط لا يستخدم HTTPS؛ البيانات المرسلة قد لا تكون مشفرة أثناء النقل."
        }
        if (uri.port > 0 && uri.port !in setOf(80, 443)) {
            findings += "الرابط يستخدم منفذًا غير معتاد لمواقع الويب."
        }
        if (asciiHost.length > 64) {
            findings += "اسم النطاق طويل بشكل ملحوظ؛ راجعه قبل فتح الصفحة."
        }

        val level = when {
            uri.userInfo != null ||
                asciiHost.startsWith("xn--") ||
                asciiHost.contains(".xn--") ||
                ipAddress ||
                shortenerHosts.any { asciiHost == it || asciiHost.endsWith(".$it") } ->
                WebLinkReviewLevel.REVIEW
            findings.isNotEmpty() -> WebLinkReviewLevel.CAUTION
            else -> WebLinkReviewLevel.NO_CLEAR_INDICATORS
        }

        if (findings.isEmpty()) {
            findings += "لم تظهر مؤشرات شكلية واضحة في الرابط."
        }

        return WebLinkAnalysis(
            input = input,
            normalizedUrl = normalized,
            host = runCatching { IDN.toUnicode(asciiHost) }.getOrDefault(normalizedHost),
            level = level,
            findings = findings
        )
    }

    private fun extractUrl(text: String): String {
        val trimmed = text.trim()
        val explicit = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).find(trimmed)
        val bareDomain = Regex(
            """(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,}(?::\d{1,5})?(?:/[^\s<>"']*)?""",
            RegexOption.IGNORE_CASE
        ).find(trimmed)
        return (explicit?.value ?: bareDomain?.value ?: trimmed)
            .trimEnd('.', ',', ';', ')', ']', '}', '،')
    }
    private fun invalid(input: String, normalized: String) = WebLinkAnalysis(
        input = input,
        normalizedUrl = null,
        host = null,
        level = WebLinkReviewLevel.REVIEW,
        findings = listOf("تعذر تحليل بنية الرابط. لا تفتحه اعتمادًا على هذا الفحص.")
    )

    private fun isIpAddress(host: String): Boolean {
        val ipv4 = host.split('.')
        if (ipv4.size == 4 && ipv4.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }) return true
        return ':' in host
    }
}

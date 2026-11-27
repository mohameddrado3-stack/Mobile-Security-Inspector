package com.msi.inspector.data.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseCheckResult(
    val latestVersion: String,
    val updateAvailable: Boolean,
    val releaseUrl: String,
    val releaseNotes: String
)

object GitHubReleaseChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/mohameddrado3-stack/Mobile-Security-Inspector/releases/latest"
    private const val RELEASE_PAGE_PREFIX =
        "https://github.com/mohameddrado3-stack/Mobile-Security-Inspector/releases/"

    suspend fun check(currentVersion: String): ReleaseCheckResult = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Mobile-Security-Inspector-Android")
        }

        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException(
                    when (status) {
                        404 -> "لا يوجد إصدار منشور حتى الآن."
                        403, 429 -> "GitHub قيّد الطلب مؤقتًا. حاول لاحقًا."
                        else -> "تعذر الوصول إلى GitHub (HTTP $status)."
                    }
                )
            }

            val body = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(2_048)
                    var total = 0
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 128 * 1024) throw IOException("استجابة GitHub أكبر من الحد المسموح.")
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
            }

            val json = JSONObject(body)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                throw IOException("لا يوجد إصدار مستقر منشور حاليًا.")
            }

            val tag = json.optString("tag_name").trim()
            val latest = tag.removePrefix("v").removePrefix("V")
            val releaseUrl = json.optString("html_url")
            if (!isValidVersion(latest)) throw IOException("صيغة رقم الإصدار المنشور غير معروفة.")
            if (!releaseUrl.startsWith(RELEASE_PAGE_PREFIX)) {
                throw IOException("رابط الإصدار غير موثوق.")
            }

            ReleaseCheckResult(
                latestVersion = latest,
                updateAvailable = compareVersions(latest, currentVersion) > 0,
                releaseUrl = releaseUrl,
                releaseNotes = json.optString("body").take(2_000)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isValidVersion(value: String): Boolean =
        Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$").matches(value)

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = numericVersion(left)
        val rightParts = numericVersion(right)
        for (index in 0..2) {
            val comparison = leftParts[index].compareTo(rightParts[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun numericVersion(value: String): List<Int> {
        val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(value)
            ?: throw IOException("تعذر مقارنة رقم الإصدار.")
        return (1..3).map { match.groupValues[it].toIntOrNull() ?: 0 }
    }
}

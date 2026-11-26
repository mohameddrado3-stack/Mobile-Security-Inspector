package com.msi.inspector.data.serialization

import com.msi.inspector.domain.model.DeviceSecurityEvidence
import com.msi.inspector.domain.model.DeviceSecuritySnapshot
import com.msi.inspector.domain.model.EvidenceAvailability
import org.json.JSONObject

object DeviceSecurityJsonCodec {
    fun encode(snapshot: DeviceSecuritySnapshot?): String {
        if (snapshot == null) return JSONObject.NULL.toString()
        return JSONObject().apply {
            put("androidRelease", encodeEvidence(snapshot.androidRelease))
            put("apiLevel", encodeEvidence(snapshot.apiLevel))
            put("securityPatch", encodeEvidence(snapshot.securityPatch))
            put("secureScreenLock", encodeEvidence(snapshot.secureScreenLock))
            put("storageEncryption", encodeEvidence(snapshot.storageEncryption))
            put("adbEnabled", encodeEvidence(snapshot.adbEnabled))
            put("developerOptionsEnabled", encodeEvidence(snapshot.developerOptionsEnabled))
            put("collectedAt", snapshot.collectedAt)
        }.toString()
    }

    fun decode(json: String?): DeviceSecuritySnapshot? {
        if (json.isNullOrBlank() || json == JSONObject.NULL.toString()) return null
        return runCatching {
            val item = JSONObject(json)
            DeviceSecuritySnapshot(
                androidRelease = decodeEvidence(item.optJSONObject("androidRelease")),
                apiLevel = decodeEvidence(item.optJSONObject("apiLevel")),
                securityPatch = decodeEvidence(item.optJSONObject("securityPatch")),
                secureScreenLock = decodeEvidence(item.optJSONObject("secureScreenLock")),
                storageEncryption = decodeEvidence(item.optJSONObject("storageEncryption")),
                adbEnabled = decodeEvidence(item.optJSONObject("adbEnabled")),
                developerOptionsEnabled = decodeEvidence(item.optJSONObject("developerOptionsEnabled")),
                collectedAt = item.optLong("collectedAt", 0L)
            )
        }.getOrNull()
    }

    private fun encodeEvidence(evidence: DeviceSecurityEvidence): JSONObject =
        JSONObject().apply {
            put("value", evidence.value ?: JSONObject.NULL)
            put("availability", evidence.availability.name)
        }

    private fun decodeEvidence(item: JSONObject?): DeviceSecurityEvidence {
        if (item == null) {
            return DeviceSecurityEvidence(null, EvidenceAvailability.UNAVAILABLE)
        }
        val availability = runCatching {
            EvidenceAvailability.valueOf(item.optString("availability"))
        }.getOrDefault(EvidenceAvailability.UNAVAILABLE)
        val value = if (item.isNull("value")) null else item.optString("value")
        return DeviceSecurityEvidence(value?.takeIf(String::isNotBlank), availability)
    }
}

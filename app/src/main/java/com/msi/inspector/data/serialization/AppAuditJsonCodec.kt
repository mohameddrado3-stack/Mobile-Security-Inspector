package com.msi.inspector.data.serialization

import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.domain.model.PackageMetadataState
import org.json.JSONArray
import org.json.JSONObject

object AppAuditJsonCodec {
    fun encode(applications: List<AppAudit>): String {
        val result = JSONArray()
        applications.forEach { app ->
            result.put(JSONObject().apply {
                put("label", app.label)
                put("packageName", app.packageName)
                put("version", app.version)
                put("installer", app.installer)
                put("isSystem", app.isSystem)
                put("targetSdk", app.targetSdk ?: JSONObject.NULL)
                put("firstInstallTime", app.firstInstallTime ?: JSONObject.NULL)
                put("lastUpdateTime", app.lastUpdateTime ?: JSONObject.NULL)
                put("signatureSha256", app.signingCertificateSha256 ?: JSONObject.NULL)
                put("category", app.categoryLabel ?: JSONObject.NULL)
                put("lastUsedAt", app.lastUsedAt ?: JSONObject.NULL)
                put("packageMetadataAvailable", app.packageMetadataAvailable)
                put("packageMetadataState", app.packageMetadataState.name)
                put("unclassifiedPermissions", JSONArray(app.unclassifiedPermissions))
                put("permissionsWithUnknownGrantState", JSONArray(app.permissionsWithUnknownGrantState))
                put("specialAccessStateAvailable", app.specialAccessStateAvailable)
                put("signals", JSONArray(app.signals))
                put("grantedSensitive", JSONArray(app.grantedSensitive))
                put("requestedSensitive", JSONArray(app.requestedSensitive))
                put("requestedSpecialAccess", JSONArray(app.requestedSpecialAccess))
                put("activeSpecialAccess", JSONArray(app.specialAccessReasons))
            })
        }
        return result.toString()
    }

    fun decode(json: String): List<AppAudit> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val metadataAvailable = item.optBoolean(
                    "packageMetadataAvailable",
                    item.optString("version", "غير متاح") != "غير متاح"
                )
                val metadataState = runCatching {
                    PackageMetadataState.valueOf(item.optString("packageMetadataState"))
                }.getOrDefault(
                    if (metadataAvailable) PackageMetadataState.AVAILABLE
                    else PackageMetadataState.NOT_FOUND_OR_NOT_VISIBLE
                )
                add(
                    AppAudit(
                        label = item.optString("label", "تطبيق غير معروف"),
                        packageName = item.optString("packageName"),
                        version = item.optString("version", "غير متاح"),
                        installer = item.optString("installer", "غير معروف"),
                        isSystem = item.optBoolean("isSystem", false),
                        grantedSensitive = item.stringList("grantedSensitive"),
                        requestedSensitive = item.stringList("requestedSensitive"),
                        requestedSpecialAccess = item.stringList("requestedSpecialAccess"),
                        signals = item.stringList("signals"),
                        specialAccessReasons = item.stringList("activeSpecialAccess"),
                        targetSdk = item.nullableInt("targetSdk"),
                        firstInstallTime = item.nullableLong("firstInstallTime"),
                        lastUpdateTime = item.nullableLong("lastUpdateTime"),
                        signingCertificateSha256 = item.nullableString("signatureSha256"),
                        categoryLabel = item.nullableString("category"),
                        lastUsedAt = item.nullableLong("lastUsedAt"),
                        packageMetadataAvailable = metadataAvailable,
                        packageMetadataState = metadataState,
                        unclassifiedPermissions = item.stringList("unclassifiedPermissions"),
                        specialAccessStateAvailable = item.optBoolean("specialAccessStateAvailable", false),
                        permissionsWithUnknownGrantState = item.stringList("permissionsWithUnknownGrantState")
                    )
                )
            }
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}

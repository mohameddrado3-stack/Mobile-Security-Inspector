package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit

data class PermissionCategory(
    val id: String,
    val title: String,
    val permissions: List<String>,
    val applicationCount: Int,
    val grantedApplicationCount: Int = applicationCount,
    val requestedApplicationCount: Int = applicationCount,
    val packageNames: Set<String> = emptySet()
)

object PermissionAnalyzer {
    private val categoryDefinitions = listOf(
        "camera" to "الكاميرا",
        "microphone" to "الميكروفون",
        "location" to "الموقع",
        "sms" to "الرسائل",
        "contacts" to "جهات الاتصال",
        "phone" to "الهاتف وسجل المكالمات",
        "calendar" to "التقويم",
        "storage" to "الملفات والتخزين",
        "sensors" to "النشاط ومستشعرات الجسم",
        "nearby" to "الأجهزة القريبة والبلوتوث",
        "notifications" to "الإشعارات",
        "media" to "الصور والفيديو والصوت"
    )

    fun categoryFor(permission: String): String? {
        val value = permission.lowercase()
        return when {
            value.contains("camera") -> "الكاميرا"
            value.contains("record_audio") -> "الميكروفون"
            value.contains("fine_location") ||
                value.contains("coarse_location") ||
                value.contains("background_location") -> "الموقع"
            value.contains("read_sms") ||
                value.contains("send_sms") ||
                value.contains("receive_sms") -> "الرسائل"
            value.contains("contacts") -> "جهات الاتصال"
            value.contains("call_log") ||
                value.contains("phone_state") ||
                value.contains("call_phone") -> "الهاتف وسجل المكالمات"
            value.contains("calendar") -> "التقويم"
            value.contains("external_storage") ||
                value.contains("manage_external_storage") -> "الملفات والتخزين"
            value.contains("body_sensors") ||
                value.contains("activity_recognition") -> "النشاط ومستشعرات الجسم"
            value.contains("bluetooth") ||
                value.contains("nearby_wifi") ||
                value.contains("nearby_devices") -> "الأجهزة القريبة والبلوتوث"
            value.contains("post_notifications") -> "الإشعارات"
            value.contains("read_media_images") ||
                value.contains("read_media_video") ||
                value.contains("read_media_audio") ||
                value.contains("read_media_visual_user_selected") -> "الصور والفيديو والصوت"
            else -> null
        }
    }

    fun categories(applications: List<AppAudit>): List<PermissionCategory> {
        return buildCategories(applications, includeEmpty = false)
    }

    fun dashboardCategories(applications: List<AppAudit>): List<PermissionCategory> {
        return buildCategories(applications, includeEmpty = true)
    }

    private fun buildCategories(
        applications: List<AppAudit>,
        includeEmpty: Boolean
    ): List<PermissionCategory> {
        return categoryDefinitions.mapNotNull { (id, title) ->
            val grantedApps = applications.filter { app ->
                app.grantedSensitive.any { categoryFor(it) == title }
            }
            val requestedApps = applications.filter { app ->
                app.requestedSensitive.any { categoryFor(it) == title }
            }
            val matchingApps = if (includeEmpty) {
                (grantedApps + requestedApps).distinctBy { it.packageName }
            } else {
                grantedApps
            }
            if (!includeEmpty && matchingApps.isEmpty()) return@mapNotNull null
            val permissions = if (includeEmpty) {
                (grantedApps.flatMap { it.grantedSensitive } + requestedApps.flatMap { it.requestedSensitive })
            } else {
                grantedApps.flatMap { it.grantedSensitive }
            }.filter { categoryFor(it) == title }.distinct()
            PermissionCategory(
                id = id,
                title = title,
                permissions = permissions,
                applicationCount = matchingApps.size,
                grantedApplicationCount = grantedApps.size,
                requestedApplicationCount = requestedApps.size,
                packageNames = matchingApps.map { it.packageName }.toSet()
            )
        }
    }
}

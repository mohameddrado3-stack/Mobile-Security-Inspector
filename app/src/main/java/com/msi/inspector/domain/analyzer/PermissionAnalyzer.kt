package com.msi.inspector.domain.analyzer

import com.msi.inspector.domain.model.AppAudit

data class PermissionCategory(
    val id: String,
    val title: String,
    val permissions: List<String>,
    val applicationCount: Int
)

object PermissionAnalyzer {
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
        val grouped = linkedMapOf<String, MutableSet<String>>()
        val appCounts = linkedMapOf<String, MutableSet<String>>()
        applications.forEach { app ->
            app.grantedSensitive.forEach { permission ->
                val category = categoryFor(permission)
                if (category != null) {
                    grouped.getOrPut(category) { linkedSetOf() }.add(permission)
                    appCounts.getOrPut(category) { linkedSetOf() }.add(app.packageName)
                }
            }
        }
        return grouped.map { (title, permissions) ->
            PermissionCategory(
                id = title,
                title = title,
                permissions = permissions.toList(),
                applicationCount = appCounts[title]?.size ?: 0
            )
        }.sortedByDescending { it.applicationCount }
    }
}

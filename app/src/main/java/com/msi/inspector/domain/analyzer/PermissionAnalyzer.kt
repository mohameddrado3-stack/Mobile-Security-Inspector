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

    private val permissionCategories = buildMap {
        fun add(category: String, vararg permissions: String) {
            permissions.forEach { put(it, category) }
        }

        add("الكاميرا", "android.permission.CAMERA")
        add("الميكروفون", "android.permission.RECORD_AUDIO")
        add(
            "الموقع",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION"
        )
        add(
            "الرسائل",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.SEND_SMS"
        )
        add(
            "جهات الاتصال",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS"
        )
        add(
            "الهاتف وسجل المكالمات",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP"
        )
        add(
            "التقويم",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        )
        add(
            "الملفات والتخزين",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )
        add(
            "النشاط ومستشعرات الجسم",
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.ACTIVITY_RECOGNITION"
        )
        add(
            "الأجهزة القريبة والبلوتوث",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.NEARBY_WIFI_DEVICES"
        )
        add("الإشعارات", "android.permission.POST_NOTIFICATIONS")
        add(
            "الصور والفيديو والصوت",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )
    }

    fun categoryFor(permission: String): String? = permissionCategories[permission]

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

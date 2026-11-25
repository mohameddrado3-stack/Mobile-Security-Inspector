package com.msi.inspector.domain.analyzer

object SpecialPermissionCatalog {
    fun requestLabel(permission: String): String? {
        return when (permission) {
            "android.permission.SYSTEM_ALERT_WINDOW" ->
                "طلب صلاحية الظهور فوق التطبيقات — حالة التفعيل غير مؤكدة."
            "android.permission.WRITE_SETTINGS" ->
                "طلب تعديل إعدادات النظام — حالة التفعيل غير مؤكدة."
            "android.permission.PACKAGE_USAGE_STATS" ->
                "طلب الوصول إلى إحصاءات الاستخدام — حالة التفعيل غير مؤكدة."
            "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                "طلب الوصول الشامل للملفات — حالة التفعيل غير مؤكدة."
            "android.permission.REQUEST_INSTALL_PACKAGES" ->
                "طلب صلاحية تثبيت حزم التطبيقات — حالة التفعيل غير مؤكدة."
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ->
                "طلب استثناء من تحسين البطارية — حالة التفعيل غير مؤكدة."
            "android.permission.ACCESS_NOTIFICATION_POLICY" ->
                "طلب الوصول إلى سياسة عدم الإزعاج — حالة التفعيل غير مؤكدة."
            else -> null
        }
    }
}

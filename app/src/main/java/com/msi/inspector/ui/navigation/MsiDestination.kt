package com.msi.inspector.ui.navigation

import com.msi.inspector.R

enum class MsiDestination(
    val title: String,
    val iconRes: Int,
    val showInNavigation: Boolean = true
) {
    HOME("الرئيسية", R.drawable.ic_msi_home),
    APPLICATIONS("التطبيقات", R.drawable.ic_msi_apps),
    PERMISSIONS("الصلاحيات", R.drawable.ic_msi_permissions),
    HISTORY("السجل", R.drawable.ic_msi_history),
    TIMELINE("الخط الزمني", R.drawable.ic_msi_timeline),
    TOOLS("الأدوات", R.drawable.ic_msi_tools),
    APK_SCANNER("فحص APK", R.drawable.ic_msi_shield, false),
    WEB_PROTECTION("حماية الويب", R.drawable.ic_msi_network, false)
}

package com.msi.inspector.ui.navigation

import com.msi.inspector.R

enum class MsiDestination(val title: String, val iconRes: Int) {
    HOME("الرئيسية", R.drawable.ic_msi_home),
    APPLICATIONS("التطبيقات", R.drawable.ic_msi_apps),
    PERMISSIONS("الصلاحيات", R.drawable.ic_msi_permissions),
    HISTORY("السجل", R.drawable.ic_msi_history),
    TIMELINE("الخط الزمني", R.drawable.ic_msi_timeline),
    TOOLS("الأدوات", R.drawable.ic_msi_tools)
}

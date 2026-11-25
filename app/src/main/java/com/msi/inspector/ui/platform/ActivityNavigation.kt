package com.msi.inspector.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast

fun openSystemPage(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "الإعداد غير متاح على هذا الجهاز.", Toast.LENGTH_SHORT).show()
    }
}

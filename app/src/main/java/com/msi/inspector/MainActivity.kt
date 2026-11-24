package com.msi.inspector

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var results: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 13, 24)
        window.navigationBarColor = Color.rgb(8, 13, 24)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(Color.rgb(8, 13, 24))
        }

        val title = TextView(this).apply {
            text = "MOBILE SECURITY\nINSPECTOR"
            textSize = 25f
            setTextColor(Color.rgb(112, 231, 209))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "فحص محلي لمعلومات التطبيقات المثبتة"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 18)
        }
        root.addView(subtitle)

        val scan = Button(this).apply {
            text = "بدء فحص التطبيقات"
            setOnClickListener { scanApps() }
        }
        root.addView(scan)

        summary = TextView(this).apply {
            text = "لم يبدأ الفحص بعد."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 18, 0, 12)
        }
        root.addView(summary)

        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val note = TextView(this).apply {
            text = "تنبيه: هذه نسخة أولية. النتائج مؤشرات للمراجعة وليست إثباتًا على وجود تجسس. لا يحذف التطبيق أي شيء ولا يرسل بياناتك إلى الإنترنت."
            textSize = 12f
            setTextColor(Color.rgb(173, 184, 202))
            setPadding(0, 14, 0, 0)
        }
        root.addView(note)
        setContentView(root)
    }

    private fun scanApps() {
        results.removeAllViews()
        summary.text = "جارٍ جمع قائمة التطبيقات..."
        val pm = packageManager
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        } catch (e: Exception) {
            summary.text = "تعذر قراءة قائمة التطبيقات: ${e.localizedMessage ?: "خطأ غير معروف"}"
            return
        }

        var systemCount = 0
        var userCount = 0
        apps.forEach { info ->
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) systemCount++ else userCount++
            val label = pm.getApplicationLabel(info).toString()
            val packageName = info.packageName
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
                setBackgroundColor(Color.rgb(19, 29, 45))
            }
            val name = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            val pkg = TextView(this).apply {
                text = packageName
                textSize = 11f
                setTextColor(Color.rgb(140, 177, 205))
            }
            val type = TextView(this).apply {
                text = if (isSystem) "تطبيق نظام" else "تطبيق غير مُصنّف كتطبيق نظام"
                textSize = 12f
                setTextColor(if (isSystem) Color.rgb(112, 231, 209) else Color.rgb(255, 196, 112))
            }
            card.addView(name)
            card.addView(pkg)
            card.addView(type)
            results.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 })
        }
        summary.text = "اكتمل جمع القائمة: ${apps.size} تطبيقًا\nتطبيقات نظام: $systemCount  |  تطبيقات أخرى: $userCount\nلم يتم تصنيف أي تطبيق كضار تلقائيًا."
    }
}

package com.msi.inspector

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private data class AppAudit(
    val label: String,
    val packageName: String,
    val version: String,
    val installer: String,
    val isSystem: Boolean,
    val grantedSensitive: List<String>,
    val requestedSensitive: List<String>,
    val signals: List<String>,
    val score: Int
) {
    val needsReview: Boolean get() = score >= 2
    val level: String get() = when {
        score >= 5 -> "مؤشرات متعددة — مراجعة مهمة"
        score >= 2 -> "يحتاج مراجعة"
        else -> "لا توجد مؤشرات بارزة"
    }
}

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var scanButton: Button
    private lateinit var search: EditText
    private lateinit var results: LinearLayout
    private lateinit var filterRow: LinearLayout
    private var allResults: List<AppAudit> = emptyList()
    private var showingReviewOnly = false
    private var scanRunning = false
    private var vpnActive = false

    private val bg = Color.rgb(8, 13, 24)
    private val panel = Color.rgb(19, 29, 45)
    private val mint = Color.rgb(112, 231, 209)
    private val muted = Color.rgb(166, 181, 201)
    private val white = Color.WHITE
    private val amber = Color.rgb(255, 196, 112)
    private val red = Color.rgb(255, 132, 132)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()
        renderEmptyState()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(13), dp(15), dp(13))
        setBackgroundColor(panel)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            setBackgroundColor(bg)
        }
        root.addView(label("MOBILE SECURITY INSPECTOR", 20f, mint, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(label("مركز فحص أمان التطبيقات", 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(13))
        })

        val overview = card()
        overview.addView(label("لوحة الأمان", 17f, white, true))
        summary = label("لم يتم إجراء فحص أمني بعد.", 14f, muted).apply {
            setPadding(0, dp(8), 0, dp(4))
        }
        overview.addView(summary)
        status = label("جاهز للفحص", 12f, mint)
        overview.addView(status)
        root.addView(overview, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        scanButton = Button(this).apply {
            text = "بدء الفحص الأمني"
            isAllCaps = false
            setOnClickListener { startAudit() }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply { bottomMargin = dp(9) })

        search = EditText(this).apply {
            hint = "ابحث باسم التطبيق أو اسم الحزمة"
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundColor(panel)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderResults() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(47)).apply { bottomMargin = dp(7) })

        filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(filterRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        updateFilterButton()

        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label(
            "الفحص محلي. التنبيهات مؤشرات للمراجعة وليست إثباتًا على وجود تجسس أو برمجية ضارة.",
            11f, muted
        ).apply { setPadding(0, dp(9), 0, 0) })
        setContentView(root)
    }

    private fun updateFilterButton() {
        filterRow.removeAllViews()
        filterRow.addView(Button(this).apply {
            text = if (showingReviewOnly) "عرض كل التطبيقات" else "عرض ما يحتاج مراجعة"
            isAllCaps = false
            setOnClickListener {
                showingReviewOnly = !showingReviewOnly
                updateFilterButton()
                renderResults()
            }
        })
        filterRow.addView(label(
            if (allResults.isEmpty()) "  لم تُحلل التطبيقات بعد" else "  " + allResults.size + " نتيجة",
            12f, muted
        ).apply { gravity = Gravity.CENTER_VERTICAL })
    }

    private fun renderEmptyState() {
        results.removeAllViews()
        val box = card()
        box.addView(label("الفحص الأمني لم يبدأ", 17f, white, true))
        box.addView(label(
            "اضغط بدء الفحص لتحليل الصلاحيات الحساسة ومصدر التثبيت وخدمات إمكانية الوصول وتطبيقات إدارة الجهاز.",
            14f, muted
        ).apply { setPadding(0, dp(8), 0, 0) })
        results.addView(box)
    }

    private fun startAudit() {
        if (scanRunning) return
        scanRunning = true
        scanButton.isEnabled = false
        scanButton.text = "جارٍ الفحص..."
        progress.visibility = View.VISIBLE
        status.text = "يتم جمع البيانات وتحليل المؤشرات..."
        results.removeAllViews()
        results.addView(label("يتم فحص التطبيقات محليًا. قد يستغرق ذلك بعض الوقت.", 14f, muted)
            .apply { setPadding(dp(8), dp(16), dp(8), dp(16)) })

        worker.execute {
            try {
                val audited = performAudit()
                mainHandler.post {
                    allResults = audited.sortedWith(
                        compareByDescending<AppAudit> { it.score }.thenBy { it.label.lowercase(Locale.ROOT) }
                    )
                    scanRunning = false
                    scanButton.isEnabled = true
                    scanButton.text = "إعادة الفحص"
                    progress.visibility = View.GONE
                    updateFilterButton()
                    renderSummary()
                    renderResults()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    scanRunning = false
                    scanButton.isEnabled = true
                    scanButton.text = "إعادة المحاولة"
                    progress.visibility = View.GONE
                    status.text = "تعذر إكمال الفحص"
                    results.removeAllViews()
                    results.addView(label("حدث خطأ أثناء الفحص: " + (e.localizedMessage ?: "خطأ غير معروف"), 14f, red))
                }
            }
        }
    }

    private fun performAudit(): List<AppAudit> {
        val pm = packageManager
        val accessibilityPackages = enabledAccessibilityPackages()
        val adminPackages = activeAdminPackages()
        vpnActive = isVpnActive()
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val output = ArrayList<AppAudit>(installed.size)

        installed.forEach { appInfo ->
            val pkg = appInfo.packageName
            val packageInfo = try { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) } catch (_: Exception) { null }
            val requested = packageInfo?.requestedPermissions?.toList().orEmpty()
            val flags = packageInfo?.requestedPermissionsFlags
            val granted = requested.filterIndexed { index, _ ->
                flags != null && index < flags.size &&
                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val grantedSensitive = granted.filter { isDangerousPermission(it) }
            val requestedSensitive = requested.filter { isDangerousPermission(it) }
            val categories = grantedSensitive.mapNotNull { permissionCategory(it) }.distinct()
            val isAccessibility = accessibilityPackages.contains(pkg)
            val isAdmin = adminPackages.contains(pkg)
            val installer = installerLabel(pkg)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val signals = ArrayList<String>()
            var score = 0

            if (isAccessibility) {
                signals.add("للتطبيق خدمة إمكانية وصول مفعّلة؛ راجع سبب احتياجه إليها.")
                score += 3
            }
            if (isAdmin) {
                signals.add("التطبيق مفعّل كمسؤول جهاز؛ راجع سبب منحه هذه الصلاحية.")
                score += 2
            }
            if (categories.isNotEmpty()) {
                signals.add("صلاحيات حساسة ممنوحة ضمن: " + categories.joinToString("، ") + ".")
                score += categories.size.coerceAtMost(3)
            }
            if (installer == "غير معروف") {
                signals.add("تعذّر تحديد مصدر التثبيت؛ هذه المعلومة وحدها لا تعني أن التطبيق ضار.")
                score += 1
            }
            if (requestedSensitive.size > grantedSensitive.size && requestedSensitive.isNotEmpty()) {
                signals.add("يطلب التطبيق صلاحيات حساسة إضافية غير ممنوحة حاليًا.")
            }
            if (signals.isEmpty()) signals.add("لم تظهر المؤشرات المحددة في هذا الفحص.")

            output.add(AppAudit(
                label = pm.getApplicationLabel(appInfo).toString(),
                packageName = pkg,
                version = packageInfo?.versionName?.takeIf { it.isNotBlank() } ?: "غير متاح",
                installer = installer,
                isSystem = isSystem,
                grantedSensitive = grantedSensitive,
                requestedSensitive = requestedSensitive,
                signals = signals,
                score = score
            ))
        }
        return output
    }

    private fun isDangerousPermission(name: String): Boolean = try {
        val info = packageManager.getPermissionInfo(name, 0)
        (info.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS) != 0
    } catch (_: Exception) { false }

    private fun permissionCategory(permission: String): String? {
        val p = permission.lowercase(Locale.ROOT)
        return when {
            p.contains("camera") -> "الكاميرا"
            p.contains("record_audio") -> "الميكروفون"
            p.contains("fine_location") || p.contains("coarse_location") || p.contains("background_location") -> "الموقع"
            p.contains("read_sms") || p.contains("send_sms") || p.contains("receive_sms") -> "الرسائل"
            p.contains("contacts") -> "جهات الاتصال"
            p.contains("call_log") || p.contains("phone_state") || p.contains("call_phone") -> "الهاتف وسجل المكالمات"
            p.contains("calendar") -> "التقويم"
            p.contains("external_storage") || p.contains("manage_external_storage") -> "الملفات والتخزين"
            p.contains("body_sensors") -> "مستشعرات الجسم"
            else -> null
        }
    }

    private fun installerLabel(packageName: String): String = try {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val source = packageManager.getInstallSourceInfo(packageName)
            val installer = source.installingPackageName ?: source.initiatingPackageName
            when (installer) {
                "com.android.vending" -> "Google Play"
                "com.sec.android.app.samsungapps" -> "Galaxy Store"
                null -> "غير معروف"
                else -> installer
            }
        } else {
            @Suppress("DEPRECATION")
            val installer = packageManager.getInstallerPackageName(packageName)
            when (installer) {
                "com.android.vending" -> "Google Play"
                null -> "غير معروف"
                else -> installer
            }
        }
    } catch (_: Exception) { "غير معروف" }

    private fun enabledAccessibilityPackages(): Set<String> {
        val raw = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return emptySet()
        return raw.split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()
    }

    private fun activeAdminPackages(): Set<String> = try {
        val manager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        manager.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }

    private fun isVpnActive(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) { false }

    private fun renderSummary() {
        val review = allResults.count { it.needsReview }
        val elevated = allResults.count { it.score >= 5 }
        summary.text = "تم تحليل " + allResults.size + " تطبيقًا\nتحتاج مراجعة: " + review +
            "  •  مؤشرات متعددة: " + elevated + "\nVPN نشط على الجهاز: " + if (vpnActive) "نعم" else "لا"
        status.text = "آخر فحص: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        status.setTextColor(mint)
    }

    private fun renderResults() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase(Locale.ROOT) else ""
        val visible = allResults.filter {
            (!showingReviewOnly || it.needsReview) &&
                (query.isEmpty() || it.label.lowercase(Locale.ROOT).contains(query) ||
                    it.packageName.lowercase(Locale.ROOT).contains(query))
        }
        if (visible.isEmpty()) {
            results.addView(label(
                if (allResults.isEmpty()) "ابدأ الفحص لعرض نتائج التحليل." else "لا توجد نتائج مطابقة.",
                14f, muted
            ).apply { setPadding(dp(8), dp(18), dp(8), dp(18)) })
            return
        }
        visible.forEach { audit ->
            val item = card()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = ImageView(this).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(audit.packageName)) } catch (_: Exception) {}
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(11) })
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(label(audit.label, 16f, white, true))
            names.addView(label(audit.packageName, 10f, muted))
            row.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(row)
            val color = when {
                audit.score >= 5 -> red
                audit.score >= 2 -> amber
                else -> mint
            }
            item.addView(label(audit.level, 12f, color, true).apply { setPadding(0, dp(9), 0, dp(4)) })
            item.addView(label(
                (if (audit.isSystem) "تطبيق نظام" else "تطبيق مستخدم") + "  •  مصدر التثبيت: " + audit.installer,
                11f, muted
            ))
            item.setOnClickListener { showDetails(audit) }
            results.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun showDetails(a: AppAudit) {
        val details = buildString {
            append("اسم الحزمة: " + a.packageName + "\n")
            append("الإصدار: " + a.version + "\n")
            append("النوع: " + (if (a.isSystem) "تطبيق نظام" else "تطبيق مستخدم") + "\n")
            append("مصدر التثبيت: " + a.installer + "\n\n")
            append("مؤشرات الفحص:\n")
            a.signals.forEach { append("• " + it + "\n") }
            append("\nالصلاحيات الحساسة الممنوحة:\n")
            append(if (a.grantedSensitive.isEmpty()) "لا توجد ضمن الصلاحيات التي أمكن قراءتها." else a.grantedSensitive.joinToString("\n"))
            append("\n\nالصلاحيات الحساسة المطلوبة:\n")
            append(if (a.requestedSensitive.isEmpty()) "لا توجد ضمن الصلاحيات التي أمكن قراءتها." else a.requestedSensitive.joinToString("\n"))
            append("\n\nملاحظة: هذه مؤشرات محدودة وليست حكمًا بأن التطبيق خبيث.")
        }
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setMessage(details)
            .setPositiveButton("فتح إعدادات التطبيق") { _, _ ->
                try {
                    val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:" + a.packageName)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

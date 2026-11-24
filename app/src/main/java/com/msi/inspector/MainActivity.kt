package com.msi.inspector

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import com.msi.inspector.terminal.TerminalActivity

private data class AppAudit(
    val label: String,
    val packageName: String,
    val version: String,
    val installer: String,
    val isSystem: Boolean,
    val grantedSensitive: List<String>,
    val requestedSensitive: List<String>,
    val requestedSpecialAccess: List<String>,
    val signals: List<String>,
    val specialAccessReasons: List<String>,
    val icon: Drawable?
) {
    val needsReview: Boolean get() = specialAccessReasons.isNotEmpty()
    val level: String get() = when {
        specialAccessReasons.isNotEmpty() -> "إعداد وصول خاص نشط — راجع السبب"
        requestedSpecialAccess.isNotEmpty() -> "طلب صلاحية وصول خاص — الحالة غير مؤكدة"
        grantedSensitive.isNotEmpty() -> "صلاحيات حساسة ممنوحة — راجع التفاصيل"
        else -> "لم تُرصد المؤشرات المحددة في هذا الفحص"
    }
}

class MainActivity : Activity() {
    private enum class Screen { HOME, APPS, UPDATES, CONTROLS }
    private enum class AppFilter { ALL, USER, SYSTEM, ACTIVE_SPECIAL, REQUESTED_SPECIAL, SENSITIVE }

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dangerousPermissionCache = HashMap<String, Boolean>()

    private var currentScreen = Screen.HOME
    private var allResults: List<AppAudit> = emptyList()
    private var appFilter = AppFilter.ALL
    private var visibleAppLimit = 60
    private var updatesLimit = 50
    private var scanRunning = false
    private var vpnActive = false
    private var lastScanText = "لم يتم إجراء فحص بعد"

    private lateinit var contentHost: LinearLayout
    private lateinit var navHost: LinearLayout
    private lateinit var scanButton: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var searchInput: EditText
    private lateinit var appFilterHost: LinearLayout
    private lateinit var appResults: LinearLayout
    private lateinit var homeSummary: TextView
    private lateinit var homeStatus: TextView

    private val bg = Color.rgb(7, 12, 23)
    private val panel = Color.rgb(17, 27, 43)
    private val panelLight = Color.rgb(24, 38, 57)
    private val mint = Color.rgb(110, 229, 205)
    private val muted = Color.rgb(161, 178, 198)
    private val white = Color.rgb(245, 248, 252)
    private val amber = Color.rgb(255, 190, 105)
    private val red = Color.rgb(255, 126, 133)
    private val blue = Color.rgb(124, 176, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
        renderScreen()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(panel)
    }

    private fun makeButton(
        title: String,
        fill: Int = panelLight,
        foreground: Int = white,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = title
        textSize = 13f
        setTextColor(foreground)
        gravity = Gravity.CENTER
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = rounded(fill, 14)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun addGap(parent: LinearLayout, height: Int = 9) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(12))
        }
        header.addView(text("MSI  /  MOBILE SECURITY", 18f, mint, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
        })
        header.addView(text("إدارة ومراجعة أمان التطبيقات", 13f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        contentHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))

        navHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
        }
        root.addView(navHost, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        renderNavigation()
    }

    private fun renderNavigation() {
        if (!::navHost.isInitialized) return
        navHost.removeAllViews()
        val entries = listOf(
            Screen.HOME to "الرئيسية",
            Screen.APPS to "التطبيقات",
            Screen.UPDATES to "التحديثات",
            Screen.CONTROLS to "الأدوات"
        )
        entries.forEach { (screen, title) ->
            val selected = currentScreen == screen
            val item = TextView(this).apply {
                text = title
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) bg else muted)
                setPadding(dp(5), dp(12), dp(5), dp(12))
                background = rounded(if (selected) mint else panel, 12)
                setOnClickListener {
                    currentScreen = screen
                    renderScreen()
                }
            }
            navHost.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
    }

    private fun renderScreen() {
        if (!::contentHost.isInitialized) return
        contentHost.removeAllViews()
        when (currentScreen) {
            Screen.HOME -> renderHome()
            Screen.APPS -> renderApps()
            Screen.UPDATES -> renderUpdates()
            Screen.CONTROLS -> renderControls()
        }
        renderNavigation()
    }

    private fun page(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(12))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        contentHost.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        return body
    }

    private fun sectionTitle(parent: LinearLayout, title: String, subtitle: String? = null) {
        parent.addView(text(title, 17f, white, true))
        if (subtitle != null) {
            addGap(parent, 4)
            parent.addView(text(subtitle, 12f, muted))
        }
        addGap(parent, 10)
    }

    private fun renderHome() {
        val body = page()
        val hero = card()
        hero.addView(text("مركز الأمان", 18f, white, true))
        addGap(hero, 7)
        hero.addView(text(
            "افحص مؤشرات التطبيقات المثبتة، ثم افتح إعدادات كل تطبيق لمراجعة صلاحياته بنفسك.",
            13f, muted
        ))
        addGap(hero, 12)

        homeSummary = text(
            if (allResults.isEmpty()) "لم يتم فحص التطبيقات بعد." else summaryText(),
            13f, white
        )
        hero.addView(homeSummary)
        addGap(hero, 7)
        homeStatus = text(lastScanText, 11f, mint)
        hero.addView(homeStatus)
        addGap(hero, 12)

        scanButton = makeButton(
            if (scanRunning) "جارٍ الفحص..." else if (allResults.isEmpty()) "ابدأ الفحص الأمني" else "إعادة الفحص",
            mint, bg
        ) { startAudit() }
        scanButton.isEnabled = !scanRunning
        hero.addView(scanButton, LinearLayout.LayoutParams(-1, dp(48)))
        scanProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = if (scanRunning) View.VISIBLE else View.GONE
            isIndeterminate = true
        }
        hero.addView(scanProgress, LinearLayout.LayoutParams(-1, dp(4)))
        body.addView(hero)
        addGap(body)

        sectionTitle(body, "نظرة سريعة", "لا نصنّف التطبيقات كضارة؛ نعرض إعدادات وصلاحيات محددة لتراجعها.")
        val stats = card()
        val reviewed = allResults.count { it.needsReview }
        val systemCount = allResults.count { it.isSystem }
        stats.addView(text("إجمالي التطبيقات: " + allResults.size, 14f, white, true))
        addGap(stats, 6)
        stats.addView(text("إعداد وصول خاص نشط: " + reviewed, 13f, amber))
        addGap(stats, 6)
        stats.addView(text(
            "تطبيقات طلبت إعداد وصول خاص (غير مؤكد التفعيل): " +
                allResults.count { it.requestedSpecialAccess.isNotEmpty() },
            12f, amber
        ))
        addGap(stats, 6)
        stats.addView(text("تطبيقات بصلاحيات حساسة ممنوحة: " +
            allResults.count { it.grantedSensitive.isNotEmpty() }, 12f, blue))
        addGap(stats, 6)
        stats.addView(text("تطبيقات نظام: " + systemCount, 13f, muted))
        addGap(stats, 6)
        stats.addView(text("VPN على الجهاز: " + if (vpnActive) "نشط" else "غير نشط أو غير متاح", 13f, blue))
        body.addView(stats)
        addGap(body)

        sectionTitle(body, "اختصارات")
        val shortcuts = card()
        shortcuts.addView(makeButton("عرض التطبيقات المثبتة") {
            currentScreen = Screen.APPS
            renderScreen()
        })
        addGap(shortcuts, 7)
        shortcuts.addView(makeButton("مراجعة التحديثات") {
            currentScreen = Screen.UPDATES
            renderScreen()
        })
        addGap(shortcuts, 7)
        shortcuts.addView(makeButton("إعدادات التطبيقات والصلاحيات") {
            currentScreen = Screen.CONTROLS
            renderScreen()
        })
        addGap(shortcuts, 7)
        shortcuts.addView(makeButton("مشاركة تقرير الفحص") {
            confirmShareReport()
        })
        body.addView(shortcuts)
        addGap(body)
        body.addView(text(
            "خصوصيتك: التحليل يتم على الهاتف، ولا يرسل قائمة التطبيقات أو نتائج الفحص إلى خادم.",
            11f, muted
        ))
    }

    private fun summaryText(): String {
        val review = allResults.count { it.needsReview }
        return "تم تحليل " + allResults.size + " تطبيقًا\n" +
            "إعدادات وصول خاصة نشطة: " + review +
            "  •  صلاحيات حساسة ممنوحة: " + allResults.count { it.grantedSensitive.isNotEmpty() } +
            "  •  طلبات وصول خاص غير مؤكدة: " +
            allResults.count { it.requestedSpecialAccess.isNotEmpty() }
    }

    private fun renderApps() {
        val body = page()
        sectionTitle(body, "التطبيقات المثبتة", "ابحث بالاسم أو اسم الحزمة، ثم استخدم الفلاتر لتضييق النتائج.")
        searchInput = EditText(this).apply {
            hint = "بحث بالاسم أو اسم الحزمة"
            textSize = 14f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(panel)
        }
        body.addView(searchInput, LinearLayout.LayoutParams(-1, dp(47)))
        addGap(body, 8)
        appFilterHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appFilterHost, LinearLayout.LayoutParams(-1, -2))
        addGap(body, 8)
        appResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appResults)
        renderAppFilters()
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                visibleAppLimit = 60
                renderAppList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderAppList()
    }

    private fun renderAppFilters() {
        if (!::appFilterHost.isInitialized) return
        appFilterHost.removeAllViews()
        val rows = listOf(
            listOf(
                AppFilter.ALL to "الكل",
                AppFilter.USER to "المثبتة",
                AppFilter.SYSTEM to "النظام"
            ),
            listOf(
                AppFilter.ACTIVE_SPECIAL to "وصول خاص نشط",
                AppFilter.REQUESTED_SPECIAL to "طلب وصول خاص",
                AppFilter.SENSITIVE to "صلاحيات حساسة"
            )
        )
        rows.forEach { rowFilters ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowFilters.forEach { (filter, title) ->
                val selected = appFilter == filter
                row.addView(makeButton(
                    title,
                    if (selected) mint else panel,
                    if (selected) bg else white
                ) {
                    appFilter = filter
                    visibleAppLimit = 60
                    renderAppFilters()
                    renderAppList()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                    bottomMargin = dp(6)
                })
            }
            appFilterHost.addView(row)
        }
    }

    private fun renderAppList() {
        if (!::appResults.isInitialized) return
        appResults.removeAllViews()
        if (allResults.isEmpty()) {
            appResults.addView(text("لا توجد نتائج بعد. ارجع إلى الرئيسية وابدأ الفحص.", 13f, muted))
            return
        }
        val query = if (::searchInput.isInitialized) {
            searchInput.text.toString().trim().lowercase(Locale.ROOT)
        } else ""
        val visible = allResults.filter { audit ->
            val filterMatch = when (appFilter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !audit.isSystem
                AppFilter.SYSTEM -> audit.isSystem
                AppFilter.ACTIVE_SPECIAL -> audit.specialAccessReasons.isNotEmpty()
                AppFilter.REQUESTED_SPECIAL -> audit.requestedSpecialAccess.isNotEmpty()
                AppFilter.SENSITIVE -> audit.grantedSensitive.isNotEmpty()
            }
            filterMatch && (query.isEmpty() ||
                audit.label.lowercase(Locale.ROOT).contains(query) ||
                audit.packageName.lowercase(Locale.ROOT).contains(query))
        }
        appResults.addView(text(
            "النتائج: " + visible.size + " تطبيقًا" +
                if (visible.size > visibleAppLimit) " — يتم عرض " + visibleAppLimit + " حاليًا" else "",
            11f, muted
        ))
        addGap(appResults, 7)
        if (visible.isEmpty()) {
            appResults.addView(text("لا توجد تطبيقات مطابقة لهذا البحث أو الفلتر.", 13f, muted))
            return
        }
        visible.take(visibleAppLimit).forEach { audit ->
            val item = card()
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val iconView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(audit.icon)
                background = rounded(panelLight, 12)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            heading.addView(iconView, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                marginEnd = dp(12)
            })
            val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titleColumn.addView(text(audit.label, 15f, white, true))
            addGap(titleColumn, 4)
            titleColumn.addView(text(audit.packageName, 10f, muted))
            heading.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(heading)
            addGap(item, 9)
            val statusColor = when {
                audit.specialAccessReasons.isNotEmpty() -> amber
                audit.requestedSpecialAccess.isNotEmpty() -> amber
                audit.grantedSensitive.isNotEmpty() -> blue
                else -> mint
            }
            item.addView(text(audit.level, 12f, statusColor, true))
            addGap(item, 4)
            item.addView(text(
                (if (audit.isSystem) "تطبيق نظام" else "تطبيق مثبت") +
                    "  •  الإصدار " + audit.version +
                    "  •  صلاحيات حساسة ممنوحة: " + audit.grantedSensitive.size,
                11f, muted
            ))
            addGap(item, 8)
            item.addView(makeButton("التفاصيل وإعدادات التطبيق") { showDetails(audit) })
            appResults.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        if (visible.size > visibleAppLimit) {
            appResults.addView(makeButton("عرض 60 تطبيقًا إضافيًا") {
                visibleAppLimit += 60
                renderAppList()
            })
        }
    }

    private fun renderUpdates() {
        val body = page()
        val intro = card()
        intro.addView(text("مراجعة تحديثات التطبيقات", 17f, white, true))
        addGap(intro, 7)
        intro.addView(text(
            "نعرض الإصدار المثبت ومصدر التثبيت المعروف. لا يستطيع Android إعطاء تطبيق عادي قائمة موحدة بكل التحديثات المتاحة؛ المتجر الأصلي هو المرجع.",
            12.5f, muted
        ))
        addGap(intro, 8)
        intro.addView(text(
            "افتح صفحة التطبيق داخل متجره، ثم راجع بنفسك إن كان هناك تحديث.",
            12f, mint
        ))
        body.addView(intro)
        addGap(body)
        sectionTitle(body, "التطبيقات المثبتة", "تُعرض على دفعات حتى لا تتكدس واجهة الهاتف.")
        if (allResults.isEmpty()) {
            body.addView(text("ابدأ الفحص أولًا حتى تظهر قائمة التطبيقات.", 13f, muted))
            return
        }
        val userApps = allResults.filter { !it.isSystem }
        body.addView(text(
            "عدد التطبيقات المثبتة: " + userApps.size +
                " — المعروض حاليًا: " + minOf(userApps.size, updatesLimit),
            11f, muted
        ))
        addGap(body, 7)
        userApps.take(updatesLimit).forEach { audit ->
            val item = card()
            item.addView(text(audit.label, 14f, white, true))
            addGap(item, 4)
            item.addView(text("الإصدار الحالي: " + audit.version, 11f, muted))
            addGap(item, 4)
            item.addView(text("مصدر التثبيت: " + audit.installer, 11f, muted))
            addGap(item, 8)
            when (audit.installer) {
                "Google Play" -> item.addView(makeButton("فتح صفحة التطبيق في Google Play", mint, bg) {
                    openPlayStore(audit.packageName)
                })
                "Galaxy Store" -> item.addView(text(
                    "تم التعرف على Galaxy Store كمصدر تثبيت. افتح المتجر وابحث عن التطبيق لمراجعة تحديثه.",
                    11f, amber
                ))
                else -> item.addView(text(
                    "لم يتم تأكيد متجر لهذا التطبيق. راجع التحديث من المتجر أو المصدر الذي ثبّت التطبيق.",
                    11f, amber
                ))
            }
            body.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        if (userApps.size > updatesLimit) {
            body.addView(makeButton("عرض 50 تطبيقًا إضافيًا") {
                updatesLimit += 50
                renderScreen()
            })
        }
    }

    private fun renderControls() {
        val body = page()
        val intro = card()
        intro.addView(text("أدوات إدارة النظام", 17f, white, true))
        addGap(intro, 7)
        intro.addView(text(
            "هذه الاختصارات تفتح صفحات Android الرسمية. أنت من يراجع ويغيّر الإعدادات؛ التطبيق لا يمنح أو يسحب صلاحيات التطبيقات الأخرى نيابةً عنك.",
            12.5f, muted
        ))
        body.addView(intro)
        addGap(body)
        sectionTitle(body, "MS Terminal", "طرفية حقيقية بجلسة PTY. البيئة الحالية محدودة بصلاحيات Android؛ Debian وAPT سيُضافان بعد تجهيز بيئة Linux والتحقق منها.")
        val terminalCard = card()
        terminalCard.addView(text("افتح جلسة الطرفية", 14f, white, true))
        addGap(terminalCard, 8)
        terminalCard.addView(makeButton("تشغيل MS Terminal", mint, bg) {
            safeStart(Intent(this, TerminalActivity::class.java))
        })
        body.addView(terminalCard)
        addGap(body)
        sectionTitle(body, "إعدادات الجهاز")
        val actions = card()
        actions.addView(makeButton("إدارة التطبيقات المثبتة") {
            openSystemSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        })
        addGap(actions, 7)
        actions.addView(makeButton("خدمات إمكانية الوصول") {
            openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        })
        addGap(actions, 7)
        actions.addView(makeButton("إعدادات الأمان ومسؤولو الجهاز") {
            openSystemSettings(Settings.ACTION_SECURITY_SETTINGS)
        })
        addGap(actions, 7)
        actions.addView(makeButton("إعدادات الإشعارات") {
            openSystemSettings(Settings.ACTION_SETTINGS)
        })
        body.addView(actions)
        addGap(body)
        sectionTitle(body, "آخر معلومات الفحص")
        val info = card()
        info.addView(text("آخر فحص: " + lastScanText, 12f, muted))
        addGap(info, 5)
        info.addView(text(
            "حالة VPN على الجهاز: " + if (vpnActive) "نشط" else "غير نشط أو غير متاح",
            12f, blue
        ))
        addGap(info, 5)
        info.addView(text(
            "خدمات إمكانية الوصول المفعّلة وتطبيقات إدارة الجهاز تُعرض كمؤشرات للمراجعة فقط.",
            11f, muted
        ))
        body.addView(info)
        addGap(body)
        body.addView(text(
            "لن يطلب التطبيق صلاحيات تشغيل خدمات Accessibility أو إدارة الجهاز لنفسه؛ فهو لا يحتاجها لإجراء الفحص الحالي.",
            11f, mint
        ))
    }

    private fun confirmShareReport() {
        if (allResults.isEmpty()) {
            Toast.makeText(this, "ابدأ الفحص قبل إنشاء التقرير.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("مشاركة تقرير الفحص")
            .setMessage(
                "سيحتوي التقرير على أسماء التطبيقات وأسماء الحزم والإصدارات ومؤشرات الصلاحيات. " +
                    "لن يُرسل تلقائيًا؛ ستختار أنت التطبيق أو الجهة التي تشاركه معها."
            )
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("متابعة") { _, _ -> shareAuditReport() }
            .show()
    }

    private fun shareAuditReport() {
        val report = StringBuilder()
        report.append("Mobile Security Inspector — تقرير محلي\n")
        report.append("آخر فحص: ").append(lastScanText).append("\n")
        report.append("عدد التطبيقات: ").append(allResults.size).append("\n")
        report.append("إعدادات وصول خاصة نشطة: ")
            .append(allResults.count { it.needsReview }).append("\n")
        report.append("تطبيقات بها صلاحيات حساسة ممنوحة: ")
            .append(allResults.count { it.grantedSensitive.isNotEmpty() }).append("\n\n")
        allResults.take(300).forEach { audit ->
            report.append("التطبيق: ").append(audit.label).append("\n")
            report.append("الحزمة: ").append(audit.packageName).append("\n")
            report.append("الإصدار: ").append(audit.version).append("\n")
            report.append("مصدر التثبيت: ").append(audit.installer).append("\n")
            if (audit.specialAccessReasons.isNotEmpty()) {
                report.append("إعداد وصول خاص نشط: ")
                    .append(audit.specialAccessReasons.joinToString("؛ ")).append("\n")
            }
            if (audit.requestedSpecialAccess.isNotEmpty()) {
                report.append("إعدادات خاصة مطلوبة (غير مؤكدة التفعيل): ")
                    .append(audit.requestedSpecialAccess.joinToString("؛ ")).append("\n")
            }
            if (audit.grantedSensitive.isNotEmpty()) {
                report.append("صلاحيات حساسة ممنوحة: ")
                    .append(audit.grantedSensitive.joinToString("؛ ")).append("\n")
            }
            report.append("\n")
        }
        if (allResults.size > 300) {
            report.append("\nتم اختصار تفاصيل التقرير إلى أول 300 تطبيق من أصل ")
                .append(allResults.size).append(".")
        }
        report.append("\nتنبيه: هذا التقرير يعرض مؤشرات من Android ولا يثبت أن أي تطبيق ضار.")
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mobile Security Inspector — تقرير الفحص")
            putExtra(Intent.EXTRA_TEXT, report.toString())
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير"))
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح قائمة المشاركة.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAudit() {
        if (scanRunning) return
        scanRunning = true
        if (currentScreen != Screen.HOME) currentScreen = Screen.HOME
        renderScreen()
        scanButton.isEnabled = false
        scanButton.text = "جارٍ الفحص..."
        scanProgress.visibility = View.VISIBLE
        homeStatus.text = "يتم تحليل التطبيقات محليًا..."
        worker.execute {
            try {
                val audited = performAudit()
                mainHandler.post {
                    allResults = audited.sortedWith(
                        compareByDescending<AppAudit> { it.specialAccessReasons.isNotEmpty() }
                            .thenByDescending { it.requestedSpecialAccess.isNotEmpty() }
                            .thenByDescending { it.grantedSensitive.size }
                            .thenBy { it.label.lowercase(Locale.ROOT) }
                    )
                    scanRunning = false
                    lastScanText = DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM, DateFormat.SHORT
                    ).apply {
                        timeZone = TimeZone.getTimeZone("Africa/Cairo")
                    }.format(Date())
                    renderScreen()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    scanRunning = false
                    renderScreen()
                    homeStatus.text = "تعذر إكمال الفحص: " +
                        (e.localizedMessage ?: "خطأ غير معروف")
                    homeStatus.setTextColor(red)
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
            val packageInfo = try {
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            } catch (_: Exception) {
                null
            }
            val requested = packageInfo?.requestedPermissions?.toList().orEmpty()
            val flags = packageInfo?.requestedPermissionsFlags
            val granted = requested.filterIndexed { index, _ ->
                flags != null && index < flags.size &&
                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val grantedSensitive = granted.filter { isDangerousPermission(it) }
            val requestedSensitive = requested.filter { isDangerousPermission(it) }
            val requestedSpecialAccess = requested.mapNotNull { specialPermissionRequestLabel(it) }.distinct()
            val categories = grantedSensitive.mapNotNull { permissionCategory(it) }.distinct()
            val isAccessibility = accessibilityPackages.contains(pkg)
            val isAdmin = adminPackages.contains(pkg)
            val installer = installerLabel(pkg)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val signals = ArrayList<String>()
            val specialAccessReasons = ArrayList<String>()
            if (isAccessibility) specialAccessReasons.add("خدمة إمكانية الوصول مفعّلة لهذا التطبيق.")
            if (isAdmin) specialAccessReasons.add("هذا التطبيق مفعّل كمسؤول جهاز.")
            if (specialAccessReasons.isNotEmpty()) signals.addAll(specialAccessReasons)
            if (requestedSpecialAccess.isNotEmpty()) {
                signals.add("التطبيق يطلب إعدادات وصول خاصة في ملفه، لكن حالة تفعيلها لا يمكن تأكيدها من هذا الفحص.")
            }
            if (categories.isNotEmpty()) {
                signals.add("صلاحيات Android الحساسة الممنوحة: " +
                    categories.joinToString("، ") +
                    ". قد تكون مطلوبة لوظيفة التطبيق ولا تعني أنه ضار.")
            }
            if (requestedSensitive.size > grantedSensitive.size && requestedSensitive.isNotEmpty()) {
                signals.add("هناك صلاحيات حساسة مطلوبة لكنها غير ممنوحة حاليًا.")
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
                requestedSpecialAccess = requestedSpecialAccess,
                signals = signals,
                specialAccessReasons = specialAccessReasons,
                icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null }
            ))
        }
        return output
    }

    private fun isDangerousPermission(name: String): Boolean {
        return dangerousPermissionCache.getOrPut(name) {
            try {
                val info = packageManager.getPermissionInfo(name, 0)
                (info.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS) != 0
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun specialPermissionRequestLabel(permission: String): String? {
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

    private fun permissionCategory(permission: String): String? {
        val p = permission.lowercase(Locale.ROOT)
        return when {
            p.contains("camera") -> "الكاميرا"
            p.contains("record_audio") -> "الميكروفون"
            p.contains("fine_location") || p.contains("coarse_location") ||
                p.contains("background_location") -> "الموقع"
            p.contains("read_sms") || p.contains("send_sms") ||
                p.contains("receive_sms") -> "الرسائل"
            p.contains("contacts") -> "جهات الاتصال"
            p.contains("call_log") || p.contains("phone_state") ||
                p.contains("call_phone") -> "الهاتف وسجل المكالمات"
            p.contains("calendar") -> "التقويم"
            p.contains("external_storage") || p.contains("manage_external_storage") ->
                "الملفات والتخزين"
            p.contains("body_sensors") || p.contains("activity_recognition") -> "النشاط ومستشعرات الجسم"
            p.contains("bluetooth") || p.contains("nearby_wifi") || p.contains("nearby_devices") -> "الأجهزة القريبة والبلوتوث"
            p.contains("post_notifications") -> "الإشعارات"
            p.contains("read_media_images") || p.contains("read_media_video") ||
                p.contains("read_media_audio") || p.contains("read_media_visual_user_selected") -> "الصور والفيديو والصوت"
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
                "com.sec.android.app.samsungapps" -> "Galaxy Store"
                null -> "غير معروف"
                else -> installer
            }
        }
    } catch (_: Exception) {
        "غير معروف"
    }

    private fun enabledAccessibilityPackages(): Set<String> {
        val raw = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptySet()
        return raw.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .toSet()
    }

    private fun activeAdminPackages(): Set<String> = try {
        val manager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        manager.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun showDetails(audit: AppAudit) {
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(12), dp(19), dp(5))
        }
        details.addView(text("اسم الحزمة: " + audit.packageName, 11f, muted))
        addGap(details, 5)
        details.addView(text("الإصدار: " + audit.version, 12f, white))
        addGap(details, 5)
        details.addView(text(
            "النوع: " + if (audit.isSystem) "تطبيق نظام" else "تطبيق مثبت",
            12f, muted
        ))
        addGap(details, 5)
        details.addView(text("مصدر التثبيت: " + audit.installer, 12f, muted))
        addGap(details, 9)
        details.addView(text("إعدادات الوصول الخاصة", 13f, mint, true))
        addGap(details, 4)
        details.addView(text(
            if (audit.specialAccessReasons.isEmpty()) "لم يتم رصد تفعيل Accessibility أو Device Admin لهذا التطبيق."
            else audit.specialAccessReasons.joinToString("\n"),
            11.5f, muted
        ))
        addGap(details, 8)
        details.addView(text("إعدادات وصول خاصة طلبها التطبيق", 13f, mint, true))
        addGap(details, 4)
        details.addView(text(
            if (audit.requestedSpecialAccess.isEmpty())
                "لم تُرصد طلبات الإعدادات الخاصة المعروفة في ملف التطبيق."
            else audit.requestedSpecialAccess.joinToString("\n") +
                "\nملحوظة: الطلب لا يثبت أن الإعداد مفعّل؛ راجع إعدادات Android.",
            11.5f, muted
        ))
        addGap(details, 8)
        details.addView(text("ملاحظات الفحص", 13f, mint, true))
        audit.signals.forEach { signal ->
            addGap(details, 4)
            details.addView(text("• " + signal, 11.5f, muted))
        }
        addGap(details, 8)
        details.addView(text("الصلاحيات الحساسة الممنوحة", 12f, white, true))
        addGap(details, 4)
        details.addView(text(
            if (audit.grantedSensitive.isEmpty()) "لا توجد ضمن ما أمكن قراءته."
            else audit.grantedSensitive.joinToString("\n"),
            10.5f, muted
        ))
        addGap(details, 8)
        details.addView(text("الصلاحيات الحساسة المطلوبة", 12f, white, true))
        addGap(details, 4)
        details.addView(text(
            if (audit.requestedSensitive.isEmpty()) "لا توجد ضمن ما أمكن قراءته."
            else audit.requestedSensitive.joinToString("\n"),
            10.5f, muted
        ))
        addGap(details, 8)
        details.addView(text(
            "هذه الصفحة لا تكشف البرامج الخبيثة ولا تحسب درجة خطورة. وجود صلاحية أو إعداد خاص قد يكون طبيعيًا حسب وظيفة التطبيق؛ تحقق من حاجتك إليه.",
            10.5f, amber
        ))
        addGap(details, 10)
        details.addView(makeButton("نسخ اسم الحزمة") {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("اسم الحزمة", audit.packageName))
            Toast.makeText(this, "تم نسخ اسم الحزمة", Toast.LENGTH_SHORT).show()
        })
        addGap(details, 6)
        details.addView(makeButton("فتح معلومات التطبيق والصلاحيات", mint, bg) {
            openAppSettings(audit.packageName)
        })
        addGap(details, 6)
        details.addView(makeButton("إعدادات إشعارات التطبيق") {
            openNotificationSettings(audit.packageName)
        })
        addGap(details, 6)
        details.addView(makeButton("فتح التطبيق") {
            launchApp(audit.packageName)
        })
        if (audit.installer == "Google Play") {
            addGap(details, 6)
            details.addView(makeButton("فتح صفحة التطبيق في Google Play") {
                openPlayStore(audit.packageName)
            })
        }

        val scroll = ScrollView(this)
        scroll.addView(details)
        AlertDialog.Builder(this)
            .setTitle(audit.label)
            .setView(scroll)
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun openAppSettings(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + packageName)
        )
        safeStart(intent)
    }

    private fun openNotificationSettings(packageName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra("android.provider.extra.APP_PACKAGE", packageName)
            safeStart(intent) { openAppSettings(packageName) }
        } else {
            openAppSettings(packageName)
        }
    }

    private fun openPlayStore(packageName: String) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=" + Uri.encode(packageName))
        )
        safeStart(marketIntent) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + Uri.encode(packageName))
            )
            safeStart(webIntent)
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "لا يوجد اختصار تشغيل لهذا التطبيق.", Toast.LENGTH_SHORT).show()
        } else {
            safeStart(launchIntent)
        }
    }

    private fun openSystemSettings(action: String) {
        safeStart(Intent(action))
    }

    private fun safeStart(intent: Intent, fallback: (() -> Unit)? = null) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            if (fallback != null) fallback()
            else Toast.makeText(this, "الإعداد غير متاح على هذا الجهاز.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

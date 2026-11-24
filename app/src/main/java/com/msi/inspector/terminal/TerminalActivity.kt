package com.msi.inspector.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * MS Terminal's first real PTY screen.
 *
 * This phase deliberately starts Android's app-scoped shell. The persistent Debian/PRoot
 * environment is a separate next integration step; this screen never pretends that Android
 * shell commands are Linux-distribution commands.
 */
class TerminalActivity : Activity(), TerminalSessionClient, TerminalViewClient {

    private val terminalBackground = Color.rgb(5, 10, 18)
    private val terminalSurface = Color.rgb(16, 25, 39)
    private val terminalForeground = Color.rgb(225, 239, 242)
    private val terminalAccent = Color.rgb(102, 226, 195)
    private val terminalMuted = Color.rgb(150, 169, 184)

    private lateinit var terminalView: TerminalView
    private lateinit var statusView: TextView
    private lateinit var setupPanel: LinearLayout
    private lateinit var setupInfo: TextView
    private lateinit var setupButton: TextView
    private lateinit var setupProgress: ProgressBar
    private val setupExecutor = Executors.newSingleThreadExecutor()
    private var setupRunning = false
    private var activeEnvironmentLabel = "Android Shell"
    private var session: TerminalSession? = null
    private var controlPressed = false
    private var altPressed = false
    private var shiftPressed = false
    private var fnPressed = false
    private var closing = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(
        caption: String,
        active: Boolean = false,
        action: () -> Unit
    ): TextView = TextView(this).apply {
        text = caption
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(if (active) terminalBackground else terminalForeground)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        setBackgroundColor(if (active) terminalAccent else terminalSurface)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = terminalBackground
        window.navigationBarColor = terminalBackground
        buildTerminalScreen()
        refreshEnvironmentPanel()
        startAvailableShell()
    }

    private fun buildTerminalScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(terminalBackground)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleColumn.addView(label("MS TERMINAL", 17f, terminalAccent, true))
        statusView = label("بدء جلسة الطرفية…", 11f, terminalMuted)
        titleColumn.addView(statusView)
        heading.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(button("إغلاق") { finish() })
        root.addView(heading, LinearLayout.LayoutParams(-1, -2))
        
        val limitation = label(
            "الجلسة الحالية: Android Shell داخل مساحة التطبيق. بيئة Debian وAPT ليست مفعّلة في هذه المرحلة.",
            11f, terminalMuted
        ).apply {
            setPadding(dp(9), dp(8), dp(9), dp(8))
            setBackgroundColor(terminalSurface)
        }
        root.addView(limitation, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        setupPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(terminalSurface)
        }
        setupInfo = label(
            "ثبّت Debian مرة واحدة للحصول على بيئة Linux دائمة ومدير حزم APT.",
            11f, terminalMuted
        )
        setupPanel.addView(setupInfo, LinearLayout.LayoutParams(-1, -2))
        setupButton = button("تنزيل وتثبيت Debian (حوالي 50 ميجابايت)") {
            confirmDebianInstall()
        }
        setupPanel.addView(setupButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        })
        setupProgress = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        setupPanel.addView(setupProgress, LinearLayout.LayoutParams(-1, dp(4)).apply {
            topMargin = dp(6)
        })
        root.addView(setupPanel, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })

        terminalView = TerminalView(this, null).apply {
            setBackgroundColor(Color.rgb(2, 6, 12))
            setTerminalViewClient(this@TerminalActivity)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(terminalView, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(button("CTRL", controlPressed) {
            controlPressed = !controlPressed
            refreshModifierButtons(controls)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("ALT", altPressed) {
            altPressed = !altPressed
            refreshModifierButtons(controls)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("ESC") { write("\u001b") },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("TAB") { write("\t") },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("↑") { write("\u001b[A") },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("↓") { write("\u001b[B") },
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(button("لوحة المفاتيح") {
            terminalView.requestFocus()
            val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        actions.addView(button("مسح الشاشة") { write("clear\n") },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        actions.addView(button("إعادة الجلسة") { restartShell() },
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(5)
        })

        setContentView(root)
    }

    private fun refreshModifierButtons(parent: LinearLayout) {
        // Keep modifier state in the client callbacks; visual state is updated in-place.
        for (index in 0 until minOf(2, parent.childCount)) {
            val view = parent.getChildAt(index) as? TextView ?: continue
            val active = if (index == 0) controlPressed else altPressed
            view.setTextColor(if (active) terminalBackground else terminalForeground)
            view.setBackgroundColor(if (active) terminalAccent else terminalSurface)
        }
    }

    private fun refreshEnvironmentPanel() {
        val supported = DebianEnvironment.isSupportedDevice() && android.os.Process.is64Bit()
        val installed = DebianEnvironment.isInstalled(this)
        setupPanel.visibility = if (installed) View.GONE else View.VISIBLE
        setupButton.isEnabled = supported && !setupRunning
        setupInfo.text = when {
            installed -> "Debian Trixie مثبتة بالفعل."
            !supported -> "تثبيت Debian مفعّل حاليًا على أجهزة ARM64 ذات نظام 64-bit فقط."
            else -> "تنزيل مرة واحدة (حوالي 50 ميجابايت). تُحفظ ملفات Debian والحزم داخل مساحة التطبيق."
        }
    }

    private fun confirmDebianInstall() {
        if (!DebianEnvironment.isSupportedDevice() || !android.os.Process.is64Bit()) {
            Toast.makeText(this, "هذا الجهاز غير مدعوم لتشغيل Debian في هذا الإصدار.", Toast.LENGTH_LONG).show()
            return
        }
        if (setupRunning || DebianEnvironment.isInstalled(this)) {
            refreshEnvironmentPanel()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تثبيت Debian Trixie")
            .setMessage(
                "سيتم تنزيل صورة Debian الرسمية بحجم يقارب 50 ميجابايت، ثم فكّها داخل مساحة التطبيق. " +
                    "يلزم توفر نحو 360 ميجابايت من المساحة الحرة. لن يتم الوصول إلى ملفات التطبيقات الأخرى."
            )
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تنزيل وتثبيت") { _, _ -> beginDebianInstall() }
            .show()
    }

    private fun beginDebianInstall() {
        if (setupRunning) return
        setupRunning = true
        setupButton.isEnabled = false
        setupProgress.visibility = View.VISIBLE
        setupProgress.isIndeterminate = false
        statusView.text = "تجهيز Debian…"

        setupExecutor.execute {
            try {
                DebianEnvironment.install(this) { progress ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        setupProgress.progress = progress.percent
                        statusView.text = progress.message
                    }
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setupRunning = false
                    setupProgress.visibility = View.GONE
                    setupPanel.visibility = View.GONE
                    session?.takeIf { it.pid > 0 && it.isRunning }?.finishIfRunning()
                    session = null
                    startAvailableShell()
                    Toast.makeText(this, "تم تثبيت Debian. ملفاتك وحزمك ستظل محفوظة.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setupRunning = false
                    setupProgress.visibility = View.GONE
                    setupButton.isEnabled = true
                    setupInfo.text = error.localizedMessage ?: "تعذر تثبيت Debian."
                    statusView.text = "فشل إعداد Debian؛ يمكنك إعادة المحاولة."
                    Toast.makeText(this, "فشل التثبيت: ${error.localizedMessage ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAvailableShell() {
        if (DebianEnvironment.isInstalled(this)) {
            startDebianShell()
        } else {
            startAndroidShell()
        }
    }

    private fun startDebianShell() {
        if (session?.let { it.pid > 0 && it.isRunning } == true) return
        try {
            val newSession = ProotRuntime.createDebianSession(this, this)
            session = newSession
            activeEnvironmentLabel = "Debian Trixie"
            terminalView.attachSession(newSession)
            terminalView.requestFocus()
            statusView.text = "Debian Trixie • Linux Userland • PRoot"
        } catch (error: Exception) {
            statusView.text = "تعذر تشغيل Debian"
            Toast.makeText(
                this,
                "تعذر تشغيل بيئة Debian: ${error.localizedMessage ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            activeEnvironmentLabel = "Android Shell"
            startAndroidShell()
        }
    }

    private fun startAndroidShell() {
        if (session?.let { it.pid > 0 && it.isRunning } == true) return
        try {
            val home = filesDir.absolutePath
            val env = arrayOf(
                "HOME=$home",
                "TMPDIR=${cacheDir.absolutePath}",
                "PATH=/system/bin:/system/xbin",
                "TERM=xterm-256color",
                "LANG=C.UTF-8"
            )
            activeEnvironmentLabel = "Android Shell"
            val newSession = TerminalSession(
                "/system/bin/sh",
                home,
                arrayOf("-i"),
                env,
                2000,
                this
            )
            session = newSession
            terminalView.attachSession(newSession)
            terminalView.requestFocus()
            statusView.text = "جلسة PTY حقيقية • Android Shell"
        } catch (error: Throwable) {
            statusView.text = "تعذر تشغيل محرك الطرفية"
            Toast.makeText(
                this,
                "فشل بدء الطرفية: ${error.message ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restartShell() {
        session?.takeIf { it.pid > 0 && it.isRunning }?.finishIfRunning()
        session = null
        terminalView.post { startAvailableShell() }
    }

    private fun write(value: String) {
        val active = session
        if (active == null || active.pid <= 0 || !active.isRunning) {
            Toast.makeText(this, "جلسة الطرفية غير نشطة.", Toast.LENGTH_SHORT).show()
            return
        }
        active.write(value)
        terminalView.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        terminalView.post {
            if (terminalView.mEmulator != null) {
                terminalView.setTerminalCursorBlinkerRate(500)
                terminalView.setTerminalCursorBlinkerState(true, true)
            }
        }
    }

    override fun onPause() {
        if (::terminalView.isInitialized) {
            terminalView.setTerminalCursorBlinkerState(false, false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        closing = true
        setupExecutor.shutdownNow()
        session?.takeIf { it.pid > 0 && it.isRunning }?.finishIfRunning()
        session = null
        super.onDestroy()
    }

    // TerminalSessionClient
    override fun onTextChanged(changedSession: TerminalSession) {
        runOnUiThread { if (::terminalView.isInitialized) terminalView.onScreenUpdated() }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        runOnUiThread {
            val title = changedSession.title
            statusView.text = title?.takeIf { it.isNotBlank() } ?: "جلسة PTY حقيقية • $activeEnvironmentLabel"
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        runOnUiThread {
            if (!closing) statusView.text = "انتهت جلسة $activeEnvironmentLabel • يمكنك إعادة تشغيلها"
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MS Terminal", text))
        runOnUiThread {
            Toast.makeText(this, "تم نسخ النص.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        session?.write(text)
    }

    override fun onBell(session: TerminalSession) {
        runOnUiThread {
            Toast.makeText(this, "تنبيه من الطرفية", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onColorsChanged(session: TerminalSession) {
        runOnUiThread { if (::terminalView.isInitialized) terminalView.invalidate() }
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        runOnUiThread {
            if (::terminalView.isInitialized) {
                terminalView.setTerminalCursorBlinkerState(state && !isFinishing, true)
            }
        }
    }


    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) { android.util.Log.e("MSI-Terminal", "$tag: $message") }
    override fun logWarn(tag: String, message: String) { android.util.Log.w("MSI-Terminal", "$tag: $message") }
    override fun logInfo(tag: String, message: String) { android.util.Log.i("MSI-Terminal", "$tag: $message") }
    override fun logDebug(tag: String, message: String) { android.util.Log.d("MSI-Terminal", "$tag: $message") }
    override fun logVerbose(tag: String, message: String) { android.util.Log.v("MSI-Terminal", "$tag: $message") }
    override fun logStackTraceWithMessage(tag: String, message: String, error: Exception) {
        android.util.Log.e("MSI-Terminal", "$tag: $message", error)
    }
    override fun logStackTrace(tag: String, error: Exception) {
        android.util.Log.e("MSI-Terminal", tag, error)
    }

    // TerminalViewClient
    override fun onScale(scale: Float): Float = scale.coerceIn(0.6f, 2.2f)
    override fun onSingleTapUp(event: MotionEvent) = Unit
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = controlPressed
    override fun readAltKey(): Boolean = altPressed
    override fun readShiftKey(): Boolean = shiftPressed
    override fun readFnKey(): Boolean = fnPressed
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {
        runOnUiThread {
            if (::terminalView.isInitialized && !isFinishing) {
                terminalView.setTerminalCursorBlinkerRate(500)
                terminalView.setTerminalCursorBlinkerState(true, true)
            }
        }
    }

    override fun onBackPressed() {
        if (session?.let { it.pid > 0 && it.isRunning } == true) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}

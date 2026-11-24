package com.msi.inspector.terminal

import android.app.Activity
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

    private val background = Color.rgb(5, 10, 18)
    private val surface = Color.rgb(16, 25, 39)
    private val foreground = Color.rgb(225, 239, 242)
    private val accent = Color.rgb(102, 226, 195)
    private val muted = Color.rgb(150, 169, 184)

    private lateinit var terminalView: TerminalView
    private lateinit var statusView: TextView
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
        setTextColor(if (active) background else foreground)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        setBackgroundColor(if (active) accent else surface)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        buildTerminalScreen()
        startAndroidShell()
    }

    private fun buildTerminalScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleColumn.addView(label("MS TERMINAL", 17f, accent, true))
        statusView = label("بدء جلسة الطرفية…", 11f, muted)
        titleColumn.addView(statusView)
        heading.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(button("إغلاق") { finish() })
        root.addView(heading, LinearLayout.LayoutParams(-1, -2))
        
        val limitation = label(
            "الجلسة الحالية: Android Shell داخل مساحة التطبيق. بيئة Debian وAPT ليست مفعّلة في هذه المرحلة.",
            11f, muted
        ).apply {
            setPadding(dp(9), dp(8), dp(9), dp(8))
            setBackgroundColor(surface)
        }
        root.addView(limitation, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
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
            view.setTextColor(if (active) background else foreground)
            view.setBackgroundColor(if (active) accent else surface)
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
        terminalView.post { startAndroidShell() }
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
            statusView.text = title?.takeIf { it.isNotBlank() } ?: "جلسة PTY حقيقية • Android Shell"
        }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        runOnUiThread {
            if (!closing) statusView.text = "انتهت الجلسة • يمكنك بدء جلسة جديدة"
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

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        // PID is intentionally not exposed as a security claim.
    }

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) = android.util.Log.e("MSI-Terminal", "$tag: $message")
    override fun logWarn(tag: String, message: String) = android.util.Log.w("MSI-Terminal", "$tag: $message")
    override fun logInfo(tag: String, message: String) = android.util.Log.i("MSI-Terminal", "$tag: $message")
    override fun logDebug(tag: String, message: String) = android.util.Log.d("MSI-Terminal", "$tag: $message")
    override fun logVerbose(tag: String, message: String) = android.util.Log.v("MSI-Terminal", "$tag: $message")
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

package com.msi.inspector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.msi.inspector.background.PeriodicScanScheduler
import com.msi.inspector.data.preferences.MsiPreferences
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.MsiApp
import com.msi.inspector.ui.security.AppLockScreen

class MainActivity : FragmentActivity() {
    private lateinit var appLockSettings: AppLockSettings
    private lateinit var preferences: MsiPreferences
    private var appLocked by mutableStateOf(false)
    private var authenticated = false
    private var promptActive = false
    private var shortcutSequence = 0
    private var shortcutRequest by mutableStateOf<Pair<Int, String>?>(null)
    private var themeMode by mutableStateOf(MsiThemeMode.DARK)
    private var useDynamicColor by mutableStateOf(false)
    private var periodicScanEnabled by mutableStateOf(false)
    private var scheduledHour by mutableStateOf(9)
    private var scheduledMinute by mutableStateOf(0)
    private var pendingPeriodicEnable = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingPeriodicEnable && granted) {
            persistPeriodicScanEnabled(true)
        } else if (pendingPeriodicEnable) {
            periodicScanEnabled = false
            Toast.makeText(
                this,
                "لم يتم تفعيل الفحص الدوري لأن إذن الإشعارات لم يُمنح.",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingPeriodicEnable = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        appLockSettings = AppLockSettings(this)
        preferences = MsiPreferences(this)
        appLocked = appLockSettings.isEnabled()
        loadThemePreferences()
        loadPeriodicScanPreference()
        acceptShortcutIntent(intent)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 24)
        setContent {
            MsiTheme(mode = themeMode, useDynamicColor = useDynamicColor) {
                if (appLocked) {
                    AppLockScreen(onUnlock = ::authenticate)
                } else {
                    MsiApp(
                        onAppLockChanged = ::configureAppLock,
                        shortcutRequest = shortcutRequest,
                        themeMode = themeMode,
                        useDynamicColor = useDynamicColor,
                        onThemeChanged = ::saveThemePreferences,
                        periodicScanEnabled = periodicScanEnabled,
                        onPeriodicScanChanged = ::configurePeriodicScan,
                        scheduledHour = scheduledHour,
                        scheduledMinute = scheduledMinute,
                        onScheduledTimeChanged = ::saveScanSchedule
                    )
                }
            }
        }
    }

    private fun loadThemePreferences() {
        val stored = preferences.loadTheme()
        themeMode = stored.first
        useDynamicColor = stored.second
    }

    private fun loadPeriodicScanPreference() {
        val savedTime = preferences.loadScanSchedule()
        scheduledHour = savedTime.first
        scheduledMinute = savedTime.second
        if (!preferences.isPeriodicScanEnabled()) return
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            periodicScanEnabled = true
            PeriodicScanScheduler.enable(this, scheduledHour, scheduledMinute)
        } else {
            preferences.setPeriodicScanEnabled(false)
            PeriodicScanScheduler.disable(this)
        }
    }

    private fun configurePeriodicScan(enabled: Boolean) {
        if (!enabled) {
            persistPeriodicScanEnabled(false)
            return
        }
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            persistPeriodicScanEnabled(true)
        } else {
            pendingPeriodicEnable = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun persistPeriodicScanEnabled(enabled: Boolean) {
        periodicScanEnabled = enabled
        preferences.setPeriodicScanEnabled(enabled)
        if (enabled) PeriodicScanScheduler.enable(this, scheduledHour, scheduledMinute)
        else PeriodicScanScheduler.disable(this)
    }

    private fun saveScanSchedule(hour: Int, minute: Int) {
        scheduledHour = hour.coerceIn(0, 23)
        scheduledMinute = minute.coerceIn(0, 59)
        preferences.saveScanSchedule(scheduledHour, scheduledMinute)
        if (periodicScanEnabled) {
            PeriodicScanScheduler.enable(this, scheduledHour, scheduledMinute)
        }
    }

    private fun saveThemePreferences(mode: MsiThemeMode, dynamic: Boolean) {
        themeMode = mode
        useDynamicColor = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        preferences.saveTheme(themeMode, useDynamicColor)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShortcutIntent(intent)
    }

    private fun acceptShortcutIntent(intent: android.content.Intent?) {
        val action = when (intent?.action) {
            ACTION_SHORTCUT_SCAN -> "scan"
            ACTION_SHORTCUT_PERMISSIONS -> "permissions"
            ACTION_SHORTCUT_HISTORY -> "history"
            else -> intent?.getStringExtra(EXTRA_SHORTCUT)
        } ?: return
        if (action in VALID_SHORTCUTS) {
            shortcutSequence += 1
            shortcutRequest = shortcutSequence to action
        }
    }

    private fun configureAppLock(enabled: Boolean) {
        appLockSettings.setEnabled(enabled)
        if (enabled) {
            authenticated = false
            appLocked = true
            authenticate()
        } else {
            authenticated = true
            appLocked = false
        }
    }

    private fun authenticate() {
        if (promptActive || authenticated) return
        val authenticators = allowedAuthenticators()
        val availability = BiometricManager.from(this).canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                this,
                "تعذر بدء التحقق. راجع إعدادات قفل الشاشة أو البصمة.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح MSI")
            .setSubtitle("تحقق باستخدام وسيلة حماية الجهاز")
            .setAllowedAuthenticators(authenticators)
            .build()
        promptActive = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptActive = false
                    authenticated = true
                    appLocked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptActive = false
                    Toast.makeText(this@MainActivity, "لم يتم فتح التطبيق: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        )
        try {
            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            promptActive = false
            Toast.makeText(this, "وسيلة التحقق غير متاحة حاليًا.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    override fun onStop() {
        if (
            ::appLockSettings.isInitialized &&
            appLockSettings.isEnabled() &&
            authenticated &&
            !isChangingConfigurations
        ) {
            authenticated = false
            appLocked = true
        }
        super.onStop()
    }

    companion object {
        const val EXTRA_SHORTCUT = "com.msi.inspector.shortcut"
        const val ACTION_SHORTCUT_SCAN = "com.msi.inspector.action.SCAN"
        const val ACTION_SHORTCUT_PERMISSIONS = "com.msi.inspector.action.PERMISSIONS"
        const val ACTION_SHORTCUT_HISTORY = "com.msi.inspector.action.HISTORY"
        val VALID_SHORTCUTS = setOf("scan", "permissions", "history")
    }
}

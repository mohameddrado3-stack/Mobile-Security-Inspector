package com.msi.inspector

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.MsiApp
import com.msi.inspector.ui.security.AppLockScreen

class MainActivity : FragmentActivity() {
    private lateinit var appLockSettings: AppLockSettings
    private var appLocked by mutableStateOf(false)
    private var authenticated = false
    private var promptActive = false
    private var shortcutSequence = 0
    private var shortcutRequest by mutableStateOf<Pair<Int, String>?>(null)
    private var themeMode by mutableStateOf(MsiThemeMode.DARK)
    private var useDynamicColor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        appLockSettings = AppLockSettings(this)
        appLocked = appLockSettings.isEnabled()
        loadThemePreferences()
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
                        onThemeChanged = ::saveThemePreferences
                    )
                }
            }
        }
    }

    private fun loadThemePreferences() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        themeMode = runCatching {
            MsiThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, MsiThemeMode.DARK.name)!!)
        }.getOrDefault(MsiThemeMode.DARK)
        useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    private fun saveThemePreferences(mode: MsiThemeMode, dynamic: Boolean) {
        themeMode = mode
        useDynamicColor = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putString(KEY_THEME_MODE, themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, useDynamicColor)
            .apply()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShortcutIntent(intent)
    }

    private fun acceptShortcutIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra(EXTRA_SHORTCUT) ?: return
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
        private const val PREFERENCES_NAME = "msi_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val EXTRA_SHORTCUT = "com.msi.inspector.shortcut"
        val VALID_SHORTCUTS = setOf("scan", "permissions", "history")
    }
}

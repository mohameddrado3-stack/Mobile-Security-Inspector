package com.msi.inspector

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.MsiApp
import com.msi.inspector.ui.security.AppLockScreen

class MainActivity : FragmentActivity() {
    private lateinit var appLockSettings: AppLockSettings
    private var appLocked by mutableStateOf(false)
    private var authenticated = false
    private var promptActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockSettings = AppLockSettings(this)
        appLocked = appLockSettings.isEnabled()
        window.statusBarColor = android.graphics.Color.rgb(7, 12, 23)
        window.navigationBarColor = android.graphics.Color.rgb(7, 12, 23)
        setContent {
            MsiTheme {
                if (appLocked) {
                    AppLockScreen(onUnlock = ::authenticate)
                } else {
                    MsiApp(onAppLockChanged = ::configureAppLock)
                }
            }
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
            .setTitle("فتح Mobile Security Inspector")
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
}

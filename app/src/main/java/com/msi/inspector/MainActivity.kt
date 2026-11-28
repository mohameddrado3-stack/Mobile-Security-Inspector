package com.msi.inspector

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.msi.inspector.background.MsiMonitoringController
import com.msi.inspector.background.MsiMonitoringService
import com.msi.inspector.background.PeriodicScanScheduler
import com.msi.inspector.data.preferences.MsiPreferences
import com.msi.inspector.design.theme.MsiTheme
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.security.applock.AppLockSettings
import com.msi.inspector.ui.MsiApp
import com.msi.inspector.ui.onboarding.MsiPermissionSetupDialog
import com.msi.inspector.ui.onboarding.MsiNotificationPermissionDialog
import com.msi.inspector.ui.security.AppLockScreen
import com.msi.inspector.ui.security.allowedAuthenticators

class MainActivity : FragmentActivity() {
    private lateinit var appLockSettings: AppLockSettings
    private lateinit var preferences: MsiPreferences
    private var appLocked by mutableStateOf(false)
    private var authenticated = false
    private var promptActive = false
    private var shortcutSequence = 0
    private var shortcutRequest by mutableStateOf<Pair<Int, String>?>(null)
    private var incomingSharedText by mutableStateOf<String?>(null)
    private var themeMode by mutableStateOf(MsiThemeMode.DARK)
    private var useDynamicColor by mutableStateOf(false)
    private var periodicScanEnabled by mutableStateOf(false)
    private var activeMonitoringEnabled by mutableStateOf(false)
    private var scanIntervalMinutes by mutableStateOf(PeriodicScanScheduler.DEFAULT_INTERVAL_MINUTES)
    private var permissionSetupVisible by mutableStateOf(false)
    private var permissionSetupStep by mutableStateOf(0)
    private var permissionSetupWaitingForUsageAccess = false
    private var permissionSetupUsageAccessGranted by mutableStateOf(false)
    private var notificationPermissionSettingsVisible by mutableStateOf(false)
    private var notificationPermissionFeature by mutableStateOf("")
    private var pendingNotificationSettingsReturn = false
    private var pendingPermissionSetupSettingsReturn = false
    private var pendingPeriodicEnable = false
    private var pendingMonitoringEnable = false
    private val setupNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionSetupStep = 1
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingPeriodicEnable) {
            if (granted) {
                pendingPeriodicEnable = false
                persistPeriodicScanEnabled(true)
            } else {
                showNotificationSettingsPrompt("الفحص الدوري")
            }
        }
    }

    private val monitoringNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingMonitoringEnable) {
            if (granted) {
                pendingMonitoringEnable = false
                persistActiveMonitoringEnabled(true)
            } else {
                showNotificationSettingsPrompt("المراقبة النشطة")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        loadActiveMonitoringPreference()
    }

    override fun onResume() {
        super.onResume()
        val permissionGranted = hasNotificationPermission()
        if (!MsiMonitoringController.isEnabled(this)) {
            activeMonitoringEnabled = false
        }
        if (activeMonitoringEnabled && !permissionGranted) {
            persistActiveMonitoringEnabled(false)
            Toast.makeText(this, "توقفت المراقبة لأن إذن الإشعارات لم يعد متاحًا.", Toast.LENGTH_LONG).show()
        }
        if (pendingNotificationSettingsReturn) {
            pendingNotificationSettingsReturn = false
            if (permissionGranted) {
                if (pendingPeriodicEnable) {
                    pendingPeriodicEnable = false
                    persistPeriodicScanEnabled(true)
                }
                if (pendingMonitoringEnable) {
                    pendingMonitoringEnable = false
                    persistActiveMonitoringEnabled(true)
                }
            } else {
                pendingPeriodicEnable = false
                pendingMonitoringEnable = false
                Toast.makeText(this, "لم يتم تفعيل الميزة لأن إشعارات MSI ما زالت غير مسموحة.", Toast.LENGTH_LONG).show()
            }
        }
        if (pendingPermissionSetupSettingsReturn) {
            pendingPermissionSetupSettingsReturn = false
            if (permissionGranted) permissionSetupStep = 1
        }
        permissionSetupUsageAccessGranted = hasUsageAccess()
        if (permissionSetupWaitingForUsageAccess) {
            permissionSetupWaitingForUsageAccess = false
            permissionSetupStep = 2
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        appLockSettings = AppLockSettings(this)
        preferences = MsiPreferences(this)
        permissionSetupVisible =
            preferences.isOnboardingComplete() && !preferences.isPermissionSetupComplete()
        permissionSetupUsageAccessGranted = hasUsageAccess()
        appLocked = appLockSettings.isEnabled()
        loadThemePreferences()
        loadPeriodicScanPreference()
        acceptShortcutIntent(intent)
        acceptSharedText(intent)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(7, 11, 22)
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
                        activeMonitoringEnabled = activeMonitoringEnabled,
                        onActiveMonitoringChanged = ::configureActiveMonitoring,
                        scanIntervalMinutes = scanIntervalMinutes,
                        onScanIntervalMinutesChanged = ::saveScanIntervalMinutes,
                        onOnboardingCompleted = ::onOnboardingCompleted,
                        onOpenPermissionSetup = ::openPermissionSetup,
                        sharedText = incomingSharedText,
                        onSharedTextConsumed = { incomingSharedText = null }
                    )
                }
                if (!appLocked && permissionSetupVisible) {
                    MsiPermissionSetupDialog(
                        step = permissionSetupStep,
                        notificationsGranted = hasNotificationPermission(),
                        notificationPermissionPreviouslyRequested =
                            preferences.hasRequestedNotificationPermission() ||
                                !NotificationManagerCompat.from(this).areNotificationsEnabled(),
                        usageAccessGranted = permissionSetupUsageAccessGranted,
                        onRequestNotifications = {
                            if (hasNotificationPermission()) {
                                permissionSetupStep = 1
                            } else if (
                                Build.VERSION.SDK_INT < 33 ||
                                preferences.hasRequestedNotificationPermission()
                            ) {
                                showNotificationSettingsPrompt("إشعارات الحماية")
                            } else {
                                preferences.markNotificationPermissionRequested()
                                setupNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenUsageAccess = {
                            if (hasUsageAccess()) {
                                permissionSetupUsageAccessGranted = true
                                permissionSetupStep = 2
                            } else {
                                permissionSetupWaitingForUsageAccess = true
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }.onFailure {
                                    permissionSetupWaitingForUsageAccess = false
                                    permissionSetupStep = 2
                                }
                            }
                        },
                        onSkip = {
                            permissionSetupStep = (permissionSetupStep + 1).coerceAtMost(2)
                        },
                        onFinish = ::finishPermissionSetup
                    )
                }
                if (!appLocked && notificationPermissionSettingsVisible) {
                    MsiNotificationPermissionDialog(
                        featureName = notificationPermissionFeature,
                        onOpenSettings = ::openNotificationAppSettings,
                        onDismiss = ::dismissNotificationSettingsPrompt
                    )
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun showNotificationSettingsPrompt(featureName: String) {
        notificationPermissionFeature = featureName
        notificationPermissionSettingsVisible = true
    }

    private fun dismissNotificationSettingsPrompt() {
        notificationPermissionSettingsVisible = false
        pendingPeriodicEnable = false
        pendingMonitoringEnable = false
    }

    private fun openNotificationAppSettings() {
        notificationPermissionSettingsVisible = false
        pendingNotificationSettingsReturn = pendingPeriodicEnable || pendingMonitoringEnable
        pendingPermissionSetupSettingsReturn = permissionSetupVisible && permissionSetupStep == 0
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching {
            startActivity(notificationIntent)
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }.onFailure {
                pendingNotificationSettingsReturn = false
                pendingPermissionSetupSettingsReturn = false
                pendingPeriodicEnable = false
                pendingMonitoringEnable = false
                Toast.makeText(
                    this,
                    "تعذر فتح إعدادات إشعارات MSI على هذا الجهاز.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun hasUsageAccess(): Boolean = runCatching {
        val appOps = getSystemService(AppOpsManager::class.java)
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun onOnboardingCompleted() {
        permissionSetupVisible = !preferences.isPermissionSetupComplete()
    }

    private fun openPermissionSetup() {
        permissionSetupStep = 0
        permissionSetupUsageAccessGranted = hasUsageAccess()
        permissionSetupVisible = true
    }

    private fun finishPermissionSetup() {
        preferences.setPermissionSetupComplete()
        permissionSetupVisible = false
        permissionSetupStep = 0
    }

    private fun loadThemePreferences() {
        val stored = preferences.loadTheme()
        themeMode = stored.first
        useDynamicColor = stored.second
    }

    private fun loadPeriodicScanPreference() {
        scanIntervalMinutes = preferences.loadScanIntervalMinutes()
        if (!preferences.isPeriodicScanEnabled()) return
        val permissionGranted = hasNotificationPermission()
        if (permissionGranted) {
            periodicScanEnabled = true
            PeriodicScanScheduler.enable(this, scanIntervalMinutes)
        } else {
            preferences.setPeriodicScanEnabled(false)
            PeriodicScanScheduler.disable(this)
        }
    }

    private fun configurePeriodicScan(enabled: Boolean) {
        if (!enabled) {
            pendingPeriodicEnable = false
            persistPeriodicScanEnabled(false)
            return
        }
        if (hasNotificationPermission()) {
            persistPeriodicScanEnabled(true)
        } else {
            pendingPeriodicEnable = true
            if (Build.VERSION.SDK_INT < 33 || preferences.hasRequestedNotificationPermission()) {
                showNotificationSettingsPrompt("الفحص الدوري")
            } else {
                preferences.markNotificationPermissionRequested()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun persistPeriodicScanEnabled(enabled: Boolean) {
        periodicScanEnabled = enabled
        preferences.setPeriodicScanEnabled(enabled)
        if (enabled) PeriodicScanScheduler.enable(this, scanIntervalMinutes)
        else PeriodicScanScheduler.disable(this)
    }

    private fun saveScanIntervalMinutes(intervalMinutes: Int) {
        scanIntervalMinutes = PeriodicScanScheduler.normalizeIntervalMinutes(intervalMinutes)
        preferences.saveScanIntervalMinutes(scanIntervalMinutes)
        if (periodicScanEnabled) {
            PeriodicScanScheduler.enable(this, scanIntervalMinutes)
        }
    }


    private fun loadActiveMonitoringPreference() {
        if (!MsiMonitoringController.isEnabled(this)) {
            activeMonitoringEnabled = false
            return
        }
        val permissionGranted = hasNotificationPermission()
        if (permissionGranted) {
            activeMonitoringEnabled = true
            if (!MsiMonitoringService.isRunning()) {
                runCatching { MsiMonitoringController.enable(this) }.onFailure {
                    activeMonitoringEnabled = false
                    MsiMonitoringController.disable(this)
                    Toast.makeText(
                        this,
                        "تعذر استئناف المراقبة النشطة.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            activeMonitoringEnabled = false
            MsiMonitoringController.disable(this)
        }
    }

    private fun configureActiveMonitoring(enabled: Boolean) {
        if (!enabled) {
            pendingMonitoringEnable = false
            persistActiveMonitoringEnabled(false)
            return
        }
        if (hasNotificationPermission()) {
            persistActiveMonitoringEnabled(true)
        } else {
            pendingMonitoringEnable = true
            if (Build.VERSION.SDK_INT < 33 || preferences.hasRequestedNotificationPermission()) {
                showNotificationSettingsPrompt("المراقبة النشطة")
            } else {
                preferences.markNotificationPermissionRequested()
                monitoringNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun persistActiveMonitoringEnabled(enabled: Boolean) {
        activeMonitoringEnabled = enabled
        if (!enabled) {
            MsiMonitoringController.disable(this)
            return
        }
        try {
            MsiMonitoringController.enable(this)
        } catch (_: Exception) {
            activeMonitoringEnabled = false
            MsiMonitoringController.disable(this)
            Toast.makeText(
                this,
                "تعذر تشغيل خدمة المراقبة. افتح التطبيق وحاول مرة أخرى.",
                Toast.LENGTH_LONG
            ).show()
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
        acceptSharedText(intent)
    }

    private fun acceptSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        if (!shared.isNullOrBlank()) incomingSharedText = shared.take(MAX_SHARED_TEXT_LENGTH)
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
        private const val MAX_SHARED_TEXT_LENGTH = 4096
        const val EXTRA_SHORTCUT = "com.msi.inspector.shortcut"
        const val ACTION_SHORTCUT_SCAN = "com.msi.inspector.action.SCAN"
        const val ACTION_SHORTCUT_PERMISSIONS = "com.msi.inspector.action.PERMISSIONS"
        const val ACTION_SHORTCUT_HISTORY = "com.msi.inspector.action.HISTORY"
        val VALID_SHORTCUTS = setOf("scan", "permissions", "history", "monitoring", "timeline")
    }
}

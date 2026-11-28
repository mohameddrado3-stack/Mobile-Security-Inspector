package com.msi.inspector.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.data.preferences.MsiPreferences
import com.msi.inspector.ui.navigation.MsiDestinationContent
import com.msi.inspector.ui.dialogs.ApplicationDetailsDialog
import com.msi.inspector.ui.platform.findActivity
import com.msi.inspector.ui.platform.openSystemPage
import com.msi.inspector.ui.reports.ReportShareConfirmationDialog
import com.msi.inspector.ui.onboarding.MsiOnboardingDialog
import com.msi.inspector.ui.reports.shareReport
import com.msi.inspector.ui.navigation.MsiBottomNavigation
import com.msi.inspector.ui.navigation.MsiNavigationRail
import com.msi.inspector.ui.navigation.MsiDestination

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun MsiApp(
    onAppLockChanged: (Boolean) -> Unit,
    shortcutRequest: Pair<Int, String>? = null,
    themeMode: MsiThemeMode = MsiThemeMode.DARK,
    useDynamicColor: Boolean = false,
    onThemeChanged: (MsiThemeMode, Boolean) -> Unit = { _, _ -> },
    periodicScanEnabled: Boolean = false,
    onPeriodicScanChanged: (Boolean) -> Unit = {},
    activeMonitoringEnabled: Boolean = false,
    onActiveMonitoringChanged: (Boolean) -> Unit = {},
    scanIntervalMinutes: Int = 1440,
    onScanIntervalMinutesChanged: (Int) -> Unit = {},
    onOnboardingCompleted: () -> Unit = {},
    onOpenPermissionSetup: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    viewModel: MsiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val preferences = remember(context) { MsiPreferences(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.destination) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageAccessState()
                viewModel.refreshScanHistory()
                viewModel.refreshBatteryUsage()
                if (state.destination == MsiDestination.TOOLS) viewModel.refreshDeviceSecurity()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val activity = context.findActivity()
    val windowWidthClass = (activity as? ComponentActivity)
        ?.let { calculateWindowSizeClass(it).widthSizeClass }
    val expandedWidth = when (windowWidthClass) {
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> true
        WindowWidthSizeClass.Compact -> false
        else -> configuration.screenWidthDp >= 600
    }
    var wasScanning by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScanning, state.error) {
        if (wasScanning && !state.isScanning && state.error == null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasScanning = state.isScanning
    }
    var showShareConfirmation by remember { mutableStateOf(false) }
    var webAddress by remember { mutableStateOf("") }
    var onboardingPage by remember { mutableStateOf(0) }
    var showOnboarding by remember {
        mutableStateOf(!preferences.isOnboardingComplete())
    }

    LaunchedEffect(shortcutRequest) {
        when (shortcutRequest?.second) {
            "scan" -> viewModel.startScan()
            "permissions" -> viewModel.navigate(MsiDestination.PERMISSIONS)
            "history" -> viewModel.navigate(MsiDestination.HISTORY)
            "monitoring" -> viewModel.navigate(MsiDestination.TOOLS)
            "timeline" -> viewModel.navigate(MsiDestination.TIMELINE)
        }
    }

    LaunchedEffect(sharedText) {
        sharedText?.takeIf(String::isNotBlank)?.let {
            webAddress = it
            viewModel.navigate(MsiDestination.WEB_PROTECTION)
            onSharedTextConsumed()
        }
    }

    BackHandler(enabled = state.destination != MsiDestination.HOME) {
        viewModel.navigate(MsiDestination.HOME)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            state.destination.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!expandedWidth) MsiBottomNavigation(state.destination, viewModel::navigate)
            }
        ) { padding ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (expandedWidth) MsiNavigationRail(state.destination, viewModel::navigate)
                MsiDestinationContent(
                    destination = state.destination,
                    state = state,
                    context = context,
                    viewModel = viewModel,
                    onAppLockChanged = onAppLockChanged,
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    onThemeChanged = onThemeChanged,
                    periodicScanEnabled = periodicScanEnabled,
                    onPeriodicScanChanged = onPeriodicScanChanged,
                    activeMonitoringEnabled = activeMonitoringEnabled,
                    onActiveMonitoringChanged = onActiveMonitoringChanged,
                    scanIntervalMinutes = scanIntervalMinutes,
                    onScanIntervalMinutesChanged = onScanIntervalMinutesChanged,
                    onOpenPermissionSetup = onOpenPermissionSetup,
                    webAddress = webAddress,
                    onWebAddressChanged = { webAddress = it },
                    onShareReportRequest = {
                        if (state.applications.isEmpty()) {
                            Toast.makeText(context, "ابدأ الفحص قبل إنشاء التقرير.", Toast.LENGTH_SHORT).show()
                        } else {
                            showShareConfirmation = true
                        }
                    },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
            }
        }
        state.selectedApplication?.let { application ->
            ApplicationDetailsDialog(
                application = application,
                onDismiss = { viewModel.selectApplication(null) },
                isTrusted = application.packageName in state.trustedPackages,
                isIgnored = application.packageName in state.ignoredPackages,
                onMarkTrusted = { viewModel.markApplicationTrusted(application.packageName) },
                onMarkIgnored = { viewModel.markApplicationIgnored(application.packageName) },
                onClearLabel = { viewModel.clearApplicationLabel(application.packageName) },
                onCopyPackage = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("اسم الحزمة", application.packageName)
                    )
                    Toast.makeText(context, "تم نسخ اسم الحزمة", Toast.LENGTH_SHORT).show()
                },
                onOpenSettings = {
                    openSystemPage(
                        context,
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${application.packageName}")
                        )
                    )
                },
                onOpenNotifications = {
                    val intent = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra("android.provider.extra.APP_PACKAGE", application.packageName)
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${application.packageName}")
                        )
                    }
                    openSystemPage(context, intent)
                },
                onLaunch = {
                    val launchIntent = runCatching {
                        context.packageManager.getLaunchIntentForPackage(application.packageName)
                    }.getOrNull()
                    if (launchIntent == null) {
                        Toast.makeText(context, "لا يوجد اختصار تشغيل لهذا التطبيق.", Toast.LENGTH_SHORT).show()
                    } else {
                        openSystemPage(context, launchIntent)
                    }
                },
                onOpenStore = {
                    val market = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=${Uri.encode(application.packageName)}")
                    )
                    try {
                        context.startActivity(market)
                    } catch (_: ActivityNotFoundException) {
                        openSystemPage(
                            context,
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=${Uri.encode(application.packageName)}")
                            )
                        )
                    }
                }
            )
        }
        if (showShareConfirmation) {
            ReportShareConfirmationDialog(
                onDismiss = { showShareConfirmation = false },
                onExport = { format ->
                    showShareConfirmation = false
                    shareReport(context, state.applications, state.lastScanAt, format)
                }
            )
        }
        if (showOnboarding) {
            MsiOnboardingDialog(
                page = onboardingPage,
                onPageChange = { onboardingPage = it },
                onComplete = {
                    showOnboarding = false
                    preferences.setOnboardingComplete()
                    onOnboardingCompleted()
                }
            )
        }
    }
}


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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.ui.navigation.MsiDestinationContent
import com.msi.inspector.ui.dialogs.ApplicationDetailsDialog
import com.msi.inspector.ui.platform.findActivity
import com.msi.inspector.ui.platform.openSystemPage
import com.msi.inspector.ui.reports.shareReport
import com.msi.inspector.ui.navigation.MsiBottomNavigation
import com.msi.inspector.ui.navigation.MsiNavigationRail
import com.msi.inspector.ui.navigation.MsiDestination
import com.msi.inspector.ui.reports.ReportFileFormat

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
    scheduledHour: Int = 9,
    scheduledMinute: Int = 0,
    onScheduledTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    viewModel: MsiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsageAccessState()
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
    var onboardingPage by remember { mutableStateOf(0) }
    var showOnboarding by remember {
        mutableStateOf(!context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
            .getBoolean("onboarding_complete", false))
    }

    LaunchedEffect(shortcutRequest) {
        when (shortcutRequest?.second) {
            "scan" -> viewModel.startScan()
            "permissions" -> viewModel.navigate(MsiDestination.PERMISSIONS)
            "history" -> viewModel.navigate(MsiDestination.HISTORY)
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
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
                    scheduledHour = scheduledHour,
                    scheduledMinute = scheduledMinute,
                    onScheduledTimeChanged = onScheduledTimeChanged,
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
            AlertDialog(
                onDismissRequest = { showShareConfirmation = false },
                title = { Text("مشاركة تقرير الفحص") },
                text = {
                    Text(
                        "سيحتوي التقرير على أسماء التطبيقات وأسماء الحزم والإصدارات ومؤشرات الصلاحيات وبصمات شهادات التوقيع المتاحة. لن يُرسل تلقائيًا؛ ستختار بنفسك التطبيق أو الجهة التي تشاركه معها."
                    )
                },
                confirmButton = {
                    Column {
                        TextButton(onClick = {
                            showShareConfirmation = false
                            shareReport(context, state.applications, state.lastScanAt, ReportFileFormat.TXT)
                        }) { Text("تصدير TXT") }
                        TextButton(onClick = {
                            showShareConfirmation = false
                            shareReport(context, state.applications, state.lastScanAt, ReportFileFormat.CSV)
                        }) { Text("تصدير CSV") }
                        TextButton(onClick = {
                            showShareConfirmation = false
                            shareReport(context, state.applications, state.lastScanAt, ReportFileFormat.JSON)
                        }) { Text("تصدير JSON") }
                        TextButton(onClick = {
                            showShareConfirmation = false
                            shareReport(context, state.applications, state.lastScanAt, ReportFileFormat.PDF)
                        }) { Text("تصدير PDF") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShareConfirmation = false }) { Text("إلغاء") }
                }
            )
        }
        if (showOnboarding) {
            val pages = listOf(
                "MSI يقرأ مؤشرات التطبيقات المثبتة والصلاحيات المتاحة من Android، ويعرض التفاصيل بصورة مفهومة.",
                "MSI لا يتصل بخادم ولا يرفع بيانات الفحص، ولا يمنح أو يسحب صلاحيات تطبيق آخر. أي تغيير يتم من إعدادات Android وبقرارك.",
                "قد يقرأ MSI قائمة التطبيقات المسموح للنظام بعرضها، وبيانات الحزم والصلاحيات وبعض مؤشرات الجهاز. النتائج مؤشرات وليست أحكامًا على سلامة التطبيق."
            )
            AlertDialog(
                onDismissRequest = {
                    showOnboarding = false
                    context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
                        .edit().putBoolean("onboarding_complete", true).apply()
                },
                title = { Text("مرحبًا بك في MSI — ${onboardingPage + 1}/3") },
                text = { Text(pages[onboardingPage]) },
                confirmButton = {
                    TextButton(onClick = {
                        if (onboardingPage < 2) {
                            onboardingPage += 1
                        } else {
                            showOnboarding = false
                            context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
                                .edit().putBoolean("onboarding_complete", true).apply()
                        }
                    }) { Text(if (onboardingPage < 2) "التالي" else "ابدأ") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showOnboarding = false
                        context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarding_complete", true).apply()
                    }) { Text("تخطي") }
                }
            )
        }
    }
}


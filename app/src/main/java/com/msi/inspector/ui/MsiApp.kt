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
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.analyzer.filterApplications
import com.msi.inspector.domain.model.AppAudit
import com.msi.inspector.ui.applications.ApplicationsScreen
import com.msi.inspector.ui.dashboard.DashboardScreen
import com.msi.inspector.ui.history.HistoryScreen
import com.msi.inspector.ui.navigation.MsiDestination
import com.msi.inspector.ui.permissions.PermissionsScreen
import com.msi.inspector.ui.reports.ReportFormatter
import com.msi.inspector.ui.settings.ToolsScreen
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MsiApp(
    onAppLockChanged: (Boolean) -> Unit,
    shortcutRequest: Pair<Int, String>? = null,
    themeMode: MsiThemeMode = MsiThemeMode.DARK,
    useDynamicColor: Boolean = false,
    onThemeChanged: (MsiThemeMode, Boolean) -> Unit = { _, _ -> },
    viewModel: MsiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showShareConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(shortcutRequest) {
        when (shortcutRequest?.second) {
            "scan" -> viewModel.startScan()
            "permissions" -> viewModel.navigate(MsiDestination.PERMISSIONS)
            "history" -> viewModel.navigate(MsiDestination.HISTORY)
        }
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MsiDestination.values().forEach { destination ->
                        NavigationBarItem(
                            selected = state.destination == destination,
                            onClick = { viewModel.navigate(destination) },
                            icon = {
                                Text(
                                    destination.symbol,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            label = { Text(destination.title, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = state.destination,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 14 })
                        .togetherWith(fadeOut() + slideOutHorizontally { -it / 14 })
                },
                label = "msi-screen-transition"
            ) { destination ->
                when (destination) {
                    MsiDestination.HOME -> DashboardScreen(
                        state = state,
                        onScan = viewModel::startScan,
                        onNavigate = viewModel::navigate,
                        onShareReport = {
                            if (state.applications.isEmpty()) {
                                Toast.makeText(context, "ابدأ الفحص قبل إنشاء التقرير.", Toast.LENGTH_SHORT).show()
                            } else {
                                showShareConfirmation = true
                            }
                        },
                        onOpenFilter = viewModel::updateFilter
                    )
                    MsiDestination.APPLICATIONS -> {
                        val filtered = filterApplications(state.applications, state.query, state.filter)
                            .filter { state.packageScope == null || it.packageName in state.packageScope }
                        ApplicationsScreen(
                            applications = filtered,
                            query = state.query,
                            filter = state.filter,
                            onQueryChange = viewModel::updateQuery,
                            onFilterChange = viewModel::updateFilter,
                            onSelect = viewModel::selectApplication
                        )
                    }
                    MsiDestination.PERMISSIONS -> PermissionsScreen(
                        applications = state.applications,
                        onCategorySelected = viewModel::showPackageScope
                    )
                    MsiDestination.HISTORY -> HistoryScreen(
                        history = state.history,
                        onClearHistory = viewModel::clearHistory
                    )
                    MsiDestination.TOOLS -> ToolsScreen(
                        appLockEnabled = state.appLockEnabled,
                        onToggleAppLock = { enabled ->
                            if (enabled) {
                                val result = BiometricManager.from(context).canAuthenticate(allowedAuthenticators())
                                if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                                    viewModel.setAppLockEnabled(true)
                                    onAppLockChanged(true)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "فعّل بصمة قوية أو قفل شاشة آمنًا من إعدادات الجهاز أولًا.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                viewModel.setAppLockEnabled(false)
                                onAppLockChanged(false)
                            }
                        },
                        onManageApps = {
                            openSystemPage(context, Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                        },
                        onAccessibilitySettings = {
                            openSystemPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onSecuritySettings = {
                            openSystemPage(context, Intent(Settings.ACTION_SECURITY_SETTINGS))
                        },
                        onGeneralSettings = {
                            openSystemPage(context, Intent(Settings.ACTION_SETTINGS))
                        },
                        themeMode = themeMode,
                        useDynamicColor = useDynamicColor,
                        onThemeChanged = onThemeChanged
                    )
                }
            }
        }
        state.selectedApplication?.let { application ->
            ApplicationDetailsDialog(
                application = application,
                onDismiss = { viewModel.selectApplication(null) },
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
                    TextButton(
                        onClick = {
                            showShareConfirmation = false
                            shareReport(context, state.applications, state.lastScanAt)
                        }
                    ) { Text("متابعة") }
                },
                dismissButton = {
                    TextButton(onClick = { showShareConfirmation = false }) { Text("إلغاء") }
                }
            )
        }
    }
}

@Composable
private fun ApplicationDetailsDialog(
    application: AppAudit,
    onDismiss: () -> Unit,
    onCopyPackage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLaunch: () -> Unit,
    onOpenStore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(application.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("اسم الحزمة: ${application.packageName}", fontSize = 11.sp)
                Text("الإصدار: ${application.version}", fontSize = 12.sp)
                Text("النوع: ${if (application.isSystem) "تطبيق نظام" else "تطبيق مثبت"}", fontSize = 12.sp)
                Text("مصدر التثبيت: ${application.installer}", fontSize = 12.sp)
                Text("تصنيف Android: ${application.categoryLabel ?: "غير محدد من التطبيق"}", fontSize = 12.sp)
                Text("Target SDK: ${application.targetSdk ?: "غير متاح"}", fontSize = 12.sp)
                Text("تاريخ التثبيت: ${formatLocalDate(application.firstInstallTime)}", fontSize = 12.sp)
                Text("آخر تحديث: ${formatLocalDate(application.lastUpdateTime)}", fontSize = 12.sp)
                Text("SHA-256 لشهادة التوقيع:", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(
                    application.signingCertificateSha256 ?: "غير متاح من Android لهذا التطبيق",
                    fontSize = 10.sp
                )
                Text("ملاحظات الفحص", color = MaterialTheme.colorScheme.primary)
                application.signals.forEach { signal -> Text("• $signal", fontSize = 11.sp) }
                Text("إعدادات وصول خاصة نشطة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.specialAccessReasons.ifEmpty {
                        listOf("لم يتم رصد تفعيل Accessibility أو Device Admin لهذا التطبيق.")
                    }.joinToString("\n"),
                    fontSize = 11.sp
                )
                Text("إعدادات خاصة مطلوبة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.requestedSpecialAccess.ifEmpty {
                        listOf("لم تُرصد طلبات الإعدادات الخاصة المعروفة.")
                    }.joinToString("\n"),
                    fontSize = 11.sp
                )
                Text("الصلاحيات الحساسة الممنوحة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.grantedSensitive.ifEmpty { listOf("لا توجد ضمن المعلومات المتاحة.") }
                        .joinToString("\n"),
                    fontSize = 10.sp
                )
                Text("الصلاحيات الحساسة المطلوبة", color = MaterialTheme.colorScheme.primary)
                Text(
                    application.requestedSensitive.ifEmpty { listOf("لا توجد ضمن المعلومات المتاحة.") }
                        .joinToString("\n"),
                    fontSize = 10.sp
                )
                Text(
                    "وجود صلاحية أو إعداد خاص لا يثبت أن التطبيق ضار. قد تكون الصلاحيات ضرورية لوظيفته.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("إعدادات التطبيق") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onCopyPackage) { Text("نسخ اسم الحزمة") }
                TextButton(onClick = onOpenNotifications) { Text("الإشعارات") }
                TextButton(onClick = onLaunch) { Text("فتح التطبيق") }
                if (application.installer == "Google Play") {
                    TextButton(onClick = onOpenStore) { Text("Google Play") }
                }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )
}

private fun allowedAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

private fun shareReport(context: Context, applications: List<AppAudit>, scannedAt: Long?) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "MSI — تقرير الفحص المحلي")
        putExtra(Intent.EXTRA_TEXT, ReportFormatter.format(applications, scannedAt))
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة التقرير"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر فتح قائمة المشاركة.", Toast.LENGTH_SHORT).show()
    }
}

private fun formatLocalDate(value: Long?): String =
    value?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(it))
    } ?: "غير متاح"

private fun openSystemPage(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "الإعداد غير متاح على هذا الجهاز.", Toast.LENGTH_SHORT).show()
    }
}

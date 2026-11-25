package com.msi.inspector.ui.navigation

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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msi.inspector.design.theme.MsiThemeMode
import com.msi.inspector.domain.analyzer.AppFilter
import com.msi.inspector.domain.analyzer.filterApplications
import com.msi.inspector.ui.MsiViewModel
import com.msi.inspector.ui.dashboard.DashboardScreen
import com.msi.inspector.ui.applications.ApplicationsScreen
import com.msi.inspector.ui.history.HistoryScreen
import com.msi.inspector.ui.permissions.PermissionsScreen
import com.msi.inspector.ui.settings.ToolsScreen
import com.msi.inspector.ui.timeline.TimelineScreen
import com.msi.inspector.ui.platform.openSystemPage
import com.msi.inspector.ui.security.allowedAuthenticators
import com.msi.inspector.ui.state.MsiUiState

@Composable
fun MsiDestinationContent(
    destination: MsiDestination,
    state: MsiUiState,
    context: Context,
    viewModel: MsiViewModel,
    onAppLockChanged: (Boolean) -> Unit,
    themeMode: MsiThemeMode,
    useDynamicColor: Boolean,
    onThemeChanged: (MsiThemeMode, Boolean) -> Unit,
    periodicScanEnabled: Boolean,
    onPeriodicScanChanged: (Boolean) -> Unit,
    scheduledHour: Int,
    scheduledMinute: Int,
    onScheduledTimeChanged: (Int, Int) -> Unit,
    onShareReportRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state.destination,
        modifier = modifier,
        transitionSpec = {
            if (ValueAnimator.areAnimatorsEnabled()) {
                (fadeIn() + slideInHorizontally { it / 14 })
                    .togetherWith(fadeOut() + slideOutHorizontally { -it / 14 })
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "msi-screen-transition"
    ) { destination ->
    when (destination) {
        MsiDestination.HOME -> DashboardScreen(
            state = state,
            onScan = viewModel::startScan,
            onNavigate = viewModel::navigate,
            onShareReport = onShareReportRequest,
            onOpenFilter = viewModel::updateFilter
        )
        MsiDestination.APPLICATIONS -> {
            val packageScope = state.packageScope
            val filtered = filterApplications(
                state.applications,
                state.query,
                state.filter,
                state.trustedPackages,
                state.ignoredPackages
            ).filter { packageScope == null || it.packageName in packageScope }
            ApplicationsScreen(
                applications = filtered,
                query = state.query,
                filter = state.filter,
                onQueryChange = viewModel::updateQuery,
                onFilterChange = viewModel::updateFilter,
                onSelect = viewModel::selectApplication,
                onCopyPackage = { packageName ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("اسم الحزمة", packageName))
                    Toast.makeText(context, "تم نسخ اسم الحزمة", Toast.LENGTH_SHORT).show()
                },
                onOpenSettings = { packageName ->
                    openSystemPage(
                        context,
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                },
                trustedPackages = state.trustedPackages,
                ignoredPackages = state.ignoredPackages
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
        MsiDestination.TIMELINE -> TimelineScreen(events = state.timelineEvents)
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
            onDeveloperSettings = {
                openSystemPage(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            },
            themeMode = themeMode,
            useDynamicColor = useDynamicColor,
            onThemeChanged = onThemeChanged,
            periodicScanEnabled = periodicScanEnabled,
            onPeriodicScanChanged = onPeriodicScanChanged,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            onScheduledTimeChanged = onScheduledTimeChanged,
            usageAccessOptIn = state.usageAccessOptIn,
            usageAccessGranted = state.usageAccessGranted,
            onUsageAccessChanged = { enabled ->
                viewModel.setUsageAccessOptIn(enabled)
                if (enabled) {
                    openSystemPage(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        )
    }
            }
}

package com.msi.inspector.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import android.provider.Settings
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.msi.inspector.MainActivity
import com.msi.inspector.R
import com.msi.inspector.domain.notification.MsiNotificationStatus

class MsiMonitoringService : Service() {
    private var packageReceiverRegistered = false
    private var settingsObserverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && isReplacing) return
            MsiMonitoringController.queueEventScan(applicationContext)
        }
    }

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MsiMonitoringController.queueEventScan(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
        registerPackageReceiver()
        registerSettingsObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!MsiMonitoringController.isEnabled(applicationContext)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification(loadSavedStatus())
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
        } catch (_: Exception) {
            MsiMonitoringController.disable(applicationContext)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        MsiMonitoringController.queueEventScan(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        if (packageReceiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            packageReceiverRegistered = false
        }
        if (settingsObserverRegistered) {
            runCatching {
                contentResolver.unregisterContentObserver(settingsObserver)
            }
            settingsObserverRegistered = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(
                this,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, filter)
        }
        packageReceiverRegistered = true
    }

    private fun registerSettingsObservers() {
        val observedSettings = listOf(
            Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        )
        var registeredAny = false
        observedSettings.forEach { uri ->
            val registered = runCatching {
                contentResolver.registerContentObserver(uri, false, settingsObserver)
                true
            }.getOrDefault(false)
            if (registered) registeredAny = true
        }
        settingsObserverRegistered = registeredAny
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: MsiNotificationStatus): Notification {
        val notificationColor = colorFor(status)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SHORTCUT, SHORTCUT_MONITORING)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pulseView = createPulseView(packageName, notificationColor)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_msi_notification)
            .setColor(notificationColor)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPendingIntent)
            .setCustomContentView(pulseView)
            .setCustomBigContentView(pulseView)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createPulseView(viewPackage: String, color: Int): RemoteViews =
        RemoteViews(viewPackage, R.layout.notification_monitoring_pulse).apply {
            setTextColor(R.id.monitor_notification_pulse_bright, color)
            setTextColor(R.id.monitor_notification_pulse_dim, color)
        }

    private fun loadSavedStatus(): MsiNotificationStatus {
        val saved = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFICATION_STATUS, null)
        return runCatching {
            MsiNotificationStatus.valueOf(saved ?: MsiNotificationStatus.REVIEW.name)
        }.getOrDefault(MsiNotificationStatus.REVIEW)
    }

    private fun colorFor(status: MsiNotificationStatus): Int = when (status) {
        MsiNotificationStatus.SAFE -> Color.rgb(112, 231, 209)
        MsiNotificationStatus.REVIEW -> Color.rgb(255, 193, 7)
        MsiNotificationStatus.ALERT -> Color.rgb(244, 82, 92)
    }

    companion object {
        private const val CHANNEL_ID = "msi_active_monitor"
        private const val NOTIFICATION_ID = 6101
        private const val PREFERENCES_NAME = "msi_preferences"
        private const val KEY_NOTIFICATION_STATUS = "monitor_notification_status"
        private const val SHORTCUT_MONITORING = "monitoring"

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        fun refreshNotification(context: Context, status: MsiNotificationStatus) {
            val appContext = context.applicationContext
            if (!MsiMonitoringController.isEnabled(appContext) || !isRunning()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return

            runCatching {
                appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_NOTIFICATION_STATUS, status.name)
                    .apply()

                val color = when (status) {
                    MsiNotificationStatus.SAFE -> Color.rgb(112, 231, 209)
                    MsiNotificationStatus.REVIEW -> Color.rgb(255, 193, 7)
                    MsiNotificationStatus.ALERT -> Color.rgb(244, 82, 92)
                }
                val openPendingIntent = PendingIntent.getActivity(
                    appContext,
                    NOTIFICATION_ID,
                    Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_SHORTCUT, SHORTCUT_MONITORING)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val pulseView = RemoteViews(
                    appContext.packageName,
                    R.layout.notification_monitoring_pulse
                ).apply {
                    setTextColor(R.id.monitor_notification_pulse_bright, color)
                    setTextColor(R.id.monitor_notification_pulse_dim, color)
                }
                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_msi_notification)
                    .setColor(color)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(openPendingIntent)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(pulseView)
                    .setCustomBigContentView(pulseView)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                appContext.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification)
            }
        }
    }
}

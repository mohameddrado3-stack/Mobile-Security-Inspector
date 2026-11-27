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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.msi.inspector.MainActivity
import com.msi.inspector.R

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
        if (intent?.action == ACTION_STOP) {
            MsiMonitoringController.disable(applicationContext)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!MsiMonitoringController.isEnabled(applicationContext)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    0
                )
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
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                false,
                settingsObserver
            )
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                settingsObserver
            )
            settingsObserverRegistered = true
        }
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

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, MsiMonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_msi_notification)
            .setColor(android.graphics.Color.rgb(112, 231, 209))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    getString(R.string.monitor_notification_big_text)
                )
            )
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_msi_notification,
                getString(R.string.monitor_notification_stop),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "msi_active_monitor"
        private const val NOTIFICATION_ID = 6101
        private const val STOP_REQUEST_CODE = 6102
        private const val ACTION_STOP = "com.msi.inspector.action.STOP_MONITORING"

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        fun refreshNotification(context: Context, lastScanText: String) {
            val appContext = context.applicationContext
            if (!MsiMonitoringController.isEnabled(appContext) || !isRunning()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return

            val openPendingIntent = PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopPendingIntent = PendingIntent.getService(
                appContext,
                STOP_REQUEST_CODE,
                Intent(appContext, MsiMonitoringService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_msi_notification)
                .setColor(android.graphics.Color.rgb(112, 231, 209))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentTitle(appContext.getString(R.string.monitor_notification_title))
                .setContentText(lastScanText)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    R.drawable.ic_msi_notification,
                    appContext.getString(R.string.monitor_notification_stop),
                    stopPendingIntent
                )
                .build()

            runCatching {
                appContext.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification)
            }
        }
    }
}

package com.msi.inspector.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.msi.inspector.MainActivity

object MsiScanNotifications {
    private const val CHANNEL_ID = "msi_scan_changes"
    private const val NOTIFICATION_ID = 6001

    fun showChange(context: Context, eventCount: Int) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تغيّر في مؤشرات MSI")
            .setContentText("اكتمل الفحص الدوري المحلي. تطبيقات ذات وصول خاص نشط: $activeSpecialAccessCount")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "اكتمل الفحص الدوري المحلي. تم تسجيل $eventCount حدث تغيّر بين الفحوصات. افتح MSI لمراجعة التفاصيل؛ هذه الأحداث ليست حكمًا على سلامة التطبيقات."
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تغيّرات فحص MSI",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "إشعار اختياري عند تغير مؤشرات الفحص المحلي."
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

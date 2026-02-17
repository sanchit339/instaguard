package com.instaguard.update

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
import com.instaguard.MainActivity
import com.instaguard.R

object UpdateNotifier {
    private const val CHANNEL_ID = "instaguard_updates"
    private const val CHANNEL_NAME = "InstaGuard Updates"
    private const val NOTIFICATION_ID = 1010
    private const val PREFS_NAME = "update_notifier"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"

    fun notifyUpdateAvailable(context: Context, latestTag: String, @Suppress("UNUSED_PARAMETER") releaseUrl: String) {
        if (!canPostNotifications(context)) return
        if (latestTag.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, null)
        if (lastTag == latestTag) return

        createChannelIfNeeded(context)

        val openReleaseIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openReleaseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update available")
            .setContentText("$latestTag is available. Update to keep InstaGuard bug free and secure.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A newer InstaGuard version ($latestTag) is available. Update to keep the app bug free and secure.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latestTag).apply()
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when a new InstaGuard release is available"
        }
        manager.createNotificationChannel(channel)
    }
}

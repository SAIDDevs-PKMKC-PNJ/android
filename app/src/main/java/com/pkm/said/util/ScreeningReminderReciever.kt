package com.pkm.said.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pkm.said.ScreeningManagementActivity
import com.pkm.said.R

class ScreeningReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreeningReminder"
        private const val NOTIFICATION_CHANNEL_ID = "screening_reminder_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📢 Screening reminder received")

        // Check if reminder is still enabled
        val sharedPrefs = context.getSharedPreferences("screening_reminder_prefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("reminder_enabled", false)

        if (!isEnabled) {
            Log.d(TAG, "Reminder disabled, skipping notification")
            return
        }

        showNotification(context)
    }

    private fun showNotification(context: Context) {
        Log.d(TAG, "🔔 Showing screening reminder notification")

        // Intent to open screening management activity
        val notificationIntent = Intent(context, ScreeningManagementActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screening_blue)
            .setContentTitle("Waktu Screening Stroke")
            .setContentText("Saatnya melakukan pemeriksaan kesehatan harian Anda")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Jangan lupa untuk melakukan screening stroke harian. Deteksi dini sangat penting untuk kesehatan Anda."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_arrow_forward,
                "Mulai Screening",
                pendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Notification shown successfully")
    }
}
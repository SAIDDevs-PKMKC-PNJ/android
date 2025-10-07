package com.pkm.said

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.pkm.said.util.ScreeningReminderReceiver
import java.text.SimpleDateFormat
import java.util.*

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var switchReminder: SwitchMaterial
    private lateinit var layoutTimePicker: LinearLayout
    private lateinit var cardTimeSelector: CardView
    private lateinit var tvSelectedTime: TextView
    private lateinit var cardCurrentStatus: CardView
    private lateinit var tvStatusMessage: TextView

    private lateinit var sharedPrefs: SharedPreferences
    private var selectedHour = 8
    private var selectedMinute = 0

    companion object {
        private const val TAG = "NotificationSettings"
        private const val PREFS_NAME = "screening_reminder_prefs"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val NOTIFICATION_CHANNEL_ID = "screening_reminder_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, NotificationSettingsActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== NOTIFICATION SETTINGS START ===")

        setContentView(R.layout.activity_notification_setting)

        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        setupViews()
        loadSettings()
        createNotificationChannel()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        switchReminder = findViewById(R.id.switchReminder)
        layoutTimePicker = findViewById(R.id.layoutTimePicker)
        cardTimeSelector = findViewById(R.id.cardTimeSelector)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)
        cardCurrentStatus = findViewById(R.id.cardCurrentStatus)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)
    }

    private fun setupViews() {
        // Setup toolbar
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Switch listener
        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Reminder switch changed: $isChecked")
            handleReminderToggle(isChecked)
        }

        // Time picker
        cardTimeSelector.setOnClickListener {
            if (switchReminder.isChecked) {
                showTimePicker()
            }
        }
    }

    private fun loadSettings() {
        val isEnabled = sharedPrefs.getBoolean(KEY_REMINDER_ENABLED, false)
        selectedHour = sharedPrefs.getInt(KEY_REMINDER_HOUR, 8)
        selectedMinute = sharedPrefs.getInt(KEY_REMINDER_MINUTE, 0)

        switchReminder.isChecked = isEnabled
        updateTimeDisplay()
        updateUI(isEnabled)

        Log.d(TAG, "Settings loaded - Enabled: $isEnabled, Time: $selectedHour:$selectedMinute")
    }

    private fun handleReminderToggle(enabled: Boolean) {
        // Save to preferences
        sharedPrefs.edit()
            .putBoolean(KEY_REMINDER_ENABLED, enabled)
            .apply()

        updateUI(enabled)

        if (enabled) {
            scheduleNotification()
            Toast.makeText(this, "Pengingat screening diaktifkan", Toast.LENGTH_SHORT).show()
        } else {
            cancelNotification()
            Toast.makeText(this, "Pengingat screening dinonaktifkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(enabled: Boolean) {
        layoutTimePicker.alpha = if (enabled) 1.0f else 0.5f
        cardTimeSelector.isEnabled = enabled
        cardCurrentStatus.isVisible = enabled

        if (enabled) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
            }
            val timeStr = timeFormat.format(calendar.time)
            tvStatusMessage.text = getString(R.string.reminder_handler_daily_true, timeStr)
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute

                // Save to preferences
                sharedPrefs.edit()
                    .putInt(KEY_REMINDER_HOUR, selectedHour)
                    .putInt(KEY_REMINDER_MINUTE, selectedMinute)
                    .apply()

                updateTimeDisplay()
                updateUI(true)

                // Reschedule notification with new time
                if (switchReminder.isChecked) {
                    scheduleNotification()
                }

                Log.d(TAG, "Time updated: $selectedHour:$selectedMinute")
            },
            selectedHour,
            selectedMinute,
            true // 24-hour format
        ).show()
    }

    private fun updateTimeDisplay() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        tvSelectedTime.text = timeFormat.format(calendar.time)
    }

    private fun scheduleNotification() {
        Log.d(TAG, "Scheduling notification for $selectedHour:$selectedMinute")

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ScreeningReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set time for today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)

            // If time has passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            // Schedule repeating alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )

            Log.d(TAG, "✅ Notification scheduled successfully for ${calendar.time}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling notification", e)
            Toast.makeText(this, "Error setting reminder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelNotification() {
        Log.d(TAG, "Cancelling scheduled notification")

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ScreeningReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "✅ Notification cancelled successfully")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Pengingat Screening",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi pengingat untuk melakukan screening stroke harian"
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "✅ Notification channel created")
        }
    }
}
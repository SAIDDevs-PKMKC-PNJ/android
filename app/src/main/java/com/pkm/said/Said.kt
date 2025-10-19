package com.pkm.said

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.pkm.said.service.VoiceActivationService

class Said : Application(), Application.ActivityLifecycleCallbacks {

    private val TAG = "AppLifecycle"
    private var visibleActivityCount = 0
    private var currentActivity: Activity? = null

    private val ACTIVITY_BLACKLIST = listOf(
        "LoginActivity",
        "RegisterActivity",
        "UserInformationActivity",
        "IntroActivity",
        "OpeningActivity"

    )

    // =======================================================
    // LIFECYCLE CALLBACKS
    // =======================================================

    private val activityCallbacks = mutableMapOf<String, VoiceActivityCallback>()

    interface VoiceActivityCallback {
        fun onVoiceCommand(command: String, extras: Bundle? = null): Boolean
//        fun onStartScreening(): Boolean
//        fun onEmergencyDetected(): Boolean
        fun onNavigateTo(destination: String): Boolean
        fun getSupportedCommands(): List<String>
    }

    companion object {
        private var instance: Said? = null

        fun getInstance(): Said {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        Log.d(TAG, "Application created. Lifecycle monitoring started.")
    }

    private fun isActivityBlacklisted(activity: Activity): Boolean {
        val activityName = activity.localClassName
        return ACTIVITY_BLACKLIST.any { activityName.contains(it) }
    }

    // Dipanggil saat Activity pertama dibuka atau kembali dari background
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity

        if (isActivityBlacklisted(activity)) {
            Log.d(TAG, "Activity resumed: ${activity.localClassName}. Blacklisted. Skipping voice service check.")
            return // Abaikan Activity ini
        }

        if (visibleActivityCount == 0) {
            // Activity pertama yang terlihat (misal: MainActivity, ChatbotActivity)
            Log.d(TAG, "Activity resumed: ${activity.localClassName}. Starting Voice Service.")
            startVoiceService()
        }
        visibleActivityCount++
        Log.d(TAG, "Visible Activity Count: $visibleActivityCount")
    }

    // Dipanggil saat Activity tidak lagi terlihat (ketika minimize atau navigasi)
    override fun onActivityStopped(activity: Activity) {
        if (isActivityBlacklisted(activity)) {
            return
        }

        if (currentActivity == activity) {
            currentActivity = null
        }
        visibleActivityCount--
        Log.d(TAG, "Activity stopped: ${activity.localClassName}. New Count: $visibleActivityCount")

        if (visibleActivityCount <= 0) {
            // Hitungan mencapai nol, berarti seluruh aplikasi sudah di-minimize/ditutup
            visibleActivityCount = 0 // Reset untuk jaga-jaga
            Log.d(TAG, "Last Activity stopped. Stopping Voice Service.")
            stopVoiceService()
        }
    }

    // =======================================================
    // SERVICE CONTROL
    // =======================================================

    fun registerActivityCallback(activityName: String, callback: VoiceActivityCallback) {
        activityCallbacks[activityName] = callback
        Log.d(TAG, "✅ Registered voice callback: $activityName - Supported: ${callback.getSupportedCommands()}")
    }

    fun unregisterActivityCallback(activityName: String) {
        activityCallbacks.remove(activityName)
        Log.d(TAG, "❌ Unregistered voice callback: $activityName")
    }

    fun getCurrentActivity(): Activity? = currentActivity

    // ✅ ENHANCED VOICE COMMAND DISPATCHER
    fun dispatchVoiceCommand(command: String, extras: Bundle? = null): Boolean {
        Log.d(TAG, "🎤 Dispatching voice command: '$command' to ${activityCallbacks.size} activities")

        val processedExtras = extras ?: Bundle().apply {
            putLong("timestamp", System.currentTimeMillis())
            putString("original_command", command)
        }

        // Priority 1: Current activity first
        currentActivity?.let { activity ->
            val activityName = activity.localClassName
            activityCallbacks[activityName]?.let { callback ->
                try {
                    Log.d(TAG, "🎤 Sending to current activity: $activityName")
                    val handled = callback.onVoiceCommand(command, processedExtras)

                    if (handled) {
                        Log.d(TAG, "✅ Command handled by current activity: $activityName")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error dispatching to current activity", e)
                }
            }
        }

        // Priority 2: Try all registered activities (fallback)
        for ((name, callback) in activityCallbacks) {
            if (name != currentActivity?.localClassName) {
                try {
                    Log.d(TAG, "🎤 Fallback: Trying activity: $name")
                    val handled = callback.onVoiceCommand(command, processedExtras)

                    if (handled) {
                        Log.d(TAG, "✅ Command handled by fallback activity: $name")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error dispatching to $name", e)
                }
            }
        }

        Log.w(TAG, "⚠️ Voice command not handled by any activity: '$command'")
        return false
    }

    // Service control methods tetap sama...
    private fun startVoiceService() {
        val intent = Intent(this, VoiceActivationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVoiceService() {
        val intent = Intent(this, VoiceActivationService::class.java).apply {
            action = VoiceActivationService.ACTION_CLEANUP
        }
        startService(intent)
    }

    // =======================================================
    // Metode Kosong Lainnya (Wajib Implementasi)
    // =======================================================

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
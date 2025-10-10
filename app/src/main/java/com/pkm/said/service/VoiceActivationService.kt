package com.pkm.said.service

import ai.picovoice.rhino.RhinoInference
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pkm.said.R
import com.pkm.said.util.PicovoiceManager
import com.pkm.said.MainActivity
import java.util.Locale
import androidx.core.content.edit
import com.pkm.said.EmergencyActivity
import com.pkm.said.screening.ScreeningActivity
import com.pkm.said.util.SessionManager

class VoiceActivationService : Service() {
    private val TAG = "VoiceActivationService"

    private lateinit var picovoiceManager: PicovoiceManager
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isServiceRunning = false
    private var isInitialized = false

    private var emergencyResponseTimer: CountDownTimer? = null
    private var isWaitingForEmergencyResponse = false
    private val EMERGENCY_RESPONSE_TIMEOUT = 10000L // 10 detik

    // Binder untuk activity-service communication
    private val binder = VoiceServiceBinder()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_service_channel"

        // Actions untuk control service
        const val ACTION_START = "START_VOICE_SERVICE"
        const val ACTION_STOP = "STOP_VOICE_SERVICE"
        const val ACTION_CLEANUP = "CLEANUP_VOICE_SERVICE"
        const val ACTION_TOGGLE = "TOGGLE_VOICE_SERVICE"
    }

    inner class VoiceServiceBinder : Binder() {
        fun getService(): VoiceActivationService = this@VoiceActivationService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        initializeTTS()
        createNotificationChannel()
        initializePicovoice()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = textToSpeech?.setLanguage(Locale.US)

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "TTS Language not supported")
                    result = textToSpeech?.setLanguage(Locale.getDefault())
                } else {
                    isTtsReady = true
                    Log.d(TAG, "TTS initialized successfully")

                    // Set slower speed for better clarity
                    textToSpeech?.setSpeechRate(0.9f)
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }

    private fun speak(text: String, delay: Long = 0) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, skipping speech: $text")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                Log.d(TAG, "TTS speaking: $text")
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak error", e)
            }
        }, delay)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startVoiceService()
            ACTION_STOP -> stopVoiceService()
            ACTION_TOGGLE -> toggleVoiceService()
            ACTION_CLEANUP -> cleanupAndStopService()
            else -> {
                // Default start jika service di-restart system
                if (!isServiceRunning) {
                    startVoiceService()
                }
            }
        }

        return START_STICKY
    }

    private fun initializePicovoice() {
        Log.d(TAG, "🔧 Starting Picovoice initialization...")

        try {
            picovoiceManager = PicovoiceManager(
                context = this,
                onIntentDetected = { inference ->
                    Log.d(TAG, "🎯 Intent detected: ${inference.intent}")
                    // Handle intent
                },
                onListeningStatusChange = { isListening ->
                    Log.d(TAG, "👂 Listening status: $isListening")
                    broadcastListeningState(isListening)
                }
            )

            // Debug initialization step by step
            val success = picovoiceManager?.initPicovoice() ?: false

            if (success) {
                Log.d(TAG, "✅ Picovoice initialized successfully")
                isInitialized = true
                startListening()
            } else {
                Log.e(TAG, "❌ Picovoice initialization failed")
                isInitialized = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Critical error during Picovoice initialization", e)
            isInitialized = false
        }
    }

    private fun startListening() {
        if (isInitialized) {
            Log.d(TAG, "🎤 Starting wake word detection...")
            picovoiceManager?.start()
            broadcastServiceState("RUNNING")
        } else {
            Log.e(TAG, "❌ Cannot start listening - Picovoice not initialized")
            broadcastServiceState("STOPPED")
        }
    }

    private fun broadcastServiceState(state: String) {
        val intent = Intent("VOICE_SERVICE_STATE").apply {
            putExtra("state", state)
            putExtra("is_initialized", isInitialized)
        }
        sendBroadcast(intent)
    }

    private fun broadcastListeningState(isListening: Boolean) {
        val intent = Intent("VOICE_LISTENING_STATE").apply {
            putExtra("is_listening", isListening)
        }
        sendBroadcast(intent)
    }


    private fun startVoiceService() {
        if (isServiceRunning) {
            Log.d(TAG, "Service already running")
            return
        }

        try {
            picovoiceManager.start()
            isServiceRunning = true

            startForeground(NOTIFICATION_ID, createNotification("Voice activation active"))
            broadcastServiceState(true)

            Log.d(TAG, "Voice service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice service", e)
            isServiceRunning = false
        }
    }

    private fun stopVoiceService() {
        if (!isServiceRunning) {
            Log.d(TAG, "Service not running")
            return
        }

        try {
            picovoiceManager.stop()
            isServiceRunning = false

            stopForeground(true)
            broadcastServiceState(false)

            Log.d(TAG, "Voice service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice service", e)
        }
    }

    private fun toggleVoiceService() {
        if (isServiceRunning) {
            stopVoiceService()
        } else {
            startVoiceService()
        }
    }

    private fun cleanupAndStopService() {
        Log.d(TAG, "Performing complete cleanup before logout/account deletion")

        try {
            // Hentikan TTS
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            isTtsReady = false

            // Hentikan Picovoice
            picovoiceManager.stop()
            picovoiceManager.release()

            // Hapus semua shared preferences terkait voice
            clearVoicePreferences()

            // Hentikan service
            isServiceRunning = false
            stopForeground(true)
            stopSelf()

            Log.d(TAG, "✅ Voice service completely cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during voice service cleanup", e)
        }
    }

    private fun clearVoicePreferences() {
        try {
            val sharedPrefs = getSharedPreferences("voice_logs", Context.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()

            // Hapus preferences lain yang terkait voice service jika ada
            val appPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            appPrefs.edit().remove("voice_service_enabled").apply()

            Log.d(TAG, "✅ Voice preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing voice preferences", e)
        }
    }

    private fun handleIntentDetection(inference: RhinoInference) {
        Log.d(TAG, "Handling intent: ${inference.intent}, Slots: ${inference.slots}")

        when (inference.intent) {
            "start_screening" -> handleStartScreening()
            "emergency_call" -> handleEmergencyCall()
            "open_app" -> handleOpenApp()
            else -> handleUnknownIntent(inference)
        }

        // Show action notification
        showActionNotification("Executed: ${inference.intent}")

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Kita asumsikan PicovoiceManager.start() me-restart engine ke mode wake word
                picovoiceManager.start()
                updateNotification("Voice activation active")
                Log.d(TAG, "✅ Picovoice restarted to wake word mode")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restart Picovoice", e)
            }
        }, 3000)
    }

    fun updateListeningStatus(isListening: Boolean) {
        if (isListening) {
            updateNotification("🎤 Listening for command...")
        } else {
            // Kembali ke status default Porcupine
            updateNotification("Voice activation active (Say 'Hi Said')")
        }
    }

    private fun handleStartScreening() {
        Log.d(TAG, "Starting screening")

        // TTS feedback sebelum action
        speak("Starting health screening", 300)

        // Delay action sedikit setelah TTS
        Handler(Looper.getMainLooper()).postDelayed({
            startScreeningActivity()
        }, 1500)

        showActionNotification("Starting health screening", "screening")
    }

    private fun handleEmergencyCall() {
        Log.d(TAG, "Emergency call requested")

        // TTS feedback untuk emergency
        speak("Emergency detected! Starting countdown", 300)

        Handler(Looper.getMainLooper()).postDelayed({
            showEmergencyCountdown()
        }, 1500)

        showActionNotification("Emergency detected", "emergency")
    }

    private fun handleOpenApp() {
        Log.d(TAG, "Opening main app")

        // TTS feedback
        speak("Opening Said application", 300)

        Handler(Looper.getMainLooper()).postDelayed({
            openMainActivity("dashboard")
        }, 1500)

        showActionNotification("Opening app", "app")
    }

    private fun handleUnknownIntent(inference: RhinoInference) {
        Log.w(TAG, "Unknown intent received: ${inference.intent}")

        // TTS feedback untuk unknown intent
        val response = when {
            inference.intent.contains("weather") -> "I can't check weather yet"
            inference.intent.contains("time") -> "I can't tell time yet"
            else -> "Sorry, I didn't understand that command"
        }

        speak(response, 300)
        showActionNotification("Unknown command: ${inference.intent}")
        logUnknownIntent(inference)
    }

    private fun logUnknownIntent(inference: RhinoInference) {
        // Log unknown intents for future training
        val sharedPrefs = getSharedPreferences("voice_logs", Context.MODE_PRIVATE)
        val unknownIntents = sharedPrefs.getStringSet("unknown_intents", mutableSetOf()) ?: mutableSetOf()

        unknownIntents.add("${System.currentTimeMillis()}: ${inference.intent} - ${inference.slots}")

        sharedPrefs.edit {
            putStringSet("unknown_intents", unknownIntents)
        }
    }

    private fun startScreeningActivity() {
        try {
            // ✅ GUNAKAN PARAMETER YANG SESUAI DENGAN SCREENINGACTIVITY ANDA
            val currentUser = getCurrentUsername() // Method dari MainActivity atau shared preferences
            ScreeningActivity.start(
                context = this,
                userId = currentUser,
                dest = null, // Biarkan ScreeningActivity tentukan dest sendiri
                startNew = true // Mulai sesi baru untuk voice command
            )
            Log.d(TAG, "✅ ScreeningActivity started for user: $currentUser")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting ScreeningActivity", e)

            // Fallback strategy
            tryFallbackScreeningStart()
        }
    }

    private fun tryFallbackScreeningStart() {
        try {
            // Fallback 1: Direct intent dengan extras
            val intent = Intent(this, ScreeningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("user_id", getCurrentUsername())
                putExtra("from_voice_service", true)
                putExtra("startNew", true)
            }
            startActivity(intent)
            Log.d(TAG, "✅ ScreeningActivity started via fallback intent")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fallback intent also failed", e)

            // Fallback 2: Buka MainActivity dengan screening command
            openMainActivity("screening")
        }
    }

    // Helper method untuk mendapatkan username
    private fun getCurrentUsername(): String {
        return try {
            SessionManager.getUserName(this) ?: "user_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "user_${System.currentTimeMillis()}"
        }
    }
    private fun showEmergencyCountdown() {
        // Gunakan Android Intent untuk start Emergency Activity
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("from_voice_service", true)
        }
        startActivity(intent)
    }

    private fun openMainActivity(fragment: String? = null) {
        // Gunakan Android Intent untuk start MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_voice_service", true)
            putExtra("target_fragment", fragment)
            action = "EMERGENCY_CALL"
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Activation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background voice recognition service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String = "Voice service running"): Notification {
        // Intent untuk control dari notification
        val stopIntent = Intent(this, VoiceActivationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Activation")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showActionNotification(action: String, type: String = "general") {
        val iconRes = when (type) {
            "screening" -> R.drawable.ic_screening_blue
            "emergency" -> R.drawable.ic_alert
            "app" -> R.drawable.ic_home_active
            else -> R.drawable.ic_mic
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Action")
            .setContentText(action)
            .setSmallIcon(iconRes)
            .setTimeoutAfter(3000) // Auto-dismiss after 3 seconds
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, notification)
    }

    private fun broadcastServiceState(isRunning: Boolean) {
        val intent = Intent("VOICE_SERVICE_STATE").apply {
            putExtra("is_running", isRunning)
        }
        sendBroadcast(intent)
    }

    // Public methods untuk activity
    fun isServiceRunning(): Boolean = isServiceRunning

    fun getServiceState(): String {
        return when {
            !isServiceRunning -> "Stopped"
            picovoiceManager.isInIntentMode() -> "Listening for command"
            else -> "Waiting for wake word"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        // Pastikan semua resources dibersihkan
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            picovoiceManager.release()
            isServiceRunning = false
            broadcastServiceState(false)
            Log.d(TAG, "✅ All voice service resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in service cleanup", e)
        }
    }
}
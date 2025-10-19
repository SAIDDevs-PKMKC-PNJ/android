package com.pkm.said.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pkm.said.MainActivity
import com.pkm.said.R
import com.pkm.said.Said
import java.util.Locale

//import ai.picovoice.rhino.RhinoInference
//import com.pkm.said.util.PicovoiceManager

class VoiceActivationService : Service() {
    private val TAG = "VoiceActivationService"

//    private lateinit var picovoiceManager: PicovoiceManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private val RESTART_DELAY: Long = 200
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isServiceRunning = false
    private var isInitialized = false

//    private var emergencyResponseTimer: CountDownTimer? = null
//    private var isWaitingForEmergencyResponse = false
//    private val EMERGENCY_RESPONSE_TIMEOUT = 10000L

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
        initializeSpeechRecognizer()
//        initializePicovoice()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {

                val indonesianLocale = Locale("id", "ID")
                var result = textToSpeech?.setLanguage(indonesianLocale)

                if (result == TextToSpeech.LANG_MISSING_DATA) {
                    Log.w(TAG, "Missing TTS data for Indonesian. Prompting user to install.")
                    promptForTtsInstallation(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

                } else if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language (ID) not supported. Falling back to US English.")
                }

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "TTS Language (ID) not supported. Falling back to US English.")
                    result = textToSpeech?.setLanguage(Locale.US)

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED)
                    {
                        Log.e(TAG, "TTS Language (US) not supported. Falling back to device default.")
                        result = textToSpeech?.setLanguage(Locale.getDefault())
                    }
                }

                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    isTtsReady = true
                    Log.d(TAG, "TTS initialized successfully with final language: ${textToSpeech?.language}")

                    textToSpeech?.setSpeechRate(0.9f)
                    textToSpeech?.setPitch(1.0f)
                } else {
                    Log.e(TAG, "TTS initialization failed: No supported language found.")
                }
            } else {
                Log.e(TAG, "TTS initialization failed (Status: $status)")
            }
        }
    }

    private fun promptForTtsInstallation(action: String) {
        val installIntent = Intent(action).apply {
            // Karena dipanggil dari Service, perlu FLAG_ACTIVITY_NEW_TASK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch TTS installation intent.", e)
            // Beri tahu pengguna bahwa mereka harus menginstal Google TTS dari Play Store
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

    //fitur legacy dengan picovoice
//    private fun initializePicovoice() {
//        Log.d(TAG, "🔧 Starting Picovoice initialization...")
//
//        try {
//            picovoiceManager = PicovoiceManager(
//                context = this,
//                onIntentDetected = { inference ->
//                    Log.d(TAG, "🎯 Intent detected: ${inference.intent}")
//                    // Handle intent
//                },
//                onListeningStatusChange = { isListening ->
//                    Log.d(TAG, "👂 Listening status: $isListening")
//                    broadcastListeningState(isListening)
//                }
//            )
//
//            // Debug initialization step by step
//            val success = picovoiceManager?.initPicovoice() ?: false
//
//            if (success) {
//                Log.d(TAG, "✅ Picovoice initialized successfully")
//                isInitialized = true
//                startListening()
//            } else {
//                Log.e(TAG, "❌ Picovoice initialization failed")
//                isInitialized = false
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "💥 Critical error during Picovoice initialization", e)
//            isInitialized = false
//        }
//    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle) {
            Log.d(TAG, "Ready for speech. Listening...")
            broadcastListeningState(true)
            updateListeningStatus(true)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected.")
        }

        // --- 1. Mendapatkan Hasil dan Memproses Intent ---
        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0].lowercase(Locale.getDefault())
                Log.d(TAG, "Speech recognized: $spokenText")
                handleSpokenCommand(spokenText) // Panggil fungsi pemrosesan intent
            } else {
                Log.d(TAG, "No speech match found.")
            }

            // Setelah memproses (atau tidak ada hasil), segera restart listening
            scheduleRestart()
        }

        // --- 2. Menangani Error dan Melanjutkan Loop ---
        override fun onError(error: Int) {
            val errorText = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                // ... error lainnya
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "Speech Recognizer Error: $errorText")

            // Jadwal restart, terutama untuk ERROR_NO_MATCH dan ERROR_SPEECH_TIMEOUT
            scheduleRestart()
        }

        // Metode lain tidak terlalu krusial untuk loop
        override fun onPartialResults(partialResults: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle) {}
    }

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer != null) {
            // Sudah diinisialisasi
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech Recognition is NOT available on this device.")
            return
        }

        // 1. Inisialisasi SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(recognitionListener)

        // 2. Siapkan Recognizer Intent (Sama untuk setiap kali listening dimulai)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // Opsi: Coba mode offline untuk mengurangi penggunaan data, tetapi akurasi bisa turun.
            // putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            // Coba fitur continuous (tidak didukung secara resmi di semua versi/provider)
            // putExtra(RecognizerIntent.EXTRA_ENDPOINTER_SILENCE_TIMEOUT, 500)
            // putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }

        isInitialized = true
        Log.d(TAG, "✅ SpeechRecognizer initialized successfully.")
        startListening()
    }

    private fun startListening() {
        //legacy untuk picovoice
//        if (isInitialized) {
//            Log.d(TAG, "🎤 Starting wake word detection...")
//            picovoiceManager?.start()
//            broadcastServiceState("RUNNING")
//        } else {
//            Log.e(TAG, "❌ Cannot start listening - Picovoice not initialized")
//            broadcastServiceState("STOPPED")
//        }
        if (speechRecognizer != null && recognizerIntent != null) {
            try {
                speechRecognizer?.startListening(recognizerIntent)

                // Perbarui status bahwa Service sedang berjalan
                broadcastServiceState("RUNNING")
                Log.d(TAG, "🎤 SpeechRecognizer started/restarted for continuous listening.")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer. Scheduling retry.", e)

                Handler(Looper.getMainLooper()).postDelayed({
                    startListening()
                }, 1000)
            }
        } else {
            Log.e(TAG, "❌ Cannot start listening - Recognizer not initialized.")
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
            //legacy untuk picovoice
//            picovoiceManager.start()
            initializeSpeechRecognizer()
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
            //legacy untuk picovoice
//            picovoiceManager.stop()
            speechRecognizer?.cancel()
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
//            picovoiceManager.stop()
//            picovoiceManager.release()

            speechRecognizer?.destroy()
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
            val sharedPrefs = getSharedPreferences("voice_logs", MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()

            // Hapus preferences lain yang terkait voice service jika ada
            val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            appPrefs.edit().remove("voice_service_enabled").apply()

            Log.d(TAG, "✅ Voice preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing voice preferences", e)
        }
    }

    private fun handleSpokenCommand(command: String) {
        Log.d(TAG, "🎤 Processing voice command: $command")

        val handled = Said.getInstance().dispatchVoiceCommand(command)

        if (handled) {
            Log.d(TAG, "✅ Command handled by activity: $command")

            val actionType = when {
                command.contains("screening") || command.contains("tes") -> {
                    speak("Memulai screening stroke", 300)
                    "screening"
                }
                command.contains("darurat") || command.contains("emergency") -> {
                    speak("Membuka mode darurat", 300)
                    "emergency"
                }
                command.contains("dashboard") || command.contains("home") -> {
                    speak("Menuju dashboard", 300)
                    "dashboard"
                }
                else -> "general"
            }

            showActionNotification("Perintah: $command", actionType)
        } else {
            //showActionNotification untuk Feddback Error
            Log.w(TAG, "⚠️ Command not handled: $command")
            showActionNotification("Perintah tidak dikenali: $command", "error")

            if (!command.contains("hi said") && !command.contains("halo said")) {
                speak("Maaf, perintah tidak dikenali", 500)
            }
        }

        // Tetap restart listening setelah processing
        scheduleRestart()
    }

    fun updateListeningStatus(isListening: Boolean) {
        if (isListening) {
            updateNotification("🎤 Listening for command...")
        } else {
            // Kembali ke status default Porcupine
            updateNotification("Voice activation active")
        }
    }

    private fun scheduleRestart() {
        broadcastListeningState(false)
        updateListeningStatus(false)

        Handler(Looper.getMainLooper()).postDelayed({
            startListening()
        }, RESTART_DELAY)
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        // Pastikan semua resources dibersihkan
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
//            picovoiceManager.release()
            speechRecognizer?.destroy()
            speechRecognizer = null

            isServiceRunning = false
            broadcastServiceState(false)
            Log.d(TAG, "✅ All voice service resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in service cleanup", e)
        }
    }
}
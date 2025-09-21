package com.pkm.said.service

import ai.picovoice.porcupine.*
import ai.picovoice.rhino.*
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pkm.said.MainActivity
import com.pkm.said.R
import kotlinx.coroutines.*

class VoiceAssistantService : Service() {

    private val binder = VoiceAssistantBinder()
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private var porcupine: Porcupine? = null
    private var rhino: Rhino? = null
    private var listeningJob: Job? = null

    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val CHANNEL_ID = "stroke_voice_assistant"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = 16000
        private const val FRAME_LENGTH = 512

        // Ganti dengan access key Anda dari picovoice.ai
        private const val ACCESS_KEY = "TJe7mlENKjcWA//xJeqZ/KZlPP8rxQsfTNcaPaLHtdtp6hpVdxtuSQ=="
    }

    inner class VoiceAssistantBinder : Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎤 Voice Assistant Service created")
        createNotificationChannel()
        initializePicovoice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Starting voice assistant service...")
        startForeground(NOTIFICATION_ID, createNotification())
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "🔗 Service bound")
        return binder
    }

    private fun checkMicrophonePermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, if (hasPermission) "✅ Microphone permission granted" else "❌ Microphone permission denied")
        return hasPermission
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stroke Screening Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice commands for stroke screening application"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAID Voice Assistant")
            .setContentText("Listening for stroke screening commands...")
            .setSmallIcon(R.drawable.ic_mic) // Pastikan icon ini ada
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun initializePicovoice() {
        try {
            Log.d(TAG, "🔧 Initializing Picovoice...")

            if (!checkMicrophonePermission()) {
                Log.e(TAG, "❌ Cannot initialize Porcupine - no microphone permission")
                return
            }

            // Initialize Porcupine untuk wake word detection
            porcupine = Porcupine.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
                .build(applicationContext)

            Log.d(TAG, "✅ Porcupine initialized")

            // Initialize Rhino untuk command recognition
            // Catatan: Anda perlu membuat context file untuk stroke screening
            rhino = Rhino.Builder()
                .setAccessKey(ACCESS_KEY)
                .setContextPath("stroke_screening_android.rhn") // File context di assets
                .build(applicationContext)

            Log.d(TAG, "✅ Rhino initialized")

            initializeAudioRecord()

        } catch (e: PorcupineException) {
            Log.e(TAG, "❌ Failed to initialize Porcupine", e)
        } catch (e: RhinoException) {
            Log.e(TAG, "❌ Failed to initialize Rhino", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Picovoice", e)
        }
    }

    private fun initializeAudioRecord() {
        try {
            // ✅ Check permission before AudioRecord
            if (!checkMicrophonePermission()) {
                Log.e(TAG, "❌ Cannot initialize AudioRecord - no microphone permission")
                return
            }

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSize = Math.max(minBufferSize, FRAME_LENGTH * 2)

            // ✅ Explicit permission check for AudioRecord
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                Log.d(TAG, "✅ AudioRecord initialized")

            } else {
                Log.e(TAG, "❌ Cannot create AudioRecord - permission denied")
                throw SecurityException("RECORD_AUDIO permission required")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException in AudioRecord init: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize AudioRecord: ${e.message}", e)
        }
    }

    private fun startListening() {
        if (!checkMicrophonePermission()) {
            Log.e(TAG, "❌ Cannot start listening - no microphone permission")
            return
        }

        if (isListening) {
            Log.w(TAG, "⚠️ Already listening")
            return
        }

        if (audioRecord == null) {
            Log.e(TAG, "❌ AudioRecord not initialized")
            return
        }

        Log.d(TAG, "👂 Starting to listen for voice commands...")
        isListening = true

        listeningJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    audioRecord?.startRecording()
                    Log.d(TAG, "🎙️ Audio recording started")
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ SecurityException starting recording: ${e.message}", e)
                    return@launch
                }


                val audioBuffer = ShortArray(FRAME_LENGTH)
                var isWakeWordDetected = false

                while (isListening && checkMicrophonePermission()) {
                    try {
                        val numRead = audioRecord?.read(audioBuffer, 0, FRAME_LENGTH) ?: 0

                        if (numRead == FRAME_LENGTH) {
                            if (!isWakeWordDetected) {
                                // Deteksi wake word dengan Porcupine
                                val keywordIndex = porcupine?.process(audioBuffer) ?: -1
                                if (keywordIndex >= 0) {
                                    Log.d(TAG, "🎯 Wake word detected! Index: $keywordIndex")
                                    isWakeWordDetected = true
                                    onWakeWordDetected()

                                    // Beri waktu user untuk bicara
                                    delay(1000)
                                }
                            } else {
                                // Proses command dengan Rhino
                                val isFinalized = rhino?.process(audioBuffer) ?: false
                                if (isFinalized) {
                                    val inference = rhino?.getInference()
                                    if (inference?.isUnderstood == true) {
                                        Log.d(TAG, "✅ Voice command understood: ${inference.intent}")
                                        handleVoiceCommand(inference)
                                    } else {
                                        Log.d(TAG, "❓ Voice command not understood")
                                    }
                                    isWakeWordDetected = false
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "❌ SecurityException during audio processing: ${e.message}", e)
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in audio processing loop: ${e.message}", e)
                        delay(100)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException in listening coroutine: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in listening coroutine: ${e.message}", e)
            } finally {
                try {
                    audioRecord?.stop()
                    Log.d(TAG, "🛑 Audio recording stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error stopping audio record: ${e.message}", e)
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "🎤 Ready for command...")
        // Bisa tambahkan feedback suara atau vibration di sini
    }
    private fun handleVoiceCommand(inference: RhinoInference) {
        val intent = inference.intent
        val slots = inference.slots

        Log.d(TAG, "🗣️ Processing voice command: $intent")
        Log.d(TAG, "🗣️ Command slots: $slots")

        when (intent) {
            "navigate" -> {
                val destination = slots["destination"] ?: "home"
                navigateToDestination(destination)
            }
            "start_screening" -> {
                val testType = slots["test_type"] ?: "full"
                startStrokeScreening(testType)
            }
//            "face_test" -> {
//                startSpecificTest("face")
//            }
//            "arm_test" -> {
//                startSpecificTest("arm")
//            }
//            "speech_test" -> {
//                startSpecificTest("speech")
//            }
//            "time_test" -> {
//                startSpecificTest("time")
//            }
//            "emergency" -> {
//                callEmergency()
//            }
            else -> {
                Log.w(TAG, "❓ Unknown command: $intent")
            }
        }
    }

    private fun startStrokeScreening(testType: String = "full") {
        Log.d(TAG, "🏥 Starting stroke screening - type: $testType")

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "START_SCREENING"
            putExtra("test_type", testType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

//    private fun startSpecificTest(testType: String) {
//        Log.d(TAG, "🧪 Starting specific test: $testType")
//
//        val intent = Intent(this, MainActivity::class.java).apply {
//            action = "START_${testType.uppercase()}_TEST"
//            putExtra("test_type", testType)
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
//        }
//        startActivity(intent)
//    }

    private fun navigateToDestination(destination: String) {
        Log.d(TAG, "🧭 Navigating to: $destination")

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "VOICE_NAVIGATE"
            putExtra("destination", destination)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

//    private fun startFaceTest() {
//        Log.d(TAG, "😊 Starting face test...")
//        // Implementasi face test
//    }
//
//    private fun startArmTest() {
//        Log.d(TAG, "💪 Starting arm test...")
//        // Implementasi arm test
//    }
//
//    private fun startSpeechTest() {
//        Log.d(TAG, "🗣️ Starting speech test...")
//        // Implementasi speech test
//    }
//
//    private fun startTimeTest() {
//        Log.d(TAG, "⏰ Starting time test...")
//        // Implementasi time test
//    }
//
//    private fun callEmergency() {
//        Log.d(TAG, "🚨 Emergency call initiated")
//        // Implementasi panggilan darurat
//    }

    fun stopListening() {
        Log.d(TAG, "🛑 Stopping voice listening...")
        isListening = false
        listeningJob?.cancel()
        audioRecord?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🧹 Voice Assistant Service destroyed")

        stopListening()
        audioRecord?.release()
        porcupine?.delete()
        rhino?.delete()
    }
}
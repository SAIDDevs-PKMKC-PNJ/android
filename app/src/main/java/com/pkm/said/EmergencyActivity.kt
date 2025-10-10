package com.pkm.said

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.ncorti.slidetoact.SlideToActView
import com.pkm.said.databinding.ActivityEmergencyBinding
import java.util.Locale

class EmergencyActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityEmergencyBinding
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener

    private var currentUserLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isEmergencyCallStarted = false
    private var isActivityFinished = false
    private var isVoiceMessagePlaying = false
    private var isTtsReady = false
    private var ttsLanguageAvailable = false
    private val countdownValue = 5

    companion object {
        private const val TAG = "EmergencyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚨 ===== EMERGENCY ACTIVITY STARTED =====")

        binding = ActivityEmergencyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ KEEP SCREEN ON
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "✅ Screen keep-on flag set")

        // ✅ INIT COMPONENTS DULU
        initializeComponents()

        // ✅ CEK PERMISSIONS SEBELUM START PROTOCOL
        checkEmergencyPermissions()
    }

    @SuppressLint("SetTextI18n")
    private fun initializeComponents() {
        Log.d(TAG, "🛠️ Initializing components...")

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            // ✅ INIT TTS DENGAN ERROR HANDLING
            textToSpeech = TextToSpeech(this, this)

            binding.tvCountdown.text = countdownValue.toString()
            binding.tvStatus.text = "KONFIRMASI DARURAT"
            binding.tvHint.text = getString(R.string.emergency_noresponse_hint)

            binding.slideToCancel.onSlideCompleteListener =
                object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        Log.d(TAG, "👆 Slide to cancel triggered")
                        cancelEmergency()
                    }
                }

            setupPhoneStateListener()
            Log.d(TAG, "✅ All components initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing components", e)
            Toast.makeText(this, "Error initializing emergency system", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ✅ CEK PERMISSIONS DARURAT
    private fun checkEmergencyPermissions() {
        Log.d(TAG, "🔐 Checking emergency permissions...")

        val emergencyPermissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )

        val missingPermissions = emergencyPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.e(TAG, "❌ Missing emergency permissions: $missingPermissions")
            showEmergencyPermissionError(missingPermissions)
        } else {
            Log.d(TAG, "✅ All emergency permissions granted")
            startEmergencyProtocol()
        }
    }

    // ✅ TAMPILKAN ERROR JIKA IZIN TIDAK DIBERIKAN
    private fun showEmergencyPermissionError(missingPermissions: List<String>) {
        Log.d(TAG, "🚨 Showing permission error dialog")

        val errorMessage = buildString {
            append("Fitur darurat membutuhkan izin berikut:\n\n")

            missingPermissions.forEach { permission ->
                when (permission) {
                    Manifest.permission.CALL_PHONE -> append("• 📞 Izin Menelepon\n")
                    Manifest.permission.READ_PHONE_STATE -> append("• 📱 Izin Status Telepon\n")
                }
            }

            append("\nSilakan berikan izin di pengaturan aplikasi.")
        }

        AlertDialog.Builder(this)
            .setTitle("Izin Darurat Diperlukan")
            .setMessage(errorMessage)
            .setPositiveButton("Buka Pengaturan") { dialog, which ->
                openAppSettings()
            }
            .setNegativeButton("Tutup") { dialog, which ->
                Log.d(TAG, "❌ User closed without granting permissions")
                finish()
            }
            .setOnCancelListener {
                Log.d(TAG, "❌ User canceled permission dialog")
                finish()
            }
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening app settings", e)
            Toast.makeText(this, "Tidak dapat membuka pengaturan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startEmergencyProtocol() {
        Log.d(TAG, "🚨 STARTING EMERGENCY PROTOCOL")

        runOnUiThread {
            binding.tvStatus.text = "MEMULAI PROTOKOL DARURAT..."
        }

        // ✅ DAPATKAN LOKASI
        getCurrentLocation()

        // ✅ MULAI COUNTDOWN
        startCountdown()

        // ✅ SPEAK ALERT (akan dihandle oleh TTS callback jika ready)
        if (isTtsReady) {
            speakEmergencyAlert()
        } else {
            Log.w(TAG, "⚠️ TTS not ready yet, alert will be spoken when ready")
        }

        Log.d(TAG, "✅ Emergency protocol started successfully")
    }

    private fun startCountdown() {
        Log.d(TAG, "⏰ Starting countdown: $countdownValue seconds")

        countDownTimer = object : CountDownTimer(
            (countdownValue * 1000).toLong(),
            1000L
        ) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvCountdown.text = secondsLeft.toString()

                Log.d(TAG, "⏳ Countdown: $secondsLeft seconds")

                when (secondsLeft) {
                    5, 4 -> binding.tvStatus.text = "KONFIRMASI DARURAT"
                    3, 2 -> binding.tvStatus.text = "BERSIAP MENGHUBUNGI..."
                    1 -> binding.tvStatus.text = "MEMULAI PANGGILAN DARURAT"
                }
            }

            override fun onFinish() {
                Log.d(TAG, "⏰ COUNTDOWN FINISHED - Starting emergency call")

                if (!isActivityFinished) {
                    startEmergencyCall()
                } else {
                    Log.w(TAG, "⚠️ Activity already finished, skipping emergency call")
                }
            }
        }.start()

        Log.d(TAG, "✅ Countdown timer started")
    }

    private fun getUserEmergencyInfo(): String {
        val currentUser = FirebaseAuth.getInstance().currentUser

        return try {
            val userInfo = StringBuilder()

            currentUser?.displayName?.let { name ->
                userInfo.append("Patient name: $name. ")
            }

            currentUser?.email?.let { email ->
                userInfo.append("Email: $email. ")
            }

            "PATIENT INFORMATION: ${userInfo}Condition: Detected acute stroke symptoms. Patient cannot speak."

        } catch (e: Exception) {
            "PATIENT INFORMATION: Patient registered in Said App. Condition: Stroke symptoms, cannot speak."
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                Log.d(TAG, "Phone state changed: $state")

                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.d(TAG, "Call answered - starting emergency voice message")
                        runOnUiThread {
                            binding.tvStatus.text = "PANGGILAN DIANGKAT - MEMUTAR PESAN DARURAT"
                        }
                        startEmergencyVoiceMessage()
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d(TAG, "📞 Call ended or idle")
                        if (isEmergencyCallStarted) {
                            Log.d(TAG, "Call ended")
                            runOnUiThread {
                                binding.tvStatus.text = "CALL COMPLETED"
                                Toast.makeText(this@EmergencyActivity, "Emergency call completed", Toast.LENGTH_LONG).show()
                            }
                            finishEmergency()
                        }
                    }

                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d(TAG, "Phone is ringing...")
                        runOnUiThread {
                            binding.tvStatus.text = "RINGING... WAITING FOR ANSWER"
                            binding.tvHint.text = "Phone is ringing... Emergency message will play when call is answered"
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                currentUserLocation = location
                Log.d(TAG, "Location obtained: $location")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
            }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun startEmergencyCall() {
        val emergencyNumber = getEmergencyNumber()
        Log.d(TAG, "📞 Starting emergency call to: $emergencyNumber")

        if (emergencyNumber.isBlank()) {
            Toast.makeText(this, "Nomor darurat tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ READ_PHONE_STATE permission not granted")
                Toast.makeText(this, "Phone state permission required", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // ✅ Daftarkan listener SEBELUM memulai panggilan (seperti kode teman)
            Log.d(TAG, "📞 Registering phone state listener...")
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "✅ Phone state listener registered")

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$emergencyNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    if (callIntent.resolveActivity(packageManager) != null) {
                        Log.d(TAG, "✅ Starting call intent...")
                        startActivity(callIntent)
                        isEmergencyCallStarted = true

                        runOnUiThread {
                            binding.tvStatus.text = "DIALING: $emergencyNumber"
                            binding.tvHint.text = "Calling emergency number... Message will play automatically when answered"
                            Toast.makeText(this, "Emergency call started", Toast.LENGTH_SHORT).show()
                        }

                        Log.d(TAG, "✅ Emergency call intent started successfully")
                    } else {
                        Log.e(TAG, "❌ No activity found to handle call intent")
                        Toast.makeText(this, "Tidak dapat melakukan panggilan", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error starting call intent", e)
                    Toast.makeText(this, "Error starting call: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }, 1000)
        } catch (e: SecurityException) {
            Log.e(TAG, "Call permission denied", e)
            Toast.makeText(this, "Izin panggilan ditolak", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency call", e)
            Toast.makeText(this, "Gagal memulai panggilan darurat", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startEmergencyVoiceMessage() {
        if (isVoiceMessagePlaying) {
            Log.w(TAG, "⚠️ Voice message already playing")
            return
        }

        isVoiceMessagePlaying = true

        // Tunggu 3 detik setelah panggilan diangkat sebelum mulai bicara
        Handler(mainLooper).postDelayed({
            Log.d(TAG, "🔊 Now playing emergency voice message...")
            speakEmergencyInformation()
        }, 3000)
    }

    private fun getEmergencyNumber(): String {
        return getString(R.string.emergency_number)
    }

    private fun getLocationInfo(): String {
        return if (currentUserLocation != null) {
            val lat = currentUserLocation!!.latitude
            val lng = currentUserLocation!!.longitude
            "GPS Coordinates: ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)
            }. Open Google Maps for navigation."
        } else {
            "LOCATION: Not available. Please check device GPS."
        }
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "🔊 TTS onInit called with status: $status")

        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "✅ TTS engine initialized successfully")

            // ✅ CEK LANGUAGE AVAILABILITY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val availableLanguages = textToSpeech.availableLanguages
                Log.d(TAG, "🌍 Available TTS languages: $availableLanguages")
            }

            // ✅ COBA BAHASA INDONESIA DULU
            var result = textToSpeech.setLanguage(Locale("id", "ID"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "❌ Indonesian TTS not available, trying English...")

                // Fallback ke English
                result = textToSpeech.setLanguage(Locale.US)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "❌ English TTS also not available")
                    ttsLanguageAvailable = false
                    // ✅ TAMPILKAN WARNING KE USER
                    runOnUiThread {
                        Toast.makeText(this, "TTS language not available", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.d(TAG, "✅ English TTS available")
                    ttsLanguageAvailable = true
                }
            } else {
                Log.d(TAG, "✅ Indonesian TTS available")
                ttsLanguageAvailable = true
            }

            isTtsReady = true

            // ✅ OPTIMALISASI TTS - TAMBAHKAN FUNCTION INI
            optimizeTTSForPhoneCall()

            // ✅ PASANG TTS LISTENER SEBELUM SPEAK
            setupTTSListener()

            // ✅ SPEAK ALERT SEKARANG KARENA TTS READY
            speakEmergencyAlert()

        } else {
            Log.e(TAG, "❌ TTS initialization failed")
            isTtsReady = false
            ttsLanguageAvailable = false
            // ✅ TAMPILKAN ERROR KE USER
            runOnUiThread {
                Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ TAMBAHKAN FUNCTION OPTIMIZE TTS YANG HILANG
    private fun optimizeTTSForPhoneCall() {
        try {
            // Set speech rate dan pitch untuk kejelasan di telepon
            textToSpeech.setSpeechRate(1.2f) // Sedikit lebih lambat untuk kejelasan
            textToSpeech.setPitch(1.1f)       // Sedikit lebih tinggi untuk kejelasan

            Log.d(TAG, "✅ TTS optimized for phone call - Rate: 0.85, Pitch: 1.1")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not optimize TTS, using default settings")
        }
    }

    // ✅ PINDAHKAN setupTTSListener KE SINI (sebelum speak)
    private fun setupTTSListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "🎤 TTS started speaking: $utteranceId")
                    runOnUiThread {
                        binding.tvStatus.text = "SPEAKING EMERGENCY MESSAGE..."
                        // ✅ TAMPILKAN TOAST SAAT TTS MULAI
                        Toast.makeText(
                            this@EmergencyActivity,
                            "TTS started speaking",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "✅ TTS finished speaking: $utteranceId")
                    runOnUiThread {
                        binding.tvStatus.text = "EMERGENCY MESSAGE DELIVERED"
                        // ✅ TAMPILKAN TOAST SAAT TTS SELESAI
                        Toast.makeText(
                            this@EmergencyActivity,
                            "TTS finished speaking",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "❌ TTS error with utterance: $utteranceId")
                    runOnUiThread {
                        binding.tvStatus.text = "MESSAGE DELIVERY FAILED"
                        // ✅ TAMPILKAN ERROR KE USER
                        Toast.makeText(
                            this@EmergencyActivity,
                            "TTS error occurred",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
            Log.d(TAG, "✅ TTS utterance listener setup complete")
        } else {
            Log.w(TAG, "⚠️ TTS utterance listener not supported on this API level")
        }
    }

    private fun speakEmergencyAlert() {
        if (!isTtsReady) {
            Log.w(TAG, "⚠️ TTS not ready, cannot speak alert")
            return
        }

        if (!ttsLanguageAvailable) {
            Log.e(TAG, "❌ TTS language not available, cannot speak")
            runOnUiThread {
                Toast.makeText(this, "TTS language not available", Toast.LENGTH_LONG).show()
            }
            return
        }

        val alertMessage =
            "Emergency alert! Calling ambulance in $countdownValue seconds. Slide to cancel."
        Log.d(TAG, "🔊 Speaking alert: $alertMessage")

        try {
            // ✅ GUNAKAN PARAMETERS UNTUK BETTER CONTROL
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "emergency_alert")
            }

            val result = textToSpeech.speak(
                alertMessage,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "emergency_alert"
            )
            Log.d(TAG, "🎤 TTS speak result: $result")

            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "❌ TTS speak returned ERROR")
                runOnUiThread {
                    Toast.makeText(this, "TTS speak error", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d(TAG, "✅ TTS speak queued successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during TTS speak", e)
            runOnUiThread {
                Toast.makeText(this, "TTS exception: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun speakEmergencyInformation() {
        if (!isTtsReady || !ttsLanguageAvailable) {
            Log.w(TAG, "⚠️ TTS not ready, skipping emergency information")
            runOnUiThread {
                Toast.makeText(
                    this,
                    "TTS not available - emergency message cannot be played",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvStatus.text = "TTS UNAVAILABLE - MANUAL HELP NEEDED"
            }
            return
        }

        Log.d(TAG, "🔊 Speaking emergency information...")

        val userInfo = getUserEmergencyInfo()
        val locationInfo = getLocationInfo()

        // ✅ PESAN DARURAT LEBIH SINGKAT DAN JELAS
        val emergencyMessage = """
            Emergency! Automated message from Said App.
            Stroke emergency detected!
            Patient cannot speak!
            $userInfo
            Location: $locationInfo
            Please send ambulance immediately.
            Confirm if you hear this message.
        """.trimIndent()

        Log.d(TAG, "📝 Emergency message prepared, length: ${emergencyMessage.length} chars")

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val originalMode = audioManager.mode
        val originalSpeakerphone = audioManager.isSpeakerphoneOn

        try {
            Log.d(TAG, "🎧 Setting up audio for emergency message...")

            // ✅ GUNAKAN MODE_IN_CALL UNTUK TELEPON
            setupAudioForPhoneCall(audioManager)

            // ✅ TTS SETTINGS UNTUK KEJELASAN
            textToSpeech.setSpeechRate(0.8f)
            textToSpeech.setPitch(1.0f)

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "emergency_info")
            }

            // ✅ TUNGGU SEBENTAR SEBELUM BERBICARA
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🎤 Starting emergency TTS speech...")

                val result = textToSpeech.speak(
                    emergencyMessage,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "emergency_info"
                )
                Log.d(TAG, "🎤 Emergency TTS speak result: $result")


                if (result == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "✅ TTS speech queued successfully")
                    runOnUiThread {
                        binding.tvStatus.text = "SPEAKING TO OPERATOR..."
                        Toast.makeText(this, "Playing emergency message to operator", Toast.LENGTH_LONG).show()
                    }

                    // ✅ MONITOR DURASI TTS
                    monitorTTSDuration()
                } else {
                    Log.e(TAG, "❌ TTS speak failed: $result")
                    // ✅ COBA FALLBACK STRATEGY
                    tryFallbackAudioStrategy(emergencyMessage)
                }

            }, 5000)

            Handler(Looper.getMainLooper()).postDelayed({
                restoreAudioSettings(audioManager, originalMode, originalSpeakerphone)
            }, 45000) // 45 detik
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up audio for emergency message", e)
            // Fallback tanpa audio modifications
            val result = textToSpeech.speak(
                emergencyMessage,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "emergency_info_fallback"
            )
            Log.d(TAG, "🔧 Fallback TTS speak result: $result")
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private fun setupAudioForPhoneCall(audioManager: AudioManager) {
        try {
            Log.d(TAG, "🎧 Setting up audio for phone call...")

            // ✅ STRATEGI 1: MODE_IN_COMMUNICATION (untuk VoIP/telepon)
            audioManager.mode = AudioManager.MODE_IN_CALL
            Log.d(TAG, "✅ Audio mode set to MODE_IN_CALL")

            // ✅ STRATEGI 2: SPEAKERPHONE ON (agar suara keras)
            audioManager.isSpeakerphoneOn = true
            Log.d(TAG, "✅ Speakerphone turned ON")

            // ✅ STRATEGI 3: SET VOLUME MAKSIMAL UNTUK VOICE CALL
            val maxCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVolume, 0)
            Log.d(TAG, "✅ Voice call volume set to maximum: $maxCallVolume")

            // ✅ STRATEGI 4: SET VOLUME MAKSIMAL UNTUK MUSIC JUGA (fallback)
            val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVolume, 0)
            Log.d(TAG, "✅ Music volume set to maximum: $maxMusicVolume")

            // ✅ STRATEGI 5: FORCE AUDIO FOCUS
            val audioFocusResult = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            Log.d(TAG, "✅ Audio focus request result: $audioFocusResult")

            // ✅ STRATEGI 6: BLUETOOTH SCO (jika tersedia)
            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "✅ Bluetooth SCO started")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Bluetooth SCO not available")
            }

            Log.d(TAG, "🎧 Audio setup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up audio", e)
        }
    }

    // ✅ FALLBACK AUDIO STRATEGY JIKA YANG PERTAMA GAGAL
    @Suppress("DEPRECATION")
    private fun tryFallbackAudioStrategy(message: String) {
        Log.d(TAG, "🔄 Trying fallback audio strategy...")

        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            // ✅ FALLBACK 1: COBA MODE_IN_CALL
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            Log.d(TAG, "🔄 Fallback 1: MODE_IN_CALL")

            Handler(Looper.getMainLooper()).postDelayed({
                val result = textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "fallback_1")
                Log.d(TAG, "🔄 Fallback 1 TTS result: $result")
            }, 1000)

            // ✅ FALLBACK 2: COBA MODE_NORMAL DENGAN SPEAKER
            Handler(Looper.getMainLooper()).postDelayed({
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "🔄 Fallback 2: MODE_NORMAL")

                val result = textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, "fallback_2")
                Log.d(TAG, "🔄 Fallback 2 TTS result: $result")
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Fallback audio strategy failed", e)
        }
    }

    // ✅ RESTORE AUDIO SETTINGS
    @Suppress("DEPRECATION")
    private fun restoreAudioSettings(audioManager: AudioManager, originalMode: Int, originalSpeakerphone: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = originalSpeakerphone
            audioManager.mode = originalMode
            audioManager.abandonAudioFocus(null)

            // ✅ STOP BLUETOOTH SCO
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            } catch (e: Exception) {
                // Ignore
            }

            Log.d(TAG, "✅ Audio settings restored")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error restoring audio settings", e)
        }
    }

    // ✅ MONITOR TTS DURATION DAN STATUS
    private fun monitorTTSDuration() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (textToSpeech.isSpeaking) {
                Log.d(TAG, "🔊 TTS still speaking...")
                // TTS masih berbicara, biarkan lanjut
            } else {
                Log.d(TAG, "🔊 TTS finished speaking")
                runOnUiThread {
                    binding.tvStatus.text = "EMERGENCY MESSAGE DELIVERED"
                }
            }
        }, 10000) // Check setelah 10 detik

        Handler(Looper.getMainLooper()).postDelayed({
            if (textToSpeech.isSpeaking) {
                Log.w(TAG, "⚠️ TTS still speaking after 30 seconds, forcing stop")
                textToSpeech.stop()
            }
        }, 30000) // Force stop setelah 30 detik
    }

    private fun cancelEmergency() {
        Log.d(TAG, "🛑 CANCEL EMERGENCY REQUESTED")

        if (!isActivityFinished) {
            try {
                countDownTimer.cancel()
                Log.d(TAG, "⏰ Countdown timer cancelled")
            } catch (e: Exception) {
                Log.d(TAG, "⏰ Timer already cancelled or not started")
            }

            if (isTtsReady) {
                textToSpeech.stop()
                Log.d(TAG, "🔇 TTS stopped")
            }

            isActivityFinished = true

            Toast.makeText(this, "Darurat dibatalkan", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ Emergency cancelled, finishing activity")
            finish()
        }
    }

    private fun finishEmergency() {
        if (!isActivityFinished) {
            isActivityFinished = true
            textToSpeech.stop()

            runOnUiThread {
                binding.tvStatus.text = "PANGILAN DARURAT SELESAI"
                binding.tvCountdown.text = "✓"
                binding.tvHint.text =
                    "Bantuan sedang dalam perjalanan. Tetap tenang dan tunggu pertolongan."
            }

            Handler(mainLooper).postDelayed({
                finish()
            }, 3000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔴 ===== EMERGENCY ACTIVITY DESTROYED =====")
        Log.d(TAG, "🔴 TTS ready: $isTtsReady, Activity finished: $isActivityFinished")

        try {
            if (::countDownTimer.isInitialized) {
                countDownTimer.cancel()
                Log.d(TAG, "⏰ Countdown timer cancelled in onDestroy")
            }
        } catch (e: Exception) {
            Log.d(TAG, "⏰ Timer already cancelled")
        }

        try {
            if (::telephonyManager.isInitialized && ::phoneStateListener.isInitialized) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                Log.d(TAG, "📞 Phone state listener unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering phone state listener", e)
        }

        if (isTtsReady) {
            textToSpeech.stop()
            textToSpeech.shutdown()
            Log.d(TAG, "🔇 TTS stopped and shutdown")
        }

        try {
            if (::telephonyManager.isInitialized) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                Log.d(TAG, "📞 Phone state listener stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping phone state listener", e)
        }

        isActivityFinished = true
        Log.d(TAG, "🔴 ===== EMERGENCY ACTIVITY CLEANUP COMPLETE =====")
    }
}
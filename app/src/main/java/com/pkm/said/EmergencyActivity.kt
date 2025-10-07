package com.pkm.said

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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

    private var currentUserLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isEmergencyCallStarted = false
    private var isActivityFinished = false
    private var isVoiceMessagePlaying = false
    private val countdownValue = 5

    companion object {
        private const val TAG = "EmergencyActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startEmergencyProtocol()
        } else {
            Toast.makeText(this, "Izin diperlukan untuk fungsi darurat", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupBackPressHandler()
        initializeComponents()
        checkPermissions()
    }

    @SuppressLint("SetTextI18n")
    private fun initializeComponents() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        textToSpeech = TextToSpeech(this, this)

        binding.tvCountdown.text = countdownValue.toString()
        binding.tvStatus.text = "KONFIRMASI DARURAT"

        binding.slideToCancel.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                cancelEmergency()
            }
        }

        binding.tvHint.text = getString(R.string.emergency_noresponse_hint)
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startEmergencyProtocol()
        }
    }

    private fun setupBackPressHandler() {
        // Handle back gesture/button dengan OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Tampilkan toast dan prevent back navigation
                Toast.makeText(
                    this@EmergencyActivity,
                    "Gunakan slider untuk membatalkan darurat",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun startEmergencyProtocol() {
        getCurrentLocation()
        startCountdown()
        speakEmergencyAlert()
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(
            (countdownValue * 1000).toLong(),
            1000L
        ) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvCountdown.text = secondsLeft.toString()

                when (secondsLeft) {
                    5, 4 -> binding.tvStatus.text = "KONFIRMASI DARURAT"
                    3, 2 -> binding.tvStatus.text = "BERSIAP MENGHUBUNGI..."
                    1 -> binding.tvStatus.text = "MEMULAI PANGGILAN DARURAT"
                }
            }

            override fun onFinish() {
                if (!isActivityFinished) {
                    startEmergencyCall()
                }
            }
        }.start()
    }

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

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.d(TAG, "Call answered - starting emergency voice message")
                        runOnUiThread {
                            binding.tvStatus.text = getString(R.string.emergency_phone_status_ready)
                        }
                        startEmergencyVoiceMessage()
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isEmergencyCallStarted) {
                            Log.d(TAG, "Call ended")
                            finishEmergency()
                        }
                    }

                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d(TAG, "Phone is ringing...")
                        runOnUiThread {
                            binding.tvStatus.text =
                                getString(R.string.emergency_phone_status_notready)
                        }
                    }
                }
            }
        }

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @SuppressLint("SetTextI18n")
    private fun startEmergencyCall() {
        val emergencyNumber = getEmergencyNumber()

        if (emergencyNumber.isBlank()) {
            Toast.makeText(this, "Nomor darurat tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            // Setup phone state listener SEBELUM memulai panggilan
            setupPhoneStateListener()

            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$emergencyNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (callIntent.resolveActivity(packageManager) != null) {
                startActivity(callIntent)
                isEmergencyCallStarted = true
                Log.d(TAG, "Emergency call started to: $emergencyNumber")

                runOnUiThread {
                    binding.tvStatus.text = "MENGHUBUNGI: $emergencyNumber"
                    binding.tvHint.text = "Panggilan darurat telah dimulai. Aplikasi akan berbicara otomatis ke petugas."
                }
            } else {
                Toast.makeText(this, "Tidak dapat melakukan panggilan", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Call permission denied", e)
            Toast.makeText(this, "Izin panggilan ditolak", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency call", e)
            Toast.makeText(this, "Gagal memulai panggilan darurat: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startEmergencyVoiceMessage() {
        if (isVoiceMessagePlaying) return

        isVoiceMessagePlaying = true

        // Tunggu 3 detik setelah panggilan diangkat sebelum mulai bicara
        android.os.Handler(mainLooper).postDelayed({
            speakEmergencyInformation()
        }, 3000)
    }

    private fun speakEmergencyAlert() {
        val alertMessage = "Peringatan darurat! Aplikasi akan menghubungi ambulans dalam $countdownValue detik. Geser untuk membatalkan."

        textToSpeech.speak(alertMessage, TextToSpeech.QUEUE_FLUSH, null, "emergency_alert")
    }

    private fun speakEmergencyInformation() {
        val userInfo = getUserEmergencyInfo()
        val locationInfo = getLocationInfo()

        val emergencyMessage = """
            Halo, ini adalah PESAN DARURAT OTOMATIS dari Aplikasi Said.
            
            PERINGATAN: POTENSI STROKE AKUT!
            
            $userInfo
            
            PASIEN TIDAK DAPAT BERBICARA!
            
            INFORMASI LOKASI:
            $locationInfo
            
            TINDAKAN DARURAT YANG DIPERLUKAN:
            1. Segera kirim ambulans UGD stroke
            2. Pasien membutuhkan CT Scan segera
            3. Siapkan terapi fibrinolitik
            
            Tolong konfirmasi jika pesan ini terdengar.
            Terima kasih.
        """.trimIndent()

        // Setup audio untuk kejelasan maksimal di telepon
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val originalMode = audioManager.mode

        try {
            // Switch ke mode communication untuk telepon
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )

            // Set TTS untuk kejelasan
            textToSpeech.setSpeechRate(0.8f)
            textToSpeech.setPitch(1.0f)

            // Speak dengan parameter khusus untuk telepon
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            }

            textToSpeech.speak(emergencyMessage, TextToSpeech.QUEUE_FLUSH, params, "emergency_info")

            Log.d(TAG, "Emergency voice message started")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio for emergency message", e)
            // Fallback: speak without audio modifications
            textToSpeech.speak(emergencyMessage, TextToSpeech.QUEUE_FLUSH, null, "emergency_info_fallback")
        }

        // Kembalikan audio settings setelah selesai
        android.os.Handler(mainLooper).postDelayed({
            try {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = originalMode
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring audio settings", e)
            }
        }, 30000) // 30 detik
    }

    private fun getEmergencyNumber(): String {
        // Langsung return nomor darurat tetap
        return getString(R.string.emergency_number) // Ambulance PMI - default Indonesia
    }

    private fun getUserEmergencyInfo(): String {
        val currentUser = FirebaseAuth.getInstance().currentUser

        return try {
            val userInfo = StringBuilder()

            currentUser?.displayName?.let { name ->
                userInfo.append("Nama pasien: $name. ")
            }

            currentUser?.email?.let { email ->
                userInfo.append("Email: $email. ")
            }

            "INFORMASI PASIEN: ${userInfo}Kondisi: Terdeteksi gejala stroke akut. Pasien tidak dapat berbicara."

        } catch (e: Exception) {
            "INFORMASI PASIEN: Pasien terdaftar di Aplikasi Said. Kondisi: Gejala stroke, tidak bisa bicara."
        }
    }

    private fun getLocationInfo(): String {
        return if (currentUserLocation != null) {
            val lat = currentUserLocation!!.latitude
            val lng = currentUserLocation!!.longitude
            "Koordinat GPS: ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}. Buka Google Maps untuk navigasi."
        } else {
            "LOKASI: Tidak tersedia. Periksa GPS perangkat."
        }
    }

    private fun cancelEmergency() {
        if (!isActivityFinished) {
            countDownTimer.cancel()
            textToSpeech.stop()
            isActivityFinished = true

            Toast.makeText(this, "Darurat dibatalkan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun finishEmergency() {
        if (!isActivityFinished) {
            isActivityFinished = true
            textToSpeech.stop()

            runOnUiThread {
                binding.tvStatus.text = getString(R.string.emergency_phone_status_finish)
                binding.tvCountdown.text = "✓"
                binding.tvHint.text = getString(R.string.emergency_finish_hint)
            }

            android.os.Handler(mainLooper).postDelayed({
                finish()
            }, 3000)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("id", "ID"))

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "TTS Bahasa Indonesia tidak support, pakai English")
                textToSpeech.language = Locale.ENGLISH
            } else {
                Log.d(TAG, "TTS initialized successfully with Indonesian")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            countDownTimer.cancel()
        } catch (e: Exception) {
            // Timer already cancelled
        }
        textToSpeech.stop()
        textToSpeech.shutdown()

        // Hentikan phone state listening
        try {
            telephonyManager.listen(null, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping phone state listener", e)
        }

        isActivityFinished = true
        Log.d(TAG, "EmergencyActivity destroyed")
    }
}
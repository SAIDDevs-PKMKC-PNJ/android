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
import com.ncorti.slidetoact.SlideToActView
import com.pkm.said.databinding.ActivityEmergencyBinding
import com.pkm.said.util.SessionManager
import java.util.Locale
// Menghapus import java.time.* karena tidak lagi menghitung usia

class EmergencyActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityEmergencyBinding
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var textToSpeech: TextToSpeech
    // private lateinit var telephonyManager: TelephonyManager // TIDAK DIPERLUKAN LAGI
    // private lateinit var phoneStateListener: PhoneStateListener // TIDAK DIPERLUKAN LAGI

    private var currentUserLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var contactEmergencyNumber: String = ""

    private var isEmergencyCallStarted = false
    private var isActivityFinished = false
    private var isVoiceMessagePlaying = false
    private var isTtsReady = false
    private var ttsLanguageAvailable = false

    // ✅ Durasi Countdown diubah menjadi 10 detik
    private val countdownValue = 10

    companion object {
        private const val TAG = "EmergencyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚨 ===== EMERGENCY ACTIVITY STARTED =====")

        binding = ActivityEmergencyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // KEEP SCREEN ON
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "✅ Screen keep-on flag set")

        // INIT COMPONENTS DULU
        initializeComponents()

        // MUAT DATA PENGGUNA TERMASUK NOMOR DARURAT
        loadEmergencyData()

        // CEK PERMISSIONS SEBELUM START PROTOCOL
        checkEmergencyPermissions()

        // ✅ DAFTARKAN LISTENER BACK PRESS
        setupOnBackPressed()
    }

    // ✅ SETUP BACK PRESS HANDLER (MENCEGAH KELUAR DARI DARURAT)
    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Di mode darurat, back press dianggap sebagai cancel
                cancelEmergency()
            }
        })
    }

    private fun loadEmergencyData() {
        val emergencyNum = SessionManager.getEmergencyPhone(this)

        if (emergencyNum.isNullOrBlank()) {
            contactEmergencyNumber = getString(R.string.emergency_number)
            Log.e(TAG, "❌ Emergency number from SessionManager is empty. Using fallback: $contactEmergencyNumber")
            runOnUiThread {
                Toast.makeText(this, "Nomor darurat kontak tidak ditemukan, menggunakan nomor layanan darurat default.", Toast.LENGTH_LONG).show()
            }
        } else {
            contactEmergencyNumber = emergencyNum.replace("[^0-9+]".toRegex(), "")
            Log.d(TAG, "✅ Loaded emergency number: $contactEmergencyNumber")
        }
    }


    @SuppressLint("SetTextI18n")
    private fun initializeComponents() {
        Log.d(TAG, "🛠️ Initializing components...")

        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            // telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager // Dihapus

            // INIT TTS DENGAN ERROR HANDLING
            textToSpeech = TextToSpeech(this, this)

            binding.tvCountdown.text = countdownValue.toString()
            binding.tvStatus.text = "ALARM LOKAL AKTIF"
            binding.tvHint.text = getString(R.string.emergency_noresponse_hint)

            binding.slideToCancel.onSlideCompleteListener =
                object : SlideToActView.OnSlideCompleteListener {
                    override fun onSlideComplete(view: SlideToActView) {
                        Log.d(TAG, "👆 Slide to cancel triggered")
                        cancelEmergency()
                    }
                }

            // setupPhoneStateListener() // Dihapus
            Log.d(TAG, "✅ All components initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing components", e)
            Toast.makeText(this, "Error initializing emergency system", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // CEK PERMISSIONS DARURAT
    private fun checkEmergencyPermissions() {
        Log.d(TAG, "🔐 Checking emergency permissions...")

        // ✅ HANYA PERLU CALL_PHONE untuk panggilan
        val emergencyPermissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION // Tambahkan Lokasi
        )

        val missingPermissions = emergencyPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "⚠️ Missing permissions: $missingPermissions. Requesting now.")
            // Hanya meminta izin yang penting di sini, sisanya nanti
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 999)
        } else {
            Log.d(TAG, "✅ All required permissions granted")
            startEmergencyProtocol()
        }
    }

    // ✅ HANDLE PERMISSION RESULT
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 999) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "✅ Permissions granted after request")
                startEmergencyProtocol()
            } else {
                Log.e(TAG, "❌ Required permissions denied")
                showEmergencyPermissionError(permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED })
            }
        }
    }


    // TAMPILKAN ERROR JIKA IZIN TIDAK DIBERIKAN (Revisi pesan)
    private fun showEmergencyPermissionError(missingPermissions: List<String>) {
        Log.d(TAG, "🚨 Showing permission error dialog")

        val errorMessage = buildString {
            append("Fitur darurat membutuhkan izin:\n\n")

            missingPermissions.forEach { permission ->
                when (permission) {
                    Manifest.permission.CALL_PHONE -> append("• 📞 Izin Menelepon\n")
                    Manifest.permission.ACCESS_FINE_LOCATION -> append("• 📍 Izin Lokasi\n")
                }
            }
            append("\nTanpa izin ini, panggilan atau pelaporan lokasi tidak dapat dilakukan.")
        }

        AlertDialog.Builder(this)
            .setTitle("Izin Darurat Diperlukan")
            .setMessage(errorMessage)
            .setPositiveButton("Tutup") { dialog, which ->
                Log.d(TAG, "❌ User closed without granting permissions")
                finish()
            }
            .setOnCancelListener {
                Log.d(TAG, "❌ User canceled permission dialog")
                finish()
            }
            .show()
    }

    private fun startEmergencyProtocol() {
        Log.d(TAG, "🚨 STARTING EMERGENCY PROTOCOL")

        runOnUiThread {
            binding.tvStatus.text = "ALARM LOKAL AKTIF"
        }

        // DAPATKAN LOKASI
        getCurrentLocation()

        // ✅ SPEAK ALERT SEKARANG KARENA INI ALARM LOKAL
        if (isTtsReady) {
            speakEmergencyAlert()
        } else {
            Log.w(TAG, "⚠️ TTS not ready yet, alert will be spoken when ready")
        }


        // MULAI COUNTDOWN
        startCountdown()

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
                    10, 9 -> binding.tvStatus.text = "ALARM LOKAL AKTIF"
                    8, 7 -> binding.tvStatus.text = "TOLONG BATALKAN JIKA AMAN"
                    6, 5 -> binding.tvStatus.text = "SIAP MENGHUBUNGI KONTAK DARURAT"
                    4, 3 -> binding.tvStatus.text = "MEMASTIKAN LOKASI..."
                    2, 1 -> binding.tvStatus.text = "MEMULAI PANGGILAN DARURAT"
                }
            }

            override fun onFinish() {
                Log.d(TAG, "⏰ COUNTDOWN FINISHED - Starting emergency call")

                if (!isActivityFinished) {
                    // ✅ PANGGILAN DIMULAI
                    startEmergencyCall()

                    // ✅ TTS PESAN JANGAN LAGI DIMULAI DI SINI, ORANG YG BANTU YG BICARA
                    speakInstructionMessage()
                } else {
                    Log.w(TAG, "⚠️ Activity already finished, skipping emergency call")
                }
            }
        }.start()

        Log.d(TAG, "✅ Countdown timer started")
    }

    // ✅ REVISI: Hanya Nama dan Lokasi GPS
    private fun getUserEmergencyInfo(): String {
        val name = SessionManager.getUserName(this).orEmpty()

        val locationInfo = getLocationInfo()

        return buildString {
            append("Nama Korban: ${name}. ")
            append("Gejala Stroke. ")
            append("Lokasi Korban Saat Ini: $locationInfo")
        }
    }

    // ✅ HAPUS FUNGSI calculateAge

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        // Cek izin (sudah dilakukan di checkEmergencyPermissions)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Jika izin belum diberikan saat ini, Location akan null
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

    // ✅ startEmergencyCall TANPA PHONE STATE LISTENER
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun startEmergencyCall() {
        val emergencyNumber = contactEmergencyNumber
        Log.d(TAG, "📞 Starting emergency call to: $emergencyNumber (CONTACT NUMBER)")

        if (emergencyNumber.isBlank()) {
            Toast.makeText(this, "Nomor darurat tidak ditemukan", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            // Tidak perlu READ_PHONE_STATE jika kita tidak memantau state panggilan

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
                            binding.tvStatus.text = "PANGGILAN DIMULAI: $emergencyNumber"
                            binding.tvHint.text = "Berikan Ponsel ini ke orang yang mengangkat telepon dan laporkan informasi korban yang tertulis di layar!"
                            Toast.makeText(this, "Panggilan darurat dimulai", Toast.LENGTH_SHORT).show()
                        }

                        // ✅ Selesaikan aktivitas setelah panggilan dimulai (karena panggilan akan berlanjut di luar app)
                        Handler(mainLooper).postDelayed({
                            finishEmergency()
                        }, 5000) // Beri waktu 5 detik sebelum finishActivity

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

    // ✅ startEmergencyVoiceMessage dihapus

    private fun getLocationInfo(): String {
        return if (currentUserLocation != null) {
            val lat = currentUserLocation!!.latitude
            val lng = currentUserLocation!!.longitude
            val mapLink = "http://maps.google.com/maps?q=${String.format("%.6f", lat)},${String.format("%.6f", lng)}"
            // ✅ Teks lebih mudah dibaca/dilaporkan
            "Koordinat GPS ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}. Link peta: $mapLink"
        } else {
            "Lokasi GPS tidak tersedia. Mohon cek GPS perangkat."
        }
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "🔊 TTS onInit called with status: $status")

        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "✅ TTS engine initialized successfully")

            // Cek Bahasa Indonesia
            var result = textToSpeech.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = textToSpeech.setLanguage(Locale.US) // Fallback English
                ttsLanguageAvailable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                ttsLanguageAvailable = true
            }

            isTtsReady = true

            // ✅ OPTIMASI VOLUME untuk ALARM LOKAL
            optimizeTTSForLocalAlarm()

            setupTTSListener()

            // ✅ Panggil alert/alarm di sini
            if (ttsLanguageAvailable) {
                speakEmergencyAlert()
            }

        } else {
            Log.e(TAG, "❌ TTS initialization failed")
            isTtsReady = false
            ttsLanguageAvailable = false
            runOnUiThread {
                Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ FUNGSI BARU: OPTIMASI TTS UNTUK ALARM LOKAL (Volume Maksimal)
    private fun optimizeTTSForLocalAlarm() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)

            textToSpeech.setSpeechRate(1.5f) // Bicara lebih cepat
            textToSpeech.setPitch(1.3f)       // Nada lebih tinggi

            Log.d(TAG, "✅ TTS optimized for local alarm (max volume)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not optimize TTS for alarm", e)
        }
    }

    // ✅ FUNGSI BARU: TTS PESAN INSTRUKSI (SETELAH COUNTDOWN)
    private fun speakInstructionMessage() {
        if (!isTtsReady || !ttsLanguageAvailable) return

        val userInfo = getUserEmergencyInfo()
        val instructionMessage = """
            Penting! Panggilan darurat dimulai. 
            Mohon berikan ponsel ini kepada orang yang mengangkat telepon.
            Laporkan informasi korban ini:
            $userInfo
        """.trimIndent()

        // ✅ KEMBALIKAN KE SETTING BICARA NORMAL/PELAN SEBELUM INSTRUKSI
        textToSpeech.setSpeechRate(1.0f)
        textToSpeech.setPitch(1.0f)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "instruction_info")
        }

        textToSpeech.speak(
            instructionMessage,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "instruction_info"
        )
    }


    private fun setupTTSListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                // ... (Listener sama, hanya untuk logging dan UI) ...
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "🎤 TTS started speaking: $utteranceId")
                    if (utteranceId == "emergency_alert") {
                        runOnUiThread { binding.tvStatus.text = "ALARM LOKAL BERBUNYI" }
                    }
                }
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "✅ TTS finished speaking: $utteranceId")
                    if (utteranceId == "emergency_alert") {
                        runOnUiThread { binding.tvStatus.text = "PERHATIAN: Alarm selesai, menunggu panggilan." }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "❌ TTS error with utterance: $utteranceId")
                }
            })
        }
    }

    // ✅ REVISI: TTS Berfungsi sebagai Alarm Lokal
    private fun speakEmergencyAlert() {
        if (!isTtsReady || !ttsLanguageAvailable) return

        val alertMessage = "Perhatian darurat! Tolong ada yang sakit! Segera bantu korban! Panggilan telepon akan dimulai dalam $countdownValue detik."
        Log.d(TAG, "🔊 Speaking local alarm: $alertMessage")

        try {
            val params = Bundle().apply {
                // ✅ STREAM_ALARM untuk bunyi yang lebih keras dan berbeda dari media
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "emergency_alert")
            }

            // ✅ QUEUE_ADD agar bisa mengulang atau menambah instruksi
            val result = textToSpeech.speak(
                alertMessage,
                TextToSpeech.QUEUE_ADD,
                params,
                "emergency_alert"
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during TTS speak (Alarm)", e)
            Toast.makeText(this, "Alarm TTS error", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ speakEmergencyInformation dihapus

    private fun cancelEmergency() {
        Log.d(TAG, "🛑 CANCEL EMERGENCY REQUESTED")

        if (!isActivityFinished) {
            try {
                countDownTimer.cancel()
                Log.d(TAG, "⏰ Countdown timer cancelled")
            } catch (e: Exception) {
                // Ignore
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
                    "Ponsel dialihkan. Pastikan orang yang mengangkat telepon sudah melaporkan info korban di layar."
            }

            // ✅ JANGAN LANGSUNG FINISH, TUNGGU BEBERAPA DETIK
            Handler(mainLooper).postDelayed({
                finish()
            }, 5000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔴 ===== EMERGENCY ACTIVITY DESTROYED =====")

        try {
            if (::countDownTimer.isInitialized) {
                countDownTimer.cancel()
            }
        } catch (e: Exception) {
            // Ignore
        }

        if (isTtsReady) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        // HAPUS UNREGISTER TELEPHONY MANAGER

        isActivityFinished = true
        Log.d(TAG, "🔴 ===== EMERGENCY ACTIVITY CLEANUP COMPLETE =====")
    }
}
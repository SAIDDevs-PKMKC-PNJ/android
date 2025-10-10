package com.pkm.said.screening

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pkm.said.databinding.FragmentSpeechTestBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

class SpeechTestFragment : Fragment() {

    private var _binding: FragmentSpeechTestBinding? = null
    private val binding get() = _binding!!

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val waveHandler = Handler(Looper.getMainLooper())
    private var updateWave = true

    private val recordDurationMs = 10000L // 10 detik
    private val progressIntervalMs = 100L
    private var recordStartTime = 0L
    private var recordProgressHandler: Handler? = Handler(Looper.getMainLooper())
    private var recordProgressRunnable: Runnable? = null
    private var progressHandler: Handler? = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSpeechTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        ensureActiveSession()
    }

    private fun setupUI() {
        binding.btnRecord.setOnClickListener { startRecording() }
        binding.btnPlay.setOnClickListener { playRecording() }
        binding.btnHeard.setOnClickListener { finishWithResult(true) }
        binding.btnNotHeard.setOnClickListener { finishWithResult(false) }
        binding.btnRetryMic.setOnClickListener { retryRecording() }

        // Set initial visibility sesuai layout
        binding.progressBarRecord.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.tvAfterRecord.visibility = View.GONE
        binding.btnPlay.visibility = View.GONE
        binding.btnRetryMic.visibility = View.GONE
        binding.llHearButtons.visibility = View.GONE
        binding.progressBarPlay.visibility = View.GONE

        // Set instruction text sesuai layout
        binding.tvInstruction.text = "Katakan\nSaya mau pulang untuk minum"
    }

    private fun ensureActiveSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (ScreeningDataManager.getCurrentSession(requireContext()) == null) {
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                ScreeningDataManager.startNewSession(requireContext(), userId)
                Log.d("SpeechTest", "Sesi baru dibuat untuk speech test")
            } else {
                Log.d("SpeechTest", "Menggunakan sesi yang sudah ada")
            }
        }
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(requireContext(), "Izin mikrofon diperlukan untuk tes ini", Toast.LENGTH_SHORT).show()
            resetToReadyState()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        audioFile = File(requireContext().cacheDir, "speech_test_${System.currentTimeMillis()}.3gp")

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordStartTime = System.currentTimeMillis()

            // Update UI untuk recording state
            updateUIForRecording()
            startProgressTimer()
            startWaveAnimation()

            Toast.makeText(requireContext(), "Merekam suara...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal memulai rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
            resetToReadyState()
        }
    }

    private fun startProgressTimer() {
        recordProgressHandler = Handler(Looper.getMainLooper())
        recordProgressRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - recordStartTime
                val progress = (elapsed.toFloat() / recordDurationMs * 10).toInt() // max=10 sesuai layout
                binding.progressBarRecord.progress = progress

                val remainingSeconds = ((recordDurationMs - elapsed).coerceAtLeast(0)) / 1000
                binding.tvCountdown.text = "Sisa waktu: $remainingSeconds"

                if (elapsed < recordDurationMs) {
                    recordProgressHandler?.postDelayed(this, progressIntervalMs)
                } else {
                    stopRecording()
                }
            }
        }
        recordProgressHandler?.post(recordProgressRunnable!!)
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("SpeechTest", "Error stopping recorder: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
            recordProgressHandler?.removeCallbacks(recordProgressRunnable ?: Runnable {})
            updateUIForRecorded()
            Toast.makeText(requireContext(), "Rekaman selesai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playRecording() {
        if (audioFile == null || !audioFile!!.exists()) {
            Toast.makeText(requireContext(), "File rekaman tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            player = MediaPlayer().apply {
                setDataSource(audioFile!!.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    binding.btnPlay.text = "Putar"
                    binding.btnPlay.isEnabled = true
                    binding.progressBarPlay.visibility = View.GONE
                    stopWaveAnimation()
                }
            }

            binding.btnPlay.isEnabled = false
            binding.progressBarPlay.visibility = View.VISIBLE
            startWaveAnimation()
            Toast.makeText(requireContext(), "Memutar rekaman...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal memutar rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.progressBarPlay.visibility = View.GONE
        }
    }

    private fun finishWithResult(success: Boolean) {
        binding.btnHeard.isEnabled = false
        binding.btnNotHeard.isEnabled = false

        val isSuccessful = success
        val severity = if (success) 0.0f else 1.0f
        val note = if (success)
            "Tes suara: Rekaman terdengar jelas (normal)"
        else
            "Tes suara: Rekaman tidak terdengar/tidak jelas (abnormal)"

        val result = TestResult(
            testName = "befast_speech",
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = severity,
            notes = note,
            timestamp = getCurrentTimestamp(),
            duration = recordDurationMs,
            testData = mapOf<String, Any>(
                "audio_file" to (audioFile?.absolutePath ?: ""),
                "file_size" to (audioFile?.length() ?: 0L),
                "test_type" to "speech_clarity",
                "phrase" to "Saya mau pulang untuk minum"
            )
        )

        // Simpan ke ScreeningDataManager (local)
        ScreeningDataManager.updateTestResult(requireContext(), result)

        // Kirim ke Firestore via ScreeningRepository
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ✅ CEK PROGRESS dulu sebelum complete session
                val progress = ScreeningDataManager.getSessionProgress(requireContext())
                val completedTests = ScreeningDataManager.getAllResults(requireContext()).count { it.isCompleted }
                val totalTests = 5 // Balance, Eyes, Face, Arms, Speech

                Toast.makeText(
                    requireContext(),
                    "✅ Hasil tes suara disimpan\nProgress: $completedTests/$totalTests tes",
                    Toast.LENGTH_LONG
                ).show()

                // ✅ JANGAN langsung complete session, tunggu semua test selesai
                // Hanya navigate back saja
                parentFragmentManager.popBackStack()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "✅ Data tersimpan lokal",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun retryRecording() {
        cleanupRecording()
        audioFile = null
        resetToReadyState()
        Toast.makeText(requireContext(), "Silakan rekam ulang suara Anda", Toast.LENGTH_SHORT).show()
    }

    // ===================== UI STATE MANAGEMENT =====================
    private fun updateUIForRecording() {
        binding.btnRecord.visibility = View.GONE
        binding.progressBarRecord.visibility = View.VISIBLE
        binding.tvCountdown.visibility = View.VISIBLE
        binding.tvAfterRecord.visibility = View.GONE
        binding.btnPlay.visibility = View.GONE
        binding.btnRetryMic.visibility = View.GONE
        binding.llHearButtons.visibility = View.GONE
        binding.progressBarPlay.visibility = View.GONE
    }

    private fun updateUIForRecorded() {
        binding.btnRecord.visibility = View.GONE
        binding.progressBarRecord.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.tvAfterRecord.visibility = View.VISIBLE
        binding.btnPlay.visibility = View.VISIBLE
        binding.btnRetryMic.visibility = View.VISIBLE
        binding.llHearButtons.visibility = View.VISIBLE
        binding.progressBarPlay.visibility = View.GONE

        // Reset progress bar untuk playback
        binding.progressBarPlay.progress = 0
    }

    private fun resetToReadyState() {
        binding.btnRecord.visibility = View.VISIBLE
        binding.progressBarRecord.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.tvAfterRecord.visibility = View.GONE
        binding.btnPlay.visibility = View.GONE
        binding.btnRetryMic.visibility = View.GONE
        binding.llHearButtons.visibility = View.GONE
        binding.progressBarPlay.visibility = View.GONE

        // Reset progress bars
        binding.progressBarRecord.progress = 0
        binding.progressBarPlay.progress = 0
    }

    // ===================== WAVE ANIMATION =====================
    private fun startWaveAnimation() {
        updateWave = true
        waveHandler.post(waveRunnable)
    }

    private fun stopWaveAnimation() {
        updateWave = false
        waveHandler.removeCallbacks(waveRunnable)
        binding.bottomWaveView.setVoiceAmplitudes(List(128) { 0f })
    }

    private val waveRunnable = object : Runnable {
        override fun run() {
            val amps: List<Float> = if (isRecording) {
                // Untuk recording - gunakan amplitude real
                val amp = try {
                    (recorder?.maxAmplitude ?: 0).toFloat()
                } catch (_: Exception) {
                    0f
                }
                List(128) { i ->
                    // Create wave pattern based on amplitude
                    val baseAmp = amp * 0.01f // Scale down
                    when {
                        i in 60..68 -> baseAmp * 1.0f
                        i in 50..58 -> baseAmp * 0.8f
                        i in 40..48 -> baseAmp * 0.6f
                        i in 30..38 -> baseAmp * 0.4f
                        else -> baseAmp * 0.2f
                    }
                }
            } else if (player?.isPlaying == true) {
                // Untuk playback - simulated wave
                List(128) { i ->
                    val time = System.currentTimeMillis() * 0.01f
                    val frequency = i * 0.1f
                    (sin(time + frequency) * 5000 + 5000).toFloat()
                }
            } else {
                List(128) { 0f }
            }

            binding.bottomWaveView.setVoiceAmplitudes(amps)

            if (updateWave) {
                waveHandler.postDelayed(this, 50)
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    // ===================== LIFECYCLE =====================
    override fun onPause() {
        super.onPause()
        cleanupResources()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanupResources()
        _binding = null
    }

    private fun cleanupResources() {
        // Cleanup recording
        try {
            if (isRecording) {
                recorder?.stop()
            }
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null

        // Cleanup playback
        player?.release()
        player = null

        // Cleanup handlers
        waveHandler.removeCallbacksAndMessages(null)
        recordProgressHandler?.removeCallbacks(recordProgressRunnable ?: Runnable {})
        progressHandler?.removeCallbacks(progressRunnable ?: Runnable {})

        isRecording = false
        updateWave = false
    }

    private fun cleanupRecording() {
        try {
            if (isRecording) {
                recorder?.stop()
            }
        } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false
    }
}
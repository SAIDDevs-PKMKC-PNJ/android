package com.pkm.said.screening

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.databinding.FragmentSpeechTestBinding
import java.io.File
import kotlin.math.*

class SpeechTestFragment : Fragment() {

    private var _binding: FragmentSpeechTestBinding? = null
    private val binding get() = _binding!!

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val waveHandler = Handler(Looper.getMainLooper())
    private var updateWave = true

    private val recordDurationMs = 10000L // detik
    private val progressIntervalMs = 50L
    private var recordStartTime = 0L
    private var recordProgressHandler: Handler? = Handler(Looper.getMainLooper())
    private var recordProgressRunnable: Runnable? = null
    private var progressHandler: Handler? = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentSpeechTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRecord.setOnClickListener { startRecording() }
        binding.btnPlay.setOnClickListener { playRecording() }
        binding.btnHeard.setOnClickListener { finishWithResult(true) }
        binding.btnNotHeard.setOnClickListener { finishWithResult(false) }
        binding.btnRetryMic.setOnClickListener { retryRecording() }

        binding.progressBarRecord.max = (recordDurationMs / progressIntervalMs).toInt()
        binding.progressBarRecord.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.btnRetryMic.visibility = View.GONE
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(requireContext(), "Mic permission ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        audioFile = File(requireContext().cacheDir, "mic_test_${System.currentTimeMillis()}.3gp")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        binding.btnRecord.isEnabled = false
        binding.progressBarRecord.progress = 0
        binding.progressBarRecord.max = (recordDurationMs / progressIntervalMs).toInt()
        binding.progressBarRecord.visibility = View.VISIBLE
        binding.tvCountdown.visibility = View.VISIBLE
        binding.tvCountdown.text = "10.0 detik"

        recordStartTime = System.currentTimeMillis()
        startSmoothProgressBar()

        waveHandler.post(waveRunnable)
        Toast.makeText(requireContext(), "Merekam selama ${recordDurationMs / 1000} detik...", Toast.LENGTH_SHORT).show()
    }

    private fun startSmoothProgressBar() {
        recordProgressHandler = Handler()
        recordProgressRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - recordStartTime
                val progress = (elapsed / progressIntervalMs).toInt()
                binding.progressBarRecord.progress = progress
                val remaining = ((recordDurationMs - elapsed).coerceAtLeast(0)).toFloat() / 1000f
                binding.tvCountdown.text = String.format("%.1f detik", remaining)
                if (elapsed < recordDurationMs) {
                    recordProgressHandler?.postDelayed(this, progressIntervalMs)
                } else {
                    binding.progressBarRecord.progress = binding.progressBarRecord.max
                    binding.tvCountdown.text = "Selesai!"
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
            Toast.makeText(requireContext(), "Error rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            recorder = null
            isRecording = false
            updateWave = false

            // Stop wave & progress bar handler jika ada
            waveHandler.removeCallbacksAndMessages(null)
            recordProgressHandler?.removeCallbacks(recordProgressRunnable ?: Runnable {})

            // Reset wave
            binding.bottomWaveView.setVoiceAmplitudes(List(128) { 0f })

            // UI perubahan
            binding.btnRecord.isEnabled = true
            binding.btnRecord.visibility = View.GONE
            binding.progressBarRecord.visibility = View.GONE
            binding.tvCountdown.visibility = View.GONE

            binding.tvAfterRecord.visibility = View.VISIBLE
            binding.btnPlay.visibility = View.VISIBLE
            binding.llHearButtons.visibility = View.VISIBLE
            binding.btnRetryMic.visibility = View.VISIBLE

            Toast.makeText(requireContext(), "Rekaman selesai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playRecording() {
        if (audioFile == null || !audioFile!!.exists()) {
            Toast.makeText(requireContext(), "File audio tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }
        if (player?.isPlaying == true) return

        binding.btnPlay.isEnabled = false
        binding.progressBarPlay.progress = 0
        binding.progressBarPlay.visibility = View.VISIBLE

        player = MediaPlayer().apply {
            setDataSource(audioFile!!.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                binding.btnPlay.isEnabled = true
                binding.progressBarPlay.visibility = View.GONE
                binding.bottomWaveView.setVoiceAmplitudes(List(128) { 0f })
                progressHandler?.removeCallbacks(progressRunnable ?: Runnable {})
            }
        }
        updateWave = true
        waveHandler.post(waveRunnable)

        // Update progress bar sesuai durasi audio
        val duration = player?.duration ?: 1
        binding.progressBarPlay.max = duration
        progressHandler = Handler()
        progressRunnable = object : Runnable {
            override fun run() {
                if (player != null && player!!.isPlaying) {
                    binding.progressBarPlay.progress = player!!.currentPosition
                    progressHandler?.postDelayed(this, 100)
                }
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    // Update amplitudo ke satu gelombang di bawah
    private val waveRunnable = object : Runnable {
        override fun run() {
            val amps: List<Float> = if (isRecording) {
                val amp = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
                // FftWaveView butuh 128 points, bukan 20
                List(128) { i ->
                    when {
                        i in 60..68 -> amp.toFloat() // Puncak di tengah
                        i in 50..78 -> amp * (0.6f + 0.4f * Math.random()).toFloat()
                        i in 40..88 -> amp * (0.3f + 0.3f * Math.random()).toFloat()
                        else -> amp * (0.1f * Math.random()).toFloat()
                    }
                }
            } else if (player?.isPlaying == true) {
                // Simulasi FFT-like data saat playback
                List(128) { i ->
                    val freq = i.toFloat() / 128f
                    val base = sin(freq * PI * 4).toFloat().absoluteValue
                    (base * (2000..8000).random()).toFloat()
                }
            } else {
                List(128) { 0f }
            }

            binding.bottomWaveView.setVoiceAmplitudes(amps)

            if ((isRecording || player?.isPlaying == true) && updateWave) {
                waveHandler.postDelayed(this, 50)
            }
        }
    }
    private fun finishWithResult(success: Boolean) {
        binding.btnHeard.isEnabled = false
        binding.btnNotHeard.isEnabled = false

        val isSuccessful = success
        val severity = if (success) 0f else 1f
        val note = if (success) "Rekaman berhasil & terdengar (aman)." else "Rekaman tidak terdengar/bermasalah."

        ScreeningDataManager.updateTestResult(
            requireContext(),
            TestResult(
                testName = "speech_test",
                isCompleted = true,
                isSuccessful = isSuccessful,
                score = severity,
                notes = note,
                timestamp = getCurrentTimestamp()
            )
        )

        Toast.makeText(
            requireContext(),
            if (success) "Tersimpan: Aman (score 0%)" else "Tersimpan: Bahaya (score 100%)",
            Toast.LENGTH_SHORT
        ).show()

        // Lanjut ke hasil akhir
        findNavController().navigate(com.pkm.said.R.id.action_speechTest_to_screeningResult)
    }


    private fun retryRecording() {
        audioFile?.delete()
        audioFile = null
        binding.bottomWaveView.setVoiceAmplitudes(List(128) { 0f })
        binding.btnRecord.visibility = View.VISIBLE
        binding.btnRecord.isEnabled = true
        binding.btnPlay.visibility = View.GONE
        binding.tvAfterRecord.visibility = View.GONE
        binding.llHearButtons.visibility = View.GONE
        binding.btnRetryMic.visibility = View.GONE
        binding.progressBarRecord.progress = 0
        binding.progressBarRecord.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        Toast.makeText(requireContext(), "Silakan rekam ulang suara Anda.", Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onPause() {
        super.onPause()
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        player?.release(); player = null
        waveHandler.removeCallbacksAndMessages(null)
        recordProgressHandler?.removeCallbacks(recordProgressRunnable ?: Runnable {})
        progressHandler?.removeCallbacks(progressRunnable ?: Runnable {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recorder?.release()
        player?.release()
        waveHandler.removeCallbacksAndMessages(null)
        recordProgressHandler?.removeCallbacks(recordProgressRunnable ?: Runnable {})
        _binding = null
    }
}
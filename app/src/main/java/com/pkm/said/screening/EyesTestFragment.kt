package com.pkm.said.screening

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentEyesTestBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EyesTestFragment : Fragment() {

    private var _binding: FragmentEyesTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var countDownTimer: CountDownTimer? = null
    private val testDurationSeconds = 10

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
            startEyesTest()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEyesTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()

        if (hasCameraPermission()) {
            startCamera()
            startEyesTest()
        } else {
            requestCameraPermission()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        binding.tvInstruction.text = "Gunakan kamera depan, ikuti titik panduan (← → ↑ ↓)"
        binding.tvTimer.text = testDurationSeconds.toString()
        binding.tvStatus.text = "Bersiap..."

        binding.btnSkip.setOnClickListener {
            skipTest()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionDenied() {
        Toast.makeText(
            requireContext(),
            "Izin kamera diperlukan untuk tes Mata",
            Toast.LENGTH_LONG
        ).show()

        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Tes dilewati - izin kamera ditolak"
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview)

            } catch (exc: Exception) {
                handleCameraError("Gagal menampilkan kamera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleCameraError(errorMessage: String) {
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Tes gagal - error kamera: $errorMessage"
        )
    }

    @SuppressLint("SetTextI18n")
    private fun startEyesTest() {
        binding.tvStatus.text = "Sedang memantau pergerakan mata..."
        binding.tvInstruction.text = "Gerakkan mata: kiri → kanan → atas → bawah"
        countDownTimer = object : CountDownTimer(
            (testDurationSeconds * 1000).toLong(),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = secondsLeft.toString()

                updateEyeTrackingStatus(secondsLeft)
            }

            override fun onFinish() {
                completeTest()
            }
        }.start()
    }

    private fun updateEyeTrackingStatus(secondsLeft: Int) {
        binding.tvStatus.text = when (secondsLeft) {
            in 8..10 -> "Lihat ke kiri..."
            in 6..7  -> "Lihat ke kanan..."
            in 4..5  -> "Lihat ke atas..."
            in 1..3  -> "Lihat ke bawah..."
            else -> "Memantau pergerakan mata..."
        }
    }

    private fun completeTest() {
        binding.tvTimer.text = "0"
        binding.tvStatus.text = "Test selesai!"

        val (isSuccessful, score, notes) = simulateEyesDetection()

        saveResultAndNext(isSuccessful, score, notes)
    }

    private fun simulateEyesDetection(): EyesDetectionResult {
        // TODO: Implement real ML eye tracking untuk deteksi:
        // - Gaze deviation
        // - Ptosis (kelopak mata turun)
        // - Saccade abnormalities
        // - Asymmetry

        return EyesDetectionResult(
            isSuccessful = true, // Simulasi: 85% normal
            score = 0.15f, // 0.15 = minor deviation (normal range)
            notes = "Pergerakan mata simetris, tidak ada ptosis terdeteksi"
        )
    }

    private fun skipTest() {
        stopTest()
        saveResultAndNext(
            isSuccessful = true,
            score = 1.0f,
            notes = "Eyes test dilewati oleh pengguna"
        )
    }

    private fun stopTest() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        val result = TestResult(
            testName = "befast_eyes", // ✅ Standardized name
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = (testDurationSeconds * 1000).toLong(),
            testData = mapOf(
                "test_type" to "eye_tracking",
                "duration_seconds" to testDurationSeconds,
                "camera_used" to "front",
                "eye_movements_tested" to listOf("left", "right", "up", "down"),
                "simulation" to true // TODO: remove when real ML implemented
            )
        )

        // ✅ SIMPLE: Simpan ke lokal saja (local-first approach)
        ScreeningDataManager.updateTestResult(requireContext(), result)

        // ✅ Navigasi setelah delay kecil untuk UX
        binding.root.postDelayed({
            navigateToNextTest()
        }, 1500)
    }

    private fun navigateToNextTest() {
        try {
            findNavController().navigate(R.id.action_eyesTest_to_facePreview)
        } catch (e: Exception) {
            // Fallback jika navigation gagal
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTest()
        cameraExecutor.shutdown()
        _binding = null
    }

    // Data class untuk hasil deteksi mata
    private data class EyesDetectionResult(
        val isSuccessful: Boolean,
        val score: Float, // 0.0 (normal) to 1.0 (severe abnormality)
        val notes: String
    )
}
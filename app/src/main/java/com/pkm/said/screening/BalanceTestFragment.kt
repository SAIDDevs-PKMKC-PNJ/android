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
import com.pkm.said.databinding.FragmentBalanceTestBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BalanceTestFragment : Fragment() {

    private var _binding: FragmentBalanceTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var countDownTimer: CountDownTimer? = null
    private val testDurationSeconds = 10

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
            startBalanceTest()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBalanceTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()

        // Check camera permission
        if (hasCameraPermission()) {
            startCamera()
            startBalanceTest()
        } else {
            requestCameraPermission()
        }
    }

    private fun setupUI() {
        binding.tvInstruction.text = "Berdiri tegap, seluruh tubuh terlihat kamera belakang"
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
            "Izin kamera diperlukan untuk tes Balance",
            Toast.LENGTH_LONG
        ).show()

        // Simpan hasil skipped dan lanjut
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f, // worst score karena tidak bisa test
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

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
    private fun startBalanceTest() {
        binding.tvStatus.text = "Sedang merekam postur keseimbangan..."
        binding.tvInstruction.text = "Tetap diam menatap lurus selama $testDurationSeconds detik"

        countDownTimer = object : CountDownTimer(
            (testDurationSeconds * 1000).toLong(),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = secondsLeft.toString()

                updateTestStatus(secondsLeft)
            }

            override fun onFinish() {
                completeTest()
            }
        }.start()
    }

    private fun updateTestStatus(secondsLeft: Int) {
        binding.tvStatus.text = when (secondsLeft) {
            in 8..10 -> "Atur posisi, seluruh tubuh dalam frame..."
            in 5..7 -> "Tahan posisi stabil..."
            in 1..4 -> "Hampir selesai..."
            else -> "Sedai merekam..."
        }
    }

    private fun completeTest() {
        binding.tvTimer.text = "0"
        binding.tvStatus.text = "Test selesai!"

        // Simulasi deteksi keseimbangan (nanti diganti ML real)
        val (isSuccessful, score, notes) = simulateBalanceDetection()

        saveResultAndNext(isSuccessful, score, notes)
    }

    private fun simulateBalanceDetection(): BalanceDetectionResult {
        // TODO: Implement real ML pose detection untuk sway analysis
        // Untuk sekarang, simulasi sederhana
        return BalanceDetectionResult(
            isSuccessful = true, // 80% chance success untuk simulasi
            score = 0.2f, // 0.2 = sedikit sway (normal)
            notes = "Keseimbangan tampak normal, sedikit sway terdeteksi"
        )
    }

    private fun skipTest() {
        stopTest()
        saveResultAndNext(
            isSuccessful = true,
            score = 1.0f,
            notes = "Balance test dilewati oleh pengguna"
        )
    }

    private fun stopTest() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        val result = TestResult(
            testName = "befast_balance", // ✅ Standardized name
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = (testDurationSeconds * 1000).toLong(),
            testData = mapOf(
                "test_type" to "balance",
                "duration_seconds" to testDurationSeconds,
                "camera_used" to "back",
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
            findNavController().navigate(R.id.action_balanceTest_to_eyesPreview)
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

    // Data class untuk hasil deteksi balance
    private data class BalanceDetectionResult(
        val isSuccessful: Boolean,
        val score: Float, // 0.0 (normal) to 1.0 (severe imbalance)
        val notes: String
    )
}
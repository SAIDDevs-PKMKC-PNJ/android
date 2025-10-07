package com.pkm.said.screening

import android.Manifest
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
import com.pkm.said.databinding.FragmentFaceTestBinding
import com.pkm.said.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceTestFragment : Fragment() {

    private var _binding: FragmentFaceTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var countDownTimer: CountDownTimer? = null
    private val testDurationSeconds = 10

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
            startFaceTest()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaceTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()

        if (hasCameraPermission()) {
            startCamera()
            startFaceTest()
        } else {
            requestCameraPermission()
        }
    }

    private fun setupUI() {
        binding.tvInstruction.text = "Posisikan wajah Anda dalam kotak"
        binding.tvTimer.text = testDurationSeconds.toString()
        binding.tvStatus.text = "Bersiap..."

        binding.btnSkip.setOnClickListener {
            skipTest()
        }
        // ❌ TIDAK ADA RETRY BUTTON
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
            "Izin kamera diperlukan untuk tes Wajah",
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

    private fun startFaceTest() {
        binding.tvStatus.text = "Sedang melakukan scan wajah..."
        binding.tvInstruction.text = "Senyum lebar dan tahan selama ${testDurationSeconds} detik"

        countDownTimer = object : CountDownTimer(
            (testDurationSeconds * 1000).toLong(),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = secondsLeft.toString()

                updateFaceTestStatus(secondsLeft)
            }

            override fun onFinish() {
                completeTest()
            }
        }.start()
    }

    private fun updateFaceTestStatus(secondsLeft: Int) {
        binding.tvStatus.text = when (secondsLeft) {
            in 8..10 -> "Mulai tersenyum lebar..."
            in 5..7  -> "Tahan senyuman Anda..."
            in 1..4  -> "Hampir selesai..."
            else -> "Memindai ekspresi wajah..."
        }
    }

    private fun completeTest() {
        binding.tvTimer.text = "0"
        binding.tvStatus.text = "Test selesai!"

        val (isSuccessful, score, notes) = simulateFaceDetection()

        saveResultAndNext(isSuccessful, score, notes)
    }

    private fun simulateFaceDetection(): FaceDetectionResult {
        // ✅ STRUCTURE SAMA: return result dengan score & notes
        return FaceDetectionResult(
            isSuccessful = true,
            score = 0.1f,
            notes = "Ekspresi wajah simetris, tidak ada drooping terdeteksi"
        )
    }

    private fun skipTest() {
        stopTest()
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Face test dilewati oleh pengguna"
        )
    }

    private fun stopTest() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        val result = TestResult(
            testName = "befast_face",
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = (testDurationSeconds * 1000).toLong(),
            testData = mapOf(
                "test_type" to "face_symmetry",
                "duration_seconds" to testDurationSeconds,
                "camera_used" to "front",
                "expression_tested" to "smile",
                "simulation" to true
            )
        )

        // ✅ SIMPLE: Simpan ke lokal saja
        ScreeningDataManager.updateTestResult(requireContext(), result)

        // ✅ Navigasi setelah delay
        binding.root.postDelayed({
            navigateToNextTest()
        }, 1500)
    }

    private fun navigateToNextTest() {
        try {
            findNavController().navigate(R.id.action_faceTest_to_armsPreview)
        } catch (e: Exception) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTest()
        cameraExecutor.shutdown()
        _binding = null
    }

    // ✅ STRUCTURE SAMA: Data class untuk hasil deteksi
    private data class FaceDetectionResult(
        val isSuccessful: Boolean,
        val score: Float,
        val notes: String
    )
}
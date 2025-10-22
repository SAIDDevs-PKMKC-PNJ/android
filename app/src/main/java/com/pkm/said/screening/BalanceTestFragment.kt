package com.pkm.said.screening

import android.Manifest
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pkm.said.R
import com.pkm.said.databinding.FragmentBalanceTestBinding
import com.pkm.said.util.PoseLandmarkerHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BalanceTestFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentBalanceTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper

    // State Management
    private var currentState = TestState.DETECTING_POSE
    private var validPoseStartTime = 0L
    private val REQUIRED_VALID_POSE_MS = 2000L      // 2 detik pose berdiri valid sebelum instruksi
    private val MAX_POSE_WAIT_MS = 10000L          // 10 detik batas waktu deteksi pose awal
    private val MAX_TEST_DURATION_MS = 10000L      // 10 detik batas waktu untuk menyelesaikan tes

    private var poseWaitTimer: CountDownTimer? = null
    private var testTimer: CountDownTimer? = null

    // State Progresif Tes Mengangkat Lutut
    private var isLeftKneeLiftedEver = false
    private var isRightKneeLiftedEver = false

    // Data collection
    private val balanceMetricsList = mutableListOf<PoseLandmarkerHelper.BalanceMetrics>()

    // Atomic flags untuk prevent race condition
    private val isTestCompleted = AtomicBoolean(false)
    private val isNavigating = AtomicBoolean(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
            startAdaptiveTest()
        } else {
            showPermissionDenied()
        }
    }

    companion object {
        private const val TAG = "BalanceTestFragment"
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
        Log.d(TAG, "onViewCreated: Fragment started")

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()
        setupPoseLandmarker()

        if (hasCameraPermission()) {
            startCamera()
            startAdaptiveTest()
        } else {
            requestCameraPermission()
        }
    }

    // ==================== SETUP & INIT ====================

    private fun setupUI() {
        // UBAH INTRUKSI SESUAI TES BARU
        binding.tvInstruction.text = "Berdiri tegap, pastikan pinggul & lutut terlihat."
        binding.tvStatus.text = "Mencari pose..."
        binding.tvTimer.text = "-"
        binding.btnSkip.visibility = View.GONE
        binding.overlay.visibility = View.VISIBLE
    }

    private fun setupPoseLandmarker() {
        if (!isAdded || context == null) return

        Log.d(TAG, "setupPoseLandmarker: Initializing pose landmarker")
        cameraExecutor.execute {
            try {
                val context = this@BalanceTestFragment.context
                if (context == null) return@execute

                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.LIVE_STREAM,
                    minPoseDetectionConfidence = 0.4f,
                    minPoseTrackingConfidence = 0.4f,
                    minPosePresenceConfidence = 0.4f,
                    currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
                    currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                    poseLandmarkerHelperListener = this@BalanceTestFragment
                )
                Log.d(TAG, "setupPoseLandmarker: Pose landmarker initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "setupPoseLandmarker: Failed to initialize pose landmarker", e)
                activity?.runOnUiThread {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Gagal memulai analisis pose", Toast.LENGTH_SHORT).show()
                        saveResultAndNext(
                            isSuccessful = false,
                            score = 1.0f,
                            notes = "Tes gagal - error inisialisasi pose detection"
                        )
                    }
                }
            }
        }
    }

    // ... (hasCameraPermission, requestCameraPermission, showPermissionDenied) ...

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

        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Tes dilewati - izin kamera ditolak"
        )
    }

    private fun startCamera() {
        Log.d(TAG, "startCamera: Starting FRONT camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .build().also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                // Image Analysis
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(640, 480))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            detectPose(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "startCamera: FRONT camera started successfully")

            } catch (exc: Exception) {
                Log.e(TAG, "startCamera: Failed to start camera", exc)
                handleCameraError("Gagal menampilkan kamera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun detectPose(imageProxy: ImageProxy) {
        try {
            if (!this::poseLandmarkerHelper.isInitialized || isTestCompleted.get()) {
                imageProxy.close()
                return
            }

            if (imageProxy.planes.isEmpty() || imageProxy.width <= 0 || imageProxy.height <= 0) {
                imageProxy.close()
                return
            }

            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "detectPose: Error in pose detection", e)
            try {
                imageProxy.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "detectPose: Error closing imageProxy", closeException)
            }
        }
    }

    private fun handleCameraError(errorMessage: String) {
        Log.e(TAG, "handleCameraError: $errorMessage")
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Tes gagal - error kamera: $errorMessage"
        )
    }

    // ==================== STATE TRANSITION ====================

    private fun startAdaptiveTest() {
        currentState = TestState.DETECTING_POSE
        validPoseStartTime = 0L
        isLeftKneeLiftedEver = false
        isRightKneeLiftedEver = false
        balanceMetricsList.clear() // Bersihkan data lama
        isTestCompleted.set(false)
        testTimer?.cancel() // Pastikan timer tes dihentikan

        startPoseWaitTimer()
        binding.tvInstruction.text = "Berdiri tegap, pastikan pinggul & lutut terlihat."
        Log.d(TAG, "startAdaptiveTest: Adaptive test started, waiting for pose detection")
    }

    private fun startPoseWaitTimer() {
        poseWaitTimer?.cancel()

        poseWaitTimer = object : CountDownTimer(MAX_POSE_WAIT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (currentState != TestState.DETECTING_POSE) {
                    this.cancel()
                    return
                }
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = secondsLeft.toString()
            }

            override fun onFinish() {
                Log.w(TAG, "❌ POSE DETECTION TIMEOUT")
                if (isAdded && currentState == TestState.DETECTING_POSE) {
                    saveResultAndNext(
                        isSuccessful = false,
                        score = 1.0f,
                        notes = "Tes dilewati (Timeout): Orang tidak terdeteksi dalam ${MAX_POSE_WAIT_MS / 1000} detik."
                    )
                }
            }
        }.start()

        binding.tvStatus.text = "Mencari pose..."
    }

    // ==================== LANDMARKER LISTENER ====================

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        if (isTestCompleted.get()) return

        activity?.runOnUiThread {
            // Update overlay
            try {
                binding.overlay.setResults(
                    resultBundle.pixelLandmarks,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error updating overlay", e)
            }

            when (currentState) {
                TestState.DETECTING_POSE -> handleDetectingPose(resultBundle)
                TestState.COUNTDOWN -> handleCountdown(resultBundle)
                TestState.TESTING -> handleTesting(resultBundle)
                TestState.COMPLETED -> Unit
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "onError: Pose detection error: $error, code: $errorCode")
    }

    // ==================== STATE HANDLERS ====================

    private fun handleDetectingPose(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        val metrics = resultBundle.balanceMetrics
        // Minimal sampai lutut (26)
        val hasRequiredLandmarks = resultBundle.pixelLandmarks.size > PoseLandmarkerHelper.RIGHT_KNEE

        if (hasRequiredLandmarks && metrics != null) {
            if (validPoseStartTime == 0L) {
                validPoseStartTime = System.currentTimeMillis()
                Log.d(TAG, "🟢 VALID POSE DETECTED - Starting timer: $validPoseStartTime")
            }

            poseWaitTimer?.cancel()

            val elapsed = System.currentTimeMillis() - validPoseStartTime
            val timeLeft = (REQUIRED_VALID_POSE_MS - elapsed) / 1000

            if (timeLeft > 0) {
                binding.tvTimer.text = timeLeft.toString()
                binding.tvStatus.text = "Pose baik! Tes mulai dalam $timeLeft detik..."
            } else {
                currentState = TestState.COUNTDOWN
                Log.d(TAG, "🟢 MOVING TO COUNTDOWN STATE")
                startShortCountdown()
            }
        } else {
            if (validPoseStartTime != 0L) {
                Log.d(TAG, "🔴 POSE LOST - Resetting timer")
                validPoseStartTime = 0L
            }
            binding.tvStatus.text = "📏 Cari posisi... Pastikan pinggul & lutut terlihat."
            binding.tvTimer.text = "-"
        }
    }

    private fun handleCountdown(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Tetap kumpulkan data meskipun tidak digunakan untuk hasil akhir
        resultBundle.balanceMetrics?.let { balanceMetricsList.add(it) }
    }

    private fun handleTesting(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        testTimer?.let {
            val metrics = resultBundle.balanceMetrics

            if (metrics != null) {
                balanceMetricsList.add(metrics)

                // 1. Update status progresif
                if (metrics.isLeftKneeLifted) isLeftKneeLiftedEver = true
                if (metrics.isRightKneeLifted) isRightKneeLiftedEver = true

                // 2. Cek syarat keberhasilan
                if (isLeftKneeLiftedEver && isRightKneeLiftedEver) {
                    testTimer?.cancel()
                    Log.i(TAG, "🎉 KEDUA LUTUT TERANGKAT! Tes Selesai (BERHASIL)!")
                    completeTest(isSuccessful = true)
                    return
                }

                // 3. Update feedback real-time
                updateTestFeedback()
            }
        }
    }

    // ==================== TIMER & FEEDBACK ====================

    private fun startShortCountdown() {
        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = seconds.toString()
                binding.tvStatus.text = "🔜 Bersiap, Angkat Lutut Anda... $seconds"
            }

            override fun onFinish() {
                currentState = TestState.TESTING
                binding.tvInstruction.text = "Angkat lutut Kiri dan Kanan secara bergantian!"
                binding.tvStatus.text = "🎬 TES MULAI! Angkat lutut..."
                startTestTimer()
            }
        }.start()
    }

    private fun startTestTimer() {
        testTimer?.cancel()

        // Timer MAX_TEST_DURATION_MS untuk batas waktu
        testTimer = object : CountDownTimer(MAX_TEST_DURATION_MS, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000)
                // Hanya update timer setiap 1 detik
                if (millisUntilFinished % 1000 < 100) {
                    binding.tvTimer.text = seconds.toString()
                }
            }

            override fun onFinish() {
                // Jika timer habis dan tes belum selesai (kedua lutut belum terangkat)
                Log.w(TAG, "❌ WAKTU HABIS. Tes Selesai (GAGAL).")
                completeTest(isSuccessful = false)
            }
        }.start()
    }

    private fun updateTestFeedback() {
        val leftStatus = if (isLeftKneeLiftedEver) "✅ Kiri" else "❌ Kiri"
        val rightStatus = if (isRightKneeLiftedEver) "✅ Kanan" else "❌ Kanan"

        binding.tvStatus.text = "Progress: $leftStatus | $rightStatus"

        // Update instruksi agar lebih jelas
        binding.tvInstruction.text = when {
            isLeftKneeLiftedEver && !isRightKneeLiftedEver -> "Lutut Kiri OK! Sekarang angkat lutut KANAN."
            !isLeftKneeLiftedEver && isRightKneeLiftedEver -> "Lutut Kanan OK! Sekarang angkat lutut KIRI."
            else -> "Angkat lutut Kiri dan Kanan secara bergantian!"
        }
    }

    // ==================== TEST COMPLETION & ANALISIS ====================

    private fun completeTest(isSuccessful: Boolean) {
        if (isTestCompleted.getAndSet(true)) return

        testTimer?.cancel()
        currentState = TestState.COMPLETED

        binding.tvTimer.text = "0"
        binding.tvInstruction.text = "Tes Selesai"

        // Cek status keberhasilan berdasarkan pencapaian progresif
        val finalIsSuccessful = isLeftKneeLiftedEver && isRightKneeLiftedEver

        binding.tvStatus.text = if (finalIsSuccessful) {
            "🎉 BERHASIL! Kedua lutut terangkat."
        } else {
            "❌ GAGAL! Waktu habis."
        }

        // Analisis hasil
        // Kirim status pencapaian lutut ke fungsi analisis skor
        val (score, notes) = analyzeBalanceResults(finalIsSuccessful)
        Log.d(TAG, "completeTest: Analysis result - Successful: $finalIsSuccessful, Score: $score")

        saveResultAndNext(finalIsSuccessful, score, notes)
    }

    private fun analyzeBalanceResults(isTestSuccessful: Boolean): Pair<Float, String> {
        val totalFrames = balanceMetricsList.size

        // 1. Kriteria Keberhasilan
        if (isTestSuccessful) {
            // Jika berhasil (kedua lutut terangkat), skor sempurna 0.0.
            return Pair(
                0.0f,
                "Berhasil: Kedua lutut terangkat dalam batas waktu. Frame dianalisis: $totalFrames."
            )
        }

        var score = 1.0f // Skor default terburuk (Gagal total)
        val progressNote: String

        val avgStability = balanceMetricsList.map { it.overallStability }.average().toFloat()

        when {
            isLeftKneeLiftedEver != isRightKneeLiftedEver -> {
                score = 0.5f

                val stabilityAdjustment = (1.0f - avgStability) * 0.5f

                score = (score - stabilityAdjustment).coerceIn(0.2f, 0.8f)

                val liftedSide = if (isLeftKneeLiftedEver) "Kiri" else "Kanan"
                progressNote = "Gagal: Waktu habis. Hanya lutut $liftedSide yang terangkat (Progres 50%)."
            }

            else -> {
                score = 1.0f
                progressNote = "Gagal total: Tidak ada lutut yang terdeteksi terangkat."
            }
        }

        val notes = buildString {
            append(progressNote)
            append(" Stabilitas rata-rata: ${String.format("%.1f", avgStability * 100)}%. ")
            append("Frame dianalisis: $totalFrames. ")
            append("Skor akhir: ${String.format("%.2f", score)}")
        }

        return Pair(score, notes)
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        Log.d(TAG, "saveResultAndNext: Saving results and navigating")

        if (isNavigating.getAndSet(true)) return
        if (!isAdded || context == null) return

        val result = TestResult(
            testName = "befast_balance_knee_lift",
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = MAX_TEST_DURATION_MS,
            testData = mapOf(
                "test_type" to "knee_lift",
                "duration_seconds" to (MAX_TEST_DURATION_MS / 1000),
                "left_knee_lifted" to isLeftKneeLiftedEver,
                "right_knee_lifted" to isRightKneeLiftedEver,
                "total_frames_analyzed" to balanceMetricsList.size
            )
        )

        ScreeningDataManager.updateTestResult(requireContext(), result)
        Log.d(TAG, "saveResultAndNext: Results saved to ScreeningDataManager")

        stopCameraAndCleanup()
    }

    private fun stopCameraAndCleanup() {
        Log.d(TAG, "🛑 Stopping camera and cleanup...")

        cleanupPoseLandmarker()
        testTimer?.cancel() // Pastikan test timer di-cancel

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProviderFuture.get().unbindAll()
                Log.d(TAG, "✅ CameraX unbindAll successful")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to unbind CameraX", e)
            }

            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdownNow()
                Log.d(TAG, "✅ cameraExecutor shutdownNow called.")
            }

            binding.root.postDelayed({
                if (isAdded && !requireActivity().isFinishing) {
                    navigateToNextTest()
                } else {
                    Log.w(TAG, "Cleanup completed, but Fragment not attached for navigation.")
                }
            }, 500) // Penundaan 500ms
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun cleanupPoseLandmarker() {
        Log.d(TAG, "cleanupPoseLandmarker: Cleaning up pose landmarker")
        if (this::poseLandmarkerHelper.isInitialized) {
            try {
                poseLandmarkerHelper.clearPoseLandmarker()
                Log.d(TAG, "cleanupPoseLandmarker: Pose landmarker cleared")
            } catch (e: Exception) {
                Log.e(TAG, "cleanupPoseLandmarker: Error clearing pose landmarker", e)
            }
        }
    }

    private fun navigateToNextTest() {
        Log.d(TAG, "navigateToNextTest: Attempting navigation to next test")
        try {
            if (isAdded && !requireActivity().isFinishing) {
                findNavController().navigate(R.id.action_balanceTest_to_eyesPreview)
                Log.d(TAG, "navigateToNextTest: Navigation successful")
            } else {
                Log.w(TAG, "navigateToNextTest: Fragment not attached, cannot navigate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "navigateToNextTest: Navigation failed", e)
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")

        isTestCompleted.set(true)
        testTimer?.cancel()
        poseWaitTimer?.cancel()

        cameraExecutor.shutdownNow()
        cleanupPoseLandmarker()
        _binding = null

        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Cleanup completed")
    }

    private enum class TestState {
        DETECTING_POSE,
        COUNTDOWN,
        TESTING,
        COMPLETED
    }

    private data class BalanceDetectionResult(
        val isSuccessful: Boolean,
        val score: Float,
        val notes: String
    )
}
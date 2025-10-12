package com.pkm.said.screening

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
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

    // State Management - HAPUS timer lama
    private var currentState = TestState.DETECTING_POSE
    private var validPoseStartTime = 0L
    private val REQUIRED_VALID_POSE_MS = 3000L // 3 detik pose valid
    private val TEST_DURATION_MS = 5000L       // 5 detik tes

    // Data collection untuk analisis
    private val balanceMetricsList = mutableListOf<PoseLandmarkerHelper.BalanceMetrics>()

    // Atomic flags untuk prevent race condition
    private val isTestCompleted = AtomicBoolean(false)
    private val isNavigating = AtomicBoolean(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
            startAdaptiveTest() // GUNAKAN YANG BARU
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
            startAdaptiveTest() // GUNAKAN YANG BARU
        } else {
            requestCameraPermission()
        }
    }

    private fun setupUI() {
        binding.tvInstruction.text = "Berdiri tegap, pastikan pinggul & lutut terlihat"
        binding.tvStatus.text = "Mencari pose..."
        binding.tvTimer.text = "-"
        binding.btnSkip.visibility = View.GONE

        // Tampilkan overlay
        binding.overlay.visibility = View.VISIBLE
    }

    private fun setupPoseLandmarker() {
        if (!isAdded || context == null) {
            Log.w(TAG, "setupPoseLandmarker: Fragment not attached, skipping")
            return
        }

        Log.d(TAG, "setupPoseLandmarker: Initializing pose landmarker")
        cameraExecutor.execute {
            try {
                val context = this@BalanceTestFragment.context
                if (context == null) {
                    Log.e(TAG, "setupPoseLandmarker: Context is null")
                    return@execute
                }

                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.LIVE_STREAM,
                    minPoseDetectionConfidence = 0.4f, // Lower threshold untuk deteksi lebih mudah
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
            // ✅ PERBAIKAN: Izinkan detection selama belum completed
            if (!this::poseLandmarkerHelper.isInitialized || isTestCompleted.get()) {
                imageProxy.close()
                return
            }

            // Validasi image proxy
            if (imageProxy.planes.isEmpty() || imageProxy.width <= 0 || imageProxy.height <= 0) {
                imageProxy.close()
                return
            }

            // ✅ PERBAIKAN: Selalu proses selama test belum selesai
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

    // ==================== STATE MANAGEMENT YANG BARU ====================

    private fun startAdaptiveTest() {
        currentState = TestState.DETECTING_POSE
        validPoseStartTime = 0L
        binding.tvStatus.text = "Cari posisi... Pastikan pinggul & lutut terlihat"
        binding.tvTimer.text = "-"
        isTestCompleted.set(false)

        Log.d(TAG, "startAdaptiveTest: Adaptive test started, waiting for pose detection")
    }

    // Implementasi LandmarkerListener
    // Di BalanceTestFragment
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        Log.d(TAG, "🎯 onResults: RECEIVED - Landmarks: ${resultBundle.pixelLandmarks.size}, " +
                "State: $currentState, Completed: ${isTestCompleted.get()}")

        if (isTestCompleted.get()) {
            Log.d(TAG, "onResults: Test completed, ignoring results")
            return
        }

        activity?.runOnUiThread {
            Log.d(TAG, "🎯 onResults UI Thread - Landmarks: ${resultBundle.pixelLandmarks.size}")

            // Update overlay dengan landmark terbaru
            try {
                binding.overlay.setResults(
                    resultBundle.pixelLandmarks,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                Log.d(TAG, "🎯 Overlay updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error updating overlay", e)
            }

            when (currentState) {
                TestState.DETECTING_POSE -> {
                    Log.d(TAG, "🎯 Handling DETECTING_POSE state")
                    handleDetectingPose(resultBundle)
                }
                TestState.COUNTDOWN -> {
                    Log.d(TAG, "🎯 Handling COUNTDOWN state")
                    handleCountdown(resultBundle)
                }
                TestState.TESTING -> {
                    Log.d(TAG, "🎯 Handling TESTING state")
                    handleTesting(resultBundle)
                }
                TestState.COMPLETED -> {
                    Log.d(TAG, "🎯 State COMPLETED, ignoring")
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "onError: Pose detection error: $error, code: $errorCode")
    }

    private fun handleDetectingPose(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        val metrics = resultBundle.balanceMetrics
        val hasRequiredLandmarks = resultBundle.pixelLandmarks.size > 26 // Minimal sampai lutut

        Log.d(TAG, "🔍 handleDetectingPose - " +
                "Landmarks: ${resultBundle.pixelLandmarks.size}, " +
                "HasRequired: $hasRequiredLandmarks, " +
                "Metrics: ${metrics != null}")

        // ✅ PERBAIKAN: Handle case ketika metrics null (karena landmarks tidak cukup)
        if (hasRequiredLandmarks && metrics != null) {
            // Pose valid terdeteksi
            if (validPoseStartTime == 0L) {
                validPoseStartTime = System.currentTimeMillis()
                binding.tvStatus.text = "✅ Pose terdeteksi! Tahan..."
                Log.d(TAG, "🟢 VALID POSE DETECTED - Starting timer: $validPoseStartTime")
            }

            val elapsed = System.currentTimeMillis() - validPoseStartTime
            val timeLeft = (REQUIRED_VALID_POSE_MS - elapsed) / 1000

            Log.d(TAG, "⏱️ Pose Timer - Elapsed: ${elapsed}ms, TimeLeft: ${timeLeft}s")

            if (timeLeft > 0) {
                binding.tvTimer.text = timeLeft.toString()
                binding.tvStatus.text = "Pose baik! Tes dimulai dalam $timeLeft detik..."
            } else {
                // Pindah ke state COUNTDOWN
                currentState = TestState.COUNTDOWN
                binding.tvStatus.text = "🎯 Pose terkunci! Tes mulai..."
                Log.d(TAG, "🟢 MOVING TO COUNTDOWN STATE")
                startShortCountdown()
            }
        } else {
            // Reset jika pose tidak valid
            if (validPoseStartTime != 0L) {
                Log.d(TAG, "🔴 POSE LOST - Resetting timer")
                validPoseStartTime = 0L
            }

            val reason = when {
                !hasRequiredLandmarks -> "Landmarks tidak lengkap (${resultBundle.pixelLandmarks.size}/33)"
                metrics == null -> "Tidak bisa analisis balance"
                else -> "Stabilitas rendah: ${metrics.overallStability}"
            }

            binding.tvStatus.text = "📏 Cari posisi... $reason"
            binding.tvTimer.text = "-"
            Log.d(TAG, "🔴 Invalid pose: $reason")
        }
    }

    private fun handleCountdown(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Selama countdown, tetap kumpulkan data untuk analisis
        resultBundle.balanceMetrics?.let { metrics ->
            balanceMetricsList.add(metrics)
        }
    }

    private fun handleTesting(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Selama tes, kumpulkan data untuk analisis akhir
        resultBundle.balanceMetrics?.let { metrics ->
            balanceMetricsList.add(metrics)
            updateRealTimeStatus(metrics)
        }
    }

    private fun startShortCountdown() {
        // Hitung mundur 3 detik sebelum tes benar-benar mulai
        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = seconds.toString()
                binding.tvStatus.text = "🔜 Bersiap... $seconds"
            }

            override fun onFinish() {
                currentState = TestState.TESTING
                binding.tvStatus.text = "🎬 TES BERJALAN! Tetap diam..."
                startTestTimer()
            }
        }.start()
    }

    private fun startTestTimer() {
        // Timer 5 detik untuk tes aktual
        object : CountDownTimer(TEST_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = seconds.toString()
                updateTestFeedback(seconds)
            }

            override fun onFinish() {
                currentState = TestState.COMPLETED
                completeTest()
            }
        }.start()
    }

    private fun updateTestFeedback(secondsLeft: Int) {
        when (secondsLeft) {
            5 -> binding.tvStatus.text = "✅ Pertahankan!"
            4, 3 -> binding.tvStatus.text = "💪 Stabil!"
            2, 1 -> binding.tvStatus.text = "⏳ Hampir selesai..."
        }
    }

    private fun updateRealTimeStatus(metrics: PoseLandmarkerHelper.BalanceMetrics) {
        val stabilityPercent = (metrics.overallStability * 100).toInt()
        val status = when {
            metrics.overallStability > 0.8f -> "Stabil ($stabilityPercent%) ✓"
            metrics.overallStability > 0.6f -> "Agak goyah ($stabilityPercent%)"
            else -> "Tidak stabil ($stabilityPercent%)!"
        }
        // Hanya update status jika tidak sedang menampilkan feedback timer
        if (!binding.tvStatus.text.contains("✅") &&
            !binding.tvStatus.text.contains("💪") &&
            !binding.tvStatus.text.contains("⏳")) {
            binding.tvStatus.text = status
        }
    }

    // ==================== TEST COMPLETION ====================

    private fun completeTest() {
        Log.d(TAG, "completeTest: Completing test")
        isTestCompleted.set(true)

        binding.tvTimer.text = "0"
        binding.tvStatus.text = "✅ Test selesai!"

        // Analisis hasil balance
        val (isSuccessful, score, notes) = analyzeBalanceResults()
        Log.d(TAG, "completeTest: Analysis result - Successful: $isSuccessful, Score: $score")

        saveResultAndNext(isSuccessful, score, notes)
    }

    private fun analyzeBalanceResults(): BalanceDetectionResult {
        Log.d(TAG, "analyzeBalanceResults: Analyzing ${balanceMetricsList.size} frames")

        if (balanceMetricsList.isEmpty()) {
            Log.w(TAG, "analyzeBalanceResults: No data collected")
            return BalanceDetectionResult(
                isSuccessful = false,
                score = 1.0f,
                notes = "Tidak ada data pose yang terdeteksi selama test"
            )
        }

        // Hitung average stability (hanya dari data TESTING phase)
        val testingMetrics = balanceMetricsList.takeLast(
            (TEST_DURATION_MS / 1000 * 10).coerceAtMost(balanceMetricsList.size.toLong()).toInt()
        )

        val avgStability = testingMetrics.map { it.overallStability }.average().toFloat()
        val balancedFrames = testingMetrics.count { it.isBalanced }
        val balancePercentage = balancedFrames.toFloat() / testingMetrics.size
        val imbalanceEvents = testingMetrics.count { it.leftKneeHigher || it.rightKneeHigher }

        val score = calculateBalanceScore(avgStability, balancePercentage, imbalanceEvents)
        val isSuccessful = score < 0.5f

        val notes = buildString {
            append("Stabilitas rata-rata: ${String.format("%.1f", avgStability * 100)}%. ")
            append("Waktu seimbang: ${String.format("%.1f", balancePercentage * 100)}%. ")
            if (imbalanceEvents > 0) {
                append("Deteksi ketidakseimbangan: $imbalanceEvents kali. ")
            }
            append(if (isSuccessful) "Keseimbangan normal." else "Keseimbangan perlu perhatian.")
        }

        Log.i(TAG, "analyzeBalanceResults: Final result - " +
                "Avg Stability: ${String.format("%.1f", avgStability * 100)}%, " +
                "Balance %: ${String.format("%.1f", balancePercentage * 100)}%, " +
                "Imbalance Events: $imbalanceEvents, " +
                "Score: $score, " +
                "Successful: $isSuccessful")

        return BalanceDetectionResult(
            isSuccessful = isSuccessful,
            score = score,
            notes = notes
        )
    }

    private fun calculateBalanceScore(
        avgStability: Float,
        balancePercentage: Float,
        imbalanceEvents: Int
    ): Float {
        val stabilityScore = 1.0f - avgStability
        val balanceTimeScore = 1.0f - balancePercentage
        val eventScore = (imbalanceEvents.toFloat() / balanceMetricsList.size).coerceAtMost(1.0f)

        val finalScore = (stabilityScore * 0.4f + balanceTimeScore * 0.4f + eventScore * 0.2f)
        Log.d(TAG, "calculateBalanceScore: stabilityScore=$stabilityScore, balanceTimeScore=$balanceTimeScore, eventScore=$eventScore, finalScore=$finalScore")
        return finalScore
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        Log.d(TAG, "saveResultAndNext: Saving results and navigating")

        if (isNavigating.getAndSet(true)) {
            Log.w(TAG, "saveResultAndNext: Navigation already in progress, skipping")
            return
        }

        if (!isAdded || context == null) {
            Log.w(TAG, "saveResultAndNext: Fragment not attached, cannot save results")
            return
        }

        val result = TestResult(
            testName = "befast_balance",
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = TEST_DURATION_MS,
            testData = mapOf(
                "test_type" to "balance",
                "duration_seconds" to (TEST_DURATION_MS / 1000),
                "camera_used" to "front",
                "total_frames_analyzed" to balanceMetricsList.size,
                "average_stability" to balanceMetricsList.map { it.overallStability }.average(),
                "balance_percentage" to balanceMetricsList.count { it.isBalanced }.toFloat() / balanceMetricsList.size.coerceAtLeast(1),
                "imbalance_events" to balanceMetricsList.count { it.leftKneeHigher || it.rightKneeHigher },
                "ml_analysis" to true
            )
        )

        ScreeningDataManager.updateTestResult(requireContext(), result)
        Log.d(TAG, "saveResultAndNext: Results saved to ScreeningDataManager")

        cleanupPoseLandmarker()

        binding.root.postDelayed({
            if (isAdded && !requireActivity().isFinishing) {
                navigateToNextTest()
            } else {
                Log.w(TAG, "saveResultAndNext: Fragment not attached, skipping navigation")
            }
        }, 1500)
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
            try {
                findNavController().popBackStack()
                Log.d(TAG, "navigateToNextTest: Fallback to popBackStack")
            } catch (e2: Exception) {
                Log.e(TAG, "navigateToNextTest: Fallback navigation also failed", e2)
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")

        // Stop any running timers
        isTestCompleted.set(true)

        cameraExecutor.shutdownNow()
        cleanupPoseLandmarker()
        _binding = null

        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Cleanup completed")
    }

    private enum class TestState {
        DETECTING_POSE,    // Mencari pose yang valid
        COUNTDOWN,         // Hitung mundur 3 detik
        TESTING,           // Tes aktif 5 detik
        COMPLETED          // Tes selesai
    }

    private data class BalanceDetectionResult(
        val isSuccessful: Boolean,
        val score: Float,
        val notes: String
    )
}
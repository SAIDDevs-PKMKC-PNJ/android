package com.pkm.said.screening

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentEyesTestBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.pkm.said.util.EyesViewModel
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pkm.said.util.FaceLandmarkerHelper
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min


class EyesTestFragment : Fragment(), OnInitListener, FaceLandmarkerHelper.LandmarkerListener {

    // --- Logging Tags ---
    private val TAG = "EyesTestFragment"
    private val TTS_TAG = "TTS_LOG"
    // --------------------

    private var _binding: FragmentEyesTestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EyesViewModel by activityViewModels()
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    private lateinit var cameraExecutor: ExecutorService
    private var countDownTimer: CountDownTimer? = null
    private val testDurationSeconds = 15
    private var totalLookPoints = 0
    private var totalTrackedFrames = 0
    private var requiredGazeDirection: String = ""
    private val BLENDSHAPE_THRESHOLD = 0.3f
    private val capturedDataList = mutableListOf<FaceLandmarkerHelper.CapturedExpressionData>()
    private var lastCaptureTime = 0L
    private val CAPTURE_COOLDOWN_MS = 1000L

    private var isTestCompleted = false
    private var pendingFrames = AtomicInteger(0)
    private val frameLock = Object()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Izin kamera diberikan. Memulai kamera...")
            startCamera()
        } else {
            Log.w(TAG, "Izin kamera ditolak. Menampilkan pesan penolakan.")
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
        Log.d(TAG, "onViewCreated: Fragment view created.")

        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(requireContext(), this)
        setupUI()

        if (hasCameraPermission()) {
            Log.i(TAG, "Memiliki izin kamera. Memulai kamera...")
            startCamera()
        } else {
            Log.w(TAG, "Meminta izin kamera.")
            requestCameraPermission()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        binding.tvInstruction.text = "Gunakan kamera depan, ikuti titik panduan (← → ↑ ↓)"
        binding.tvTimer.text = testDurationSeconds.toString()
        binding.tvStatus.text = "Bersiap..."

        binding.btnSkip.visibility = View.GONE
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.i(TTS_TAG, "Inisialisasi TTS berhasil.")
            val result = tts.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TTS_TAG, "Bahasa (US) tidak didukung. Menggunakan mode senyap.")
                isTtsInitialized = false
            } else {
                Log.i(TTS_TAG, "Bahasa (US) berhasil diatur.")
                isTtsInitialized = true
            }
        } else {
            Log.e(TTS_TAG, "Inisialisasi TTS gagal (Status: $status). Menggunakan mode senyap.")
            isTtsInitialized = false
        }

        // ✅ Setelah TTS siap/gagal, kita tentukan cara mulai tes:
        if (hasCameraPermission()) {
            if (isTtsInitialized) {
                Log.i(TAG, "Memulai tes dengan panduan suara (TTS).")
                startEyesTestWithTts()
            } else {
                Log.i(TAG, "Memulai tes tanpa panduan suara (Silent mode).")
                startEyesTestSilently()
            }
        } else {
            Log.w(TAG, "TTS siap, tetapi kamera belum siap atau izin belum diberikan.")
            // Tes akan dimulai setelah izin diberikan di requestPermissionLauncher
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

    private fun setupFaceLandmarker() {
        faceLandmarkerHelper = FaceLandmarkerHelper(
            minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
            maxNumFaces = 1,
            currentDelegate = viewModel.currentDelegate,
            runningMode = RunningMode.LIVE_STREAM,
            context = requireContext(),
            faceLandmarkerHelperListener = this
        )
    }

    private fun startCamera() {
        Log.d(TAG, "startCamera: Memulai konfigurasi CameraX.")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                setupFaceLandmarker()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            detectFace(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalyzer )

                Log.i(TAG, "CameraX berhasil diinisialisasi dan di-bind ke lifecycle.")

            } catch (exc: Exception) {
                Log.e(TAG, "Gagal menginisialisasi CameraX.", exc)
                handleCameraError("Gagal menampilkan kamera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun speakInstruction(text: String) {
        if (isTtsInitialized) {
            Log.d(TTS_TAG, "TTS: Mengucapkan instruksi: \"$text\"")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.v(TTS_TAG, "TTS tidak diinisialisasi. Gagal mengucapkan: \"$text\"")
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        // ✅ TAMBAHKAN LOG INI
        Log.v(TAG, "Menerima frame ${imageProxy.imageInfo.timestamp} untuk deteksi.")
        faceLandmarkerHelper.detectLiveStream(imageProxy, true)
    }

    private fun handleCameraError(errorMessage: String) {
        Log.e(TAG, "Camera Error: $errorMessage")
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Tes gagal - error kamera: $errorMessage"
        )
    }

    private fun startEyesTestWithTts() {
        Log.i(TAG, "Memulai Timer Tes dengan panduan suara.")
        binding.tvStatus.text = "Monitoring eye movement..."
        binding.tvInstruction.text = "Ikuti intruksi suara..."

        speakInstruction("Get ready. The eye test will begin now.")
        startTestTimer()
    }

    // ✅ FUNGSI FALLBACK TANPA SUARA
    private fun startEyesTestSilently() {
        Log.i(TAG, "Memulai Timer Tes tanpa panduan suara.")
        binding.tvStatus.text = "Sedang memantau pergerakan mata..."
        binding.tvInstruction.text = "Mata akan berputar searah jarum jam" // Teks panduan
        startTestTimer()
    }

    // Di EyesTestFragment.kt
    private fun getClockwiseGazeDirection(secondsLeft: Int): Pair<String, String> {
        val rotation = when (secondsLeft) {
            // 🔄 SMOOTH CLOCKWISE ROTATION
            14, 13 -> Pair("⬆️ Lihat ke ATAS", "eyeLookUpLeft")           // 0° - 2 detik
            12, 11 -> Pair("↗️ Lihat KANAN ATAS", "eyeLookInRight")       // 45° - 2 detik
            10, 9 -> Pair("➡️ Lihat ke KANAN", "eyeLookInRight")          // 90° - 2 detik
            8, 7 -> Pair("↘️ Lihat KANAN BAWAH", "eyeLookDownRight")      // 135° - 2 detik
            6, 5 -> Pair("⬇️ Lihat ke BAWAH", "eyeLookDownLeft")          // 180° - 2 detik
            4, 3 -> Pair("↙️ Lihat KIRI BAWAH", "eyeLookInLeft")          // 225° - 2 detik
            2, 1 -> Pair("⬅️ Lihat ke KIRI", "eyeLookInLeft")             // 270° - 2 detik
            0 -> Pair("↖️ Kembali ke ATAS", "eyeLookUpLeft")              // 315° - finish
            else -> Pair("⏳ Bersiap...", "")
        }

        return Pair(rotation.first, rotation.second)
    }

    @SuppressLint("SetTextI18n")
    private fun updateEyeTrackingStatus(secondsLeft: Int) {
        val (instruction, gazeDirection) = getClockwiseGazeDirection(secondsLeft)
        requiredGazeDirection = gazeDirection

        binding.tvStatus.text = instruction

        // 🗣️ VOICE GUIDANCE untuk rotasi
        val speechText = when (secondsLeft) {
            14 -> "Start from the top position"
            12 -> "Slowly move to the top right"
            10 -> "Continue to the right"
            8 -> "Descend to the bottom right"
            6 -> "Continue downwards"
            4 -> "Move to the bottom left"
            2 -> "Continue to the left"
            0 -> "Return to the top, finished"
            else -> null
        }

        speechText?.let {
            Log.d(TAG, "🗣️ Rotation guidance: $it")
            speakInstruction(it)
        }

        Log.d(TAG, "🎯 Rotation direction: '$requiredGazeDirection' - $instruction")
    }

    private fun startTestTimer() {
        Log.i(TAG, "Timer dimulai selama $testDurationSeconds detik.")
        // Reset state
        totalLookPoints = 0
        totalTrackedFrames = 0
        capturedDataList.clear()
        lastCaptureTime = 0L

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

    private fun detectClockwiseGazeCompliance(blendshapes: List<Category>, requiredDirection: String): Float {
        return when (requiredDirection) {
            // ↗️ KANAN ATAS (45°)
            "eyeLookInRight" -> {
                val rightScore = blendshapes.find { it.categoryName() == "eyeLookInRight" }?.score() ?: 0f
                val upScore = blendshapes.find { it.categoryName() == "eyeLookUpRight" }?.score() ?: 0f
                (rightScore + upScore) / 2f // Rata-rata horizontal + vertical
            }
            // ↘️ KANAN BAWAH (135°)
            "eyeLookDownRight" -> {
                val rightScore = blendshapes.find { it.categoryName() == "eyeLookInRight" }?.score() ?: 0f
                val downScore = blendshapes.find { it.categoryName() == "eyeLookDownRight" }?.score() ?: 0f
                (rightScore + downScore) / 2f
            }
            // ↙️ KIRI BAWAH (225°)
            "eyeLookInLeft" -> {
                val leftScore = blendshapes.find { it.categoryName() == "eyeLookInLeft" }?.score() ?: 0f
                val downScore = blendshapes.find { it.categoryName() == "eyeLookDownLeft" }?.score() ?: 0f
                (leftScore + downScore) / 2f
            }
            // ↖️ KIRI ATAS (315°)
            "eyeLookUpLeft" -> {
                val leftScore = blendshapes.find { it.categoryName() == "eyeLookInLeft" }?.score() ?: 0f
                val upScore = blendshapes.find { it.categoryName() == "eyeLookUpLeft" }?.score() ?: 0f
                (leftScore + upScore) / 2f
            }
            // Arah cardinal normal
            else -> blendshapes.find { it.categoryName() == requiredDirection }?.score() ?: 0f
        }
    }

    private fun completeTest() {
        Log.i(TAG, "Tes Selesai. Total frame terlacak: $totalTrackedFrames, Poin Kepatuhan: $totalLookPoints")
        binding.tvTimer.text = "0"
        binding.tvStatus.text = "Test selesai!"
        speakInstruction("Tes mata selesai.")

        isTestCompleted = true

        // ✅ TUNGGU SAMPAI SEMUA FRAME SELESAI DIPROSES
        waitForPendingFrames()
    }

    private fun waitForPendingFrames() {
        Log.d(TAG, "⏳ Menunggu frame yang sedang diproses...")

        // Beri timeout 3 detik maksimal
        val startTime = System.currentTimeMillis()
        val timeoutMs = 3000L

        Thread {
            while (pendingFrames.get() > 0 &&
                System.currentTimeMillis() - startTime < timeoutMs) {
                Thread.sleep(50)
            }

            // ✅ SETELAH SEMUA FRAME SELESAI, LANJUTKAN
            activity?.runOnUiThread {
                Log.d(TAG, "✅ Semua frame selesai diproses, lanjut ke hasil")
                processFinalResults()
            }
        }.start()
    }

    private fun processFinalResults() {
        val finalScore = calculateFinalScore()
        val complianceRatio = (1.0f - finalScore) * 100

        Log.i(TAG, "Final Score (Lower is better): $finalScore, Kepatuhan: $complianceRatio%")

        // 1. Simpan data mentah ke ScreeningDataManager
        ScreeningDataManager.updateEyesRawData(requireContext(), capturedDataList)

        // 2. Simpan skor final
        saveResultAndNext(
            isSuccessful = finalScore < 0.5f,
            score = finalScore,
            notes = "Eyes tracking test selesai. Rasio Kepatuhan: ${(1.0f - finalScore) * 100}%. Total frame terlacak: $totalTrackedFrames"
        )
    }

    private fun calculateFinalScore(): Float {
        if (totalTrackedFrames == 0) {
            Log.w(TAG, "calculateFinalScore: Total tracked frames = 0. Mengembalikan skor 1.0f.")
            return 1.0f
        }

        val successRatio = totalLookPoints.toFloat() / totalTrackedFrames.toFloat()
        Log.d(TAG, "Success Ratio: $successRatio (Points: $totalLookPoints / Frames: $totalTrackedFrames)")

        return when {
            // Excellent - koordinasi mata normal
            successRatio >= 0.7f -> 0.1f + (1 - successRatio) * 0.3f

            // Good - sedikit gangguan koordinasi
            successRatio >= 0.5f -> 0.3f + (0.7f - successRatio) * 0.4f

            // Moderate - gangguan koordinasi sedang (khas stroke)
            successRatio >= 0.3f -> 0.6f + (0.5f - successRatio) * 0.4f

            // Severe - gangguan koordinasi berat
            successRatio >= 0.15f -> 0.8f + (0.3f - successRatio) * 0.2f

            // Very severe - hampir tidak ada koordinasi
            else -> 1.0f
        }.coerceIn(0f, 1f)
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Log.e(TAG, "Landmarker Error: $error (Code: $errorCode)")
            Toast.makeText(requireContext(), "Error deteksi mata: $error", Toast.LENGTH_LONG).show()

            // Hentikan tes dan catat kegagalan jika terjadi error fatal
            stopTest()
            saveResultAndNext(
                isSuccessful = false,
                score = 1.0f,
                notes = "Tes gagal karena error ML: $error"
            )
        }
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        if (isTestCompleted || countDownTimer == null) {
            Log.v(TAG, "Mengabaikan hasil. Tes sudah selesai atau timer sudah berhenti.")
            return
        }
        activity?.runOnUiThread {
            binding.overlay.setRawResults(
                resultBundle.result.faceLandmarks(),
                resultBundle.result.faceBlendshapes(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )

            // Lakukan perhitungan hanya jika tes sedang berjalan
            if (countDownTimer != null && resultBundle.result.faceLandmarks().isNotEmpty()) {
                totalTrackedFrames++

                val blendshapes = resultBundle.result.faceBlendshapes().orElse(emptyList())?.firstOrNull()?.filterNotNull() ?: emptyList()

                val requiredScore = detectClockwiseGazeCompliance(blendshapes, requiredGazeDirection)
                // ✅ DEBUG: Log untuk melihat score
                Log.d(TAG, "🔍 Gaze: $requiredGazeDirection, Score: $requiredScore, Threshold: $BLENDSHAPE_THRESHOLD")

                if (totalTrackedFrames == 1) {
                    Log.d(TAG, "=== AVAILABLE BLENDSHAPES ===")
                    blendshapes.forEach { category ->
                        Log.d(TAG, "Blendshape: ${category.categoryName()} -> ${category.score()}")
                    }
                    Log.d(TAG, "=== END BLENDSHAPES ===")
                }

                if (totalTrackedFrames % 30 == 0) { // Log every 30 frames to avoid spam
                    val availableBlendshapes = blendshapes.take(5).joinToString {
                        "${it.categoryName()}: ${it.score()}"
                    }
                    Log.d(TAG, "Available blendshapes (first 5): $availableBlendshapes")
                }

                // Cek kepatuhan dan cooldown
                if (requiredGazeDirection.isNotEmpty() && requiredScore > BLENDSHAPE_THRESHOLD) {
                    totalLookPoints++

                    // Logic penyimpanan data mentah (cooldown per frame)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastCaptureTime > CAPTURE_COOLDOWN_MS) {

                        val faceLandmarks = resultBundle.result.faceLandmarks().firstOrNull() ?: emptyList()
                        val relevantLandmarks = faceLandmarks.subList(0, min(478, faceLandmarks.size))

                        val capturedData = FaceLandmarkerHelper.CapturedExpressionData(
                            blendshapeName = requiredGazeDirection,
                            score = requiredScore,
                            featureCoordinates = relevantLandmarks,
                            timestamp = currentTime
                        )
                        capturedDataList.add(capturedData)
                        lastCaptureTime = currentTime
                    }
                }
            }
        }
    }

    private fun skipTest() {
        Log.i(TAG, "Pengguna melewatkan tes mata.")
        stopTest()
        saveResultAndNext(
            isSuccessful = true,
            score = 1.0f,
            notes = "Eyes test dilewati oleh pengguna"
        )
    }

    private fun stopTest() {
        Log.i(TAG, "Menghentikan Timer Tes.")
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        Log.i(TAG, "Menyimpan hasil tes: Berhasil? $isSuccessful, Score: ${"%.2f".format(score)}")

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

        stopCameraAndCleanup()
    }

    private fun stopCameraAndCleanup() {
        Log.d(TAG, "🛑 Stopping camera and cleanup...")

        // Stop FaceLandmarker
        if (::faceLandmarkerHelper.isInitialized) {
            faceLandmarkerHelper.clearFaceLandmarker()
        }

        // Stop analyzer
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
            Log.d(TAG, "CameraX unbindAll() dipanggil.")
            // CATATAN: Kami tidak menunggu operasi ini selesai 100% sebelum navigasi.

            // (D) Matikan TTS
            if (::tts.isInitialized) {
                tts.stop()
                tts.shutdown()
            }

            // (E) Matikan Executor untuk mencegah tugas baru masuk
            // CATATAN: Tugas yang sudah ada (analyzer lama) mungkin masih berjalan
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
                Log.d(TAG, "cameraExecutor dimatikan.")
            }

            binding.root.postDelayed({
                navigateToNextTest()
            }, 1000)

            Log.d(TAG, "✅ Camera cleanup ASYNC tasks STARTED.")
        }, ContextCompat.getMainExecutor(requireContext()))

        Log.d(TAG, "✅ Camera stopped and cleanup completed")
    }

    private fun navigateToNextTest() {
        try {
            Log.i(TAG, "Navigasi ke facePreview.")
            findNavController().navigate(R.id.action_eyesTest_to_facePreview)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal navigasi. Pop back stack sebagai fallback.", e)
            Toast.makeText(requireContext(), "Gagal pindah ke tes berikutnya. Mohon coba ulang.", Toast.LENGTH_LONG).show()
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Membersihkan sumber daya.")

        _binding = null
    }
}
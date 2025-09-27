package com.pkm.said.screening

import android.Manifest
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.databinding.FragmentFaceTestBinding
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.PermissionChecker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.pkm.said.R

class FaceTestFragment : Fragment() {

    private var _binding: FragmentFaceTestBinding? = null // <- Binding yang benar
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var countDownTimer: CountDownTimer? = null
    private var testDurationSeconds = 10

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
            startFaceTest()
        } else {
            Toast.makeText(requireContext(), "Izin kamera diperlukan untuk test wajah", Toast.LENGTH_SHORT).show()
            navigateToNextTest()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFaceTestBinding.inflate(inflater, container, false) // <- Binding yang benar
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupUI()

        // Cek izin kamera dan mulai test
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED) {
            startCamera()
            startFaceTest()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupUI() {
        // Setup initial UI dengan ID yang benar dari layout baru
        binding.tvInstruction.text = "Posisikan wajah Anda dalam kotak"
        binding.tvTimer.text = "10"
        binding.tvStatus.text = "Bersiap..."

        // Setup button listener dengan ID yang benar
        binding.btnSkip.setOnClickListener {
            stopTest()
            saveResultAndNext(false, "Test dilewati oleh pengguna")
        }
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
                // Gunakan viewLifecycleOwner untuk Fragment
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview)

            } catch (exc: Exception) {
                Toast.makeText(requireContext(), "Gagal menampilkan kamera: ${exc.message}", Toast.LENGTH_SHORT).show()
                navigateToNextTest()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startFaceTest() {
        binding.tvStatus.text = "Sedang melakukan scan wajah..."
        binding.tvInstruction.text = "Senyum lebar dan tahan selama ${testDurationSeconds} detik"

        countDownTimer = object : CountDownTimer((testDurationSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = secondsRemaining.toString()

                // Update status berdasarkan waktu tersisa
                when (secondsRemaining) {
                    in 8..10 -> binding.tvStatus.text = "Mulai tersenyum lebar..."
                    in 5..7 -> binding.tvStatus.text = "Tahan senyuman Anda..."
                    in 1..4 -> binding.tvStatus.text = "Hampir selesai..."
                }
            }

            override fun onFinish() {
                binding.tvTimer.text = "0"
                binding.tvStatus.text = "Test selesai!"

                // Simulasi hasil test
                val isSuccessful = simulateFaceDetection()
                saveResultAndNext(isSuccessful, if (isSuccessful) "Ekspresi wajah normal" else "Kemungkinan asimetri wajah terdeteksi")
            }
        }
        countDownTimer?.start()
    }

    private fun simulateFaceDetection(): Boolean {
        // Simulasi deteksi wajah - nanti akan diganti dengan ML model
        return true
    }

    private fun stopTest() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun saveResultAndNext(isSuccessful: Boolean, notes: String) {
        ScreeningDataManager.updateTestResult(
            requireContext(),
            TestResult(
                testName = "face_test",
                isCompleted = true,
                isSuccessful = isSuccessful,
                score = if (isSuccessful) 0f else 1f,
                notes = notes,
                timestamp = getCurrentTimestamp()
            )
        )

        // Delay sebentar sebelum navigasi
        binding.root.postDelayed({
            navigateToNextTest()
        }, 1500)
    }

    private fun navigateToNextTest() {
        try {
            findNavController().navigate(R.id.action_faceTest_to_armsPreview)
        } catch (e: Exception) {
            // Fallback navigation atau kembali ke menu utama
            findNavController().popBackStack()
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTest()
        cameraExecutor.shutdown()
        _binding = null
    }
}
package com.pkm.said.screening

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentArmTestBinding
import kotlin.math.abs

class ArmTestFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentArmTestBinding? = null
    private val binding get() = _binding!!

    private var sensorManager: SensorManager? = null
    private var isTesting = false
    private var testCompleted = false

    // Test duration in milliseconds
    private val testDurationMs = 10000L
    private val countdownInterval = 1000L
    private val vibrationDuration = 500L

    // Parameters for detecting imbalance
    private var rotationHistory = mutableListOf<Triple<Float, Float, Float>>()
    private var accelerationHistory = mutableListOf<Triple<Float, Float, Float>>()
    private val maxRotationThreshold = 0.3f // Maximum acceptable rotation
    private val maxAccelerationThreshold = 1.0f // Maximum acceptable acceleration change

    // Countdown timer
    private var countDownTimer: CountDownTimer? = null

    // Sound and vibration
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArmTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setupInitialView()
        setupButtonListeners()
        prepareMediaPlayer()
    }

    private fun prepareMediaPlayer() {
        mediaPlayer = MediaPlayer.create(context, R.raw.alarm_beep)
        mediaPlayer?.isLooping = false
    }

    private fun setupInitialView() {
        binding.btnStartArmTest.visibility = View.VISIBLE
        binding.btnConfirmResult.visibility = View.GONE
        binding.btnRetryTest.visibility = View.GONE
        binding.btnSkipTest.visibility = View.GONE
        binding.btnRetryArmOnly.visibility = View.GONE
        binding.progressBarTest.progress = 0
        binding.tvTimeRemaining.text = "${testDurationMs / 1000}"
        binding.tvInstruction.text = "Tes BEFAST - Arm (Kekuatan Lengan)\n\n" +
                "1. Letakkan HP di antara kedua tangan (atas dan bawah)\n" +
                "2. Julurkan lengan ke depan dan tutup mata\n" +
                "3. Tahan posisi selama 10 detik\n\n" +
                "Tekan tombol mulai saat siap."
        binding.tvResult.text = ""
        binding.tvResult.visibility = View.GONE
    }

    private fun setupButtonListeners() {
        binding.btnStartArmTest.setOnClickListener {
            if (!isTesting) {
                startTest()
            }
        }

        binding.btnConfirmResult.setOnClickListener {
            val isNormal = !detectImbalance()
            val notes = if (isNormal)
                "Kedua lengan dapat dipertahankan stabil selama 10 detik"
            else
                "Terdeteksi ketidakseimbangan lengan, salah satu lengan mungkin lebih lemah"

            saveBefastArmResult(isNormal, notes)
        }

        binding.btnSkipTest.setOnClickListener {
            saveBefastArmResult(false, "Tes dilewati oleh pengguna")
        }

        binding.btnRetryTest.setOnClickListener {
            resetTest()
        }

        binding.btnRetryArmOnly.setOnClickListener {
            resetTest()
            startTest()
        }
    }

    private fun startTest() {
        isTesting = true
        testCompleted = false
        rotationHistory.clear()
        accelerationHistory.clear()

        binding.btnStartArmTest.visibility = View.GONE
        binding.btnConfirmResult.visibility = View.GONE
        binding.btnRetryTest.visibility = View.GONE
        binding.btnSkipTest.visibility = View.GONE
        binding.btnRetryArmOnly.visibility = View.GONE
        binding.tvResult.visibility = View.GONE
        binding.progressBarTest.visibility = View.VISIBLE
        binding.tvTimeRemaining.visibility = View.VISIBLE

        binding.tvInstruction.text = "Julurkan lengan ke depan dan tutup mata.\nTahan posisi ini hingga tes selesai."

        // Register sensor listeners
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)

        // Start the countdown
        countDownTimer = object : CountDownTimer(testDurationMs, countdownInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.tvTimeRemaining.text = "$secondsRemaining"

                // Update progress bar
                val progress = ((testDurationMs - millisUntilFinished) * 100 / testDurationMs).toInt()
                binding.progressBarTest.progress = progress
            }

            override fun onFinish() {
                completeTest()
            }
        }.start()
    }

    private fun completeTest() {
        isTesting = false
        testCompleted = true
        sensorManager?.unregisterListener(this)

        binding.progressBarTest.progress = 100
        binding.tvTimeRemaining.text = "0"

        // Vibrate the phone
        vibrateDevice()

        // Play alarm sound
        playAlarmSound()

        // After a short delay, show the results
        Handler(Looper.getMainLooper()).postDelayed({
            binding.progressBarTest.visibility = View.GONE
            binding.tvTimeRemaining.visibility = View.GONE

            val hasImbalance = detectImbalance()
            if (hasImbalance) {
                binding.tvResult.text = "Terdeteksi ketidakseimbangan! Salah satu lengan mungkin lebih lemah."
                binding.tvResult.setTextColor(requireContext().getColor(R.color.warning_color))
            } else {
                binding.tvResult.text = "Tidak terdeteksi ketidakseimbangan. Kedua lengan tampak normal."
                binding.tvResult.setTextColor(requireContext().getColor(R.color.success_color))
            }

            binding.tvResult.visibility = View.VISIBLE
            binding.btnConfirmResult.visibility = View.VISIBLE
            binding.btnRetryTest.visibility = View.VISIBLE
            binding.btnRetryArmOnly.visibility = View.VISIBLE
            binding.tvInstruction.text = "Konfirmasi hasil atau ulangi tes jika diperlukan."
        }, 500)
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationDuration)
                }
            }
        } catch (e: Exception) {
            // Log error or silently fail if vibration is not available
        }
    }

    private fun playAlarmSound() {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Log error or silently fail if sound playback fails
        }
    }

    private fun resetTest() {
        countDownTimer?.cancel()
        countDownTimer = null
        sensorManager?.unregisterListener(this)
        isTesting = false
        testCompleted = false
        rotationHistory.clear()
        accelerationHistory.clear()
        setupInitialView()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTesting || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0] // Roll (tilt left/right)
                val y = event.values[1] // Pitch (tilt up/down)
                val z = event.values[2] // Yaw (turn left/right)

                rotationHistory.add(Triple(x, y, z))
                if (rotationHistory.size > 100) { // Keep last 100 readings
                    rotationHistory.removeAt(0)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                accelerationHistory.add(Triple(x, y, z))
                if (accelerationHistory.size > 100) { // Keep last 100 readings
                    accelerationHistory.removeAt(0)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun detectImbalance(): Boolean {
        if (rotationHistory.isEmpty() || accelerationHistory.isEmpty()) {
            return false // Not enough data
        }

        // Analyze rotation data
        var maxRotationVarianceX = 0f
        var maxRotationVarianceY = 0f

        // Skip first second of data (people adjusting position)
        val relevantRotationData = if (rotationHistory.size > 10) rotationHistory.subList(10, rotationHistory.size) else rotationHistory

        for (i in 1 until relevantRotationData.size) {
            val prevReading = relevantRotationData[i-1]
            val currentReading = relevantRotationData[i]

            // Calculate deltas
            val deltaX = abs(currentReading.first - prevReading.first)
            val deltaY = abs(currentReading.second - prevReading.second)

            if (deltaX > maxRotationVarianceX) maxRotationVarianceX = deltaX
            if (deltaY > maxRotationVarianceY) maxRotationVarianceY = deltaY
        }

        // Analyze acceleration data
        var significantAccelerationChanges = 0

        // Skip first second of data
        val relevantAccelData = if (accelerationHistory.size > 10) accelerationHistory.subList(10, accelerationHistory.size) else accelerationHistory

        for (i in 1 until relevantAccelData.size) {
            val prevReading = relevantAccelData[i-1]
            val currentReading = relevantAccelData[i]

            // Calculate deltas
            val deltaX = abs(currentReading.first - prevReading.first)
            val deltaY = abs(currentReading.second - prevReading.second)
            val deltaZ = abs(currentReading.third - prevReading.third)

            // Count significant acceleration changes
            if (deltaX > maxAccelerationThreshold || deltaY > maxAccelerationThreshold || deltaZ > maxAccelerationThreshold) {
                significantAccelerationChanges++
            }
        }

        // Determine if imbalance detected
        val rotationImbalance = maxRotationVarianceX > maxRotationThreshold || maxRotationVarianceY > maxRotationThreshold
        val accelerationImbalance = significantAccelerationChanges > relevantAccelData.size * 0.1 // If more than 10% of readings show significant change

        return rotationImbalance || accelerationImbalance
    }

    private fun saveBefastArmResult(isNormal: Boolean, notes: String) {
        ScreeningDataManager.updateTestResult(
            requireContext(),
            TestResult(
                testName = "befast_arm",
                isCompleted = true,
                isSuccessful = isNormal, // Success means normal arm function
                score = if (isNormal) 1f else 0f,
                notes = notes,
                timestamp = getCurrentTimestamp()
            )
        )
        Toast.makeText(requireContext(), "Tes BEFAST Arm selesai!", Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_armsTest_to_speechPreview)
    }

    private fun getCurrentTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager?.unregisterListener(this)
        countDownTimer?.cancel()
        countDownTimer = null
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
}
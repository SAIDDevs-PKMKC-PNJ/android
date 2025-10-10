package com.pkm.said.screening

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.databinding.FragmentArmTestBinding
import com.pkm.said.R
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max

class ArmTestFragment : Fragment(), SensorEventListener {

    // Full View Binding
    private var _binding: FragmentArmTestBinding? = null
    private val binding get() = _binding!!

    // Sensor
    private lateinit var sm: SensorManager
    private var acc: Sensor? = null
    private var gyro: Sensor? = null
    private var rotVec: Sensor? = null
    private val samplingUs = 10_000
    private var startNs: Long = 0L
    private var preTimer: CountDownTimer? = null
    private val warmupNs = 1_000_000_000L
    private val testDurationMs = 10_000L
    private val testDurationNs get() = testDurationMs * 1_000_000

    // State
    private var isTesting = false
    private var timer: CountDownTimer? = null
    private var manualFailReason: String? = null
    private var manualFlag: ManualFlag? = null

    private enum class ManualFlag { BOTH_CANT_LIFT, ONE_HAND_ONLY }

    // Data series
    private data class Angles(val pitchDeg: Float, val rollDeg: Float)
    private val pitchSeries = mutableListOf<Pair<Long, Float>>()
    private val rollSeries  = mutableListOf<Pair<Long, Float>>()
    private val accMagSeries = mutableListOf<Pair<Long, Float>>()
    private val gyroMagSeries = mutableListOf<Pair<Long, Float>>()
    private var baselinePitch = Float.NaN
    private var baselineRoll  = Float.NaN

    // Thresholds
    private val thetaFailDeg   = 9f
    private val thetaDriftDeg  = 6f
    private val thetaStdDeg    = 2.5f
    private val accelDipG      = 0.7f
    private val freeFallG      = 0.3f
    private val impactG        = 2.2f
    private val gyroSpikeRad   = 3.5f
    private val rapidAngleDeg  = 12f
    private val rapidWindowN   = 15
    private val streakMinN     = 3

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArmTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Sensors
        sm = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        setupInitialView()
        setupButtons()
    }

    override fun onPause() {
        super.onPause()
        if (isTesting) {
            timer?.cancel()
            stopSensors()
            isTesting = false
            Toast.makeText(requireContext(), "Tes dihentikan sementara. Ulangi.", Toast.LENGTH_SHORT).show()
        }
        preTimer?.cancel(); preTimer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSensors()
        timer?.cancel(); timer = null
        preTimer?.cancel(); preTimer = null
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    private fun setupInitialView() {
        binding.tvInstruction.text = """
            Instruksi:
            • Pegang HP dengan kedua tangan, julurkan lengan sejajar lantai
            • Tutup mata, tahan posisi stabil selama 10 detik
            • Jika HP miring/turun atau terlepas → tes gagal
        """.trimIndent()

        // Sembunyikan CardView di awal
        binding.cardTest.visibility = View.GONE

        binding.tvResult.text = ""
        binding.tvResult.visibility = View.GONE
        binding.progressBarTest.progress = 0
        binding.progressBarTest.visibility = View.GONE
        binding.tvTimeRemaining.text = (testDurationMs/1000).toString()
        binding.tvTimeRemaining.visibility = View.GONE

        binding.btnStartArmTest.visibility = View.VISIBLE
        binding.contBtnHand.visibility = View.VISIBLE
        binding.btnCantLiftArms.visibility = View.VISIBLE
        binding.btnOneHandOnly.visibility = View.VISIBLE

        binding.btnConfirmResult.visibility = View.GONE
        binding.btnSkipTest.visibility = View.GONE

        manualFailReason = null
        manualFlag = null
    }

    private fun setupButtons() {
        binding.btnStartArmTest.setOnClickListener { if (!isTesting) startTest() }

        // Manual: severe / warning
        binding.btnCantLiftArms.setOnClickListener {
            manualFailReason = "Tidak bisa mengangkat kedua tangan."
            manualFlag = ManualFlag.BOTH_CANT_LIFT
            showManualResult(text = "GAGAL (severe): $manualFailReason", severe = true)
        }
        binding.btnOneHandOnly.setOnClickListener {
            manualFailReason = "Hanya satu tangan dapat diangkat / pegang."
            manualFlag = ManualFlag.ONE_HAND_ONLY
            showManualResult(text = "Peringatan: indikasi satu tangan lemah.", severe = false)
        }

        binding.btnConfirmResult.setOnClickListener { onConfirmSaveResult() }
        binding.btnSkipTest.setOnClickListener { skipTest() }
    }

    private fun showManualResult(text: String, severe: Boolean) {
        stopSensors()
        binding.btnStartArmTest.visibility = View.GONE
        binding.contBtnHand.visibility = View.GONE
        binding.btnCantLiftArms.visibility = View.GONE
        binding.btnOneHandOnly.visibility = View.GONE
        binding.progressBarTest.visibility = View.GONE
        binding.tvTimeRemaining.visibility = View.GONE

        // Tampilkan CardView untuk hasil manual
        binding.cardTest.visibility = View.VISIBLE

        binding.tvResult.text = text
        binding.tvResult.setTextColor(ContextCompat.getColor(requireContext(),
            if (severe) R.color.warning_color else R.color.warning_color))
        binding.tvResult.visibility = View.VISIBLE

        binding.btnConfirmResult.visibility = View.VISIBLE
        binding.btnSkipTest.visibility = View.VISIBLE
    }

    private fun startTest() {
        isTesting = false
        manualFailReason = null
        manualFlag = null
        startNs = 0L
        baselinePitch = Float.NaN
        baselineRoll  = Float.NaN
        pitchSeries.clear(); rollSeries.clear()
        accMagSeries.clear(); gyroMagSeries.clear()

        binding.btnStartArmTest.visibility = View.GONE
        binding.contBtnHand.visibility = View.GONE
        binding.btnCantLiftArms.visibility = View.GONE
        binding.btnOneHandOnly.visibility = View.GONE
        binding.btnConfirmResult.visibility = View.GONE
        binding.btnSkipTest.visibility = View.GONE
        binding.tvResult.visibility = View.GONE

        binding.cardTest.visibility = View.VISIBLE
        binding.progressBarTest.visibility = View.GONE
        binding.progressBarTest.progress = 0
        binding.tvTimeRemaining.visibility = View.VISIBLE
        binding.tvTimeRemaining.text = "3"
        binding.tvInstruction.text = "Bersiap... Posisikan lengan sejajar lantai"

        timer?.cancel(); timer = null
        preTimer?.cancel(); preTimer = null
        stopSensors()

        preTimer = object : CountDownTimer(3_000L, 1_000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1_000L + 1).toInt()
                binding.tvTimeRemaining.text = sec.toString()
            }
            override fun onFinish() { beginMeasurementPhase() }
        }.start()
    }

    private fun beginMeasurementPhase() {
        isTesting = true

        // TAMPILKAN CardView saat tes dimulai
        binding.cardTest.visibility = View.VISIBLE

        binding.tvInstruction.text = "Tahan posisi stabil selama 10 detik..."
        binding.progressBarTest.visibility = View.VISIBLE
        binding.progressBarTest.progress = 0
        binding.tvTimeRemaining.text = (testDurationMs/1000).toString()

        acc?.let { sm.registerListener(this, it, samplingUs) }
        gyro?.let { sm.registerListener(this, it, samplingUs) }
        rotVec?.let { sm.registerListener(this, it, samplingUs) }

        timer = object : CountDownTimer(testDurationMs, 1_000L) {
            override fun onTick(ms: Long) {
                binding.tvTimeRemaining.text = (ms / 1_000L).toString()
                binding.progressBarTest.progress = (((testDurationMs - ms) * 100) / testDurationMs).toInt()
            }
            override fun onFinish() { completeTestAfterFullDuration() }
        }.start()
    }

    private fun completeTestAfterFullDuration() {
        isTesting = false
        stopSensors()
        binding.cardTest.visibility = View.VISIBLE
        binding.progressBarTest.progress = 100
        binding.tvTimeRemaining.text = "0"

        vibrateDouble()
        beep()

        val failed = detectImbalance()
        if (failed) {
            binding.tvResult.text = "Gagal: Terdeteksi ketidakstabilan lengan"
            binding.tvResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_color))
        } else {
            binding.tvResult.text = "Berhasil: Posisi lengan stabil"
            binding.tvResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_color))
        }
        binding.tvResult.visibility = View.VISIBLE
        binding.tvInstruction.text = "Konfirmasi hasil tes"

        binding.btnConfirmResult.visibility = View.VISIBLE
        binding.btnSkipTest.visibility = View.VISIBLE
    }

    /** === KONFIRM & SIMPAN === */
    private fun onConfirmSaveResult() {
        setButtonsEnabled(false)
        try {
            manualFlag?.let { flag ->
                val (isSuccessful, score, notes) = when (flag) {
                    ManualFlag.BOTH_CANT_LIFT -> ArmDetectionResult(
                        isSuccessful = false,
                        score = 1.0f,
                        notes = "Critical: tidak bisa angkat kedua tangan. $manualFailReason"
                    )
                    ManualFlag.ONE_HAND_ONLY -> ArmDetectionResult(
                        isSuccessful = false,
                        score = 0.66f,
                        notes = "Warning: hanya satu tangan yang bisa. $manualFailReason"
                    )
                }
                saveResultAndNext(isSuccessful, score, notes)
                return
            }

            // Sensor path
            val t0 = listOfNotNull(
                pitchSeries.firstOrNull()?.first,
                rollSeries.firstOrNull()?.first,
                accMagSeries.firstOrNull()?.first
            ).minOrNull()

            if (t0 == null || baselinePitch.isNaN() || baselineRoll.isNaN()) {
                // fallback normal
                saveResultAndNext(
                    isSuccessful = true,
                    score = 0.0f,
                    notes = "Fallback normal: data sensor kurang. (izin/guncangan tidak terekam)"
                )
                return
            }

            val P = pitchSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
            val R = rollSeries.filter  { it.first - t0 >= warmupNs }.map { it.second }
            val A = accMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
            val G = gyroMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }

            val armResult = computeArmDetection(P, R, A, G, baselinePitch, baselineRoll)
            saveResultAndNext(armResult.isSuccessful, armResult.score, armResult.notes)
        } finally {
            setButtonsEnabled(true)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnConfirmResult.isEnabled = enabled
        binding.btnSkipTest.isEnabled = enabled
    }

    private fun skipTest() {
        saveResultAndNext(
            isSuccessful = false,
            score = 1.0f,
            notes = "Arm test dilewati oleh pengguna"
        )
    }

    private fun saveResultAndNext(isSuccessful: Boolean, score: Float, notes: String) {
        // ✅ Gunakan test name yang STANDARD
        val result = TestResult(
            testName = "befast_arms",  // ✅ Sesuai dengan ScreeningDataManager
            isCompleted = true,
            isSuccessful = isSuccessful,
            score = score,
            notes = notes,
            timestamp = ScreeningDataManager.getCurrentTimestamp(),
            duration = testDurationMs,
            testData = mapOf(
                "test_type" to "arm_stability",
                "duration_seconds" to (testDurationMs / 1000),
                "manual_flag" to manualFlag?.name,
                "manual_reason" to manualFailReason
            ) as Map<String, Any>
        )

        // ✅ Simpan ke lokal - pastikan ini synchronous
        ScreeningDataManager.updateTestResult(requireContext(), result)

        // ✅ Debug: Cek apakah benar tersimpan
        val currentSession = ScreeningDataManager.getCurrentSession(requireContext())
        val armsResult = currentSession?.armsResult
        Log.d("ArmTest", "✅ Saved arms test: completed=${armsResult?.isCompleted}, successful=${armsResult?.isSuccessful}")

        // ✅ Navigasi dengan delay lebih aman
        binding.root.postDelayed({
            navigateToNextTest()
        }, 500) // Delay lebih pendek tapi aman
    }

    private fun navigateToNextTest() {
        try {
            // ✅ Cek dulu apakah data benar-benar tersimpan
            val currentSession = ScreeningDataManager.getCurrentSession(requireContext())
            if (currentSession?.armsResult?.isCompleted != true) {
                Log.e("ArmTest", "❌ Arms test not saved properly!")
                // Fallback: coba save lagi
                val fallbackResult = TestResult(
                    testName = "befast_arms",
                    isCompleted = true,
                    isSuccessful = false,
                    score = 1.0f,
                    notes = "Fallback: data mungkin tidak tersimpan sempurna",
                    timestamp = ScreeningDataManager.getCurrentTimestamp()
                )
                ScreeningDataManager.updateTestResult(requireContext(), fallbackResult)
            }

            findNavController().navigate(R.id.action_armsTest_to_speechPreview)

        } catch (e: Exception) {
            Log.e("ArmTest", "Navigation failed: ${e.message}")
            findNavController().popBackStack()
        }
    }

    private fun resetTest() {
        preTimer?.cancel(); preTimer = null
        timer?.cancel(); timer = null
        stopSensors()
        isTesting = false
        setupInitialView()
    }

    private fun stopSensors() {
        try { sm.unregisterListener(this) } catch (_: Exception) {}
    }

    // === SensorEventListener ===
    override fun onSensorChanged(event: SensorEvent) {
        if (!isTesting) return
        if (startNs == 0L) startNs = event.timestamp
        val t = event.timestamp
        if (t - startNs > testDurationNs) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val a = rotationVectorToAngles(event.values)
                pitchSeries.add(t to a.pitchDeg)
                rollSeries.add(t to a.rollDeg)
                if (baselinePitch.isNaN() && (t - startNs) > warmupNs) {
                    baselinePitch = a.pitchDeg
                    baselineRoll  = a.rollDeg
                }
                if (pitchSeries.size > 1200) pitchSeries.removeAt(0)
                if (rollSeries.size > 1200)  rollSeries.removeAt(0)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val g = sqrt(
                    (event.values[0]*event.values[0] +
                            event.values[1]*event.values[1] +
                            event.values[2]*event.values[2]).toDouble()
                ).toFloat() / 9.80665f
                accMagSeries.add(t to g)
                if (accMagSeries.size > 1200) accMagSeries.removeAt(0)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val w = sqrt(
                    (event.values[0]*event.values[0] +
                            event.values[1]*event.values[1] +
                            event.values[2]*event.values[2]).toDouble()
                ).toFloat()
                gyroMagSeries.add(t to w)
                if (gyroMagSeries.size > 1200) gyroMagSeries.removeAt(0)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // === Quick UI detection ===
    private fun detectImbalance(): Boolean {
        if (pitchSeries.isEmpty() || rollSeries.isEmpty() || accMagSeries.isEmpty()) return false

        val t0 = listOfNotNull(
            pitchSeries.firstOrNull()?.first,
            rollSeries.firstOrNull()?.first,
            accMagSeries.firstOrNull()?.first
        ).minOrNull() ?: return false

        val P = pitchSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
        val R = rollSeries.filter  { it.first - t0 >= warmupNs }.map { it.second }
        val A = accMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
        val G = gyroMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }

        if (P.isEmpty() || R.isEmpty() || A.isEmpty()) return false
        if (baselinePitch.isNaN() || baselineRoll.isNaN()) return false

        val driftPitch = abs(P.last() - baselinePitch)
        val driftRoll  = abs(R.last() - baselineRoll)

        val overStatic   = P.any { abs(it) > thetaFailDeg } || R.any { abs(it) > thetaFailDeg }
        val overDrift    = (driftPitch > thetaDriftDeg || driftRoll > thetaDriftDeg)
        val overUnstable = (std(P) > thetaStdDeg || std(R) > thetaStdDeg)
        val tiltFail = overStatic || overDrift || overUnstable

        val freeFall = hasStreakBelow(A, freeFallG, streakMinN)
        val impact   = A.any { it > impactG }
        val releaseSuspect = rapidAngleChange(P) || rapidAngleChange(R) || hasGyroSpike(G) || hasAccelDip(A)

        return (freeFall || impact) || tiltFail || (releaseSuspect && (overStatic || impact))
    }

    /** === Arm Detection Result === */
    private data class ArmDetectionResult(
        val isSuccessful: Boolean,
        val score: Float,
        val notes: String
    )

    private fun computeArmDetection(
        P: List<Float>, R: List<Float>, A: List<Float>, G: List<Float>,
        baselinePitch: Float, baselineRoll: Float
    ): ArmDetectionResult {
        if (P.isEmpty() || R.isEmpty() || A.isEmpty() || baselinePitch.isNaN() || baselineRoll.isNaN()) {
            return ArmDetectionResult(true, 0f, "Data tidak cukup; diasumsikan normal")
        }

        val driftPitch = abs(P.last() - baselinePitch)
        val driftRoll  = abs(R.last() - baselineRoll)
        val maxDrift   = max(
            P.maxOf { abs(it - baselinePitch) },
            R.maxOf { abs(it - baselineRoll) }
        )
        val stdP = std(P)
        val stdR = std(R)

        val freeFall = hasStreakBelow(A, freeFallG, streakMinN)
        val impact   = A.any { it > impactG }
        val gyroSpike= hasGyroSpike(G)
        val rapid    = rapidAngleChange(P) || rapidAngleChange(R)
        val accelDip = hasAccelDip(A)

        val bothHandsDown = (maxDrift >= 12f && (stdP < 4f && stdR < 4f) && !freeFall && !impact)
        val oneHandRelease = freeFall || impact || rapid || gyroSpike || accelDip

        return when {
            bothHandsDown -> ArmDetectionResult(false, 1.0f, "Critical: indikasi kedua lengan turun")
            oneHandRelease -> ArmDetectionResult(false, 0.66f, "Warning: indikasi satu tangan lemah/pelepasan")
            (maxDrift >= 6f || stdP >= 2.5f || stdR >= 2.5f) -> ArmDetectionResult(false, 0.33f, "Mild: drift/instabilitas ringan")
            else -> ArmDetectionResult(true, 0.0f, "Normal: posisi lengan stabil")
        }
    }

    // === Helper functions ===
    private fun std(list: List<Float>): Float {
        val mean = list.average().toFloat()
        var s = 0.0
        for (v in list) s += (v - mean) * (v - mean)
        return sqrt((s / list.size).toFloat())
    }

    private fun hasStreakBelow(A: List<Float>, thr: Float, minStreak: Int): Boolean {
        var streak = 0
        for (m in A) {
            if (m < thr) { streak++; if (streak >= minStreak) return true } else streak = 0
        }
        return false
    }

    private fun rapidAngleChange(values: List<Float>, window: Int = rapidWindowN, threshDeg: Float = rapidAngleDeg): Boolean {
        if (values.size < window + 1) return false
        val recent = values.takeLast(window + 1)
        val delta = abs(recent.last() - recent.first())
        return delta > threshDeg
    }

    private fun hasGyroSpike(G: List<Float>, thresh: Float = gyroSpikeRad, minSamples: Int = 2): Boolean {
        var streak = 0
        for (w in G) {
            if (w > thresh) { streak++; if (streak >= minSamples) return true } else streak = 0
        }
        return false
    }

    private fun hasAccelDip(A: List<Float>, lowThreshG: Float = accelDipG, minSamples: Int = streakMinN): Boolean {
        var streak = 0
        for (m in A) {
            if (m < lowThreshG) { streak++; if (streak >= minSamples) return true } else streak = 0
        }
        return false
    }

    private fun rotationVectorToAngles(values: FloatArray): Angles {
        val rotMat = FloatArray(9)
        val ori = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotMat, values)
        SensorManager.getOrientation(rotMat, ori)
        val pitch = Math.toDegrees(ori[1].toDouble()).toFloat()
        val roll  = Math.toDegrees(ori[2].toDouble()).toFloat()
        return Angles(pitch, roll)
    }

    // === Feedback ===
    @Suppress("DEPRECATION")
    private fun vibrateDouble() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 200, 150, 200)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(200)
                Handler(Looper.getMainLooper()).postDelayed({ v.vibrate(200) }, 350)
            }
        } catch (_: Exception) {}
    }

    private fun beep() {
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 200) }
        catch (_: Exception) {}
    }
}
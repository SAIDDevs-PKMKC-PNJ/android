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
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.screening.ScreeningDataManager.getCurrentTimestamp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max

class ArmTestFragment : Fragment(), SensorEventListener {

    // UI
    private var tvInstruction: TextView? = null
    private var tvResult: TextView? = null
    private var tvTime: TextView? = null
    private var progress: ProgressBar? = null
    private var btnStart: Button? = null
    private var btnConfirm: Button? = null
    private var btnRetry: Button? = null
    private var btnRetryArmOnly: Button? = null
    private var btnSkip: Button? = null
    private var btnCantLiftArms: Button? = null
    private var btnOneHandOnly: Button? = null
    private var contBtnHand: View? = null

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
        return inflater.inflate(R.layout.fragment_arm_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Bind UI
        tvInstruction = view.findViewById(R.id.tvInstruction)
        tvResult = view.findViewById(R.id.tvResult)
        tvTime = view.findViewById(R.id.tvTimeRemaining)
        progress = view.findViewById(R.id.progressBarTest)
        btnStart = view.findViewById(R.id.btnStartArmTest)
        btnConfirm = view.findViewById(R.id.btnConfirmResult)
        btnRetry = view.findViewById(R.id.btnRetryTest)
        btnRetryArmOnly = view.findViewById(R.id.btnRetryArmOnly)
        btnSkip = view.findViewById(R.id.btnSkipTest)
        btnCantLiftArms = view.findViewById(R.id.btnCantLiftArms)
        btnOneHandOnly = view.findViewById(R.id.btnOneHandOnly)
        contBtnHand = view.findViewById(R.id.contBtnHand)

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
            btnRetry?.visibility = View.VISIBLE
        }
        preTimer?.cancel(); preTimer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSensors()
        timer?.cancel(); timer = null
        preTimer?.cancel(); preTimer = null

        contBtnHand = null
        tvInstruction = null; tvResult = null; tvTime = null; progress = null
        btnStart = null; btnConfirm = null; btnRetry = null; btnRetryArmOnly = null
        btnSkip = null; btnCantLiftArms = null; btnOneHandOnly = null
    }

    @SuppressLint("SetTextI18n")
    private fun setupInitialView() {
        tvInstruction?.text = """
            Instruksi:
            • Pegang HP dengan kedua tangan, julurkan lengan sejajar lantai
            • Tutup mata, tahan posisi stabil selama 10 detik
            • Jika HP miring/turun atau terlepas → tes gagal
        """.trimIndent()

        tvResult?.text = ""
        tvResult?.visibility = View.GONE
        progress?.progress = 0
        progress?.visibility = View.GONE
        tvTime?.text = (testDurationMs/1000).toString()
        tvTime?.visibility = View.GONE

        btnStart?.visibility = View.VISIBLE
        contBtnHand?.visibility = View.VISIBLE
        btnCantLiftArms?.visibility = View.VISIBLE
        btnOneHandOnly?.visibility = View.VISIBLE

        btnConfirm?.visibility = View.GONE
        btnRetry?.visibility = View.GONE
        btnRetryArmOnly?.visibility = View.GONE
        btnSkip?.visibility = View.GONE

        manualFailReason = null
        manualFlag = null
    }

    private fun setupButtons() {
        btnStart?.setOnClickListener { if (!isTesting) startTest() }

        // Manual: severe / warning
        btnCantLiftArms?.setOnClickListener {
            manualFailReason = "Tidak bisa mengangkat kedua tangan."
            manualFlag = ManualFlag.BOTH_CANT_LIFT
            showManualResult(text = "GAGAL (severe): $manualFailReason", severe = true)
        }
        btnOneHandOnly?.setOnClickListener {
            manualFailReason = "Hanya satu tangan dapat diangkat / pegang."
            manualFlag = ManualFlag.ONE_HAND_ONLY
            showManualResult(text = "Peringatan: indikasi satu tangan lemah.", severe = false)
        }

        btnConfirm?.setOnClickListener { onConfirmSaveResult() }
        btnRetry?.setOnClickListener { resetTest() }
        btnRetryArmOnly?.setOnClickListener { resetTest(); startTest() }
        btnSkip?.setOnClickListener {
            // Simpan sebagai pending (tidak selesai) agar progress tersimpan
            ScreeningDataManager.updateTestResult(
                requireContext(),
                TestResult(
                    testName = "befast_arm",
                    isCompleted = false,
                    isSuccessful = false,
                    score = 0f,
                    notes = "Tes dilewati pengguna (pending)",
                    timestamp = getCurrentTimestamp()
                )
            )
            Toast.makeText(requireContext(), "Tes ARM disimpan sebagai pending.", Toast.LENGTH_SHORT).show()
            navigateToNextTest()
        }
    }

    private fun showManualResult(text: String, severe: Boolean) {
        stopSensors()
        btnStart?.visibility = View.GONE
        contBtnHand?.visibility = View.GONE
        btnCantLiftArms?.visibility = View.GONE
        btnOneHandOnly?.visibility = View.GONE
        progress?.visibility = View.GONE
        tvTime?.visibility = View.GONE

        tvResult?.text = text
        tvResult?.setTextColor(ContextCompat.getColor(requireContext(),
            if (severe) R.color.warning_color else R.color.warning_color))
        tvResult?.visibility = View.VISIBLE

        btnConfirm?.visibility = View.VISIBLE
        btnRetry?.visibility = View.VISIBLE
        btnRetryArmOnly?.visibility = View.VISIBLE
        btnSkip?.visibility = View.VISIBLE
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

        btnStart?.visibility = View.GONE
        contBtnHand?.visibility = View.GONE
        btnCantLiftArms?.visibility = View.GONE
        btnOneHandOnly?.visibility = View.GONE
        btnConfirm?.visibility = View.GONE
        btnRetry?.visibility = View.GONE
        btnRetryArmOnly?.visibility = View.GONE
        btnSkip?.visibility = View.GONE
        tvResult?.visibility = View.GONE

        progress?.visibility = View.GONE
        progress?.progress = 0
        tvTime?.visibility = View.VISIBLE
        tvTime?.text = "3"
        tvInstruction?.text = getString(R.string.pre_test_reminder)

        timer?.cancel(); timer = null
        preTimer?.cancel(); preTimer = null
        stopSensors()

        preTimer = object : CountDownTimer(3_000L, 1_000L) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1_000L + 1).toInt()
                tvTime?.text = sec.toString()
            }
            override fun onFinish() { beginMeasurementPhase() }
        }.start()
    }

    private fun beginMeasurementPhase() {
        isTesting = true
        tvInstruction?.text = getString(R.string.test_hint)
        progress?.visibility = View.VISIBLE
        progress?.progress = 0
        tvTime?.text = (testDurationMs/1000).toString()

        acc?.let { sm.registerListener(this, it, samplingUs) }
        gyro?.let { sm.registerListener(this, it, samplingUs) }
        rotVec?.let { sm.registerListener(this, it, samplingUs) }

        timer = object : CountDownTimer(testDurationMs, 1_000L) {
            override fun onTick(ms: Long) {
                tvTime?.text = (ms / 1_000L).toString()
                progress?.progress = (((testDurationMs - ms) * 100) / testDurationMs).toInt()
            }
            override fun onFinish() { completeTestAfterFullDuration() }
        }.start()
    }

    private fun completeTestAfterFullDuration() {
        isTesting = false
        stopSensors()
        progress?.progress = 100
        tvTime?.text = "0"

        vibrateDouble()
        beep()

        val failed = detectImbalance()
        if (failed) {
            tvResult?.text = getString(R.string.fail_detected)
            tvResult?.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_color))
        } else {
            tvResult?.text = getString(R.string.finish_correctly)
            tvResult?.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_color))
        }
        tvResult?.visibility = View.VISIBLE
        tvInstruction?.text = getString(R.string.hint_result)

        btnConfirm?.visibility = View.VISIBLE
        btnRetry?.visibility = View.VISIBLE
        btnRetryArmOnly?.visibility = View.VISIBLE
        btnSkip?.visibility = View.VISIBLE
    }

    /** === KONFIRM & SIMPAN === */
    private fun onConfirmSaveResult() {
        setButtonsEnabled(false)
        try {
            manualFlag?.let { flag ->
                val (category, severity, label) = when (flag) {
                    ManualFlag.BOTH_CANT_LIFT -> Triple(3, 1.0f, "Critical: tidak bisa angkat kedua tangan (manual)")
                    ManualFlag.ONE_HAND_ONLY  -> Triple(2, 0.66f, "Warning: hanya satu tangan yang bisa (manual)")
                }
                saveArmResult(
                    isCompleted = true,
                    category = category,
                    severity = severity,
                    notes = "${label}. ${manualFailReason ?: ""}".trim()
                )
                return
            }

            // Sensor path
            val t0 = listOfNotNull(
                pitchSeries.firstOrNull()?.first,
                rollSeries.firstOrNull()?.first,
                accMagSeries.firstOrNull()?.first
            ).minOrNull()

            if (t0 == null || baselinePitch.isNaN() || baselineRoll.isNaN()) {
                // data tidak cukup → simpan pending
                // data tidak cukup → fallback normal agar hasil tetap tampil
                ScreeningDataManager.updateTestResult(
                    requireContext(),
                    TestResult(
                        testName = "befast_arm",
                        isCompleted = true,
                        isSuccessful = true,     // anggap normal
                        score = 0f,              // severity nol
                        notes = "Fallback normal: data sensor kurang. (izin/guncangan tidak terekam)",
                        timestamp = getCurrentTimestamp(),
                        testData = mapOf("fallback" to true)
                    )
                )
                Toast.makeText(requireContext(), "Data kurang, disimpan sebagai hasil normal (fallback).", Toast.LENGTH_SHORT).show()
                navigateToNextTest()
                return

            }

            val P = pitchSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
            val R = rollSeries.filter  { it.first - t0 >= warmupNs }.map { it.second }
            val A = accMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }
            val G = gyroMagSeries.filter { it.first - t0 >= warmupNs }.map { it.second }

            val scoring = computeArmScoring(P, R, A, G, baselinePitch, baselineRoll)
            Log.d("Arms", "cat=${scoring.category} sev=${scoring.severity} details=${scoring.details}")
            saveArmResult(
                isCompleted = true,
                category = scoring.category,
                severity = scoring.severity,
                notes = "ArmScore=${scoring.category}/3 (${(scoring.severity*100).toInt()}%). ${scoring.notes}",
                details = scoring.details
            )
        } finally {
            setButtonsEnabled(true)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnConfirm?.isEnabled = enabled
        btnRetry?.isEnabled = enabled
        btnRetryArmOnly?.isEnabled = enabled
        btnSkip?.isEnabled = enabled
    }

    private fun saveArmResult(
        isCompleted: Boolean,
        category: Int,
        severity: Float,
        notes: String,
        details: Map<String, Any> = emptyMap()
    ) {
        val isNormal = (category == 0)
        ScreeningDataManager.updateTestResult(
            requireContext(),
            TestResult(
                testName = "befast_arm",
                isCompleted = isCompleted,
                isSuccessful = isNormal,
                score = severity.coerceIn(0f, 1f), // severity 0..1 (semakin besar = makin abnormal)
                notes = notes,
                timestamp = getCurrentTimestamp(),
                testData = details
            )
        )
        Toast.makeText(
            requireContext(),
            if (isCompleted)
                when (category) {
                    0 -> "Tersimpan: PASS (Normal)."
                    1,2 -> "Tersimpan: WARNING."
                    else -> "Tersimpan: CRITICAL."
                }
            else "Tersimpan sebagai pending.",
            Toast.LENGTH_SHORT
        ).show()

        Log.d("Arms", "saved: completed=$isCompleted isSuccessful=$isNormal score=$severity")
        navigateToNextTest()
    }

    private fun navigateToNextTest() {
        try {
            findNavController().navigate(R.id.action_armsTest_to_speechPreview)
        } catch (e: Exception) {
            // fallback
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

    // === Quick UI detection (untuk pesan cepat; skoring final pakai computeArmScoring) ===
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

    /** === Skoring final ARM (0..3 → severity 0..1) === */
    private data class ArmScoring(
        val category: Int,      // 0..3
        val severity: Float,    // 0f..1f
        val notes: String,
        val details: Map<String, Any>
    )

    private fun computeArmScoring(
        P: List<Float>, R: List<Float>, A: List<Float>, G: List<Float>,
        baselinePitch: Float, baselineRoll: Float
    ): ArmScoring {
        if (P.isEmpty() || R.isEmpty() || A.isEmpty() || baselinePitch.isNaN() || baselineRoll.isNaN()) {
            return ArmScoring(0, 0f, "Data tidak cukup; diasumsikan normal untuk skoring.", emptyMap())
        }

        fun std(list: List<Float>): Float {
            val mean = list.average().toFloat()
            var s = 0.0
            for (v in list) s += (v - mean)*(v - mean)
            return sqrt((s / list.size).toFloat())
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

        // Heuristik skenario:
        // - BOTH_HANDS_DOWN (kritikal): tilt besar/menetap tanpa drop/impact (simetris turun)
        val bothHandsDown = (maxDrift >= 12f && (stdP < 4f && stdR < 4f) && !freeFall && !impact)
        // - ONE_HAND_RELEASE (warning): freefall/impact/rapid/gyroSpike/accelDip
        val oneHandRelease = freeFall || impact || rapid || gyroSpike || accelDip

        val category = when {
            bothHandsDown -> 3    // Critical
            oneHandRelease -> 2   // Warning (indikasi satu tangan lepas/pegang lemah)
            (maxDrift >= 6f || stdP >= 2.5f || stdR >= 2.5f) -> 1 // Mild warning
            else -> 0
        }

        val severity = when (category) {
            0 -> 0f
            1 -> 0.33f
            2 -> 0.66f
            else -> 1f
        }

        val label = when (category) {
            0 -> "Normal/stabil"
            1 -> "Mild: drift/instabilitas ringan"
            2 -> "Warning: indikasi satu tangan lemah/pelepasan singkat"
            else -> "Critical: indikasi kedua lengan turun (stabil miring) atau pelepasan berat"
        }

        val details = mapOf(
            "driftPitchDeg" to "%.1f".format(driftPitch),
            "driftRollDeg"  to "%.1f".format(driftRoll),
            "maxDriftDeg"   to "%.1f".format(maxDrift),
            "stdPitchDeg"   to "%.1f".format(stdP),
            "stdRollDeg"    to "%.1f".format(stdR),
            "freeFall"      to freeFall,
            "impact"        to impact,
            "gyroSpike"     to gyroSpike,
            "rapidChange"   to rapid,
            "accelDip"      to accelDip,
            "bothHandsDown" to bothHandsDown,
            "oneHandRelease" to oneHandRelease
        )

        return ArmScoring(category, severity, label, details)
    }

    // === Small helpers ===
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

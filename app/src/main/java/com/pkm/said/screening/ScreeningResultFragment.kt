package com.pkm.said.screening

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.pkm.said.EmergencyActivity
import com.pkm.said.MainActivity
import com.pkm.said.R
import com.pkm.said.databinding.FragmentScreeningResultBinding
import kotlinx.coroutines.launch
import java.util.Locale

class ScreeningResultFragment : Fragment() {

    private var _binding: FragmentScreeningResultBinding? = null
    private val binding get() = _binding!!
    private var completedSession: ScreeningResult? = null
    private var currentCity: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ✅ Activity Result Launcher untuk meminta Izin
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCityFromLocation() // Izin diberikan, coba ambil lokasi
        } else {
            // Izin ditolak, tetapkan nilai default dan lanjutkan setup
            currentCity = "Not Permitted"
            setupResult()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreeningResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        completedSession?.let { out ->
            outState.putString("completed_session_json", Gson().toJson(out))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        savedInstanceState?.getString("completed_session_json")?.let { json ->
            completedSession = Gson().fromJson(json, ScreeningResult::class.java)
            completedSession?.city?.let { currentCity = it }
        }

        if (completedSession != null) {
            setupResult()
        } else {
            checkLocationPermission() // ✅ Mulai proses pengambilan lokasi
        }

        setupStaticClickListeners()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCityFromLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCityFromLocation() {
        // Set default sementara
        currentCity = "Getting Location..."

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val city = geocodeLocation(location.latitude, location.longitude)
                    currentCity = city ?: "SAID"
                    setupResult() // Lanjutkan proses setup dengan data kota
                }
            } else {
                currentCity = "Location Null"
                Log.w("ScreeningResult", "❌ Last known location is null.")
                setupResult()
            }
        }.addOnFailureListener { e ->
            currentCity = "Location Error"
            Log.e("ScreeningResult", "❌ Failed to get location: ${e.message}")
            setupResult()
        }
    }

    // Gunakan Geocoder untuk mengubah koordinat menjadi nama kota
    private suspend fun geocodeLocation(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            return@withContext try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    // Ambil nama kota (locality) atau sub-admin area sebagai fallback
                    addresses[0].locality ?: addresses[0].subAdminArea
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("ScreeningResult", "Geocoding failed: ${e.message}")
                null
            }
        }
    }

    private fun setupResult() {
        // Jika sudah ada hasil terserialisasi (rotasi), render langsung.
        completedSession?.let {
            displayBEFASResults(it)
            return
        }

        // ✅ CEK LOKAL DULU - Ambil sesi aktif dari ScreeningDataManager
        val activeSession = ScreeningDataManager.getCurrentSession(requireContext())
        if (activeSession != null) {
            val pendingTests = ScreeningDataManager.getPendingTests(requireContext())

            if (pendingTests.isNotEmpty()) {
                // ❌ Ada tes yang belum selesai → tampilkan mode incomplete
                displayIncompleteBEFAS(activeSession, pendingTests)
                return
            }

            // ✅ SEMUA TES SELESAI - Complete di lokal dulu
            val locallyCompleted = ScreeningDataManager.completeSession(requireContext(), currentCity)
            if (locallyCompleted != null) {
                completedSession = locallyCompleted

                // ✅ COBA KIRIM KE FIRESTORE (background, tidak blocking UI)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val success = ScreeningRepository.saveCompleteScreeningSession(locallyCompleted)
                        if (success) {
                            Log.d("ScreeningResult", "✅ Berhasil sync ke Firestore")
                        } else {
                            Log.w("ScreeningResult", "❌ Gagal sync ke Firestore, data tetap tersimpan lokal")
                        }
                    } catch (e: Exception) {
                        Log.e("ScreeningResult", "Error sync ke Firestore: ${e.message}")
                        // Data tetap aman di lokal
                    }
                }

                // ✅ LANGSUNG TAMPILKAN HASIL (tidak nunggu Firestore)
                displayBEFASResults(locallyCompleted)
            } else {
                displayErrorResult()
            }
            return
        }

        // ✅ FALLBACK: Coba ambil sesi terakhir dari history lokal
        ScreeningDataManager.getLastCompletedSession(requireContext())?.let {
            completedSession = it
            displayBEFASResults(it)
        } ?: displayErrorResult()
    }

    /** ====== MODE: SCREENING LENGKAP (FINAL) ====== */
    @SuppressLint("SetTextI18n")
    private fun displayBEFASResults(session: ScreeningResult) {
        Log.d("ScreeningResult", "🔍 TEST RESULTS FOR UI:")
        Log.d("ScreeningResult", "   - Balance: ${session.balanceResult?.isCompleted} | ${session.balanceResult?.score}")
        Log.d("ScreeningResult", "   - Eyes: ${session.eyesResult?.isCompleted} | ${session.eyesResult?.score}")
        Log.d("ScreeningResult", "   - Face: ${session.faceResult?.isCompleted} | ${session.faceResult?.score}")
        Log.d("ScreeningResult", "   - Arms: ${session.armsResult?.isCompleted} | ${session.armsResult?.score}")
//        Log.d("ScreeningResult", "   - Speech: ${session.speechResult?.isCompleted} | ${session.speechResult?.score}")

        setupRiskAssessment(session.overallRisk)
        setupBEFASTestResults(session)
        setupRecommendation(session.overallRisk)

        // BEFA overall % (0..100)
        val befaOverall = ScreeningDataManager.calculateBEFASTOverallPercent(session)
        binding.tvRiskDescription.text = session.overallRisk.description
        binding.tvFastOverall.text = "BEFA overall: $befaOverall% (0..100)"

//        binding.tvSessionInfo.text = "Sesi: ${session.sessionId}\nWaktu: ${session.completedAtFormatted()}"

        // Tombol default (Finish, Retry, SaveReport, History)
        setupFinalButtons()
    }

    /** ====== MODE: SCREENING TIDAK LENGKAP (PENDING/ERROR) ====== */
    @SuppressLint("SetTextI18n")
    private fun displayIncompleteBEFAS(session: ScreeningResult, pending: List<String>) {
        setupRiskAssessment(RiskLevel.UNKNOWN)
        val pendingLabel = pending.joinToString(", ") { humanizeTestKey(it) }
        binding.tvRiskDescription.text =
            "Screening belum lengkap. Tes belum selesai: $pendingLabel.\nSilakan lanjutkan terlebih dahulu."

        setupBEFASTestResults(session)

        binding.tvRecommendation.text =
            "Beberapa tes belum selesai. Tekan \"Lanjutkan Tes\" untuk melanjutkan tanpa menghapus progres yang sudah ada."

//        binding.tvSessionInfo.text = "Sesi: ${session.sessionId}\nDimulai: ${session.timestamp}"

        // Ubah tombol
        binding.btnRetry.text = "Lanjutkan Tes"
        binding.btnRetry.setOnClickListener { navigateToFirstPending(pending) }

        binding.btnFinish.setOnClickListener { showFinishDialogForIncomplete(pending) }

        binding.btnSaveReport.isEnabled = false
        binding.btnSaveReport.alpha = 0.5f

        binding.btnViewHistory.setOnClickListener { navigateToHistory() }
    }

    /** ====== Komponen UI umum ====== */
    private fun setupRiskAssessment(riskLevel: RiskLevel) {
        binding.apply {
            tvRiskLevel.text = riskLevel.displayName
            tvRiskDescription.text = riskLevel.description

            val (backgroundColor, textColor) = when (riskLevel) {
                RiskLevel.LOW -> Pair(R.color.risk_low_bg, R.color.risk_low_text)
                RiskLevel.MEDIUM -> Pair(R.color.risk_medium_bg, R.color.risk_medium_text)
                RiskLevel.HIGH -> Pair(R.color.risk_high_bg, R.color.risk_high_text)
                RiskLevel.CRITICAL -> Pair(R.color.risk_critical_bg, R.color.risk_critical_text)
                RiskLevel.UNKNOWN -> Pair(R.color.risk_unknown_bg, R.color.risk_unknown_text)
            }

            cardRiskAssessment.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), backgroundColor)
            )
            tvRiskLevel.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            tvRiskDescription.setTextColor(ContextCompat.getColor(requireContext(), textColor))
        }
    }

    /**
     * Render 5 hasil BEFAS.
     */
    private fun setupBEFASTestResults(session: ScreeningResult) {
        // 1) Balance
        setupTestResultItemByNames(
            slot = SlotNames(
                layout = "layout_balance_result",
                name = "tv_balance_test_name",
                result = "tv_balance_test_result",
                icon = "iv_balance_test_icon"
            ),
            label = "B - Balance Test",
            testResult = session.balanceResult,
            fallbackIfMissing = null
        )

        // 2) Eyes
        setupTestResultItemByNames(
            slot = SlotNames(
                layout = "layout_eyes_result",
                name = "tv_eyes_test_name",
                result = "tv_eyes_test_result",
                icon = "iv_eyes_test_icon"
            ),
            label = "E - Eyes Test",
            testResult = session.eyesResult,
            fallbackIfMissing = SlotNames(
                layout = "layout_eyes_result",
                name = "tv_eyes_test_name",
                result = "tv_eyes_test_result",
                icon = "iv_eyes_test_icon"
            ) to "E - Eyes Test"
        )

        // 3) Face
        setupTestResultItemByNames(
            slot = SlotNames(
                layout = "layout_face_result",
                name = "tv_face_test_name",
                result = "tv_face_test_result",
                icon = "iv_face_test_icon"
            ),
            label = "F - Face Test",
            testResult = session.faceResult
        )

        // 4) Arms
        setupTestResultItemByNames(
            slot = SlotNames(
                layout = "layout_arms_result",
                name = "tv_arms_test_name",
                result = "tv_arms_test_result",
                icon = "iv_arms_test_icon"
            ),
            label = "A - Arms Test",
            testResult = session.armsResult
        )

        // 5) SPEECH
//        setupTestResultItemByNames(
//            slot = SlotNames(
//                layout = "layout_speech_result",
//                name = "tv_speech_test_name",
//                result = "tv_speech_test_result",
//                icon = "iv_speech_test_icon"
//            ),
//            label = "S - Speech Test",
//            testResult = session.speechResult,
//            fallbackIfMissing = null
//        )
    }

    // Utility: representasi id view per slot
    private data class SlotNames(
        val layout: String,
        val name: String,
        val result: String,
        val icon: String
    )

    private fun <T : View> findByName(name: String): T? {
        val id = resources.getIdentifier(name, "id", requireContext().packageName)
        if (id == 0) return null
        return binding.root.findViewById(id)
    }

    private fun setupTestResultItemByNames(
        slot: SlotNames,
        label: String,
        testResult: TestResult?,
        fallbackIfMissing: Pair<SlotNames, String>? = null
    ) {
        // coba slot utama
        val layout: View? = findByName(slot.layout)
        val nameTv: TextView? = findByName(slot.name)
        val resultTv: TextView? = findByName(slot.result)
        val iconIv: ImageView? = findByName(slot.icon)

        Log.d("ScreeningResult", "📦 Found - Layout: ${layout != null}, NameTV: ${nameTv != null}, ResultTV: ${resultTv != null}, IconIV: ${iconIv != null}")

        if (layout != null && nameTv != null && resultTv != null && iconIv != null) {
            Log.d("ScreeningResult", "✅ Rendering: $label")
            applyTestResultToViews(layout, nameTv, resultTv, iconIv, label, testResult)
            return
        } else {
            Log.w("ScreeningResult", "❌ Missing views for: $label")
        }

        // fallback
        fallbackIfMissing?.let { (fbSlot, fbLabel) ->
            Log.d("ScreeningResult", "🔄 Trying fallback for: $label")
            val fbLayout: View? = findByName(fbSlot.layout)
            val fbNameTv: TextView? = findByName(fbSlot.name)
            val fbResultTv: TextView? = findByName(fbSlot.result)
            val fbIconIv: ImageView? = findByName(fbSlot.icon)
            if (fbLayout != null && fbNameTv != null && fbResultTv != null && fbIconIv != null) {
                Log.d("ScreeningResult", "✅ Fallback successful for: $fbLabel")
                applyTestResultToViews(fbLayout, fbNameTv, fbResultTv, fbIconIv, fbLabel, testResult)
                return
            }
        }
        Log.e("ScreeningResult", "❌❌❌ FAILED to render: $label - no views found!")
    }

    @SuppressLint("SetTextI18n")
    private fun applyTestResultToViews(
        layout: View,
        nameTextView: TextView,
        resultTextView: TextView,
        iconImageView: ImageView,
        testNameLabel: String,
        testResult: TestResult?
    ) {
        nameTextView.text = testNameLabel

        fun sevStr(score: Float?): String {
            return if (score == null) "(—%)" else "(${(score.coerceIn(0f, 1f) * 100).toInt()}%)"
        }

        // ✅ FUNGSI BARU: Get color berdasarkan score
        fun getColorForScore(score: Float?): Int {
            return when {
                score == null -> R.color.risk_unknown_text // Tidak dilakukan
                score < 0.3f -> R.color.risk_low_text // Normal (0-30%)
                score < 0.6f -> R.color.risk_medium_text // Sedang (30-60%)
                else -> R.color.risk_high_text // Tinggi/Kritis (60-100%)
            }
        }

        // ✅ FUNGSI BARU: Get icon color filter berdasarkan score
        fun getColorFilterForScore(score: Float?): Int {
            return ContextCompat.getColor(requireContext(), getColorForScore(score))
        }

        // ✅ FUNGSI BARU: Get background color untuk layout berdasarkan score
        fun getBackgroundColorForScore(score: Float?): Int {
            return when {
                score == null -> R.color.risk_unknown_bg // Tidak dilakukan
                score < 0.3f -> R.color.risk_low_bg // Normal
                score < 0.6f -> R.color.risk_medium_bg // Sedang
                else -> R.color.risk_high_bg // Tinggi/Kritis
            }
        }

        when {
            testResult == null -> {
                resultTextView.text = "Tidak Dilakukan ${sevStr(null)}"
                val textColor = getColorForScore(null)
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
                iconImageView.setImageResource(R.drawable.ic_test_skipped)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.GrayLight))

                val bgColor = getBackgroundColorForScore(null)
                layout.setBackgroundColor(ContextCompat.getColor(requireContext(), bgColor))

                layout.visibility = View.VISIBLE
            }
            !testResult.isCompleted -> {
                resultTextView.text = "Belum Selesai ${sevStr(testResult.score)}"
                val textColor = getColorForScore(null)
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
                iconImageView.setImageResource(R.drawable.ic_test_incomplete)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.GrayLight))

                val bgColor = getBackgroundColorForScore(null)
                layout.setBackgroundColor(ContextCompat.getColor(requireContext(), bgColor))

                layout.visibility = View.VISIBLE
            }
            testResult.isSuccessful -> {
                resultTextView.text = "Normal ${sevStr(testResult.score)}"
                val textColor = getColorForScore(testResult.score)
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
                iconImageView.setImageResource(R.drawable.ic_test_success)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_color))

                val bgColor = getBackgroundColorForScore(null)
                layout.setBackgroundColor(ContextCompat.getColor(requireContext(), bgColor))

                layout.visibility = View.VISIBLE
            }
            else -> {
                resultTextView.text = "Abnormal ${sevStr(testResult.score)}"
                val textColor = getColorForScore(testResult.score)
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
                iconImageView.setImageResource(R.drawable.ic_test_failed)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_color))

                val bgColor = getBackgroundColorForScore(null)
                layout.setBackgroundColor(ContextCompat.getColor(requireContext(), bgColor))

                layout.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecommendation(riskLevel: RiskLevel) {
        val recommendation = when (riskLevel) {
            RiskLevel.LOW -> "Hasil screening menunjukkan kondisi normal. Lanjutkan gaya hidup sehat dan rutin check-up."
            RiskLevel.MEDIUM -> "Ada indikasi yang perlu diperhatikan. Disarankan konsultasi dengan dokter."
            RiskLevel.HIGH -> "Beberapa gejala signifikan. Segera konsultasi dengan dokter/tenaga medis."
            RiskLevel.CRITICAL -> {
                showCriticalRiskAlert()
                "⚠️ PENTING: Indikasi kritis. Segera hubungi layanan medis darurat. Anda bisa mengatakan \\\"Darurat\\\" untuk membuka mode emergency"
            }
            RiskLevel.UNKNOWN -> "Data belum lengkap. Silakan lanjutkan tes yang tertunda."
        }
        binding.tvRecommendation.text = recommendation

        if (riskLevel == RiskLevel.CRITICAL) {
            binding.tvRecommendation.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }
    }

    private fun showCriticalRiskAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle("🚨 HASIL SCREENING KRITIS")
            .setMessage("Hasil screening menunjukkan indikasi stroke yang serius.\n\n" +
                    "• Segera hubungi layanan medis darurat\n" +
                    "• Jangan mengemudi sendiri ke rumah sakit\n" +
                    "• Tetap tenang dan cari bantuan\n\n" +
                    "Anda bisa menggunakan voice command \"Darurat\" untuk membuka mode emergency.")
            .setPositiveButton("Buka Emergency") { _, _ ->
                openEmergencyFromCriticalResult()
            }
            .setNegativeButton("Saya Paham") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // ✅ BUKA EMERGENCY DARI HASIL KRITIS
    private fun openEmergencyFromCriticalResult() {
        try {
            val intent = Intent(requireContext(), EmergencyActivity::class.java).apply {
                putExtra("from_screening_result", true)
                putExtra("risk_level", "CRITICAL")
                putExtra("screening_session_id", completedSession?.sessionId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            requireActivity().finish()
        } catch (e: Exception) {
            Log.e("ScreeningResult", "Failed to open emergency from critical result", e)
            Toast.makeText(requireContext(), "Gagal membuka emergency", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayErrorResult() {
        binding.apply {
            tvRiskLevel.text = "Error"
            tvRiskDescription.text = "Terjadi kesalahan dalam memproses hasil"
            tvRecommendation.text = "Silakan coba lakukan screening ulang"
            layoutFastResults.visibility = View.GONE
        }
    }

    /** ====== Tombol & Navigasi ====== */

    private fun setupStaticClickListeners() {
        binding.btnFinish.setOnClickListener { navigateToMain() }
        binding.btnRetry.setOnClickListener { restartScreening() }
        binding.btnSaveReport.setOnClickListener { saveReport() }
        binding.btnViewHistory.setOnClickListener { navigateToHistory() }
    }

    private fun setupFinalButtons() {
        binding.btnRetry.text = getString(R.string.retry)
        binding.btnRetry.setOnClickListener { restartScreening() }
        binding.btnFinish.setOnClickListener { navigateToMain() }
        binding.btnSaveReport.isEnabled = true
        binding.btnSaveReport.alpha = 1f
    }

    private fun navigateToFirstPending(pending: List<String>) {
        val first = pending.firstOrNull() ?: run {
            findNavController().navigate(R.id.action_screeningResult_to_balancePreview)
            return
        }
        when (first.lowercase()) {
            "balance", "b" -> findNavController().navigate(R.id.action_screeningResult_to_balancePreview)
            "eyes", "e" -> findNavController().navigate(R.id.action_screeningResult_to_eyesPreview)
            "face", "f" -> findNavController().navigate(R.id.action_screeningResult_to_facePreview)
            "arms", "a" -> findNavController().navigate(R.id.action_screeningResult_to_armPreview)
//            "speech", "s" -> findNavController().navigate(R.id.action_screeningResult_to_speechPreview)
            else -> findNavController().navigate(R.id.action_screeningResult_to_balancePreview)
        }
    }

    private fun showFinishDialogForIncomplete(pending: List<String>) {
        val pendingLabel = pending.joinToString(", ") { humanizeTestKey(it) }
        AlertDialog.Builder(requireContext())
            .setTitle("Selesaikan Screening?")
            .setMessage(
                "Masih ada tes yang belum selesai: $pendingLabel.\n\n" +
                        "Pilih tindakan:"
            )
            .setPositiveButton("Finalkan (parsial)") { _, _ -> finalizePartialSession() }
            .setNeutralButton("Lanjutkan Tes") { _, _ -> navigateToFirstPending(pending) }
            .setNegativeButton("Keluar & Hapus") { _, _ ->
                ScreeningDataManager.cancelSession(requireContext())
                navigateToMain()
            }
            .show()
    }

    /**
     * ✅ FINALKAN PARSIAL - Local first approach
     */
    private fun finalizePartialSession() {
        val active = ScreeningDataManager.getCurrentSession(requireContext())
        if (active == null) {
            Toast.makeText(requireContext(), "Tidak ada sesi aktif.", Toast.LENGTH_SHORT).show()
            return
        }

        val anyCompleted = listOfNotNull(
            active.balanceResult,
            active.eyesResult,
            active.faceResult,
            active.armsResult
//            active.speechResult
        ).any { it.isCompleted }

        if (!anyCompleted) {
            AlertDialog.Builder(requireContext())
                .setTitle("Belum ada tes yang selesai")
                .setMessage("Tidak bisa menyelesaikan karena belum ada segmen yang selesai. Silakan lanjutkan tes terlebih dahulu.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // ✅ COMPLETE DI LOKAL DULU
        val locallyCompleted = ScreeningDataManager.completeSession(requireContext(), currentCity)
        if (locallyCompleted != null) {
            completedSession = locallyCompleted

            // ✅ COBA KIRIM KE FIRESTORE (background)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val success = ScreeningRepository.saveCompleteScreeningSession(locallyCompleted)
                    if (success) {
                        Log.d("ScreeningResult", "✅ Partial session synced to Firestore")
                    }
                } catch (e: Exception) {
                    Log.e("ScreeningResult", "Error syncing partial session: ${e.message}")
                }
            }

            // ✅ TAMPILKAN HASIL
            displayBEFASResults(locallyCompleted)
            Toast.makeText(requireContext(), "Screening diselesaikan sebagai hasil parsial.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Gagal menyelesaikan sesi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun restartScreening() {
        ScreeningDataManager.cancelSession(requireContext())
        try {
            findNavController().navigate(R.id.action_screeningResult_to_balancePreview)
        } catch (e: Exception) {
            requireActivity().recreate()
        }
    }

    private fun saveReport() {
        completedSession?.let {
            Toast.makeText(
                requireContext(),
                "Fitur simpan laporan akan segera tersedia",
                Toast.LENGTH_SHORT
            ).show()
        } ?: run {
            Toast.makeText(
                requireContext(),
                "Screening belum lengkap — laporan belum bisa dibuat",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToHistory() {
        // ✅ Navigasi ke Dashboard di MainActivity
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            putExtra("navigate_to", "history") // Flag untuk navigasi ke dashboard
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun humanizeTestKey(key: String): String = when (key.lowercase()) {
        "balance", "b" -> "Balance"
        "eyes", "e" -> "Eyes"
        "face", "f" -> "Face"
        "arms", "a" -> "Arms"
//        "speech", "s" -> "Speech"
        else -> key
    }
}
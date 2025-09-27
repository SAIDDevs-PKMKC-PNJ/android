package com.pkm.said.screening

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.pkm.said.MainActivity
import com.pkm.said.R
import com.pkm.said.databinding.FragmentScreeningResultBinding

class ScreeningResultFragment : Fragment() {

    private var _binding: FragmentScreeningResultBinding? = null
    private val binding get() = _binding!!
    private var completedSession: ScreeningResult? = null

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
        savedInstanceState?.getString("completed_session_json")?.let { json ->
            completedSession = Gson().fromJson(json, ScreeningResult::class.java)
        }
        setupResult()
        setupStaticClickListeners()
    }

    private fun setupResult() {
        completedSession?.let {
            displayFASTResults(it)
            return
        }

        val active = ScreeningDataManager.getCurrentSession(requireContext())
        if (active != null) {
            val pending = ScreeningDataManager.getPendingTests(requireContext())
            if (pending.isNotEmpty()) {
                displayIncompleteFAST(active, pending)
                return
            }
            completedSession = ScreeningDataManager.completeSession(requireContext())
            completedSession?.let { displayFASTResults(it) } ?: displayErrorResult()
            return
        }

        // Tidak ada sesi aktif → coba ambil sesi terakhir dari history (mis. setelah rotasi)
        ScreeningDataManager.getLastCompletedSession(requireContext())?.let {
            completedSession = it
            displayFASTResults(it)
        } ?: displayErrorResult()
    }


    /** ====== MODE: SCREENING LENGKAP (FINAL) ====== */
    private fun displayFASTResults(session: ScreeningResult) {
        setupRiskAssessment(session.overallRisk)
        setupFASTTestResults(session)
        setupRecommendation(session.overallRisk)

        // FAST overall % (0..80)
        val fastOverall = ScreeningDataManager.calculateFASTOverallPercent(session)
        binding.tvRiskDescription.text = session.overallRisk.description
        binding.tvFastOverall.text = "FAST overall: $fastOverall% (maks 80%)"

        binding.tvSessionInfo.text = "Sesi: ${session.sessionId}\nWaktu: ${session.completedAt}"

        // Tombol-tombol default (Finish, Retry = mulai ulang, SaveReport, History)
        setupFinalButtons()
    }

    /** ====== MODE: SCREENING TIDAK LENGKAP (PENDING/ERROR) ====== */
    private fun displayIncompleteFAST(session: ScreeningResult, pending: List<String>) {
        // Tampilkan status UNKNOWN + banner “belum lengkap”
        setupRiskAssessment(RiskLevel.UNKNOWN)
        val pendingLabel = pending.joinToString(", ") { humanizeTestKey(it) }
        binding.tvRiskDescription.text =
            "Screening belum lengkap. Tes belum selesai: $pendingLabel.\n" +
                    "Silakan lanjutkan terlebih dahulu."

        // Tampilkan hasil per tes yang sudah ada (Normal/Abnormal/Tidak Dilakukan)
        setupFASTTestResults(session)

        // Rekomendasi khusus
        binding.tvRecommendation.text =
            "Beberapa tes belum selesai. Tekan \"Lanjutkan Tes\" untuk melanjutkan " +
                    "tanpa menghapus progres yang sudah ada."

        // Info sesi aktif
        binding.tvSessionInfo.text = "Sesi: ${session.sessionId}\nDimulai: ${session.timestamp}"

        // **Ubah tombol:**
        // - btnRetry → “Lanjutkan Tes” (tanpa cancel session)
        // - btnFinish → konfirmasi keluar (cancel session)
        // - Nonaktifkan Save Report (belum final)
        binding.btnRetry.text = "Lanjutkan Tes"
        binding.btnRetry.setOnClickListener { navigateToFirstPending(pending) }

        binding.btnFinish.setOnClickListener { showFinishDialogForIncomplete(pending)  }

        binding.btnSaveReport.isEnabled = false
        binding.btnSaveReport.alpha = 0.5f

        // History tetap boleh dibuka
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

    private fun setupFASTTestResults(session: ScreeningResult) {
        setupTestResultItem(
            binding.layoutFaceResult,
            binding.tvFaceTestName,
            binding.tvFaceTestResult,
            binding.ivFaceTestIcon,
            "F - Face Test",
            session.faceResult
        )
        setupTestResultItem(
            binding.layoutArmsResult,
            binding.tvArmsTestName,
            binding.tvArmsTestResult,
            binding.ivArmsTestIcon,
            "A - Arms Test",
            session.armsResult
        )
        setupTestResultItem(
            binding.layoutSpeechResult,
            binding.tvSpeechTestName,
            binding.tvSpeechTestResult,
            binding.ivSpeechTestIcon,
            "S - Speech Test",
            session.speechResult
        )
    }

    private fun setupTestResultItem(
        layout: View,
        nameTextView: android.widget.TextView,
        resultTextView: android.widget.TextView,
        iconImageView: android.widget.ImageView,
        testName: String,
        testResult: TestResult?
    ) {
        nameTextView.text = testName

        fun sevStr(score: Float?): String {
            return if (score == null) "(—%)" else "(${(score.coerceIn(0f,1f) * 100).toInt()}%)"
        }

        when {
            testResult == null -> {
                resultTextView.text = "Tidak Dilakukan ${sevStr(null)}"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.GrayLight))
                iconImageView.setImageResource(R.drawable.ic_test_skipped)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.GrayLight))
            }
            !testResult.isCompleted -> {
                resultTextView.text = "Belum Selesai ${sevStr(testResult.score)}"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.GrayLight))
                iconImageView.setImageResource(R.drawable.ic_test_incomplete)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.GrayLight))
            }
            testResult.isSuccessful -> {
                resultTextView.text = "Normal ${sevStr(testResult.score)}"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_color))
                iconImageView.setImageResource(R.drawable.ic_test_success)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_color))
            }
            else -> {
                resultTextView.text = "Abnormal ${sevStr(testResult.score)}"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_color))
                iconImageView.setImageResource(R.drawable.ic_test_failed)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_color))
            }
        }
    }

    private fun setupRecommendation(riskLevel: RiskLevel) {
        val recommendation = when (riskLevel) {
            RiskLevel.LOW -> "Hasil screening menunjukkan kondisi normal. Lanjutkan gaya hidup sehat dan rutin check-up."
            RiskLevel.MEDIUM -> "Ada indikasi yang perlu diperhatikan. Disarankan konsultasi dengan dokter."
            RiskLevel.HIGH -> "Beberapa gejala signifikan. Segera konsultasi dengan dokter/tenaga medis."
            RiskLevel.CRITICAL -> "⚠️ PENTING: Indikasi kritis. Segera hubungi layanan medis darurat."
            RiskLevel.UNKNOWN -> "Data belum lengkap. Silakan lanjutkan tes yang tertunda."
        }
        binding.tvRecommendation.text = recommendation

        if (riskLevel == RiskLevel.CRITICAL) {
            binding.cardRecommendation.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.error_light)
            )
            binding.tvRecommendation.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }
    }

    private fun displayErrorResult() {
        binding.apply {
            tvRiskLevel.text = "Error"
            tvRiskDescription.text = "Terjadi kesalahan dalam memproses hasil"
            tvRecommendation.text = "Silakan coba lakukan screening ulang"
            layoutFastResults.visibility = View.GONE
        }
    }

    /** ====== Tombol & Navigasi ====== */

    // Listener statis (tetap), akan dioverride dinamis pada mode incomplete jika perlu
    private fun setupStaticClickListeners() {
        binding.btnFinish.setOnClickListener { navigateToMain() }
        binding.btnRetry.setOnClickListener { restartScreening() }
        binding.btnSaveReport.setOnClickListener { saveReport() }
        binding.btnViewHistory.setOnClickListener { navigateToHistory() }
    }

    // Setelah hasil final (complete), biarkan behavior default
    private fun setupFinalButtons() {
        binding.btnRetry.text = getString(R.string.retry) // pastikan ada string atau biarkan default
        binding.btnRetry.setOnClickListener { restartScreening() }
        binding.btnFinish.setOnClickListener { navigateToMain() }
        binding.btnSaveReport.isEnabled = true
        binding.btnSaveReport.alpha = 1f
    }

    private fun navigateToFirstPending(pending: List<String>) {
        val first = pending.firstOrNull() ?: run {
            // fallback: kembali ke tes awal
            findNavController().navigate(R.id.action_screeningResult_to_facePreview)
            return
        }
        when (first) {
            "face_test", "face" -> {
                findNavController().navigate(R.id.action_screeningResult_to_facePreview)
            }
            "arms_test", "arms" -> findNavController().navigate(R.id.action_screeningResult_to_armPreview)
            "speech_test", "speech" -> findNavController().navigate(R.id.action_screeningResult_to_speechPreview)
            else -> {
                // fallback aman: mulai dari Face
                findNavController().navigate(R.id.action_screeningResult_to_facePreview)
            }
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

    private fun finalizePartialSession() {
        val active = ScreeningDataManager.getCurrentSession(requireContext())
        if (active == null) {
            android.widget.Toast.makeText(requireContext(), "Tidak ada sesi aktif.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Wajib minimal SATU segmen selesai
        val anyCompleted = listOfNotNull(active.faceResult, active.armsResult, active.speechResult)
            .any { it.isCompleted }

        if (!anyCompleted) {
            AlertDialog.Builder(requireContext())
                .setTitle("Belum ada tes yang selesai")
                .setMessage("Tidak bisa menyelesaikan karena belum ada segmen FAST yang selesai. Silakan lanjutkan tes terlebih dahulu.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Finalkan sesi dengan data parsial yang ada
        val finalSession = ScreeningDataManager.completeSession(requireContext())
        if (finalSession != null) {
            completedSession = finalSession
            // Tampilkan mode final (risk level, FAST overall %, tombol SaveReport aktif, dst.)
            displayFASTResults(finalSession)
            android.widget.Toast.makeText(requireContext(), "Screening diselesaikan sebagai hasil parsial.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(requireContext(), "Gagal menyelesaikan sesi.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun restartScreening() {
        // Restart penuh (hapus sesi & mulai dari awal)
        ScreeningDataManager.cancelSession(requireContext())
        try {
            findNavController().navigate(R.id.action_screeningResult_to_facePreview)
        } catch (e: Exception) {
            requireActivity().recreate()
        }
    }

    private fun saveReport() {
        completedSession?.let {
            android.widget.Toast.makeText(
                requireContext(),
                "Fitur simpan laporan akan segera tersedia",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } ?: run {
            android.widget.Toast.makeText(
                requireContext(),
                "Screening belum lengkap — laporan belum bisa dibuat",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToHistory() {
        android.widget.Toast.makeText(
            requireContext(),
            "Fitur akan segera tersedia",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun humanizeTestKey(key: String): String = when (key.lowercase()) {
        "face_test", "face" -> "Face"
        "arms_test", "arms", "arm_test", "arm", "befast_arm" -> "Arms"
        "speech_test", "speech" -> "Speech"
        else -> key
    }
}

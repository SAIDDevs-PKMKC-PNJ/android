package com.pkm.said.screening

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentScreeningResultBinding
import com.pkm.said.MainActivity

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupResult()
        setupClickListeners()
    }

    private fun setupResult() {
        // Complete the current session dan get final result
        completedSession = ScreeningDataManager.completeSession(requireContext())

        completedSession?.let { session ->
            displayFASTResults(session)
        } ?: run {
            // Fallback jika tidak ada session
            displayErrorResult()
        }
    }

    private fun displayFASTResults(session: ScreeningResult) {
        // Overall Risk Assessment
        setupRiskAssessment(session.overallRisk)

        // FAST Test Results
        setupFASTTestResults(session)

        // Recommendation
        setupRecommendation(session.overallRisk)

        // Session Info
        binding.tvSessionInfo.text = "Sesi: ${session.sessionId}\nWaktu: ${session.completedAt}"
    }

    private fun setupRiskAssessment(riskLevel: RiskLevel) {
        binding.apply {
            tvRiskLevel.text = riskLevel.displayName
            tvRiskDescription.text = riskLevel.description

            // Set color based on risk level
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
            tvRiskLevel.setTextColor(
                ContextCompat.getColor(requireContext(), textColor)
            )
            tvRiskDescription.setTextColor(
                ContextCompat.getColor(requireContext(), textColor)
            )
        }
    }

    private fun setupFASTTestResults(session: ScreeningResult) {
        // Face Test Result
        setupTestResultItem(
            binding.layoutFaceResult,
            binding.tvFaceTestName,
            binding.tvFaceTestResult,
            binding.ivFaceTestIcon,
            "F - Face Test",
            session.faceResult
        )

        // Arms Test Result
        setupTestResultItem(
            binding.layoutArmsResult,
            binding.tvArmsTestName,
            binding.tvArmsTestResult,
            binding.ivArmsTestIcon,
            "A - Arms Test",
            session.armsResult
        )

        // Speech Test Result
        setupTestResultItem(
            binding.layoutSpeechResult,
            binding.tvSpeechTestName,
            binding.tvSpeechTestResult,
            binding.ivSpeechTestIcon,
            "S - Speech Test",
            session.speechResult
        )

        // Time Test Result
//        setupTestResultItem(
//            binding.layoutTimeResult,
//            binding.tvTimeTestName,
//            binding.tvTimeTestResult,
//            binding.ivTimeTestIcon,
//            "T - Time Test",
//            session.timeResult
//        )
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

        when {
            testResult == null -> {
                resultTextView.text = "Tidak Dilakukan"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.GrayLight))
                iconImageView.setImageResource(R.drawable.ic_test_skipped)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.GrayLight))
            }
            !testResult.isCompleted -> {
                resultTextView.text = "Tidak Selesai"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_color))
                iconImageView.setImageResource(R.drawable.ic_test_incomplete)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_color))
            }
            testResult.isSuccessful -> {
                resultTextView.text = "Normal (${(testResult.score * 100).toInt()}%)"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_color))
                iconImageView.setImageResource(R.drawable.ic_test_success)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_color))
            }
            else -> {
                resultTextView.text = "Abnormal (${(testResult.score * 100).toInt()}%)"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_color))
                iconImageView.setImageResource(R.drawable.ic_test_failed)
                iconImageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_color))
            }
        }
    }

    private fun setupRecommendation(riskLevel: RiskLevel) {
        val recommendation = when (riskLevel) {
            RiskLevel.LOW -> {
                "Hasil screening menunjukkan kondisi normal. Lanjutkan gaya hidup sehat dan rutin check-up berkala."
            }
            RiskLevel.MEDIUM -> {
                "Terdapat beberapa indikasi yang perlu diperhatikan. Disarankan untuk konsultasi dengan dokter."
            }
            RiskLevel.HIGH -> {
                "Hasil screening menunjukkan beberapa gejala signifikan. Segera konsultasi dengan dokter atau tenaga medis."
            }
            RiskLevel.CRITICAL -> {
                "⚠️ PENTING: Hasil screening menunjukkan indikasi kritis. Segera hubungi layanan medis darurat atau rumah sakit terdekat."
            }
            RiskLevel.UNKNOWN -> {
                "Data tidak cukup untuk memberikan penilaian. Disarankan untuk mengulangi screening dengan lengkap."
            }
        }

        binding.tvRecommendation.text = recommendation

        // Set emergency style for critical risk
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

            // Hide test results
            layoutFastResults.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnFinish.setOnClickListener {
            // Kembali ke main activity
            navigateToMain()
        }

        binding.btnRetry.setOnClickListener {
            // Restart screening
            restartScreening()
        }

        binding.btnSaveReport.setOnClickListener {
            // Save or share report
            saveReport()
        }

        binding.btnViewHistory.setOnClickListener {
            // Navigate to history
            navigateToHistory()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun restartScreening() {
        // Clear any remaining session
        ScreeningDataManager.cancelSession(requireContext())

        // Navigate back to first test
        try {
            findNavController().navigate(R.id.action_screeningResult_to_facePreview)
        } catch (e: Exception) {
            // Fallback: restart activity
            requireActivity().recreate()
        }
    }

    private fun saveReport() {
        completedSession?.let { session ->
            // TODO: Implement report saving/sharing
            // For now, show a simple message
            android.widget.Toast.makeText(
                requireContext(),
                "Fitur simpan laporan akan segera tersedia",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToHistory() {
        // TODO: Navigate to history fragment/activity
        android.widget.Toast.makeText(
            requireContext(),
            "Fitur riwayat akan segera tersedia",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
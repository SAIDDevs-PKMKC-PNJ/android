package com.pkm.said

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkm.said.databinding.FragmentScreeningHistoryBinding
import com.pkm.said.screening.RiskLevel
import com.pkm.said.screening.ScreeningDataManager
import com.pkm.said.screening.ScreeningRepository
import com.pkm.said.screening.ScreeningResult
import com.pkm.said.adapter.ScreeningHistoryAdapter
import com.pkm.said.screening.completedAtFormatted
import com.pkm.said.util.AuthManager
import kotlinx.coroutines.launch

class ScreeningHistoryFragment : Fragment() {

    private var _binding: FragmentScreeningHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScreeningHistoryAdapter
    private var fullHistory: List<ScreeningResult> = emptyList()
    private var currentFilter: RiskLevel? = null  // null = semua

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreeningHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupRecycler()
        setupChips()
        loadData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        val title = "Riwayat Screening"
        val span = SpannableString(title).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, 0)
        }
        binding.tvTitle.text = span
    }

    private fun setupRecycler() {
        adapter = ScreeningHistoryAdapter(
            onDetail = { result ->
                val bundle = Bundle().apply { putString("sessionId", result.sessionId) }
                findNavController().navigate(R.id.navigation_historyDetail, bundle)
            },
            onShare = { result -> shareResult(result) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter
    }

    private fun setupChips() {
        // All
        binding.chipAll.setOnClickListener { selectFilter(null) }
        // High (termasuk CRITICAL)
        binding.chipHigh.setOnClickListener { selectFilter(RiskLevel.HIGH) }
        binding.chipMedium.setOnClickListener { selectFilter(RiskLevel.MEDIUM) }
        binding.chipLow.setOnClickListener { selectFilter(RiskLevel.LOW) }

        binding.chipAll.isChecked = true
    }

    private fun selectFilter(risk: RiskLevel?) {
        currentFilter = risk
        applyFilter()
        updateChipSelection()
    }

    private fun updateChipSelection() {
        binding.chipAll.isChecked = currentFilter == null
        binding.chipHigh.isChecked = currentFilter == RiskLevel.HIGH
        binding.chipMedium.isChecked = currentFilter == RiskLevel.MEDIUM
        binding.chipLow.isChecked = currentFilter == RiskLevel.LOW
    }

    private fun loadData() {
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // NEW: Langsung ambil dari Firestore (data permanen)
                fullHistory = ScreeningRepository.getScreeningHistory(limit = 50)

                // NEW: Jika kosong, coba fallback ke data lokal yang completed
                if (fullHistory.isEmpty()) {
                    fullHistory = ScreeningDataManager.getScreeningHistory(requireContext())
                        .filter { it.isCompleted }
                        .take(50)
                }

            } catch (e: Exception) {
                // NEW: Error handling yang lebih baik
                Log.e("ScreeningHistory", "Gagal memuat riwayat: ${e.message}")

                // Fallback ke data lokal saja
                fullHistory = ScreeningDataManager.getScreeningHistory(requireContext())
                    .filter { it.isCompleted }
                    .take(50)

                // Tampilkan pesan error
                if (fullHistory.isEmpty()) {
                    showErrorState("Gagal memuat data")
                } else {
                    showWarningState("Menggunakan data lokal")
                }
            } finally {
                setLoading(false)
                applyFilter()
            }
        }
    }
    private fun setLoading(loading: Boolean) {
        binding.loadingOverlay.isVisible = loading
        binding.recyclerHistory.isVisible = !loading && fullHistory.isNotEmpty()
        binding.layoutEmpty.isVisible = !loading && fullHistory.isEmpty()
        binding.layoutError.isVisible = false
        binding.layoutWarning.isVisible = false
    }

    private fun showErrorState(message: String) {
        binding.layoutError.isVisible = true
        binding.tvErrorText.text = message
        binding.recyclerHistory.isVisible = false
        binding.layoutEmpty.isVisible = false
    }

    private fun showWarningState(message: String) {
        binding.layoutWarning.isVisible = true
        binding.tvWarningText.text = message
        binding.btnRetry.setOnClickListener { loadData() }
    }

    private fun applyFilter() {
        val list = when (currentFilter) {
            null -> fullHistory
            RiskLevel.HIGH -> fullHistory.filter {
                it.overallRisk == RiskLevel.HIGH || it.overallRisk == RiskLevel.CRITICAL
            }
            RiskLevel.MEDIUM -> fullHistory.filter { it.overallRisk == RiskLevel.MEDIUM }
            RiskLevel.LOW -> fullHistory.filter { it.overallRisk == RiskLevel.LOW }
            else -> fullHistory.filter { it.overallRisk == currentFilter }
        }
        adapter.submitList(list)
        binding.tvCount.text = getString(R.string.session, list.size)
        binding.layoutEmpty.isVisible = list.isEmpty()
        binding.recyclerHistory.isVisible = list.isNotEmpty()
        if (list.isNotEmpty()) {
            binding.layoutError.isVisible = false
            binding.layoutWarning.isVisible = false
        }
    }

    private fun shareResult(result: ScreeningResult) {
        val percent = ScreeningDataManager.calculateBEFASTOverallPercent(result)
        val whenText = result.completedAtFormatted()
        val shareText = """
            Hasil FAST Screening:
            - Tanggal: $whenText
            - Risiko: ${result.overallRisk.displayName}
            - Perkiraan: ${percent}%
            #FAST #StrokeAwareness
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Bagikan hasil screening"))
    }

    override fun onResume() {
        super.onResume()
        loadData()
        if (!AuthManager.ensureUserLoggedIn(requireActivity())) return
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

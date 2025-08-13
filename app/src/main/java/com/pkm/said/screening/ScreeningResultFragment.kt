package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pkm.said.databinding.FragmentScreeningResultBinding

class ScreeningResultFragment : Fragment() {

    private var _binding: FragmentScreeningResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScreeningResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showTestResults()

        binding.btnConfirm.setOnClickListener {
            requireActivity().finish() // langsung tutup, kembali ke home
        }
    }

    private fun showTestResults() {
        val testResults = ScreeningDataManager.getAllResults(requireContext())
        val resultText = buildString {
            append("Hasil Screening:\n\n")
            testResults.forEach { result ->
                append("${result.testName.capitalize()}: ")
                append(if (result.isSuccessful) "Berhasil\n" else "Gagal\n")
            }
        }
        binding.tvResult.text = resultText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
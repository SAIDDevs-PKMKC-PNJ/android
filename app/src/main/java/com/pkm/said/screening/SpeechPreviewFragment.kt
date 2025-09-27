package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.pkm.said.R
import com.pkm.said.databinding.FragmentSpeechPreviewBinding

class SpeechPreviewFragment : Fragment() {
    private var _binding: FragmentSpeechPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeechPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGifPreview()
        setupClickListeners()
    }

    private fun setupGifPreview() {
        // Load GIF pakai Glide
        try {
            Glide.with(this)
                .asGif()
                .load(R.raw.speech_test_preview) // ganti dengan nama file gif di drawable/raw
                .into(binding.gifPreview)
        } catch (e: Exception) {
            binding.gifPreview.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnStartTest.setOnClickListener {
            // Navigate to actual speech test
            findNavController().navigate(
                R.id.action_speechPreview_to_speechTest
            )
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
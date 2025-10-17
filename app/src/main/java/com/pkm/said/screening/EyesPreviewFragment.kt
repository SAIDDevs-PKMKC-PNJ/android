package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.pkm.said.R
import com.pkm.said.databinding.FragmentEyesPreviewBinding

class EyesPreviewFragment : Fragment() {
    private var _binding: FragmentEyesPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEyesPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupGifPreview()
        setupClickListeners()
    }

//    private fun setupGifPreview() {
//        // Load GIF pakai Glide (placeholder bila asset belum ada)
//        try {
//            Glide.with(this)
//                .asGif()
//                .load(R.raw.eyes_test_preview) // ganti dengan nama file gif di /res/raw
//                .into(binding.gifPreview)
//        } catch (_: Exception) {
//            binding.gifPreview.visibility = View.GONE
//        }
//    }

    private fun setupClickListeners() {
        binding.btnStartTest.setOnClickListener {
            findNavController().navigate(
                R.id.action_eyesPreview_to_eyesTest
            )
        }
//        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnBack.setOnClickListener {
            requireActivity().finish()
            // Anda juga bisa memanggil fungsi di Activity induk untuk memicu dialog keluar
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentArmPreviewBinding

class ArmPreviewFragment : Fragment() {
    private var _binding: FragmentArmPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArmPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        setupViews()
        setupClickListeners()
    }

//    private fun setupViews() {
//        // Setup lottie animation untuk arms test
//        binding.lottiePreview.apply {
//            try {
//                setAnimation("arms_test_preview.json")
//                repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
//                playAnimation()
//            } catch (e: Exception) {
//                // Jika animasi tidak ada, sembunyikan lottie
//                visibility = View.GONE
//            }
//        }
//
//        // Content sudah di-set di layout XML, tapi bisa di-customize lagi jika perlu
//        // Test number: "2 / 4"
//        // FAST Letter: "A"
//        // Test Title: "Arms Test"
//        // Test Subtitle: "Tes Kekuatan Lengan"
//        // Instructions: sudah lengkap di XML
//    }

    private fun setupClickListeners() {
        binding.btnStartTest.setOnClickListener {
            // Navigate to actual arms test
            findNavController().navigate(
                R.id.action_armsPreview_to_armsTest
            )
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume animation jika ada
        try {
            binding.lottiePreview.resumeAnimation()
        } catch (e: Exception) {
            // Handle jika lottie error
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause animation untuk save memory
        try {
            binding.lottiePreview.pauseAnimation()
        } catch (e: Exception) {
            // Handle jika lottie error
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            binding.lottiePreview.apply {
                cancelAnimation()
                clearAnimation()
            }
        } catch (e: Exception) {
            // Handle cleanup error
        }
        _binding = null
    }
}
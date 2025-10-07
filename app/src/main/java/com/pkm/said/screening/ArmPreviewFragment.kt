package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
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

//        setupGifPreview()
        setupClickListeners()
    }

//    private fun setupGifPreview() {
//        // Load GIF pakai Glide
//        try {
//            Glide.with(this)
//                .asGif()
//                .load(R.drawable.arm_test_preview) // ganti dengan nama file gif di drawable/raw
//                .into(binding.gifPreview)
//        } catch (e: Exception) {
//            binding.gifPreview.visibility = View.GONE
//        }
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

//    override fun onResume() {
//        super.onResume()
//        // Resume animation jika ada
//        try {
//            binding.lottiePreview.resumeAnimation()
//        } catch (e: Exception) {
//            // Handle jika lottie error
//        }
//    }

//    override fun onPause() {
//        super.onPause()
//        // Pause animation untuk save memory
//        try {
//            binding.lottiePreview.pauseAnimation()
//        } catch (e: Exception) {
//            // Handle jika lottie error
//        }
//    }

    override fun onDestroyView() {
        super.onDestroyView()
//        try {
//            binding.lottiePreview.apply {
//                cancelAnimation()
//                clearAnimation()
//            }
//        } catch (e: Exception) {
//            // Handle cleanup error
//        }
        _binding = null
    }
}
package com.pkm.said.screening

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkm.said.R
import com.pkm.said.databinding.FragmentFacePreviewBinding

class FacePreviewFragment : Fragment() {
    private var _binding: FragmentFacePreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFacePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        setupViews()
        setupClickListeners()
    }

//    private fun setupViews() {
//        // Setup lottie animation
//        binding.lottiePreview.apply {
//            setAnimation("face_test_preview.json")
//            playAnimation()
//        }
//    }

    private fun setupClickListeners() {
        binding.btnStartTest.setOnClickListener {
            // Navigate to actual face test
            findNavController().navigate(
                R.id.action_facePreview_to_faceTest
            )
        }

//        binding.btnSkipTest.setOnClickListener {
//            // Navigate to arms preview
//            findNavController().navigate(
//                R.id.action_facePreview_to_armsPreview
//            )
//        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
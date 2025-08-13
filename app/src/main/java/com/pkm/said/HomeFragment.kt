package com.pkm.said

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.pkm.said.screening.ScreeningActivity

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("HomeFragment", "=== HOME FRAGMENT DEBUG START ===")
        Log.d("HomeFragment", "Current Date: 2025-07-29 13:03:39")
        Log.d("HomeFragment", "Current User: itsLuxra")

        try {
            Log.d("HomeFragment", "Inflating fragment_home layout...")
            val view = inflater.inflate(R.layout.fragment_home, container, false)
            Log.d("HomeFragment", "✅ Layout inflated successfully")
            return view
        } catch (e: Exception) {
            Log.e("HomeFragment", "❌ Error inflating layout", e)
            e.printStackTrace()
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("HomeFragment", "🔧 Setting up view components...")

        try {
            // ✅ Fix: Gunakan CardView, bukan Button
            Log.d("HomeFragment", "Looking for buttonScan CardView...")
            val buttonScanCard: CardView = view.findViewById(R.id.buttonScan)
            Log.d("HomeFragment", "✅ buttonScan CardView found: ${buttonScanCard != null}")

            if (buttonScanCard != null) {
                Log.d("HomeFragment", "CardView isClickable: ${buttonScanCard.isClickable}")
                Log.d("HomeFragment", "CardView isEnabled: ${buttonScanCard.isEnabled}")
            }

            // ✅ Set click listener pada CardView
            buttonScanCard.setOnClickListener {
                Log.d("HomeFragment", "🔘 Scan button clicked!")
                Log.d("HomeFragment", "Current Date: 2025-07-29 13:03:39")
                Log.d("HomeFragment", "Current User: itsLuxra")

                startScreeningProcess()

                // TODO: Implement scan functionality
                // findNavController().navigate(R.id.action_home_to_scan)
            }

            Log.d("HomeFragment", "✅ CardView click listener set successfully")
            Log.d("HomeFragment", "✅ HomeFragment setup completed successfully")

        } catch (e: Exception) {
            Log.e("HomeFragment", "❌ Error setting up view components", e)
            Log.e("HomeFragment", "Error type: ${e.javaClass.simpleName}")
            Log.e("HomeFragment", "Error message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startScreeningProcess() {
        try {
            Log.d("HomeFragment", "🚀 Launching dedicated screening activity...")
            Log.d("HomeFragment", "User: itsLuxra starting screening at 2025-07-31 14:52:26")

            // Launch dedicated screening activity
            ScreeningActivity.start(requireContext(), "itsLuxra")

            Log.d("HomeFragment", "✅ Screening activity launched")
        } catch (e: Exception) {
            Log.e("HomeFragment", "❌ Error starting screening process", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "🗑️ HomeFragment view destroyed")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HomeFragment", "=== HOME FRAGMENT DEBUG END ===")
    }
}